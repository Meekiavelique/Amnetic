package com.meekdev.amnetic.client.render;

import com.meekdev.amnetic.client.dev.ShaderHotReload;
import org.slf4j.Logger;

public final class FailureGuard {

    private final int maxConsecutive;
    private final String name;
    private int consecutive;
    private boolean dead;
    // reload generation at death, a newer one means new shader source arrived so retry
    private long deadGeneration;
    private Logger log;

    public FailureGuard(String name, int maxConsecutive) {
        this.name = name;
        this.maxConsecutive = maxConsecutive;
    }

    public boolean alive() {
        if (dead && ShaderHotReload.generation() != deadGeneration) {
            dead = false;
            consecutive = 0;
            if (log != null) log.info("[{}] shader source changed, re-enabling", name);
        }
        return !dead;
    }

    public void success() { consecutive = 0; }

    public void fail(Logger log, Throwable e) {
        this.log = log;
        if (++consecutive >= maxConsecutive) {
            dead = true;
            deadGeneration = ShaderHotReload.generation();
            log.error("[{}] failed {} frames in a row; disabling until shader reload or restart", name, consecutive, e);
        } else {
            log.warn("[{}] failed (frame {}/{}); skipping this frame", name, consecutive, maxConsecutive, e);
        }
    }
}
