package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.entityfx.EntityEffect;
import com.meekdev.amnetic.client.entityfx.internal.EntityEffectRegistry;
import com.meekdev.amnetic.client.entityfx.internal.SurfaceTarget;
import com.meekdev.amnetic.client.geometry.PosedMesh;
import com.meekdev.amnetic.client.geometry.internal.MeshTapRegistry;
import com.meekdev.amnetic.client.geometry.internal.MeshTapTarget;
import com.meekdev.amnetic.client.entityfx.internal.TextureOverrideRegistry;
import com.meekdev.amnetic.client.entityfx.internal.TextureOverrideTarget;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void amnetic$stampSurfaceEffect(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        if (!EntityEffectRegistry.INSTANCE.isEmpty()) {
            EntityEffect effect = EntityEffectRegistry.INSTANCE.get(entity);
            ((SurfaceTarget) state).amnetic$setSurfaceEffect(effect != null && !effect.isRemoved() ? effect : null);
        }
        if (!MeshTapRegistry.INSTANCE.isEmpty()) {
            PosedMesh tap = MeshTapRegistry.INSTANCE.get(entity);
            ((MeshTapTarget) state).amnetic$setMeshTap(tap != null && !tap.isRemoved() ? tap : null);
        }
        if (!TextureOverrideRegistry.INSTANCE.isEmpty()) {
            TextureOverrideRegistry.Entry e = TextureOverrideRegistry.INSTANCE.get(entity);
            ((TextureOverrideTarget) state).amnetic$setTextureOverride(
                    e != null ? e.texture() : null, e != null && e.hideLayers());
        } else {
            ((TextureOverrideTarget) state).amnetic$setTextureOverride(null, false);
        }
    }
}
