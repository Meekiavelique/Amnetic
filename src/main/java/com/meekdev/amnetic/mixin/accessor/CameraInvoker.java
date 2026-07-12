package com.meekdev.amnetic.mixin.accessor;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraInvoker {

    @Invoker("setPosition")
    void amnetic$setPosition(Vec3 pos);

    @Invoker("setRotation")
    void amnetic$setRotation(float yaw, float pitch);

    @Accessor("cullFrustum")
    Frustum amnetic$getCullFrustum();

    @Accessor("cullFrustum")
    void amnetic$setCullFrustum(Frustum frustum);

    @Accessor("xRot")
    void amnetic$setXRot(float xRot);

    @Accessor("yRot")
    void amnetic$setYRot(float yRot);

    @Accessor("matrixPropertiesDirty")
    int amnetic$getMatrixPropertiesDirty();

    @Accessor("matrixPropertiesDirty")
    void amnetic$setMatrixPropertiesDirty(int value);
}
