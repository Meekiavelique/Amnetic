package com.meekdev.amnetic.client.model.internal;

import com.meekdev.amnetic.client.model.ModelLighting;
import com.meekdev.amnetic.client.render.ProgramLink;
import com.meekdev.amnetic.client.render.ShaderProgram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.FloatBuffer;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import com.meekdev.amnetic.client.material.SurfaceInputs;
import org.lwjgl.system.MemoryStack;

final class ModelShader implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Model");

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/model/model.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/model/model.fsh");

    private int program;
    private int projViewLoc;
    private int baseColorLoc;
    private int emissiveLoc;
    private int metallicLoc;
    private int roughnessLoc;
    private int alphaCutoffLoc;
    private int transmissionLoc;
    private int emissiveStrengthLoc;
    private int hasAlbedoLoc;
    private int hasNormalLoc;
    private int hasOrmLoc;
    private int hasEmissiveLoc;
    private int materialIdLoc;
    private int skinnedLoc;
    private int jointCountLoc;
    private int prevJointBaseLoc;
    private int cameraPosLoc;
    private int worldSpaceLoc;
    private int sourceIndexedLoc;

    static final int JOINT_UNIT = 6;
    private int hasEnvCubeLoc;
    private int envMaxLodLoc;
    private int sunDirectionLoc;
    private int sunColorLoc;
    private int sunIntensityLoc;
    private int ambientStrengthLoc;
    private int envIntensityLoc;
    private int exposureLoc;
    private int tonemapLoc;
    private int timeLoc;
    private int surfaceValuesLoc;
    private int uvTransformLoc;
    private int doubleSidedLoc;
    private int albedoEncodedLoc;

    static final int SURFACE_TEXTURE_UNIT = 8;
    static final int ROUGHNESS_UNIT = 4;
    static final int METALLIC_UNIT = 7;
    static final int OCCLUSION_UNIT = 12;
    static final int LIGHTMAP_UNIT = 13;
    static final int[] MATERIAL_UNITS = {0, 1, 2, 3, ROUGHNESS_UNIT, METALLIC_UNIT, OCCLUSION_UNIT};
    private int emissiveEncodedLoc;
    private int hasRoughnessMapLoc;
    private int hasMetallicMapLoc;
    private int hasOcclusionMapLoc;
    private int textureWarpLoc;
    private int vertexColorsLoc;
    private int unlitLoc;

    private final Identifier vshId;
    private final Identifier fshId;
    private ProgramLink link;
    private boolean failed;

    private ModelShader(Identifier vshId, Identifier fshId, ProgramLink link) {
        this.vshId = vshId;
        this.fshId = fshId;
        this.link = link;
    }

    boolean ready() {
        if (program != 0) return true;
        if (failed || link == null || !link.ready()) return false;
        ProgramLink done = link;
        link = null;
        if (done.failed()) {
            failed = true;
            LOG.error("model shader {}/{} does not build: {}", vshId, fshId, done.problem());
            return false;
        }
        linked(done.program());
        return true;
    }

    boolean failed() {
        return failed;
    }

    private void linked(int program) {
        this.program = program;
        this.projViewLoc = uniform("ProjViewMatrix");
        this.baseColorLoc = uniform("BaseColor");
        this.emissiveLoc = uniform("Emissive");
        this.metallicLoc = uniform("Metallic");
        this.roughnessLoc = uniform("Roughness");
        this.alphaCutoffLoc = uniform("AlphaCutoff");
        this.transmissionLoc = uniform("Transmission");
        this.emissiveStrengthLoc = uniform("EmissiveStrength");
        this.hasAlbedoLoc = uniform("HasAlbedo");
        this.hasNormalLoc = uniform("HasNormal");
        this.hasOrmLoc = uniform("HasOrm");
        this.hasEmissiveLoc = uniform("HasEmissive");
        this.materialIdLoc = uniform("MaterialId");
        this.skinnedLoc = uniform("Skinned");
        this.jointCountLoc = uniform("JointCount");
        this.prevJointBaseLoc = uniform("PrevJointBase");
        this.cameraPosLoc = uniform("CameraPos");
        this.worldSpaceLoc = uniform("WorldSpace");
        this.sourceIndexedLoc = uniform("SourceIndexed");
        this.hasEnvCubeLoc = uniform("HasEnvCube");
        this.envMaxLodLoc = uniform("EnvMaxLod");
        this.sunDirectionLoc = uniform("SunDirection");
        this.sunColorLoc = uniform("SunColor");
        this.sunIntensityLoc = uniform("SunIntensity");
        this.ambientStrengthLoc = uniform("AmbientStrength");
        this.envIntensityLoc = uniform("EnvIntensity");
        this.exposureLoc = uniform("Exposure");
        this.tonemapLoc = uniform("Tonemap");
        this.timeLoc = uniform("Time");
        this.surfaceValuesLoc = uniform("SurfaceValues");
        this.uvTransformLoc = uniform("UvTransform");
        this.doubleSidedLoc = uniform("DoubleSided");
        this.albedoEncodedLoc = uniform("AlbedoEncoded");
        this.emissiveEncodedLoc = uniform("EmissiveEncoded");
        this.hasRoughnessMapLoc = uniform("HasRoughnessMap");
        this.hasMetallicMapLoc = uniform("HasMetallicMap");
        this.hasOcclusionMapLoc = uniform("HasOcclusionMap");
        this.textureWarpLoc = uniform("TextureWarp");
        this.vertexColorsLoc = uniform("VertexColors");
        this.unlitLoc = uniform("Unlit");
        bindSamplerUnits();
    }

    static ModelShader load() {
        return loadResolved(VSH, FSH);
    }

    static ModelShader loadCustom(Identifier rawVsh, Identifier rawFsh) {
        return loadResolved(shaderPath(rawVsh, ".vsh"), shaderPath(rawFsh, ".fsh"));
    }

    private static Identifier shaderPath(Identifier id, String ext) {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "shaders/" + id.getPath() + ext);
    }

    private static ModelShader loadResolved(Identifier vshId, Identifier fshId) {
        ProgramLink link = ProgramLink.builder("model shader " + vshId + "/" + fshId)
                .vertex(loadSource(vshId), vshId)
                .fragment(loadSource(fshId), fshId)
                .start();
        return new ModelShader(vshId, fshId, link);
    }

    void bind() {
        GlStateManager._glUseProgram(program);
    }

    void uploadProjView(Matrix4fc m) {
        if (projViewLoc == -1) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(16);
            m.get(buf);
            GL20.glUniformMatrix4fv(projViewLoc, false, buf);
        }
    }

    void uploadEmissiveStrength(float strength) {
        if (emissiveStrengthLoc != -1) {
            GL20.glUniform1f(emissiveStrengthLoc, strength);
        }
    }

    void uploadLighting(ModelLighting l) {
        if (sunDirectionLoc != -1) GL20.glUniform3f(sunDirectionLoc, l.sunX(), l.sunY(), l.sunZ());
        if (sunColorLoc != -1) GL20.glUniform3f(sunColorLoc, l.sunR(), l.sunG(), l.sunB());
        if (sunIntensityLoc != -1) GL20.glUniform1f(sunIntensityLoc, l.sunIntensity());
        if (ambientStrengthLoc != -1) GL20.glUniform1f(ambientStrengthLoc, l.ambientStrength());
        if (envIntensityLoc != -1) GL20.glUniform1f(envIntensityLoc, l.envIntensity());
        if (exposureLoc != -1) GL20.glUniform1f(exposureLoc, l.exposure());
        if (tonemapLoc != -1) GL20.glUniform1i(tonemapLoc, l.tonemap() ? 1 : 0);
    }

    void uploadEnv(boolean hasCube, float maxLod) {
        if (hasEnvCubeLoc != -1) {
            GL20.glUniform1i(hasEnvCubeLoc, hasCube ? 1 : 0);
        }
        if (envMaxLodLoc != -1) {
            GL20.glUniform1f(envMaxLodLoc, maxLod);
        }
    }

    void uploadTime(float seconds) {
        if (timeLoc != -1) {
            GL20.glUniform1f(timeLoc, seconds);
        }
    }

    void setSkinned(boolean skinned) {
        if (skinnedLoc != -1) {
            GL20.glUniform1i(skinnedLoc, skinned ? 1 : 0);
        }
    }

    void setCameraPos(double x, double y, double z) {
        if (cameraPosLoc != -1) {
            GL20.glUniform3f(cameraPosLoc, (float) x, (float) y, (float) z);
        }
    }

    void setWorldSpace(boolean worldSpace) {
        if (worldSpaceLoc != -1) {
            GL20.glUniform1i(worldSpaceLoc, worldSpace ? 1 : 0);
        }
    }

    void setSourceIndexed(boolean sourceIndexed) {
        if (sourceIndexedLoc != -1) {
            GL20.glUniform1i(sourceIndexedLoc, sourceIndexed ? 1 : 0);
        }
    }

    void setPrevJointBase(int base) {
        if (prevJointBaseLoc != -1) {
            GL20.glUniform1i(prevJointBaseLoc, base);
        }
    }

    int programId() {
        return program;
    }

    void setJointCount(int count) {
        if (jointCountLoc != -1) {
            GL20.glUniform1i(jointCountLoc, count);
        }
    }

    void uploadMaterial(ModelIR.Material mat, boolean hasAlbedo, boolean hasNormal, boolean hasOrm, boolean hasEmissive) {
        if (baseColorLoc != -1) {
            GL20.glUniform4f(baseColorLoc, mat.baseR, mat.baseG, mat.baseB, mat.baseA);
        }
        if (emissiveLoc != -1) {
            GL20.glUniform3f(emissiveLoc, mat.emR, mat.emG, mat.emB);
        }
        if (metallicLoc != -1) {
            GL20.glUniform1f(metallicLoc, mat.metallic);
        }
        if (roughnessLoc != -1) {
            GL20.glUniform1f(roughnessLoc, mat.roughness);
        }
        if (alphaCutoffLoc != -1) {
            GL20.glUniform1f(alphaCutoffLoc, mat.alphaCutoff);
        }
        if (transmissionLoc != -1) {
            GL20.glUniform1f(transmissionLoc, mat.transmission);
        }
        if (hasAlbedoLoc != -1) {
            GL20.glUniform1i(hasAlbedoLoc, hasAlbedo ? 1 : 0);
        }
        if (hasNormalLoc != -1) {
            GL20.glUniform1i(hasNormalLoc, hasNormal ? 1 : 0);
        }
        if (hasOrmLoc != -1) {
            GL20.glUniform1i(hasOrmLoc, hasOrm ? 1 : 0);
        }
        if (hasEmissiveLoc != -1) {
            GL20.glUniform1i(hasEmissiveLoc, hasEmissive ? 1 : 0);
        }
        if (materialIdLoc != -1) {
            GL20.glUniform1i(materialIdLoc, mat.shadingModelId);
        }
        if (doubleSidedLoc != -1) {
            GL20.glUniform1i(doubleSidedLoc, mat.doubleSided ? 1 : 0);
        }
        if (unlitLoc != -1) {
            GL20.glUniform1i(unlitLoc, mat.unlit ? 1 : 0);
        }
    }

    void uploadAlbedoEncoded(boolean encoded) {
        if (albedoEncodedLoc != -1) {
            GL20.glUniform1i(albedoEncodedLoc, encoded ? 1 : 0);
        }
    }

    void uploadOwnMaps(boolean emissiveEncoded, boolean roughness, boolean metallic, boolean occlusion) {
        if (emissiveEncodedLoc != -1) GL20.glUniform1i(emissiveEncodedLoc, emissiveEncoded ? 1 : 0);
        if (hasRoughnessMapLoc != -1) GL20.glUniform1i(hasRoughnessMapLoc, roughness ? 1 : 0);
        if (hasMetallicMapLoc != -1) GL20.glUniform1i(hasMetallicMapLoc, metallic ? 1 : 0);
        if (hasOcclusionMapLoc != -1) GL20.glUniform1i(hasOcclusionMapLoc, occlusion ? 1 : 0);
    }

    void uploadWarp(float warp) {
        if (textureWarpLoc != -1) GL20.glUniform1f(textureWarpLoc, warp);
    }

    void uploadVertexColors(boolean colored) {
        if (vertexColorsLoc != -1) GL20.glUniform1i(vertexColorsLoc, colored ? 1 : 0);
    }

    void uploadUv(float repeatU, float repeatV, float offsetU, float offsetV) {
        if (uvTransformLoc != -1) {
            GL20.glUniform4f(uvTransformLoc, repeatU, repeatV, offsetU, offsetV);
        }
    }

    private static final float[] NO_VALUES = new float[SurfaceInputs.VEC4_SLOTS * 4];

    void uploadSurface(SurfaceInputs inputs) {
        if (surfaceValuesLoc != -1) GL20.glUniform4fv(surfaceValuesLoc, inputs == null ? NO_VALUES : inputs.values());
        for (int slot = 0; slot < SurfaceInputs.TEXTURE_SLOTS; slot++) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + SURFACE_TEXTURE_UNIT + slot);
            GlStateManager._bindTexture(inputs == null ? 0 : inputs.textures()[slot]);
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    void setMaterialId(int id) {
        if (materialIdLoc != -1) {
            GL20.glUniform1i(materialIdLoc, id);
        }
    }

    private void bindSamplerUnits() {
        GlStateManager._glUseProgram(program);
        setSampler("AlbedoSampler", 0);
        setSampler("NormalSampler", 1);
        setSampler("OrmSampler", 2);
        setSampler("EmissiveSampler", 3);
        setSampler("RoughnessSampler", ROUGHNESS_UNIT);
        setSampler("MetallicSampler", METALLIC_UNIT);
        setSampler("OcclusionSampler", OCCLUSION_UNIT);
        setSampler("EnvCube", 5);
        setSampler("LightMap", LIGHTMAP_UNIT);
        setSampler("JointMatrixTex", JOINT_UNIT);
        for (int slot = 0; slot < SurfaceInputs.TEXTURE_SLOTS; slot++) {
            setSampler("SurfaceTexture" + slot, SURFACE_TEXTURE_UNIT + slot);
        }
        GlStateManager._glUseProgram(0);
    }

    private void setSampler(String name, int unit) {
        int loc = uniform(name);
        if (loc != -1) {
            GL20.glUniform1i(loc, unit);
        }
    }

    private int uniform(String name) {
        return GlStateManager._glGetUniformLocation(program, name);
    }

    private static String loadSource(Identifier id) {
        return ShaderProgram.readSource(id);
    }

    @Override
    public void close() {
        if (link != null) link.delete();
        link = null;
        if (program != 0) {
            GlStateManager.glDeleteProgram(program);
            program = 0;
        }
    }
}
