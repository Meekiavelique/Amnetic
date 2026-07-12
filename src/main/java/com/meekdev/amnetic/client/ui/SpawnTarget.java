package com.meekdev.amnetic.client.ui;

import imgui.ImGui;
import imgui.type.ImBoolean;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

public final class SpawnTarget {

    private final ImBoolean useLook = new ImBoolean(true);
    private final float[] manual = new float[3];

    public void controls() {
        ImGui.checkbox("Use look point##spawn", useLook);
        if (useLook.get()) {
            Vec3 p = lookPoint();
            manual[0] = (float) p.x;
            manual[1] = (float) p.y;
            manual[2] = (float) p.z;
            ImGui.beginDisabled();
            ImGui.inputFloat3("Position##spawn", manual);
            ImGui.endDisabled();
        } else {
            ImGui.inputFloat3("Position##spawn", manual);
        }
    }

    public Vec3 position() {
        if (useLook.get()) return lookPoint();
        return new Vec3(manual[0], manual[1], manual[2]);
    }

    private static Vec3 lookPoint() {
        Minecraft mc = Minecraft.getInstance();
        HitResult hit = mc.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation();
        }
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 pos = cam.position();
        Vector3fc look = cam.forwardVector();
        double dist = 4.0;
        return pos.add(look.x() * dist, look.y() * dist, look.z() * dist);
    }
}
