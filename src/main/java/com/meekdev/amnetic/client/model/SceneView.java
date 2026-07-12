package com.meekdev.amnetic.client.model;

import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.model.internal.OffscreenModelRenderer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * offscreen composition of several models with a free camera, the multi-model sibling of
 * {@link ModelView} (which auto-frames a single model). meant for menu backdrops and dioramas: add
 * (model, world transform) entries once, point the camera, call {@link #render()} each frame, and
 * draw {@link #textureId()} (or the {@link #register(Identifier) registered} texture) wherever GUI
 * textures go. forward-lit via the stock model shader, no deferred pipeline, shadows or post.
 * entries whose model isn't uploaded yet are skipped so the scene fills in as async loads finish
 */
public final class SceneView {

    private record Entry(Model model, Matrix4f world) {}

    private final int width;
    private final int height;

    private Framebuffer framebuffer;
    private final OffscreenModelRenderer renderer = new OffscreenModelRenderer();
    private final List<Entry> entries = new ArrayList<>();

    private final Vector3f eye = new Vector3f(0f, 2f, 6f);
    private final Vector3f look = new Vector3f(0f, 0f, 0f);
    private float fov = (float) Math.toRadians(60.0);
    private float near = 0.1f;
    private float far = 600f;
    private float clearR = 0.55f, clearG = 0.72f, clearB = 0.86f; // sky-ish default
    private float blockLight = 1f;
    private float skyLight = 1f;
    private boolean disposed;

    public SceneView(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    // adds a model drawn with the given absolute world matrix (referenced, not copied, mutate to animate)
    public SceneView add(Model model, Matrix4f world) {
        entries.add(new Entry(model, world));
        return this;
    }

    public SceneView clearModels() {
        entries.clear();
        return this;
    }

    public SceneView camera(float eyeX, float eyeY, float eyeZ, float lookX, float lookY, float lookZ) {
        eye.set(eyeX, eyeY, eyeZ);
        look.set(lookX, lookY, lookZ);
        return this;
    }

    public SceneView fov(float degrees) {
        this.fov = (float) Math.toRadians(degrees);
        return this;
    }

    public SceneView clip(float near, float far) {
        this.near = near;
        this.far = far;
        return this;
    }

    public SceneView clearColor(float r, float g, float b) {
        this.clearR = r; this.clearG = g; this.clearB = b;
        return this;
    }

    public SceneView light(float block, float sky) {
        this.blockLight = block;
        this.skyLight = sky;
        return this;
    }

    public SceneView render() {
        if (disposed || entries.isEmpty()) {
            return this;
        }
        if (framebuffer == null) {
            FramebufferSpec spec = FramebufferSpec.builder()
                    .color(ColorFormat.RGBA8)
                    .depthTexture()
                    .build();
            framebuffer = Framebuffers.fixed("SceneView Render Target", width, height, spec);
        }

        float aspect = (float) width / (float) height;
        // match the device's clip-space convention: MC 26 runs 0..1 depth (glClipControl), a -1..1
        // JOML projection under that produces torn, z-fighting depth
        boolean zeroToOne = com.mojang.blaze3d.systems.RenderSystem.getDevice().isZZeroToOne();
        Matrix4f projView = new Matrix4f().perspective(fov, aspect, near, far, zeroToOne)
                .mul(new Matrix4f().lookAt(eye, look, UP));

        framebuffer.begin();
        framebuffer.clear(clearR, clearG, clearB, 1f);
        for (Entry e : entries) {
            if (e.model() == null || !e.model().isReady()) continue; // async load still in flight, pops in later
            renderer.draw(e.model().internalGpu(), projView, e.world(), null, blockLight, skyLight, 1f);
        }
        framebuffer.end();
        return this;
    }

    public int textureId() {
        return framebuffer == null ? 0 : framebuffer.colorTextureGlId(0);
    }

    // exposes the color texture under an Identifier so vanilla GUI blits can draw it, call after render()
    public SceneView register(Identifier id) {
        if (framebuffer != null) {
            framebuffer.registerColorTexture(id);
        }
        return this;
    }

    public int viewWidth() {
        return width;
    }

    public int viewHeight() {
        return height;
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        renderer.close();
        entries.clear();
        if (framebuffer != null) {
            framebuffer.dispose();
            framebuffer = null;
        }
    }

    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);
}
