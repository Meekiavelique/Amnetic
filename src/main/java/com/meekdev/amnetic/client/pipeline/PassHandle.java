package com.meekdev.amnetic.client.pipeline;

import com.meekdev.amnetic.client.pipeline.internal.GpuTimer;

// handle to a registered RenderPass, used to toggle or remove it after registration
public final class PassHandle {

    final RenderStage stage;
    final RenderPass pass;
    final int order;
    final String label;
    volatile boolean enabled = true;
    volatile boolean removed;
    GpuTimer gpuTimer;

    PassHandle(RenderStage stage, RenderPass pass, int order, String label) {
        this.stage = stage;
        this.pass = pass;
        this.order = order;
        this.label = label;
    }

    public String label() {
        return label;
    }

    public PassHandle setEnabled(boolean v) {
        this.enabled = v;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void remove() {
        Pipeline.remove(this);
    }
}
