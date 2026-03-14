package com.meekdev.amnetic.client.post;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;

public final class PostEffectContext {

    private final MinecraftClient client;
    private final float deltaTick;
    private final int screenWidth;
    private final int screenHeight;
    private final PostEffectProcessor processor;

    public PostEffectContext(MinecraftClient client, float deltaTick, int screenWidth, int screenHeight, PostEffectProcessor processor) {
        this.client = client;
        this.deltaTick = deltaTick;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.processor = processor;
    }

    public MinecraftClient getClient() {
        return client;
    }

    public float getDeltaTick() {
        return deltaTick;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public PostEffectProcessor getProcessor() {
        return processor;
    }
}
