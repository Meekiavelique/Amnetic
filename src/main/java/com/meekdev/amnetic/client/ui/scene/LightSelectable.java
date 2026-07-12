package com.meekdev.amnetic.client.ui.scene;

import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.LightType;
import com.meekdev.amnetic.client.ui.widget.Widgets;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

/** adapter exposing a {@link Light} to the editor (outliner entry + gizmo transform + inspector controls) */
public final class LightSelectable implements Selectable, Transformable {

    private static final String[] IES = {"None", "Narrow", "Wide wash", "Multi-lobe", "Tight beam", "Ribbed"};

    private final Light light;
    private final String name;

    public LightSelectable(Light light, String name) {
        this.light = light;
        this.name = name;
    }

    @Override public Object target() { return light; }
    @Override public String category() { return "Lights"; }
    @Override public String displayName() { return name + "  (" + light.type().name().toLowerCase() + ")"; }
    @Override public Transformable transform() { return this; }
    @Override public void remove() { light.remove(); }

    @Override public double posX() { return light.x(); }
    @Override public double posY() { return light.y(); }
    @Override public double posZ() { return light.z(); }
    @Override public void setPosition(double x, double y, double z) { light.setPosition(x, y, z); }

    @Override public boolean aimable() {
        LightType t = light.type();
        return t == LightType.SPOT || t == LightType.DIRECTIONAL || t == LightType.AREA_RECT || t == LightType.AREA_DISC;
    }
    @Override public float dirX() { return light.dirX(); }
    @Override public float dirY() { return light.dirY(); }
    @Override public float dirZ() { return light.dirZ(); }
    @Override public void setDirection(float x, float y, float z) { light.setDirection(x, y, z); }

    @Override public boolean resizable() { return light.type() != LightType.DIRECTIONAL; }
    @Override public float extent() { return light.range(); }
    @Override public void setExtent(float e) { light.setRange(e); }

    @Override
    public void renderInspector() {
        ImBoolean en = new ImBoolean(light.isEnabled());
        if (Widgets.checkbox("Enabled", en)) light.setEnabled(en.get());

        float[] pos = {(float) light.x(), (float) light.y(), (float) light.z()};
        if (Widgets.vec3("Position", pos, 0.05f)) light.setPosition(pos[0], pos[1], pos[2]);

        float[] col = {light.red(), light.green(), light.blue()};
        if (Widgets.color3("Color", col)) light.setColor(col[0], col[1], col[2]);

        float[] intensity = {light.intensity()};
        if (Widgets.dragFloat("Intensity", intensity, 0.05f, 0f, 100f)) light.setIntensity(intensity[0]);

        LightType type = light.type();
        if (type != LightType.DIRECTIONAL) {
            float[] range = {light.range()};
            if (Widgets.dragFloat("Range", range, 0.1f, 0f, 256f)) light.setRange(range[0]);
        }
        if (aimable()) {
            float dx = light.dirX(), dy = light.dirY(), dz = light.dirZ();
            float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
            float pitch = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, dy))));
            float[] yp = {yaw, pitch};
            if (vec2("Rotation yaw/pitch", yp)) {
                double y = Math.toRadians(yp[0]);
                double p = Math.toRadians(Math.max(-89.9f, Math.min(89.9f, yp[1])));
                float h = (float) Math.cos(p);
                light.setDirection((float) (h * Math.sin(y)), (float) Math.sin(p), (float) (-h * Math.cos(y)));
            }
        }
        if (type == LightType.SPOT) {
            float inner = (float) Math.toDegrees(Math.acos(clampCos(light.cosInner())));
            float outer = (float) Math.toDegrees(Math.acos(clampCos(light.cosOuter())));
            float[] ang = {inner, outer};
            if (vec2("Cone in/out", ang)) light.setSpotAngles(ang[0], ang[1]);
            ImBoolean ck = new ImBoolean(light.cookie());
            if (Widgets.checkbox("Cookie", ck)) light.cookie(ck.get());
        }
        if (type == LightType.SPOT || type == LightType.POINT) {
            ImInt ies = new ImInt(light.iesProfile());
            if (Widgets.combo("IES profile", ies, IES)) light.iesProfile(ies.get());
        }

        if (Widgets.section("Shadows")) {
            ImBoolean casts = new ImBoolean(light.castsShadow());
            if (Widgets.checkbox("Casts shadow", casts)) light.castsShadow(casts.get());
            if (light.castsShadow()) {
                float[] str = {light.shadowStrength()};
                if (Widgets.slider("Strength", str, 0f, 1f)) light.shadowStrength(str[0]);
            }
        }
        if (type != LightType.DIRECTIONAL && Widgets.section("God-rays")) {
            boolean on = light.godray() > 0f;
            ImBoolean gOn = new ImBoolean(on);
            if (Widgets.checkbox("Enabled##gr", gOn)) light.godray(gOn.get() ? 1f : 0f);
            if (light.godray() > 0f) {
                float[] gr = {light.godray()};
                if (Widgets.slider("Strength##gr", gr, 0f, 4f)) light.godray(gr[0]);
                int[] st = {light.godraySteps()};
                if (Widgets.sliderInt("Steps", st, 1, 64)) light.godraySteps(st[0]);
                float[] de = {light.godrayDensity()};
                if (Widgets.slider("Density", de, 0f, 2f)) light.godrayDensity(de[0]);
            }
        }
    }

    private static boolean vec2(String lbl, float[] v) {
        ImGui.alignTextToFramePadding();
        ImGui.text(lbl);
        ImGui.sameLine(116f);
        ImGui.setNextItemWidth(-1f);
        return ImGui.dragFloat2("##" + lbl, v, 0.5f);
    }

    private static float clampCos(float c) { return Math.max(-1f, Math.min(1f, c)); }
}
