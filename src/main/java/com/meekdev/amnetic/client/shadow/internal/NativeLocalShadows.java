package com.meekdev.amnetic.client.shadow.internal;

import com.meekdev.amnetic.client.framebuffer.*;
import com.meekdev.amnetic.client.framebuffer.internal.NativeFramebuffer;
import com.meekdev.amnetic.client.light.*;
import com.meekdev.amnetic.client.light.internal.LightRegistry;
import com.meekdev.amnetic.client.render.*;
import com.meekdev.amnetic.client.shadow.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import java.util.*;

/** Local shadow maps use ordinary 2D atlases on both native backends. */
public final class NativeLocalShadows implements AutoCloseable {
    public static final NativeLocalShadows INSTANCE = new NativeLocalShadows();
    private static final float[][] FACES = {{1,0,0,0,-1,0},{-1,0,0,0,-1,0},{0,1,0,0,0,1},
            {0,-1,0,0,0,-1},{0,0,1,0,-1,0},{0,0,-1,0,-1,0}};
    private final NativeFramebuffer point = target("Amnetic point shadows"), spot = target("Amnetic spot shadows");
    private final NativeFramebuffer sun = target("Amnetic directional shadows");
    private Mesh sunMesh;
    private final Matrix4f[] sunBakedMatrices = new Matrix4f[4];
    private long sunEntitySignature;
    private boolean sunEntityModels;
    public int sunCascades;
    public final float[] sunMatrices = new float[64], sunRadii = new float[4];
    public GpuTextureView sunDepth() { return sun.isAllocated() ? sun.depthView() : null; }
    private final EntityOccluders entities = new EntityOccluders();
    private boolean entityGeometry;
    private final Map<Long, Mesh> meshes = new HashMap<>();
    private NativeScreenProgram opaque, cutout, clear;
    private final Map<Long, Integer> baked = new HashMap<>();
    private final ArrayList<Light> candidates = new ArrayList<>();
    private final Set<Long> live = new HashSet<>();
    private java.nio.ByteBuffer opaqueUpload, cutoutUpload;
    public int points, spots, rows;
    public int spotGrid = 1;
    public final float[] spotMatrices = new float[ShadowSettings.MAX_SPOT * 16];
    private record Mesh(OccluderCache.Collected collected, GpuBuffer opaque, int opaqueCount, GpuBuffer cutout, int cutoutCount) {
        void close() { if (opaque != null) opaque.close(); if (cutout != null) cutout.close(); }
    }
    private static NativeFramebuffer target(String name) {
        return new NativeFramebuffer(FramebufferSpec.builder().color(ColorFormat.RGBA8).depthTexture().build(), name);
    }
    public GpuTextureView pointDepth() { return point.isAllocated() ? point.depthView() : null; }
    public GpuTextureView spotDepth() { return spot.isAllocated() ? spot.depthView() : null; }
    public GpuTextureView spotColor() { return spot.isAllocated() ? spot.colorView(0) : null; }
    public void render(CameraSnapshot camera) {
        points = spots = sunCascades = 0;
        var mc = Minecraft.getInstance();
        for (var light : LightRegistry.INSTANCE.all()) light.setShadowRef(-1);
        if (!Shadows.isEnabled() || camera == null || mc.level == null) return;
        var settings = ShadowSettings.defaults();
        renderSun(camera, settings);
        candidates.clear();
        for (var light : LightRegistry.INSTANCE.all()) {
            double dx = light.x()-camera.eye.x, dy=light.y()-camera.eye.y, dz=light.z()-camera.eye.z;
            if (light.isEnabled() && light.castsShadow() && (light.type()==LightType.POINT || light.type()==LightType.SPOT)
                    && dx*dx+dy*dy+dz*dz <= settings.maxDistance()*settings.maxDistance()) candidates.add(light);
        }
        candidates.sort(Comparator.comparingDouble(l -> {
            double dx=l.x()-camera.eye.x, dy=l.y()-camera.eye.y, dz=l.z()-camera.eye.z;
            return dx*dx+dy*dy+dz*dz;
        }));
        if (candidates.isEmpty()) { for (var mesh : meshes.values()) mesh.close(); meshes.clear(); return; }
        ensure();
        int wantedPoints = Math.min(settings.maxPointShadows(), (int)candidates.stream().filter(l -> l.type()==LightType.POINT).count());
        int wantedSpots = Math.min(settings.maxSpotShadows(), (int)candidates.stream().filter(l -> l.type()==LightType.SPOT).count());
        rows = Math.max(1, wantedPoints);
        int faceSize = settings.pointFaceSize(), tileSize = settings.resolution();
        int neededGrid = wantedSpots <= 1 ? 1 : wantedSpots <= 4 ? 2 : 4;
        int nextGrid = Math.max(spot.isAllocated() ? spotGrid : 1, neededGrid);
        boolean resized = wantedPoints>0 && (point.width()!=faceSize*6 || point.height()!=faceSize*rows)
                || wantedSpots>0 && (spot.width()!=tileSize*nextGrid || spot.height()!=tileSize*nextGrid);
        if (wantedPoints>0) point.allocate(faceSize*6, faceSize*rows); else point.close();
        if (wantedSpots>0) {
            spotGrid = nextGrid;
            spot.allocate(tileSize*spotGrid, tileSize*spotGrid);
        }
        if (resized) baked.clear();
        live.clear();
        for (var light : candidates) {
            boolean isPoint = light.type()==LightType.POINT;
            if (isPoint ? points>=settings.maxPointShadows() : spots>=settings.maxSpotShadows()) continue;
            live.add(light.id());
            var collected = OccluderCache.getOrCompute(light.id(), mc.level, (float)light.x(), (float)light.y(), (float)light.z(), light.range());
            var mesh = meshes.get(light.id());
            if (mesh == null || mesh.collected.list != collected.list) {
                if (mesh != null) mesh.close(); mesh = build(collected); meshes.put(light.id(), mesh);
            }
            float far = Math.max(.15f, light.range());
            var projection = new Matrix4f().perspective(isPoint ? (float)Math.PI/2 :
                    Math.max(.0175f,2*(float)Math.acos(Math.clamp(light.cosOuter(),-1f,1f))),1,.05f,far);
            float x=(float)(light.x()-collected.anchorX), y=(float)(light.y()-collected.anchorY), z=(float)(light.z()-collected.anchorZ);
            float partial=com.meekdev.amnetic.client.compat.VanillaCompat.partialTick(false);
            long entitySignature=settings.entityShadows()?entities.gather(mc.level,light.x(),light.y(),light.z(),light.range(),partial):0;
            int signature = 31 + Long.hashCode(entitySignature);
            signature=31*signature+System.identityHashCode(collected.list);
            signature=31*signature+Double.hashCode(light.x());
            signature=31*signature+Double.hashCode(light.y());
            signature=31*signature+Double.hashCode(light.z());
            signature=31*signature+Float.hashCode(light.dirX());
            signature=31*signature+Float.hashCode(light.dirY());
            signature=31*signature+Float.hashCode(light.dirZ());
            signature=31*signature+Float.hashCode(light.cosOuter());
            signature=31*signature+Float.hashCode(light.range());
            signature=31*signature+Boolean.hashCode(settings.entityModels());
            signature=31*signature+(isPoint?1000+points:spots);
            boolean dirty = !Objects.equals(baked.get(light.id()),signature);
            entityGeometry=dirty && entitySignature!=0 && entities.build(collected.anchorX,collected.anchorY,collected.anchorZ,settings.entityModels(),partial);
            if (isPoint) {
                light.setShadowRef(1000+points);
                for (int face=0; dirty && face<6; face++) {
                    var f=FACES[face]; var view=new Matrix4f().lookAt(x,y,z,x+f[0],y+f[1],z+f[2],f[3],f[4],f[5]);
                    draw(mesh, new Matrix4f(projection).mul(view), point, face, points,6,rows);
                }
                points++;
            } else {
                light.setShadowRef(spots);
                var up = Math.abs(light.dirY())>.99f ? new Vector3f(1,0,0) : new Vector3f(0,1,0);
                if (dirty) draw(mesh,new Matrix4f(projection).mul(new Matrix4f().lookAt(new Vector3f(x,y,z),
                        new Vector3f(x+light.dirX(),y+light.dirY(),z+light.dirZ()),up)),spot,spots%spotGrid,spots/spotGrid,spotGrid,spotGrid);
                x=(float)(light.x()-camera.eye.x); y=(float)(light.y()-camera.eye.y); z=(float)(light.z()-camera.eye.z);
                new Matrix4f(projection).mul(new Matrix4f().lookAt(new Vector3f(x,y,z),
                        new Vector3f(x+light.dirX(),y+light.dirY(),z+light.dirZ()),up)).get(spotMatrices,spots*16);
                spots++;
            }
            baked.put(light.id(),signature);
        }
        meshes.entrySet().removeIf(e -> { if(live.contains(e.getKey())) return false; e.getValue().close(); return true; });
        if (sunCascades>0) live.add(Long.MIN_VALUE);
        OccluderCache.retainOnly(live);
        baked.keySet().retainAll(live);
    }
    private void renderSun(CameraSnapshot cam, ShadowSettings settings) {
        Light light = null;
        for (var candidate : LightRegistry.INSTANCE.all())
            if (candidate.isEnabled() && candidate.castsShadow() && candidate.type()==LightType.DIRECTIONAL) { light=candidate; break; }
        if (light==null) { sun.close(); if(sunMesh!=null){sunMesh.close();sunMesh=null;} return; }
        ensure();
        var level=Minecraft.getInstance().level;
        int resolution=settings.sunResolution(), count=settings.sunCascades();
        boolean resized=sun.width()!=resolution*4 || sun.height()!=resolution;
        sun.allocate(resolution*4,resolution);
        float far=settings.sunDistance(), near=.05f;
        float radiusLimit=Math.min(settings.sunBlockOccluderRadius(),far);
        float sx=(float)(Math.floor(cam.eye.x/8)*8+4), sy=(float)(Math.floor(cam.eye.y/8)*8+4), sz=(float)(Math.floor(cam.eye.z/8)*8+4);
        var collected=OccluderCache.getOrCompute(Long.MIN_VALUE,level,sx,sy,sz,radiusLimit+7);
        boolean geometryChanged=sunMesh==null || sunMesh.collected.list!=collected.list;
        if(geometryChanged) { if(sunMesh!=null)sunMesh.close();sunMesh=build(collected); }
        float partial=com.meekdev.amnetic.client.compat.VanillaCompat.partialTick(false);
        long entitySignature=settings.entityShadows()?entities.gather(level,cam.eye.x,cam.eye.y,cam.eye.z,Math.min(far,64),partial):0;
        entityGeometry=false;boolean entitiesPrepared=false;
        var direction=new Vector3f(light.dirX(),light.dirY(),light.dirZ()).normalize();
        var up=Math.abs(direction.y)>.99f?new Vector3f(0,0,1):new Vector3f(0,1,0);
        var right=new Vector3f(direction).cross(up).normalize();
        up=new Vector3f(right).cross(direction).normalize();
        var forward=cam.view.positiveZ(new Vector3f()).negate();
        float tanH=1/cam.projection.m00(), tanV=1/cam.projection.m11(), k2=tanH*tanH+tanV*tanV;
        float[] splits=new float[count+1];splits[0]=near;splits[count]=far;
        for(int i=1;i<count;i++){float f=i/(float)count;splits[i]=settings.sunSplitLambda()*near*(float)Math.pow(far/near,f)+(1-settings.sunSplitLambda())*(near+(far-near)*f);}
        for(int i=0;i<count;i++) {
            float a=splits[i], b=splits[i+1], t=Math.clamp((a+b)*(1+k2)*.5f,a,b);
            float radius=(float)Math.ceil(Math.sqrt(Math.max(b*b*k2+(t-b)*(t-b),a*a*k2+(t-a)*(t-a)))*2)/2;
            radius=Math.max(radius,.5f);
            double cx=cam.eye.x+forward.x*t, cy=cam.eye.y+forward.y*t, cz=cam.eye.z+forward.z*t;
            double texel=2.0*radius/resolution, r=cx*right.x+cy*right.y+cz*right.z, u=cx*up.x+cy*up.y+cz*up.z;
            double dr=Math.floor(r/texel)*texel-r, du=Math.floor(u/texel)*texel-u;
            cx+=right.x*dr+up.x*du;cy+=right.y*dr+up.y*du;cz+=right.z*dr+up.z*du;
            float eyeDistance=radius+settings.sunCasterExtension();
            var projection=new Matrix4f().ortho(-radius,radius,-radius,radius,0,2*radius+settings.sunCasterExtension());
            var eye=new Vector3f((float)(cx-collected.anchorX)-direction.x*eyeDistance,(float)(cy-collected.anchorY)-direction.y*eyeDistance,(float)(cz-collected.anchorZ)-direction.z*eyeDistance);
            var bake=new Matrix4f(projection).mul(new Matrix4f().lookAt(eye,new Vector3f(eye).add(direction),up));
            if(resized || geometryChanged || entitySignature!=sunEntitySignature || settings.entityModels()!=sunEntityModels || sunBakedMatrices[i]==null || !bake.equals(sunBakedMatrices[i])) {
                if(!entitiesPrepared){entityGeometry=entitySignature!=0 && entities.build(collected.anchorX,collected.anchorY,collected.anchorZ,settings.entityModels(),partial);entitiesPrepared=true;}
                draw(sunMesh,bake,sun,i,0,4,1);
                if(sunBakedMatrices[i]==null)sunBakedMatrices[i]=new Matrix4f();sunBakedMatrices[i].set(bake);
            }
            eye.set((float)(cx-cam.eye.x)-direction.x*eyeDistance,(float)(cy-cam.eye.y)-direction.y*eyeDistance,(float)(cz-cam.eye.z)-direction.z*eyeDistance);
            new Matrix4f(projection).mul(new Matrix4f().lookAt(eye,new Vector3f(eye).add(direction),up)).get(sunMatrices,i*16);
            sunRadii[i]=radius;
        }
        sunEntitySignature=entitySignature;sunEntityModels=settings.entityModels();sunCascades=count;light.setShadowRef(2000);
    }

    private void ensure() {
        if (opaque != null) return;
        String vertex = """
                #version 450
                layout(location=0) in vec3 Position;
                uniform mat4 uViewProj;
                uniform vec4 Tile;
                void main() { vec4 p=uViewProj*vec4(Position,1); p.z=(p.z+p.w)*0.5;
                    p.xy=p.xy*Tile.xy+Tile.zw*p.w; gl_Position=p; }
                """;
        if (!com.meekdev.amnetic.client.compat.VanillaCompat.zeroToOne())
            vertex = vertex.replace("p.z=(p.z+p.w)*0.5;", "");
        var depth = Optional.of(new DepthStencilState(CompareOp.LESS_THAN,true));
        opaque = new NativeScreenProgram(vertex,id("depth.fsh"),new VertexFormat[]{VertexFormat.builder(0)
                .addAttribute("Position",GpuFormat.RGB32_FLOAT).build()},false,depth,ColorTargetState.DEFAULT);
        vertex=vertex.replace("uniform mat4", "layout(location=1) in vec2 UV;\nout vec2 vUV;\nuniform mat4")
                .replace("vec4 p=uViewProj", "vUV=UV; vec4 p=uViewProj");
        cutout=new NativeScreenProgram(vertex,id("depth_cutout.fsh"),new VertexFormat[]{VertexFormat.builder(0)
                .addAttribute("Position",GpuFormat.RGB32_FLOAT).addAttribute("UV",GpuFormat.RG32_FLOAT).build()},false,depth,ColorTargetState.DEFAULT);
        clear = new NativeScreenProgram("""
                #version 450
                uniform vec4 Tile;
                void main() { vec2 p=vec2(float((gl_VertexIndex<<1)&2),float(gl_VertexIndex&2))*2-1;
                    gl_Position=vec4(p*Tile.xy+Tile.zw,1,1); }
                """,id("clear.fsh"),new VertexFormat[0],false,
                Optional.of(new DepthStencilState(CompareOp.ALWAYS_PASS,true)),ColorTargetState.DEFAULT);
    }
    private static Identifier id(String file) { return Identifier.fromNamespaceAndPath("amnetic","shaders/shadow/"+file); }
    private void draw(Mesh mesh,Matrix4f matrix,NativeFramebuffer target,int x,int y,int cols,int rows) {
        clear.setVector("Tile",1f/cols,1f/rows,(2f*x+1)/cols-1,(2f*y+1)/rows-1);
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        clear.draw(encoder,target.descriptor(),pass->{
            pass.enableScissor(x*target.width()/cols,y*target.height()/rows,target.width()/cols,target.height()/rows);
            pass.draw(3,1,0,0);
        });
        draw(encoder,opaque,mesh.opaque,mesh.opaqueCount,matrix,target,x,y,cols,rows);
        if(entityGeometry)draw(encoder,opaque,entities.nativeVertices(),entities.nativeCount(),matrix,target,x,y,cols,rows);
        if (mesh.cutoutCount>0) {
            var atlas=Minecraft.getInstance().getTextureManager().getTexture(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
            cutout.setTexture("uAtlas",atlas.getTextureView());
            draw(encoder,cutout,mesh.cutout,mesh.cutoutCount,matrix,target,x,y,cols,rows);
        }
        encoder.submit();
    }
    private static void draw(com.mojang.renderpearl.api.commands.CommandEncoder encoder, NativeScreenProgram program,GpuBuffer buffer,int count,Matrix4f matrix,NativeFramebuffer target,int x,int y,int cols,int rows) {
        if(count==0)return;
        draw(encoder,program,buffer.slice(),count,matrix,target,x,y,cols,rows);
    }
    private static void draw(com.mojang.renderpearl.api.commands.CommandEncoder encoder, NativeScreenProgram program,com.mojang.renderpearl.api.buffers.GpuBufferSlice buffer,int count,Matrix4f matrix,NativeFramebuffer target,int x,int y,int cols,int rows) {
        if(count==0) return;
        program.setMatrix("uViewProj",matrix); program.setVector("Tile",1f/cols,1f/rows,(2f*x+1)/cols-1,(2f*y+1)/rows-1);
        program.draw(encoder,target.descriptor(),pass->{
            pass.enableScissor(x*target.width()/cols,y*target.height()/rows,target.width()/cols,target.height()/rows);
            pass.setVertexBuffer(0,buffer); pass.draw(count,1,0,0);
        });
    }
    private Mesh build(OccluderCache.Collected c) {
        int[] boxes={0}; int cutFloats=0;
        for(var e:c.list) { if(e.shape!=null)e.shape.forAllBoxes((a,b,d,f,g,h)->boxes[0]++); if(e.cutoutVerts!=null)cutFloats+=e.cutoutVerts.length; }
        opaqueUpload=reserve(opaqueUpload,boxes[0]*108*4);
        cutoutUpload=reserve(cutoutUpload,cutFloats*4);
        var opaque=opaqueUpload.clear().limit(boxes[0]*108*4); var floats=opaque.asFloatBuffer();
        var cut=cutoutUpload.clear().limit(cutFloats*4); var cutBuffer=cut.asFloatBuffer();
        for(var e:c.list) {
            float x=e.pos.getX()-c.anchorX,y=e.pos.getY()-c.anchorY,z=e.pos.getZ()-c.anchorZ;
            if(e.shape!=null)e.shape.forAllBoxes((a,b,d,f,g,h)->OccluderMesh.emitBox(floats,x+(float)a,y+(float)b,z+(float)d,x+(float)f,y+(float)g,z+(float)h));
            if(e.cutoutVerts!=null) for(int i=0;i+4<e.cutoutVerts.length;i+=5)cutBuffer.put(e.cutoutVerts[i]+x).put(e.cutoutVerts[i+1]+y).put(e.cutoutVerts[i+2]+z).put(e.cutoutVerts[i+3]).put(e.cutoutVerts[i+4]);
        }
        var device=RenderSystem.getDevice();
        return new Mesh(c,opaque.remaining()>0?device.createBuffer(()->"Amnetic shadow geometry",GpuBuffer.USAGE_VERTEX,opaque):null,boxes[0]*36,
                cut.remaining()>0?device.createBuffer(()->"Amnetic cutout shadows",GpuBuffer.USAGE_VERTEX,cut):null,cutFloats/5);
    }
    private static java.nio.ByteBuffer reserve(java.nio.ByteBuffer buffer,int bytes) {
        if(buffer!=null && buffer.capacity()>=bytes)return buffer;
        var next=MemoryUtil.memAlloc(Math.max(4096,bytes));
        if(buffer!=null)MemoryUtil.memFree(buffer);
        return next;
    }
    public void close() {
        candidates.clear();live.clear();entityGeometry=false;
        entities.dispose();
        if(opaqueUpload!=null){MemoryUtil.memFree(opaqueUpload);opaqueUpload=null;}
        if(cutoutUpload!=null){MemoryUtil.memFree(cutoutUpload);cutoutUpload=null;}
        sun.close();if(sunMesh!=null){sunMesh.close();sunMesh=null;}
        Arrays.fill(sunBakedMatrices,null);sunCascades=0;
        for(var mesh:meshes.values())mesh.close(); meshes.clear(); point.close(); spot.close();
        if(opaque!=null){opaque.close();opaque=null;} if(cutout!=null){cutout.close();cutout=null;} points=spots=0;spotGrid=1;
        if(clear!=null){clear.close();clear=null;} baked.clear();
    }
}
