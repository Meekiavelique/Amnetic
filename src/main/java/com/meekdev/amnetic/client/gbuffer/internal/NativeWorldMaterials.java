package com.meekdev.amnetic.client.gbuffer.internal;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.*;
import net.minecraft.resources.Identifier;
import java.util.*;


public final class NativeWorldMaterials {
    public static boolean world;
    private static final Set<CompiledRenderPipeline> SODIUM=Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean sodiumBound;
    public static boolean sodiumReady(){return sodiumBound;}
    private static final Map<CompiledRenderPipeline,CompiledRenderPipeline> VARIANTS=Collections.synchronizedMap(new IdentityHashMap<>());
    private NativeWorldMaterials() {}
    public static boolean eligible(RenderPipeline p) {
        return !p.getLocation().getNamespace().equals("amnetic") && p.getColorTargetStates().size()==1
                && (p.getLocation().getNamespace().equals("minecraft") || p.getLocation().getNamespace().equals("sodium"));
    }
    public static void put(CompiledRenderPipeline original,CompiledRenderPipeline variant,boolean sodium){VARIANTS.put(original,variant);if(sodium)SODIUM.add(original);}
    public static CompiledRenderPipeline variant(CompiledRenderPipeline original){if(SODIUM.contains(original))sodiumBound=true;return VARIANTS.getOrDefault(original,original);}
    public static void release(CompiledRenderPipeline original){SODIUM.remove(original);if(SODIUM.isEmpty())sodiumBound=false;var variant=VARIANTS.remove(original);if(variant!=null)variant.close();}
    public static boolean captures(RenderPipeline p) {
        return p.getDepthStencilState()!=null && p.getDepthStencilState().writeDepth()
                && p.getColorTargetStates().getFirst().blendFunction().isEmpty();
    }
    public static RenderPipeline definition(RenderPipeline p) {
        var colors=new ColorTargetState[ColorTargetState.MAX_COLOR_TARGETS];colors[0]=p.getColorTargetStates().getFirst();
        var formats=new GpuFormat[]{GpuFormat.RGBA16_FLOAT,GpuFormat.RGBA8_UNORM,GpuFormat.RGBA16_FLOAT,GpuFormat.RGBA8_UNORM};
        for(int i=1;i<5;i++)colors[i]=new ColorTargetState(Optional.empty(),formats[i-1],captures(p)&&(i==2||i==4)?15:0);
        var snippet=new RenderPipeline.Snippet(p.getShaders(),Optional.of(p.getShaderDefines()),Optional.of(p.getBindGroupLayouts()),
                colors,5,Optional.ofNullable(p.getDepthStencilState()),Optional.of(p.getPolygonMode()),Optional.of(p.isCull()),
                p.getVertexFormatBindings().toArray(com.mojang.renderpearl.api.vertex.VertexFormat[]::new),Optional.of(p.getPrimitiveTopology()),p.pushConstantSize());
        return RenderPipeline.builder(snippet).withShaderDefine("AMNETIC_NATIVE_MATERIAL")
                .withLocation(Identifier.fromNamespaceAndPath("amnetic","world_material/"+p.getLocation().getNamespace()+"/"+p.getLocation().getPath())).build();
    }
    public static ShaderSource source(ShaderSource original,boolean capture) {
        return new ShaderSource() {
            public String getShader(Identifier id,ShaderType type) {
                String source=original.getShader(id,type);
                if(!capture||source==null)return source;
                if(id.getNamespace().equals("sodium") && id.getPath().equals("blocks/block_layer_opaque")) {
                    if(type==ShaderType.VERTEX) {
                        source=source.replace("void main() {","layout(location=8) out vec4 AmneticTint;\nlayout(location=9) out vec2 AmneticLight;\nvoid main() {");
                        source=source.replace("v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);",
                            "AmneticTint=_vert_color;\nAmneticLight=clamp((_vert_tex_light_coord*256.0-8.0)/16.0,0.0,15.0);\n"
                            +"v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);");
                    } else if(type==ShaderType.FRAGMENT) {
                        source=source.replace("void main() {","layout(location=8) in vec4 AmneticTint;\nlayout(location=9) in vec2 AmneticLight;\n"
                            +"layout(location=2) out vec4 AmneticMaterial;\nlayout(location=4) out vec4 AmneticAlbedo;\nvoid main() {");
                        source=source.replace("color *= v_Color;","AmneticAlbedo=vec4(color.rgb*AmneticTint.rgb,1.0);\n"
                            +"AmneticMaterial=vec4(.9,0,0,(floor(AmneticLight.x+.5)*16.0+floor(AmneticLight.y+.5))/255.0);\ncolor *= v_Color;");
                    }
                    return source;
                }
                boolean terrain=id.getPath().equals("core/terrain"),entity=id.getPath().equals("core/entity");
                if(type==ShaderType.VERTEX && terrain) {
                    source=source.replace("void main() {","layout(location=8) out vec4 AmneticTint;\nlayout(location=9) out vec2 AmneticLight;\nvoid main() {\nAmneticTint=Color;AmneticLight=vec2(UV2)/16.0;");
                }
                if(type!=ShaderType.FRAGMENT)return source;
                String declarations="layout(location=2) out vec4 AmneticMaterial;\nlayout(location=4) out vec4 AmneticAlbedo;\n";
                if(terrain) {
                    declarations+="layout(location=8) in vec4 AmneticTint;\nlayout(location=9) in vec2 AmneticLight;\n";
                    source=source.replace(") * vertexColor;",
                        ");\nAmneticAlbedo=vec4(color.rgb * AmneticTint.rgb,1);\nAmneticMaterial=vec4(.9,0,0,(floor(AmneticLight.x+.5)*16.0+floor(AmneticLight.y+.5))/255.0);\ncolor *= vertexColor;");
                } else if(entity) {
                    source=source.replace("color *= faceVertexColor * ColorModulator;","color *= faceVertexColor * ColorModulator;\nAmneticAlbedo=vec4(color.rgb,1);AmneticMaterial=vec4(.85,0,0,0);");
                } else {
                    source=source.replace("void main() {","void main() {\nAmneticAlbedo=vec4(0);AmneticMaterial=vec4(0);");
                }
                return source.replace("void main() {",declarations+"void main() {");
            }
            public CachedIncludeSource getInclude(Identifier id){return original.getInclude(id);}
            public void close(){}
        };
    }
}
