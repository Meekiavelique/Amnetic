package com.meekdev.amnetic.client.ui;

import com.meekdev.amnetic.client.decal.Decal;
import com.meekdev.amnetic.client.decal.Decals;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.ui.scene.EditorSelection;
import imgui.ImDrawList;
import imgui.ImGui;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class DecalGizmo {

    public static boolean enabled = true;

    private DecalGizmo() {}

    public static void draw() {
        if (!enabled) return;
        CameraSnapshot cam = CameraSnapshot.current();
        if (cam == null) return;
        float w = ImGui.getIO().getDisplaySizeX(), h = ImGui.getIO().getDisplaySizeY();
        if (w <= 0 || h <= 0) return;

        ImDrawList dl = ImGui.getBackgroundDrawList(); // behind panels, over the world
        Vector4f tmp = new Vector4f();
        float mx = ImGui.getMousePosX(), my = ImGui.getMousePosY();
        boolean clickable = ImGui.isMouseClicked(0) && !ImGui.getIO().getWantCaptureMouse() && !GizmoLayer.isDragging();
        Decal hit = null;
        float hitD2 = 100f;

        for (Decal d : Decals.active()) {
            if (d.isRemoved()) continue;
            Vector3f n = new Vector3f(d.normal()).normalize();
            Vector3f right = new Vector3f(n).cross(0f, 1f, 0f);
            if (right.lengthSquared() < 1e-6f) right.set(1f, 0f, 0f);
            right.normalize();
            Vector3f up = new Vector3f(right).cross(n).normalize();
            Vec3 c = d.center();
            // decal local space is [-0.5,0.5] so half extents here
            float halfW = d.width() * 0.5f, halfH = d.height() * 0.5f, halfD = d.depth() * 0.5f;

            float[][] corners = new float[8][];
            int i = 0;
            for (int sx = -1; sx <= 1; sx += 2)
                for (int sy = -1; sy <= 1; sy += 2)
                    for (int sz = -1; sz <= 1; sz += 2) {
                        double px = c.x + right.x * halfW * sx + up.x * halfH * sy + n.x * halfD * sz;
                        double py = c.y + right.y * halfW * sx + up.y * halfH * sy + n.y * halfD * sz;
                        double pz = c.z + right.z * halfW * sx + up.z * halfH * sy + n.z * halfD * sz;
                        corners[i++] = Gizmos.project(cam, tmp, px, py, pz, w, h);
                    }
            int[][] edges = {
                    {0,1},{0,2},{0,4},{1,3},{1,5},{2,3},{2,6},{3,7},{4,5},{4,6},{5,7},{6,7}
            };
            boolean isSel = EditorSelection.is(d);
            int color = isSel ? Gizmos.col(1f, 0.85f, 0.2f, 1f) : Gizmos.col(0.3f, 0.9f, 1f, 0.8f);
            for (int[] e : edges) {
                float[] a = corners[e[0]], b = corners[e[1]];
                if (a != null && b != null) dl.addLine(a[0], a[1], b[0], b[1], color, isSel ? 1.8f : 1.2f);
            }
            float[] cs = Gizmos.project(cam, tmp, c.x, c.y, c.z, w, h);
            if (cs != null) {
                dl.addCircleFilled(cs[0], cs[1], 4f, color);
                float d2 = (mx - cs[0]) * (mx - cs[0]) + (my - cs[1]) * (my - cs[1]);
                if (d2 <= hitD2) { hit = d; hitD2 = d2; }
            }
        }
        if (clickable && hit != null) EditorSelection.setTarget(hit);
    }
}
