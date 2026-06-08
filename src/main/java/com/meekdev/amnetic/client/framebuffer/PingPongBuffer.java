package com.meekdev.amnetic.client.framebuffer;

import java.util.function.BiConsumer;

public final class PingPongBuffer {

    private Framebuffer a;
    private Framebuffer b;

    public PingPongBuffer(FramebufferSpec spec) {
        this.a = Framebuffers.screen(spec);
        this.b = Framebuffers.screen(spec);
    }

    public void pass(int passes, BiConsumer<Framebuffer, Framebuffer> body) {
        if (passes < 1) {
            throw new FramebufferException("PingPongBuffer.pass needs at least 1 pass, got " + passes);
        }
        for (int i = 0; i < passes; i++) {
            body.accept(a, b);
            Framebuffer tmp = a;
            a = b;
            b = tmp;
        }
    }

    public Framebuffer read() {
        return a;
    }

    public void dispose() {
        a.dispose();
        b.dispose();
    }
}
