package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.entityfx.EntityEffect;
import com.meekdev.amnetic.client.entityfx.internal.SurfaceTarget;
import com.meekdev.amnetic.client.geometry.PosedMesh;
import com.meekdev.amnetic.client.geometry.internal.MeshTapTarget;
import com.meekdev.amnetic.client.entityfx.internal.TextureOverrideTarget;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements SurfaceTarget, MeshTapTarget, TextureOverrideTarget {

    @Unique
    private EntityEffect amnetic$surfaceEffect;
    @Unique
    private PosedMesh amnetic$meshTap;
    @Unique
    private Identifier amnetic$overrideTexture;
    @Unique
    private boolean amnetic$overrideHideLayers;

    @Override
    public EntityEffect amnetic$getSurfaceEffect() {
        return amnetic$surfaceEffect;
    }

    @Override
    public void amnetic$setSurfaceEffect(EntityEffect effect) {
        this.amnetic$surfaceEffect = effect;
    }

    @Override
    public PosedMesh amnetic$getMeshTap() {
        return amnetic$meshTap;
    }

    @Override
    public void amnetic$setMeshTap(PosedMesh tap) {
        this.amnetic$meshTap = tap;
    }

    @Override
    public Identifier amnetic$getTextureOverride() {
        return amnetic$overrideTexture;
    }

    @Override
    public boolean amnetic$isOverrideHideLayers() {
        return amnetic$overrideHideLayers;
    }

    @Override
    public void amnetic$setTextureOverride(Identifier texture, boolean hideLayers) {
        this.amnetic$overrideTexture = texture;
        this.amnetic$overrideHideLayers = hideLayers;
    }
}
