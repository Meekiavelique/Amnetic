package com.meekdev.amnetic.client.entityfx.internal;

import net.minecraft.resources.Identifier;

public interface TextureOverrideTarget {

    Identifier amnetic$getTextureOverride();

    boolean amnetic$isOverrideHideLayers();

    void amnetic$setTextureOverride(Identifier texture, boolean hideLayers);
}
