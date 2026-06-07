package com.meekdev.amnetic.client.post;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;

public final class PostEffectContext {

    private final Minecraft client;
    private final float deltaTick;
    private final int screenWidth;
    private final int screenHeight;
    private final PostChain processor;

    public PostEffectContext(Minecraft client, float deltaTick, int screenWidth, int screenHeight, PostChain processor) {
        this.client = client;
        this.deltaTick = deltaTick;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.processor = processor;
    }

    public Minecraft getClient() {
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

    public PostChain getProcessor() {
        return processor;
    }
}
