package com.meekdev.amnetic.client.ui.inspector;

import com.meekdev.amnetic.client.ssgi.Ssgi;
import com.meekdev.amnetic.client.ssgi.SsgiSettings;
import com.meekdev.amnetic.client.ui.Inspector;
import imgui.ImGui;
import imgui.type.ImBoolean;

public final class SsgiInspector extends Inspector {

    public SsgiInspector() {
        super("Renderer", "SSGI", false);
    }

    @Override
    public void render() {
        SsgiSettings s = Ssgi.settings();
        ImBoolean enabled = new ImBoolean(s.isEnabled());
        if (ImGui.checkbox("Enable SSGI", enabled)) s.enabled(enabled.get());
        ImGui.separator();

        float[] radius = {s.radius()};
        if (ImGui.dragFloat("Radius (blocks)", radius, 0.05f, 0.1f, 16f)) s.radius(radius[0]);

        float[] intensity = {s.intensity()};
        if (ImGui.sliderFloat("Intensity", intensity, 0f, 4f)) s.intensity(intensity[0]);

        float[] scale = {s.scale()};
        if (ImGui.sliderFloat("Render scale", scale, 0.25f, 1f)) s.scale(scale[0]);
    }
}
