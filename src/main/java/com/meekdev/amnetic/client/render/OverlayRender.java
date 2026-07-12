package com.meekdev.amnetic.client.render;

import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.scene.internal.CaptureManager;
import net.minecraft.client.Minecraft;

public final class OverlayRender {

    private OverlayRender() {}

    public static void render() {
        if (CaptureManager.INSTANCE.isCapturing()) return;
        CameraSnapshot cam = CameraSnapshot.current();
        if (cam == null) return;
        Minecraft mc = Minecraft.getInstance();
        float delta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        int prevFbo = MainTargetFramebuffer.bind();
        try {
            InstanceMeshRegistry.INSTANCE.renderAll(InstancePhase.OVERLAY, mc, delta, cam.view, cam.projection);
        } catch (Throwable e) {

        } finally {
            MainTargetFramebuffer.restore(prevFbo);
        }
    }
}
