package com.meekdev.amnetic.client.model;

import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.model.internal.OffscreenModelRenderer;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL45;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class ModelView {

    private final int width;
    private final int height;

    private Framebuffer framebuffer;
    private final OffscreenModelRenderer renderer = new OffscreenModelRenderer();

    private Model model;
    private Animator animator;

    private float yaw = (float) Math.toRadians(30.0);
    private float pitch = (float) Math.toRadians(20.0);
    private float fov = (float) Math.toRadians(45.0);
    private float zoom = 1f;
    private float blockLight = 1f;
    private float skyLight = 0f;
    private float emissiveStrength = 1f;
    private boolean disposed;

    public ModelView(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    public ModelView model(Model model) {
        this.model = model;
        this.animator = null;
        return this;
    }

    public ModelView yaw(float degrees) {
        this.yaw = (float) Math.toRadians(degrees);
        return this;
    }

    public ModelView pitch(float degrees) {
        this.pitch = (float) Math.toRadians(degrees);
        return this;
    }

    public ModelView fov(float degrees) {
        this.fov = (float) Math.toRadians(degrees);
        return this;
    }

    public ModelView zoom(float zoom) {
        this.zoom = zoom <= 0f ? 1f : zoom;
        return this;
    }

    public ModelView light(float block, float sky) {
        this.blockLight = block;
        this.skyLight = sky;
        return this;
    }

    public ModelView emissive(float strength) {
        this.emissiveStrength = strength;
        return this;
    }

    public ModelView play(String clip) {
        ensureAnimator();
        if (animator != null) {
            animator.play(clip);
        }
        return this;
    }

    public ModelView update(float dt) {
        ensureAnimator();
        if (animator != null) {
            animator.update(dt);
        }
        return this;
    }

    public ModelView spin(float degreesPerSecond, float dt) {
        this.yaw += (float) Math.toRadians(degreesPerSecond) * dt;
        return this;
    }

    public ModelView render() {
        if (disposed || model == null || !model.isReady()) {
            return this;
        }
        if (framebuffer == null) {
            FramebufferSpec spec = FramebufferSpec.builder()
                    .color(ColorFormat.RGBA8)
                    .depthTexture()
                    .build();
            framebuffer = Framebuffers.fixed("ModelView Render Target", width, height, spec);
        }

        Matrix4f projView = cameraMatrix();
        Matrix4f[] pose = currentPose();

        framebuffer.begin();
        framebuffer.clear(0f, 0f, 0f, 0f);
        renderer.draw(model.internalGpu(), projView, IDENTITY, pose, blockLight, skyLight, emissiveStrength);
        resolveAlphaFromDepth();
        framebuffer.end();
        return this;
    }

    private static ShaderProgram alphaResolve;

    // material alpha is wrong for ui compositing (glass writes ~0), coverage comes from
    // the depth buffer instead: alpha becomes 1 wherever the model wrote depth
    private void resolveAlphaFromDepth() {
        int depthTex = framebuffer.depthTextureGlId();
        if (depthTex == 0) return;
        if (alphaResolve == null) {
            alphaResolve = new ShaderProgram(
                    Identifier.fromNamespaceAndPath("amnetic", "shaders/util/fullscreen.vsh"),
                    Identifier.fromNamespaceAndPath("amnetic", "shaders/model/viewport_alpha.fsh"));
        }
        GL45.glTextureBarrier(); // depth was just written and is sampled next, same fbo
        GlStateManager._depthMask(false); GL11.glDepthMask(false);
        GlStateManager._disableDepthTest(); GL11.glDisable(GL11.GL_DEPTH_TEST);
        GlStateManager._disableBlend(); GL11.glDisable(GL11.GL_BLEND);
        GL11.glColorMask(false, false, false, true);
        alphaResolve.begin();
        alphaResolve.setSampler("DepthSampler", 0);
        GlState.bindTexture(0, depthTex);
        alphaResolve.draw();
        GlStateManager._glUseProgram(0);
        GL11.glColorMask(true, true, true, true);
        GlStateManager._depthMask(true); GL11.glDepthMask(true);
        GlStateManager._enableDepthTest(); GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    public int textureId() {
        return framebuffer == null ? 0 : framebuffer.colorTextureGlId(0);
    }

    public ModelView register(Identifier id) {
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
        if (framebuffer != null) {
            framebuffer.dispose();
            framebuffer = null;
        }
    }

    private void ensureAnimator() {
        if (animator == null && model != null && model.isAnimated()) {
            animator = model.createAnimator();
        }
    }

    private Matrix4f[] currentPose() {
        if (animator == null || animator.current() == null) {
            return null;
        }
        return animator.pose();
    }

    private Matrix4f cameraMatrix() {
        Vector3f center = model.center();
        float radius = model.radius();
        float distance = radius / (float) Math.tan(fov * 0.5f) / zoom + radius;

        float cosPitch = (float) Math.cos(pitch);
        Vector3f eye = new Vector3f(
                center.x + cosPitch * (float) Math.sin(yaw) * distance,
                center.y + (float) Math.sin(pitch) * distance,
                center.z + cosPitch * (float) Math.cos(yaw) * distance);

        float near = Math.max(0.01f, distance - radius * 2f);
        float far = distance + radius * 2f;
        float aspect = (float) width / (float) height;

        // match the device clip convention (0..1 depth on MC 26) or depth breaks, see SceneView
        Matrix4f proj = new Matrix4f().perspective(fov, aspect, near, far,
                com.mojang.blaze3d.systems.RenderSystem.getDevice().isZZeroToOne());
        Matrix4f view = new Matrix4f().lookAt(eye, center, UP);
        return proj.mul(view);
    }

    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);
    private static final Matrix4f IDENTITY = new Matrix4f();
}
