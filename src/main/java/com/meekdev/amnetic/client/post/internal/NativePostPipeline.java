package com.meekdev.amnetic.client.post.internal;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.framebuffer.internal.NativeFramebuffer;
import com.meekdev.amnetic.client.post.UniformValue;
import com.meekdev.amnetic.client.render.NativeScreenProgram;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import java.util.regex.Matcher;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

final class NativePostPipeline implements AutoCloseable {
    private record Program(NativeScreenProgram shader, Map<String, List<String>> blocks, GpuFormat format) {}
    private final Identifier id;
    private final Map<Identifier, PostPipeline.TargetSpec> specs;
    private final List<PostPipeline.Pass> passes;
    private final Map<Identifier, TextureTarget> targets = new HashMap<>();
    private final Map<Integer, Program> programs = new HashMap<>();
    private final Map<GpuFormat, TextureTarget> feedback = new HashMap<>();
    private static final Pattern BLOCK = Pattern.compile("(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+(\\w+)\\s*\\{([^}]+)}\\s*;", Pattern.DOTALL);
    private static final Pattern MEMBER = Pattern.compile("(\\w+)\\s+(\\w+)(?:\\s*\\[\\s*(\\d+)\\s*])?\\s*;");

    NativePostPipeline(Identifier id, Map<Identifier, PostPipeline.TargetSpec> specs, List<PostPipeline.Pass> passes) {
        this.id=id;this.specs=specs;this.passes=passes;
    }

    void run(Map<String, List<UniformValue>> slots, Function<Identifier, RenderTarget> external) {
        var client=Minecraft.getInstance();var main=client.gameRenderer.mainRenderTarget();
        for(int index=0;index<passes.size();index++) {
            var pass=passes.get(index);var output=resolve(pass.output(),external,main);
            if(output==null)throw new IllegalStateException("Missing native post output "+pass.output());
            var format=output.getColorTexture().getFormat();
            var program=programs.get(index);
            if(program==null||program.format()!=format) {
                if(program!=null)program.shader().close();
                var blocks=new LinkedHashMap<String,List<String>>();
                var shaderId=pass.fragmentShader().withPath(path->"shaders/"+path+".fsh");
                var shader=new NativeScreenProgram(shaderId, source->flatten(source,blocks),new ColorTargetState(pass.blend()?Optional.of(BlendFunction.TRANSLUCENT):Optional.empty(),format,15));
                program=new Program(shader,blocks,format);programs.put(index,program);
            }
            var shader=program.shader();
            shader.setVector("OutSize",output.width,output.height);
            shader.setVector("ScreenSize",main.width,main.height);
            shader.setVector("ColorModulate",1,1,1,1);
            shader.setVector("ColorModulator",1,1,1,1);
            shader.setFloat("GameTime",((client.level==null?0:client.level.getGameTime())%24000L+VanillaCompat.partialTick(false))/24000f);
            for(var input:pass.inputs()) {
                GpuTextureView view;
                int width,height;
                if(input.texture()!=null) {
                    var location=input.texture().withPath(path->path.endsWith(".png")?path:"textures/effect/"+path+".png");
                    view=client.getTextureManager().getTexture(location).getTextureView();
                    width=view.texture().getWidth(0);height=view.texture().getHeight(0);
                } else {
                    var source=resolve(input.target(),external,main);
                    if(source==null)throw new IllegalStateException("Missing native post input "+input.target());
                    view=input.depth()?source.getDepthTextureView():source.getColorTextureView();
                    width=source.width;height=source.height;
                    if(!input.depth()&&view.texture()==output.getColorTexture()) {
                        var copy=feedback.get(format);
                        if(copy==null||copy.width!=width||copy.height!=height) {
                            if(copy!=null)copy.destroyBuffers();
                            copy=target("feedback",width,height,format);feedback.put(format,copy);
                        }
                        NativeFramebuffer.blitColor(view,copy.getColorTextureView());view=copy.getColorTextureView();
                    }
                }
                if(view==null)throw new IllegalStateException("Missing texture for native sampler "+input.sampler());
                shader.setTexture(input.sampler()+"Sampler",view);
                shader.setVector(input.sampler()+"Size",width,height);
            }
            for(var slot:pass.blockUniforms().entrySet())setBlock(program,slot.getKey(),slot.getValue());
            for(var member:pass.memberUniforms().entrySet())shader.setUniformValue(member.getKey(),member.getValue());
            for(var slot:slots.entrySet())setBlock(program,slot.getKey(),slot.getValue());
            shader.draw(RenderPassDescriptor.builder(()->"Amnetic post "+id).withColorAttachment(output.getColorTextureView()).build());
        }
    }

    private RenderTarget resolve(Identifier name,Function<Identifier,RenderTarget> external,RenderTarget main) {
        if(PostPipeline.MAIN.equals(name))return main;
        var spec=specs.get(name);
        if(spec==null)return external.apply(name);
        float scale = spec.scale() == null ? 1f : spec.scale();
        int width = spec.width() == null ? Math.max(1, Math.round(main.width * scale)) : spec.width();
        int height = spec.height() == null ? Math.max(1, Math.round(main.height * scale)) : spec.height();
        var target=targets.get(name);
        if(target==null||target.width!=width||target.height!=height) {
            if(target!=null)target.destroyBuffers();
            target=target(name.toString(),width,height,GpuFormat.RGBA16_FLOAT);targets.put(name,target);
        }
        return target;
    }

    private static TextureTarget target(String name,int width,int height,GpuFormat format) {
        //? if >=26.3 {
        return new TextureTarget("Amnetic post "+name,width,height,format,null);
        //?} else {
        /*return new TextureTarget("Amnetic post "+name,width,height,false,format);
        *///?}
    }

    private static String flatten(String source,Map<String,List<String>> blocks) {
        int first=source.indexOf('\n');
        source=source.substring(0,first+1)+source.substring(first+1).replaceAll("(?m)^\\s*#version[^\\r\\n]*","");
        var matcher=BLOCK.matcher(source);var result=new StringBuffer();
        while(matcher.find()) {
            var fields=new ArrayList<String>();var declarations=new StringBuilder();var members=MEMBER.matcher(matcher.group(2));
            while(members.find()) {
                int count=members.group(3)==null?1:Integer.parseInt(members.group(3));
                declarations.append("\nuniform ").append(members.group(1)).append(' ').append(members.group(2));
                if(count>1)declarations.append('[').append(count).append(']');declarations.append(";\n");
                for(int i=0;i<count;i++)fields.add(members.group(2)+(count>1?"["+i+"]":""));
            }
            blocks.put(matcher.group(1),fields);matcher.appendReplacement(result,Matcher.quoteReplacement(declarations.toString()));
        }
        matcher.appendTail(result);
        return result.toString().replaceAll("\\btexCoord\\b","vUV");
    }

    private static void setBlock(Program program,String block,List<UniformValue> values) {
        if(values==null)return;var names=program.blocks().get(block);
        if(names==null){if(values.size()==1)program.shader().setUniformValue(block,values.getFirst());return;}
        if(values.size()>names.size())throw new IllegalArgumentException("Too many values for native post block "+block);
        for(int i=0;i<values.size();i++)program.shader().setUniformValue(names.get(i),values.get(i));
    }

    @Override public void close() {
        for(var program:programs.values())program.shader().close();programs.clear();
        for(var target:targets.values())target.destroyBuffers();targets.clear();
        for(var target:feedback.values())target.destroyBuffers();feedback.clear();
    }
}
