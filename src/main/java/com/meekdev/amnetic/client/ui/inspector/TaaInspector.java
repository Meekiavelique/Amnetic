package com.meekdev.amnetic.client.ui.inspector;

import com.meekdev.amnetic.client.taa.Taa;
import com.meekdev.amnetic.client.taa.TaaSettings;
import com.meekdev.amnetic.client.ui.Inspector;
import imgui.ImGui;
import imgui.type.ImBoolean;

public final class TaaInspector extends Inspector {

    public TaaInspector() {
        super("Renderer", "TAA", false);
    }

    @Override
    public void render() {
        TaaSettings s = Taa.settings();
        ImBoolean enabled = new ImBoolean(s.isEnabled());
        if (ImGui.checkbox("Enable TAA", enabled)) s.enabled(enabled.get());
        ImGui.separator();

        float[] feedback = {s.feedback()};
        if (ImGui.sliderFloat("History feedback", feedback, 0f, 0.98f)) s.feedback(feedback[0]);

        float[] sharpness = {s.sharpness()};
        if (ImGui.sliderFloat("CAS sharpness", sharpness, 0f, 1f)) s.sharpness(sharpness[0]);
    }
}
