package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.camera.internal.Orthographic;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Frustum.class)
public class FrustumMixin {

    @Inject(method = "offsetToFullyIncludeCameraCube", at = @At("HEAD"), cancellable = true)
    private void amnetic$orthographicNeverRecedes(int size, CallbackInfoReturnable<Frustum> cir) {
        if (Orthographic.active()) cir.setReturnValue((Frustum) (Object) this);
    }
}
