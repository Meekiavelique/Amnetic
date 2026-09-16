package com.meekdev.amnetic.client.post.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.post.UniformValue;
import com.meekdev.amnetic.client.render.GlState;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Vector2f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

final class PostPipeline implements AutoCloseable {

    static final Identifier MAIN = Identifier.fromNamespaceAndPath("minecraft", "main");

    private record Input(String sampler, Identifier target, Identifier texture, boolean depth, boolean bilinear) {}

    private record Pass(Identifier vertexShader, Identifier fragmentShader, List<Input> inputs, Identifier output,
                        Map<String, List<UniformValue>> blockUniforms, Map<String, UniformValue> memberUniforms) {}

    private record TargetSpec(Integer width, Integer height, boolean persistent, int clearColor) {}

    private static final class Surface {
        int color;
        int depth;
        int width;
        int height;
        boolean owned;
    }

    private final Identifier id;
    private final Map<Identifier, TargetSpec> targetSpecs;
    private final List<Pass> passes;
    private final Map<Identifier, Surface> internal = new HashMap<>();
    private final List<PostProgram> programs = new ArrayList<>();
    private int fbo;
    private int scratchColor;
    private int scratchWidth;
    private int scratchHeight;
    private int linearSampler;
    private int nearestSampler;

    private PostPipeline(Identifier id, Map<Identifier, TargetSpec> targetSpecs, List<Pass> passes) {
        this.id = id;
        this.targetSpecs = targetSpecs;
        this.passes = passes;
    }

    static PostPipeline load(Identifier id, Identifier resourcePath, Map<String, Identifier> textureOverrides) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(resourcePath);
        if (resource.isEmpty()) throw new IllegalStateException("post effect resource not found: " + resourcePath);
        JsonObject root;
        try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("failed to read post effect " + resourcePath + ": " + e.getMessage(), e);
        }

        Map<Identifier, TargetSpec> targets = new LinkedHashMap<>();
        if (root.has("targets")) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("targets").entrySet()) {
                JsonObject t = e.getValue().isJsonObject() ? e.getValue().getAsJsonObject() : new JsonObject();
                targets.put(Identifier.parse(e.getKey()), new TargetSpec(
                        t.has("width") ? t.get("width").getAsInt() : null,
                        t.has("height") ? t.get("height").getAsInt() : null,
                        t.has("persistent") && t.get("persistent").getAsBoolean(),
                        t.has("clear_color") ? t.get("clear_color").getAsInt() : 0));
            }
        }

        List<Pass> passes = new ArrayList<>();
        for (JsonElement el : root.getAsJsonArray("passes")) {
            JsonObject p = el.getAsJsonObject();
            List<Input> inputs = new ArrayList<>();
            if (p.has("inputs")) {
                for (JsonElement in : p.getAsJsonArray("inputs")) {
                    JsonObject i = in.getAsJsonObject();
                    inputs.add(new Input(
                            i.get("sampler_name").getAsString(),
                            i.has("target") ? Identifier.parse(i.get("target").getAsString()) : null,
                            i.has("location")
                                    ? textureOverrides.getOrDefault(i.get("sampler_name").getAsString(), Identifier.parse(i.get("location").getAsString()))
                                    : null,
                            i.has("use_depth_buffer") && i.get("use_depth_buffer").getAsBoolean(),
                            i.has("bilinear") && i.get("bilinear").getAsBoolean()));
                }
            }
            Map<String, List<UniformValue>> blocks = new LinkedHashMap<>();
            Map<String, UniformValue> members = new LinkedHashMap<>();
            if (p.has("uniforms")) {
                for (Map.Entry<String, JsonElement> block : p.getAsJsonObject("uniforms").entrySet()) {
                    List<UniformValue> values = new ArrayList<>();
                    for (JsonElement u : block.getValue().getAsJsonArray()) {
                        JsonObject uo = u.getAsJsonObject();
                        UniformValue value = UniformValue.parse(uo.get("type").getAsString(), uo.get("value"));
                        values.add(value);
                        if (uo.has("name")) members.put(uo.get("name").getAsString(), value);
                    }
                    blocks.put(block.getKey(), values);
                }
            }
            passes.add(new Pass(
                    Identifier.parse(p.get("vertex_shader").getAsString()),
                    Identifier.parse(p.get("fragment_shader").getAsString()),
                    inputs,
                    Identifier.parse(p.get("output").getAsString()),
                    blocks, members));
        }
        return new PostPipeline(id, targets, passes);
    }

    boolean usesTarget(Identifier target) {
        for (Pass pass : passes) {
            if (target.equals(pass.output())) return true;
            for (Input input : pass.inputs()) if (target.equals(input.target())) return true;
        }
        return false;
    }

    void run(Map<String, List<UniformValue>> slots, Function<Identifier, RenderTarget> external) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (main == null) return;
        ensureResources();
        if (programs.isEmpty()) {
            List<PostProgram> built = new ArrayList<>();
            try {
                for (Pass pass : passes) built.add(new PostProgram(pass.vertexShader(), pass.fragmentShader()));
            } catch (RuntimeException e) {
                for (PostProgram program : built) program.close();
                throw e;
            }
            programs.addAll(built);
        }

        int prevFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);

        Map<Identifier, Surface> frame = new HashMap<>();
        for (Map.Entry<Identifier, TargetSpec> e : targetSpecs.entrySet()) {
            frame.put(e.getKey(), internalSurface(e.getKey(), e.getValue(), main));
        }

        GlState.beginFullscreen();
        try {
            for (int i = 0; i < passes.size(); i++) {
                Pass pass = passes.get(i);
                Surface out = resolve(pass.output(), frame, external);
                if (out == null) return;
                runPass(pass, programs.get(i), out, frame, external, slots);
            }
        } finally {
            for (int unit = 7; unit >= 0; unit--) GlState.bindTexture(unit, 0);
            GlState.endFullscreen();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            GlStateManager._viewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
        }
    }

    private void runPass(Pass pass, PostProgram program, Surface out, Map<Identifier, Surface> frame,
                         Function<Identifier, RenderTarget> external, Map<String, List<UniformValue>> slots) {
        program.begin();

        List<Integer> units = new ArrayList<>();
        Map<String, UniformValue> samplerInfo = new LinkedHashMap<>();
        samplerInfo.put("OutSize", new UniformValue.Vec2Uniform(new Vector2f(out.width, out.height)));
        int unit = 0;
        for (Input input : pass.inputs()) {
            int texture;
            int w;
            int h;
            if (input.texture() != null) {
                var textures = Minecraft.getInstance().getTextureManager();
                Identifier location = input.texture().withPath(p -> p.endsWith(".png") ? p : "textures/effect/" + p + ".png");
                texture = VanillaCompat.glId(textures.getTexture(location));
                w = h = 0;
            } else {
                Surface in = resolve(input.target(), frame, external);
                if (in == null) {
                    program.end();
                    return;
                }
                texture = input.depth() ? in.depth : in.color;
                w = in.width;
                h = in.height;
                if (!input.depth() && in.color == out.color && texture != 0) {
                    texture = copyToScratch(in);
                }
            }
            GlState.bindTexture(unit, texture);
            GL33.glBindSampler(unit, input.bilinear() ? linearSampler : nearestSampler);
            program.setSampler(input.sampler() + "Sampler", unit);
            samplerInfo.put(input.sampler() + "Size", new UniformValue.Vec2Uniform(new Vector2f(w, h)));
            units.add(unit);
            unit++;
        }

        for (Map.Entry<String, UniformValue> e : samplerInfo.entrySet()) program.set(e.getKey(), e.getValue());
        writeGlobals(program, out);
        for (Map.Entry<String, List<UniformValue>> e : pass.blockUniforms().entrySet()) program.setBlock(e.getKey(), e.getValue());
        for (Map.Entry<String, UniformValue> e : pass.memberUniforms().entrySet()) program.set(e.getKey(), e.getValue());
        for (Map.Entry<String, List<UniformValue>> e : slots.entrySet()) program.setSlot(e.getKey(), e.getValue());
        program.upload();

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, out.color, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GlStateManager._viewport(0, 0, out.width, out.height);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);

        program.end();
    }

    private void writeGlobals(PostProgram program, Surface out) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        program.set("ScreenSize", new UniformValue.Vec2Uniform(new Vector2f(main.width, main.height)));
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long gameTime = mc.level != null ? mc.level.getGameTime() : 0L;
        program.set("GameTime", new UniformValue.FloatUniform(((gameTime % 24000L) + partial) / 24000f));
        program.set("GlintAlpha", new UniformValue.FloatUniform(mc.options.glintStrength().get().floatValue()));
        program.set("MenuBlurRadius", new UniformValue.IntUniform(mc.options.menuBackgroundBlurriness().get()));
    }

    private Surface resolve(Identifier target, Map<Identifier, Surface> frame, Function<Identifier, RenderTarget> external) {
        Surface own = frame.get(target);
        if (own != null) return own;
        RenderTarget rt = MAIN.equals(target) ? Minecraft.getInstance().getMainRenderTarget() : external.apply(target);
        if (rt == null) return null;
        Surface s = new Surface();
        s.color = VanillaCompat.colorTextureGlId(rt);
        s.depth = VanillaCompat.depthTextureGlId(rt);
        s.width = rt.width;
        s.height = rt.height;
        if (s.color == 0) return null;
        frame.put(target, s);
        return s;
    }

    private Surface internalSurface(Identifier target, TargetSpec spec, RenderTarget main) {
        int w = spec.width() != null ? spec.width() : main.width;
        int h = spec.height() != null ? spec.height() : main.height;
        Surface s = internal.get(target);
        boolean fresh = false;
        if (s == null || s.width != w || s.height != h) {
            if (s != null) deleteSurface(s);
            s = new Surface();
            s.owned = true;
            s.width = w;
            s.height = h;
            s.color = texture(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, w, h);
            s.depth = texture(GL14.GL_DEPTH_COMPONENT24, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, w, h);
            internal.put(target, s);
            fresh = true;
        }
        if (fresh || !spec.persistent()) {
            int argb = spec.clearColor();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, s.color, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, s.depth, 0);
            GL11.glClearColor(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
            GL11.glClearDepth(1.0);
            GlStateManager._depthMask(true);
            GL11.glDepthMask(true);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
        }
        return s;
    }

    private int copyToScratch(Surface src) {
        if (scratchColor == 0 || scratchWidth != src.width || scratchHeight != src.height) {
            if (scratchColor != 0) GL11.glDeleteTextures(scratchColor);
            scratchColor = texture(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, src.width, src.height);
            scratchWidth = src.width;
            scratchHeight = src.height;
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, src.color, 0);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, scratchColor);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, src.width, src.height);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GlStateManager._bindTexture(0);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
        return scratchColor;
    }

    private void ensureResources() {
        if (fbo == 0) fbo = GL30.glGenFramebuffers();
        if (linearSampler == 0) {
            linearSampler = sampler(GL11.GL_LINEAR);
            nearestSampler = sampler(GL11.GL_NEAREST);
        }
    }

    private static int sampler(int filter) {
        int s = GL33.glGenSamplers();
        GL33.glSamplerParameteri(s, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL33.glSamplerParameteri(s, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL33.glSamplerParameteri(s, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL33.glSamplerParameteri(s, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return s;
    }

    private static int texture(int internalFormat, int format, int type, int w, int h) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, w, h, 0, format, type, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GlStateManager._bindTexture(0);
        return tex;
    }

    private static void deleteSurface(Surface s) {
        if (!s.owned) return;
        if (s.color != 0) GL11.glDeleteTextures(s.color);
        if (s.depth != 0) GL11.glDeleteTextures(s.depth);
    }

    Identifier id() {
        return id;
    }

    @Override
    public void close() {
        for (PostProgram p : programs) p.close();
        programs.clear();
        for (Surface s : internal.values()) deleteSurface(s);
        internal.clear();
        if (scratchColor != 0) { GL11.glDeleteTextures(scratchColor); scratchColor = 0; }
        if (fbo != 0) { GL30.glDeleteFramebuffers(fbo); fbo = 0; }
        if (linearSampler != 0) { GL33.glDeleteSamplers(linearSampler); GL33.glDeleteSamplers(nearestSampler); linearSampler = nearestSampler = 0; }
    }
}
