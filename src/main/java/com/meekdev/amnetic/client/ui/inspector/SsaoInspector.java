package com.meekdev.amnetic.client.ui.inspector;

import com.meekdev.amnetic.client.ssao.Ssao;
import com.meekdev.amnetic.client.ssao.SsaoSettings;
import com.meekdev.amnetic.client.ui.Inspector;
import imgui.ImGui;
import imgui.type.ImBoolean;

public final class SsaoInspector extends Inspector {

    public SsaoInspector() {
        super("Renderer", "SSAO", false);
    }

    @Override
    public void render() {
        SsaoSettings s = Ssao.settings();
        ImBoolean enabled = new ImBoolean(s.isEnabled());
        if (ImGui.checkbox("Enable SSAO", enabled)) s.enabled(enabled.get());
        ImGui.separator();

        float[] radius = {s.radius()};
        if (ImGui.dragFloat("Radius (blocks)", radius, 0.02f, 0.05f, 5f)) s.radius(radius[0]);

        float[] intensity = {s.intensity()};
        if (ImGui.sliderFloat("Intensity", intensity, 0f, 4f)) s.intensity(intensity[0]);

        float[] bias = {s.bias()};
        if (ImGui.dragFloat("Bias", bias, 0.001f, 0f, 0.2f, "%.3f")) s.bias(bias[0]);

        float[] power = {s.power()};
        if (ImGui.sliderFloat("Power (contrast)", power, 0.1f, 4f)) s.power(power[0]);

        float[] scale = {s.scale()};
        if (ImGui.sliderFloat("Render scale", scale, 0.25f, 1f)) s.scale(scale[0]);

        ImBoolean temporal = new ImBoolean(s.temporal());
        if (ImGui.checkbox("Temporal denoise (TAA-lite)", temporal)) s.temporal(temporal.get());
        if (s.temporal()) {
            float[] fb = {s.feedback()};
            if (ImGui.sliderFloat("History feedback", fb, 0f, 0.95f)) s.feedback(fb[0]);
        }
    }
}
