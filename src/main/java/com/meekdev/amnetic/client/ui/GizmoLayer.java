package com.meekdev.amnetic.client.ui;

import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.ui.scene.EditorSelection;
import com.meekdev.amnetic.client.ui.scene.Selectable;
import com.meekdev.amnetic.client.ui.scene.SelectableRegistry;
import com.meekdev.amnetic.client.ui.scene.Transformable;
import imgui.ImDrawList;
import imgui.ImGui;
import org.joml.Vector4f;

/**
 * in-world transform gizmo for the {@link EditorSelection}: move along X/Y/Z plus a free-move centre,
 * aim the facing, and resize. handles are projected to screen and dragged with the mouse, panels win
 * the mouse via WantCaptureMouse. screen-space math only via {@link Gizmos#project}, no world raycast
 */
public final class GizmoLayer {

    private static final float AXIS_LEN = 0.85f; // world length of the move arrows
    private static final float HIT = 8f; // pixel pick threshold
    private static final int NONE = 0, AX_X = 1, AX_Y = 2, AX_Z = 3, PLANE = 4, AIM = 5, RESIZE = 6;

    public static boolean enabled = true;
    private static int active = NONE;
    private static float lastX, lastY;

    private GizmoLayer() {}

    public static boolean isDragging() { return active != NONE; }

    public static void draw() {
        if (!enabled || !EditorSelection.has()) { active = NONE; return; }
        CameraSnapshot cam = CameraSnapshot.current();
        if (cam == null) { active = NONE; return; }

        Transformable t = resolve();
        if (t == null) { active = NONE; return; }

        float w = ImGui.getIO().getDisplaySizeX(), h = ImGui.getIO().getDisplaySizeY();
        Vector4f tmp = new Vector4f();
        float mx = ImGui.getMousePosX(), my = ImGui.getMousePosY();
        boolean down = ImGui.isMouseDown(0);
        boolean wantCapture = ImGui.getIO().getWantCaptureMouse();
        ImDrawList dl = ImGui.getBackgroundDrawList(); // behind panels (panels win the mouse via WantCaptureMouse)

        float[] o = Gizmos.project(cam, tmp, t.posX(), t.posY(), t.posZ(), w, h);
        if (o == null) { active = NONE; return; }

        float[] ex = axisEnd(cam, tmp, t, 1, 0, 0, w, h);
        float[] ey = axisEnd(cam, tmp, t, 0, 1, 0, w, h);
        float[] ez = axisEnd(cam, tmp, t, 0, 0, 1, w, h);

        // drag in progress
        if (active != NONE) {
            if (!down) { active = NONE; }
            else {
                float dx = mx - lastX, dy = my - lastY;
                applyDrag(t, cam, tmp, o, ex, ey, ez, dx, dy);
                lastX = mx; lastY = my;
                drawAll(dl, t, o, ex, ey, ez, active);
                return;
            }
        }

        // hover + maybe start
        int hover = NONE;
        float best = HIT;
        float d;
        if (ex != null && (d = segDist(mx, my, o, ex)) < best) { best = d; hover = AX_X; }
        if (ey != null && (d = segDist(mx, my, o, ey)) < best) { best = d; hover = AX_Y; }
        if (ez != null && (d = segDist(mx, my, o, ez)) < best) { best = d; hover = AX_Z; }
        if (dist(mx, my, o[0], o[1]) < 7f) hover = PLANE;
        float[] aim = aimHandle(cam, tmp, t, w, h);
        if (aim != null && dist(mx, my, aim[0], aim[1]) < 9f) hover = AIM;
        float[] rz = resizeHandle(cam, tmp, t, w, h);
        if (rz != null && dist(mx, my, rz[0], rz[1]) < 9f) hover = RESIZE;

        if (hover != NONE && ImGui.isMouseClicked(0) && !wantCapture) {
            active = hover; lastX = mx; lastY = my;
        }
        drawAll(dl, t, o, ex, ey, ez, hover);
    }

    private static Transformable resolve() {
        for (Selectable s : SelectableRegistry.all()) {
            if (EditorSelection.is(s)) return s.transform();
        }
        return null;
    }

    private static void applyDrag(Transformable t, CameraSnapshot cam, Vector4f tmp,
                                  float[] o, float[] ex, float[] ey, float[] ez, float dx, float dy) {
        switch (active) {
            case AX_X -> moveAxis(t, o, ex, 1, 0, 0, dx, dy);
            case AX_Y -> moveAxis(t, o, ey, 0, 1, 0, dx, dy);
            case AX_Z -> moveAxis(t, o, ez, 0, 0, 1, dx, dy);
            case PLANE -> movePlane(t, cam, tmp, dx, dy);
            case AIM -> aim(t, dx, dy);
            case RESIZE -> t.setExtent(Math.max(0f, t.extent() + dx * extentPerPixel(t, cam, tmp)));
            default -> {}
        }
    }

    private static void moveAxis(Transformable t, float[] o, float[] end, float ax, float ay, float az, float dx, float dy) {
        if (end == null) return;
        float sx = end[0] - o[0], sy = end[1] - o[1];
        float len = (float) Math.sqrt(sx * sx + sy * sy);
        if (len < 1e-3f) return;
        float along = (dx * sx + dy * sy) / len; // mouse delta projected onto the axis on screen
        float worldPerPx = AXIS_LEN / len;
        float wd = along * worldPerPx;
        t.setPosition(t.posX() + ax * wd, t.posY() + ay * wd, t.posZ() + az * wd);
    }

    private static void movePlane(Transformable t, CameraSnapshot cam, Vector4f tmp, float dx, float dy) {
        float rx = cam.view.m00(), ry = cam.view.m10(), rz = cam.view.m20(); // camera right (world)
        float ux = cam.view.m01(), uy = cam.view.m11(), uz = cam.view.m21(); // camera up (world)
        float wpp = extentPerPixel(t, cam, tmp);
        double nx = t.posX() + rx * dx * wpp - ux * dy * wpp;
        double ny = t.posY() + ry * dx * wpp - uy * dy * wpp;
        double nz = t.posZ() + rz * dx * wpp - uz * dy * wpp;
        t.setPosition(nx, ny, nz);
    }

    private static void aim(Transformable t, float dx, float dy) {
        if (!t.aimable()) return;
        float yaw = (float) Math.toDegrees(Math.atan2(t.dirX(), -t.dirZ())) + dx * 0.5f;
        float pitch = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, t.dirY())))) - dy * 0.5f;
        pitch = Math.max(-89.9f, Math.min(89.9f, pitch));
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        float hc = (float) Math.cos(p);
        t.setDirection((float) (hc * Math.sin(y)), (float) Math.sin(p), (float) (-hc * Math.cos(y)));
    }

    // world units per screen pixel at the object, measured via camera right
    private static float extentPerPixel(Transformable t, CameraSnapshot cam, Vector4f tmp) {
        float w = ImGui.getIO().getDisplaySizeX(), h = ImGui.getIO().getDisplaySizeY();
        float[] o = Gizmos.project(cam, tmp, t.posX(), t.posY(), t.posZ(), w, h);
        float rx = cam.view.m00(), ry = cam.view.m10(), rz = cam.view.m20();
        float[] e = Gizmos.project(cam, tmp, t.posX() + rx, t.posY() + ry, t.posZ() + rz, w, h);
        if (o == null || e == null) return 0.02f;
        float px = dist(o[0], o[1], e[0], e[1]);
        return px > 1e-3f ? 1f / px : 0.02f;
    }

    private static float[] axisEnd(CameraSnapshot cam, Vector4f tmp, Transformable t, float ax, float ay, float az, float w, float h) {
        return Gizmos.project(cam, tmp, t.posX() + ax * AXIS_LEN, t.posY() + ay * AXIS_LEN, t.posZ() + az * AXIS_LEN, w, h);
    }

    private static float[] aimHandle(CameraSnapshot cam, Vector4f tmp, Transformable t, float w, float h) {
        if (!t.aimable()) return null;
        float len = 1.3f;
        return Gizmos.project(cam, tmp, t.posX() + t.dirX() * len, t.posY() + t.dirY() * len, t.posZ() + t.dirZ() * len, w, h);
    }

    private static float[] resizeHandle(CameraSnapshot cam, Vector4f tmp, Transformable t, float w, float h) {
        if (!t.resizable()) return null;
        float rx = cam.view.m00(), ry = cam.view.m10(), rz = cam.view.m20();
        float e = Math.max(0.2f, t.extent());
        return Gizmos.project(cam, tmp, t.posX() + rx * e, t.posY() + ry * e, t.posZ() + rz * e, w, h);
    }

    private static void drawAll(ImDrawList dl, Transformable t, float[] o, float[] ex, float[] ey, float[] ez, int hl) {
        arrow(dl, o, ex, EditorTheme.AXIS_X, hl == AX_X);
        arrow(dl, o, ey, EditorTheme.AXIS_Y, hl == AX_Y);
        arrow(dl, o, ez, EditorTheme.AXIS_Z, hl == AX_Z);
        // centre free-move
        int c = hl == PLANE ? 0xFF66E0FF : 0xC0FFFFFF;
        dl.addRectFilled(o[0] - 4f, o[1] - 4f, o[0] + 4f, o[1] + 4f, c);
        CameraSnapshot cam = CameraSnapshot.current();
        Vector4f tmp = new Vector4f();
        float w = ImGui.getIO().getDisplaySizeX(), h = ImGui.getIO().getDisplaySizeY();
        if (cam != null) {
            float[] aim = aimHandle(cam, tmp, t, w, h);
            if (aim != null) {
                dl.addLine(o[0], o[1], aim[0], aim[1], 0xC000FFFF, 1.5f);
                dl.addCircleFilled(aim[0], aim[1], hl == AIM ? 6f : 4.5f, 0xFF00FFFF);
            }
            float[] rz = resizeHandle(cam, tmp, t, w, h);
            if (rz != null) {
                dl.addCircle(rz[0], rz[1], hl == RESIZE ? 7f : 5f, 0xFFE0E0E0, 16, 2f);
            }
        }
    }

    private static void arrow(ImDrawList dl, float[] o, float[] e, int col, boolean hot) {
        if (e == null) return;
        float th = hot ? 3.5f : 2f;
        dl.addLine(o[0], o[1], e[0], e[1], col, th);
        dl.addCircleFilled(e[0], e[1], hot ? 5f : 3.5f, col);
    }

    private static float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by; return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float segDist(float px, float py, float[] a, float[] b) {
        if (a == null || b == null) return 1e9f;
        float vx = b[0] - a[0], vy = b[1] - a[1];
        float wx = px - a[0], wy = py - a[1];
        float len2 = vx * vx + vy * vy;
        float tt = len2 < 1e-6f ? 0f : Math.max(0f, Math.min(1f, (wx * vx + wy * vy) / len2));
        float cx = a[0] + vx * tt, cy = a[1] + vy * tt;
        return dist(px, py, cx, cy);
    }
}
