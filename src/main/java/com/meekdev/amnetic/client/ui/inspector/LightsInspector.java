package com.meekdev.amnetic.client.ui.inspector;

import com.meekdev.amnetic.client.light.*;
import com.meekdev.amnetic.client.light.internal.LightRegistry;
import com.meekdev.amnetic.client.shadow.Shadows;
import com.meekdev.amnetic.client.ui.Inspector;
import com.meekdev.amnetic.client.ui.LightGizmo;
import com.meekdev.amnetic.client.ui.SpawnTarget;
import com.meekdev.amnetic.client.ui.scene.EditorSelection;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import java.util.IdentityHashMap;
import java.util.Map;

import imgui.type.ImString;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class LightsInspector extends Inspector {

    private static final String[] FALLOFF = {"SMOOTH", "LINEAR", "INVERSE_SQUARE", "EXPONENT"};
    private static final String[] IES = {"None", "Narrow", "Wide wash", "Multi-lobe", "Tight beam", "Ribbed"};

    private final ImString cookiePath = new ImString("minecraft:textures/block/glass.png", 256);
    private final SpawnTarget spawn = new SpawnTarget();
    private final Map<Light, String> names = new IdentityHashMap<>();
    private int nameCounter;

    public LightsInspector() {
        super("Renderer", "Lights", true);
    }

    @Override
    public void render() {
        spawn.controls();
        ImBoolean giz = new ImBoolean(LightGizmo.enabled);
        if (ImGui.checkbox("Show light gizmos", giz)) LightGizmo.enabled = giz.get();
        globalLighting();
        ImGui.separator();

        if (ImGui.beginTabBar("light_types")) {
            for (LightType type : LightType.values()) {
                if (ImGui.beginTabItem(type.name())) {
                    if (ImGui.button("Add " + type.name())) {
                        create(type, spawn.position());
                    }
                    ImGui.sameLine();
                    if (ImGui.button("Remove all " + type.name())) {
                        for (Light l : LightRegistry.INSTANCE.all()) {
                            if (l.type() == type) l.remove();
                        }
                    }
                    ImGui.separator();
                    int idx = 0;
                    for (Light light : LightRegistry.INSTANCE.all()) {
                        if (light.type() != type) continue;
                        ImGui.pushID(idx++);
                        editLight(light);
                        ImGui.popID();
                    }
                    ImGui.endTabItem();
                }
            }
            ImGui.endTabBar();
        }
    }

    private void globalLighting() {
        if (!ImGui.collapsingHeader("Global lighting")) return;
        LightSettings s = LightSettings.defaults();

        ImBoolean cs = new ImBoolean(s.contactShadows());
        if (ImGui.checkbox("Contact shadows (screen-space)", cs)) s.contactShadows(cs.get());

        ImBoolean vol = new ImBoolean(s.volumetric());
        if (ImGui.checkbox("Volumetric god-rays (master)", vol)) s.volumetric(vol.get());
        if (s.volumetric()) {
            float[] sc = {s.volumetricScale()};
            if (ImGui.sliderFloat("Resolution scale", sc, 0.25f, 1f)) s.volumetricScale(sc[0]);
            ImBoolean temp = new ImBoolean(s.volumetricTemporal());
            if (ImGui.checkbox("Temporal accumulation", temp)) s.volumetricTemporal(temp.get());
        }

        if (ImGui.inputText("Cookie texture", cookiePath)) { }
        ImGui.sameLine();
        if (ImGui.button("Set cookie")) {
            String t = cookiePath.get().trim();
            s.cookieTexture(t.isEmpty() ? null : Identifier.parse(t));
        }
    }

    private void editLight(Light light) {
        String name = names.computeIfAbsent(light, l -> "light_" + (nameCounter++));
        boolean selected = EditorSelection.is(light);
        if (selected) ImGui.setNextItemOpen(true);
        if (ImGui.collapsingHeader(selected ? name + "  <" : name)) {
            ImBoolean en = new ImBoolean(light.isEnabled());
            if (ImGui.checkbox("Enabled", en)) light.setEnabled(en.get());

            ImBoolean casts = new ImBoolean(light.castsShadow());
            if (ImGui.checkbox("Casts shadow", casts)) light.castsShadow(casts.get());
            ImGui.sameLine();
            ImGui.textDisabled(Shadows.isEnabled() ? "(shadows on)" : "(enable Shadows panel)");

            float[] pos = {(float) light.x(), (float) light.y(), (float) light.z()};
            if (ImGui.dragFloat3("Position", pos, 0.05f)) light.setPosition(pos[0], pos[1], pos[2]);
            if (ImGui.button("Set to spawn point")) {
                Vec3 p = spawn.position();
                light.setPosition(p.x, p.y, p.z);
            }

            float[] col = {light.red(), light.green(), light.blue()};
            if (ImGui.colorEdit3("Color", col)) light.setColor(col[0], col[1], col[2]);

            float[] kelvin = {6500f};
            if (ImGui.dragFloat("Temperature (K)", kelvin, 50f, 1000f, 40000f)) {
                light.setTemperature(kelvin[0]);
            }

            float[] intensity = {light.intensity()};
            if (ImGui.dragFloat("Intensity", intensity, 0.05f, 0f, 100f)) light.setIntensity(intensity[0]);

            LightType type = light.type();
            if (type != LightType.DIRECTIONAL) {
                float[] range = {light.range()};
                if (ImGui.dragFloat("Range", range, 0.1f, 0f, 256f)) light.setRange(range[0]);
            }
            if (type == LightType.SPOT || type == LightType.DIRECTIONAL
                    || type == LightType.AREA_RECT || type == LightType.AREA_DISC) {
                float dx = light.dirX(), dy = light.dirY(), dz = light.dirZ();
                float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
                float pitch = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, dy))));
                float[] yp = {yaw, pitch};
                if (ImGui.dragFloat2("Rotation (yaw/pitch)", yp, 1f, -180f, 180f)) {
                    double y = Math.toRadians(yp[0]);
                    double p = Math.toRadians(Math.max(-89.9f, Math.min(89.9f, yp[1])));
                    float h = (float) Math.cos(p);
                    light.setDirection((float) (h * Math.sin(y)), (float) Math.sin(p), (float) (-h * Math.cos(y)));
                }
            }
            if (type == LightType.SPOT) {
                float inner = (float) Math.toDegrees(Math.acos(clampCos(light.cosInner())));
                float outer = (float) Math.toDegrees(Math.acos(clampCos(light.cosOuter())));
                float[] angles = {inner, outer};
                if (ImGui.dragFloat2("Cone inner/outer (deg)", angles, 0.5f, 0f, 89f)) {
                    light.setSpotAngles(angles[0], angles[1]);
                }
                ImBoolean ck = new ImBoolean(light.cookie());
                if (ImGui.checkbox("Cookie (project global texture)", ck)) light.cookie(ck.get());
            }
            if (type == LightType.SPOT || type == LightType.POINT) {
                ImInt ies = new ImInt(light.iesProfile());
                if (ImGui.combo("IES profile", ies, IES)) light.iesProfile(ies.get());
            }
            if (type != LightType.DIRECTIONAL) {
                boolean on = light.godray() > 0f;
                ImBoolean gOn = new ImBoolean(on);
                if (ImGui.checkbox("Volumetric god-rays##gr", gOn)) light.godray(gOn.get() ? 1f : 0f);
                if (light.godray() > 0f) {
                    float[] gr = {light.godray()};
                    if (ImGui.sliderFloat("  strength", gr, 0f, 4f)) light.godray(gr[0]);
                    int[] gsteps = {light.godraySteps()};
                    if (ImGui.sliderInt("  steps", gsteps, 1, 64)) light.godraySteps(gsteps[0]);
                    float[] gdens = {light.godrayDensity()};
                    if (ImGui.sliderFloat("  density", gdens, 0f, 2f)) light.godrayDensity(gdens[0]);
                    float[] gani = {light.godrayAniso()};
                    if (ImGui.sliderFloat("  aniso (forward glow)", gani, 0f, 0.95f)) light.godrayAniso(gani[0]);
                    ImBoolean gsh = new ImBoolean(light.godrayShadows());
                    if (ImGui.checkbox("  shadowed##grsh", gsh)) light.godrayShadows(gsh.get());
                }
            }
            if (type == LightType.AREA_RECT || type == LightType.AREA_DISC) {
                float[] size = {light.areaW(), light.areaH()};
                if (ImGui.dragFloat2("Area size", size, 0.05f, 0f, 64f)) {
                    light.setAreaSize(size[0], size[1]);
                }
            }
            if (type == LightType.TUBE) {
                float[] len = {light.tubeLen()};
                if (ImGui.dragFloat("Tube length", len, 0.05f, 0f, 64f)) light.setTubeLength(len[0]);
            }

            ImInt falloff = new ImInt(Math.min(light.falloffId(), FALLOFF.length - 1));
            if (ImGui.combo("Falloff", falloff, FALLOFF)) {
                light.setFalloff(FalloffCurve.values()[falloff.get()]);
            }

            if (ImGui.button("Remove")) {
                light.remove();
                names.remove(light);
            }
        }
    }

    private static float clampCos(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }

    private static void create(LightType type, Vec3 p) {
        switch (type) {
            case POINT -> Lights.point(p, 1f, 1f, 1f, 12f, 1f);
            case SPOT -> Lights.spot(p, new Vector3f(0f, -1f, 0f), 12f, 25f, 1f, 1f, 1f, 12f, 1f);
            case DIRECTIONAL -> Lights.directional(new Vector3f(0f, -1f, 0f), 1f, 1f, 1f, 1f);
            case AREA_RECT -> Lights.areaRect(p, new Vector3f(0f, 1f, 0f), 1f, 1f, 1f, 1f, 1f, 12f, 1f);
            case AREA_DISC -> Lights.areaDisc(p, new Vector3f(0f, 1f, 0f), 1f, 1f, 1f, 1f, 12f, 1f);
            case TUBE -> Lights.tube(p, new Vector3f(1f, 0f, 0f), 2f, 1f, 1f, 1f, 12f, 1f);
        }
    }
}
