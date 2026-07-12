package com.meekdev.amnetic.client.ui.inspector;

import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.LightType;
import com.meekdev.amnetic.client.light.internal.LightRegistry;
import com.meekdev.amnetic.client.shadow.ShadowSettings;
import com.meekdev.amnetic.client.shadow.Shadows;
import com.meekdev.amnetic.client.shadow.internal.ShadowMapPass;
import com.meekdev.amnetic.client.ui.Inspector;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

public final class ShadowsInspector extends Inspector {

    private static final String[] RESOLUTIONS = {"512", "1024", "2048"};
    private static final int[] RESOLUTION_PX = {512, 1024, 2048};

    public ShadowsInspector() {
        super("Renderer", "Shadows", false);
    }

    @Override
    public void render() {
        ImBoolean enabled = new ImBoolean(Shadows.isEnabled());
        if (ImGui.checkbox("Enable shadows", enabled)) Shadows.setEnabled(enabled.get());
        ImGui.sameLine();
        ImGui.textDisabled(ShadowMapPass.INSTANCE.isActive()
                ? "(" + ShadowMapPass.INSTANCE.spotCount() + " spot / " + ShadowMapPass.INSTANCE.pointCount() + " point"
                + (ShadowMapPass.INSTANCE.sunActive() ? " / sun x" + ShadowMapPass.INSTANCE.sunCascadeCount() : "") + " baking)"
                : "(idle)");
        ImGui.separator();

        settings();
        ImGui.separator();
        sunSettings();
        ImGui.separator();
        casters();
        ImGui.separator();
        stats();
    }

    private void stats() {
        if (ImGui.collapsingHeader("Stats & debug")) {
            ShadowMapPass pass = ShadowMapPass.INSTANCE;
            ImGui.text(String.format("Bake: %.2f ms", pass.lastBakeMs()));
            ImGui.text("Casters: " + pass.spotCount() + " spot, " + pass.pointCount() + " point"
                    + (pass.sunActive() ? ", sun (" + pass.sunCascadeCount() + " cascades)" : ""));
            ImGui.text("Entity occluders baked: " + pass.entityBoxes());
            ImGui.text(String.format("Shadow VRAM: %.1f MB", pass.vramBytes() / (1024.0 * 1024.0)));
        }
    }

    private void settings() {
        ShadowSettings s = ShadowSettings.defaults();
        if (ImGui.collapsingHeader("Map settings")) {
            ImInt res = new ImInt(resToIndex(s.resolution()));
            if (ImGui.combo("Resolution (px)", res, RESOLUTIONS)) s.resolution(RESOLUTION_PX[res.get()]);

            int[] maxSpot = {s.maxSpotShadows()};
            if (ImGui.sliderInt("Max spot casters", maxSpot, 0, ShadowSettings.MAX_SPOT)) s.maxSpotShadows(maxSpot[0]);

            int[] maxPoint = {s.maxPointShadows()};
            if (ImGui.sliderInt("Max point casters", maxPoint, 0, ShadowSettings.MAX_POINT)) s.maxPointShadows(maxPoint[0]);

            int[] budget = {s.bakeBudget()};
            if (ImGui.sliderInt("Bake budget/frame (0=all)", budget, 0, ShadowSettings.MAX_SPOT + ShadowSettings.MAX_POINT)) s.bakeBudget(budget[0]);

            float[] softness = {s.softness()};
            if (ImGui.sliderFloat("Softness (texels)", softness, 0f, 8f)) s.softness(softness[0]);

            ImBoolean pcss = new ImBoolean(s.pcss());
            if (ImGui.checkbox("Contact-hardening (PCSS)", pcss)) s.pcss(pcss.get());
            float[] lightSize = {s.lightSize()};
            if (ImGui.sliderFloat("Light size (penumbra)", lightSize, 0.1f, 16f)) s.lightSize(lightSize[0]);

            float[] bias = {s.bias()};
            if (ImGui.dragFloat("Depth bias", bias, 0.0001f, 0f, 0.05f, "%.4f")) s.bias(bias[0]);

            float[] normalBias = {s.normalBias()};
            if (ImGui.dragFloat("Normal bias (blocks)", normalBias, 0.005f, 0f, 0.5f)) s.normalBias(normalBias[0]);

            float[] maxDist = {s.maxDistance()};
            if (ImGui.dragFloat("Max distance (blocks)", maxDist, 1f, 8f, 512f)) s.maxDistance(maxDist[0]);

            float[] fade = {s.fadeStart()};
            if (ImGui.sliderFloat("Fade start (frac of max)", fade, 0f, 1f)) s.fadeStart(fade[0]);

            ImBoolean ents = new ImBoolean(s.entityShadows());
            if (ImGui.checkbox("Entity shadows", ents)) s.entityShadows(ents.get());
            ImBoolean entModels = new ImBoolean(s.entityModels());
            if (ImGui.checkbox("Entity model shape (off = bounding box)", entModels)) s.entityModels(entModels.get());
        }
    }

    private static final String[] SUN_RESOLUTIONS = {"1024", "2048", "4096"};
    private static final int[] SUN_RESOLUTION_PX = {1024, 2048, 4096};

    private void sunSettings() {
        ShadowSettings s = ShadowSettings.defaults();
        if (ImGui.collapsingHeader("Sun cascades")) {
            ImInt res = new ImInt(sunResToIndex(s.sunResolution()));
            if (ImGui.combo("Cascade resolution (px)", res, SUN_RESOLUTIONS)) s.sunResolution(SUN_RESOLUTION_PX[res.get()]);

            int[] cascades = {s.sunCascades()};
            if (ImGui.sliderInt("Cascades", cascades, 1, ShadowSettings.MAX_CASCADES)) s.sunCascades(cascades[0]);

            float[] dist = {s.sunDistance()};
            if (ImGui.dragFloat("Shadow distance (blocks)", dist, 1f, 16f, 512f)) s.sunDistance(dist[0]);

            float[] lambda = {s.sunSplitLambda()};
            if (ImGui.sliderFloat("Split lambda (0=uniform, 1=log)", lambda, 0f, 1f)) s.sunSplitLambda(lambda[0]);

            float[] extension = {s.sunCasterExtension()};
            if (ImGui.dragFloat("Caster extension (blocks)", extension, 1f, 0f, 256f)) s.sunCasterExtension(extension[0]);

            float[] blockR = {s.sunBlockOccluderRadius()};
            if (ImGui.dragFloat("Block occluder radius (0=off)", blockR, 1f, 0f, 96f)) s.sunBlockOccluderRadius(blockR[0]);
        }
    }

    private static int sunResToIndex(int res) {
        for (int i = 0; i < SUN_RESOLUTION_PX.length; i++) if (SUN_RESOLUTION_PX[i] == res) return i;
        return 1;
    }

    private void casters() {
        if (ImGui.collapsingHeader("Shadow casters")) {
            int idx = 0;
            int casting = 0;
            for (Light light : LightRegistry.INSTANCE.all()) {
                if (!light.isEnabled()) continue;
                boolean supported = light.type() == LightType.SPOT || light.type() == LightType.POINT
                        || light.type() == LightType.DIRECTIONAL;
                ImGui.pushID(idx++);
                ImBoolean casts = new ImBoolean(light.castsShadow());
                if (supported) {
                    if (ImGui.checkbox(label(light), casts)) light.castsShadow(casts.get());
                    if (light.castsShadow()) {
                        float[] str = {light.shadowStrength()};
                        if (ImGui.sliderFloat("strength", str, 0f, 1f)) light.shadowStrength(str[0]);
                    }
                } else {
                    ImGui.textDisabled(label(light) + " - shadows unsupported for this type");
                }
                ImGui.popID();
                if (supported && light.castsShadow()) casting++;
            }
            if (idx == 0) ImGui.textDisabled("No enabled lights.");
            else ImGui.textDisabled(casting + " of " + idx + " lights casting shadows");
        }
    }

    private static String label(Light light) {
        return String.format("%s @ (%.1f, %.1f, %.1f)", light.type().name(), light.x(), light.y(), light.z());
    }

    private static int resToIndex(int res) {
        for (int i = 0; i < RESOLUTION_PX.length; i++) if (RESOLUTION_PX[i] == res) return i;
        return 1;
    }
}
