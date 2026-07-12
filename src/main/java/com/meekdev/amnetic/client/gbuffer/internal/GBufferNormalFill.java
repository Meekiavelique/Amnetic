package com.meekdev.amnetic.client.gbuffer.internal;

import com.meekdev.amnetic.client.gbuffer.GBuffer;
import com.meekdev.amnetic.client.light.internal.LightRegistry;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ScreenPass;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.client.ssr.Ssr;
import java.nio.IntBuffer;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

public final class GBufferNormalFill extends ScreenPass {

    public static final GBufferNormalFill INSTANCE = new GBufferNormalFill();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/light/deferred.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/gbuffer/normal_fill.fsh");

    private GBufferNormalFill() { super("GBufferFill"); }

    public void render() { dispatch(); }

    @Override
    protected boolean enabled() {
        return GBuffer.isEnabled();
    }

    @Override protected ShaderProgram createProgram() { return new ShaderProgram(VSH, FSH); }

    @Override
    protected boolean record(CameraSnapshot cam, ShaderProgram program) {
        GBufferTargets g = GBufferTargets.INSTANCE;
        int prevFbo = g.bind();
        if (prevFbo == -1) return false;
        int depthId = g.depthGlId();
        if (depthId == 0) { g.restore(prevFbo); return false; }

        try {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
            try (MemoryStack s = MemoryStack.stackPush()) {
                IntBuffer bufs = s.ints(GL30.GL_NONE, GL30.GL_COLOR_ATTACHMENT1, GL30.GL_COLOR_ATTACHMENT2, GL30.GL_NONE);
                GL30.glDrawBuffers(bufs);
            }

            GlState.bindTexture(0, depthId);
            program.begin();
            program.setSampler("DepthSampler", 0);
            program.setMatrix4("InvViewProj", cam.invViewProj);
            program.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
            program.setFloat("DefaultRoughness", 1.0f);
            program.draw();

            g.setPopulated(true);
        } finally {
            try (MemoryStack s = MemoryStack.stackPush()) {
                IntBuffer bufs = s.ints(GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1, GL30.GL_COLOR_ATTACHMENT2,
                        GL30.GL_COLOR_ATTACHMENT3);
                GL30.glDrawBuffers(bufs);
            }
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthId, 0);
            g.restore(prevFbo);
        }
        return true;
    }
}
