package com.meekdev.amnetic.client.pipeline;

import com.meekdev.amnetic.client.pipeline.internal.GpuTimer;

public final class PassHandle {
    final RenderStage stage;
    final RenderPass pass;
    final int order;
    final String label;
    volatile boolean enabled = true;
    volatile boolean removed;
    GpuTimer gpuTimer;
    private PassProfiler.Entry profile;

    PassHandle(RenderStage stage, RenderPass pass, int order, String label) {
        this.stage = stage;
        this.pass = pass;
        this.order = order;
        this.label = label;
    }

    public String label() {
        return label;
    }

    PassProfiler.Entry profile() {
        if (profile == null) profile = PassProfiler.INSTANCE.entry(stage, label != null ? label : pass.getClass().getSimpleName());
        return profile;
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
