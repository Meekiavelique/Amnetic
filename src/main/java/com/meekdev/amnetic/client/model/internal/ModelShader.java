package com.meekdev.amnetic.client.model.internal;

import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.client.model.ModelLighting;
import java.nio.FloatBuffer;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

final class ModelShader implements AutoCloseable {

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/model/model.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/model/model.fsh");

    private final int program;
    private final int projViewLoc;
    private final int baseColorLoc;
    private final int emissiveLoc;
    private final int metallicLoc;
    private final int roughnessLoc;
    private final int alphaCutoffLoc;
    private final int transmissionLoc;
    private final int emissiveStrengthLoc;
    private final int hasAlbedoLoc;
    private final int hasNormalLoc;
    private final int hasOrmLoc;
    private final int hasEmissiveLoc;
    private final int materialIdLoc;
    private final int skinnedLoc;
    private final int jointCountLoc;
    private final int cameraPosLoc;
    private final int worldSpaceLoc;
    private final int sourceIndexedLoc;

    static final int JOINT_UNIT = 6;
    private final int hasEnvCubeLoc;
    private final int envMaxLodLoc;
    private final int sunDirectionLoc;
    private final int sunColorLoc;
    private final int sunIntensityLoc;
    private final int ambientStrengthLoc;
    private final int envIntensityLoc;
    private final int exposureLoc;
    private final int tonemapLoc;
    private final int timeLoc;

    private ModelShader(int program) {
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
        int vs = compile(GL20.GL_VERTEX_SHADER, loadSource(vshId), vshId);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, loadSource(fshId), fshId);
        int prog = GlStateManager.glCreateProgram();
        GlStateManager.glAttachShader(prog, vs);
        GlStateManager.glAttachShader(prog, fs);
        GlStateManager.glLinkProgram(prog);
        GlStateManager.glDeleteShader(vs);
        GlStateManager.glDeleteShader(fs);
        if (GlStateManager.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            int length = GlStateManager.glGetProgrami(prog, GL20.GL_INFO_LOG_LENGTH);
            String log = GlStateManager.glGetProgramInfoLog(prog, Math.max(length, 512));
            GlStateManager.glDeleteProgram(prog);
            throw new RuntimeException("Failed to link model shader (" + vshId + "/" + fshId + "): " + log);
        }
        return new ModelShader(prog);
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
        setSampler("EnvCube", 5);
        setSampler("JointMatrixTex", JOINT_UNIT);
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

    private static int compile(int type, String src, Identifier id) {
        int shader = GlStateManager.glCreateShader(type);
        GlStateManager.glShaderSource(shader, src);
        GlStateManager.glCompileShader(shader);
        if (GlStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            int length = GlStateManager.glGetShaderi(shader, GL20.GL_INFO_LOG_LENGTH);
            String log = GlStateManager.glGetShaderInfoLog(shader, Math.max(length, 512));
            GlStateManager.glDeleteShader(shader);
            throw new RuntimeException("Failed to compile model shader " + id + ": " + log);
        }
        return shader;
    }

    private static String loadSource(Identifier id) {
        return ShaderProgram.readSource(id);
    }

    @Override
    public void close() {
        if (program != 0) {
            GlStateManager.glDeleteProgram(program);
        }
    }
}
