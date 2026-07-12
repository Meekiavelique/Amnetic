package com.meekdev.amnetic.client.ui.inspector;

import com.meekdev.amnetic.client.bloom.Bloom;
import com.meekdev.amnetic.client.bloom.BloomSettings;
import com.meekdev.amnetic.client.grade.ColorGrade;
import com.meekdev.amnetic.client.grade.ColorGradeSettings;
import com.meekdev.amnetic.client.ui.Inspector;
import imgui.ImGui;
import imgui.type.ImBoolean;

public final class PostFxInspector extends Inspector {

    public PostFxInspector() {
        super("Renderer", "Post FX", false);
    }

    @Override
    public void render() {
        bloom();
        ImGui.separator();
        colorGrade();
    }

    private void bloom() {
        BloomSettings b = Bloom.settings();
        if (ImGui.collapsingHeader("Bloom")) {
            ImBoolean enabled = new ImBoolean(b.isEnabled());
            if (ImGui.checkbox("Enabled##bloom", enabled)) b.enabled(enabled.get());
            ImBoolean all = new ImBoolean(b.isAll());
            if (ImGui.checkbox("Bloom everything (not just emissive)", all)) b.all(all.get());
            ImBoolean occlude = new ImBoolean(b.isOcclude());
            if (ImGui.checkbox("Terrain occludes", occlude)) b.occlude(occlude.get());
            float[] intensity = {b.intensity()};
            if (ImGui.dragFloat("Intensity##bloom", intensity, 0.02f, 0f, 10f)) b.intensity(intensity[0]);
            int[] levels = {b.levels()};
            if (ImGui.sliderInt("Levels", levels, 2, 8)) b.levels(levels[0]);
            float[] scale = {b.scale()};
            if (ImGui.sliderFloat("Scale", scale, 0.05f, 1f)) b.scale(scale[0]);
        }
    }

    private void colorGrade() {
        ColorGradeSettings c = ColorGrade.settings();
        if (ImGui.collapsingHeader("Color Grade")) {
            ImBoolean enabled = new ImBoolean(c.isEnabled());
            if (ImGui.checkbox("Enabled##grade", enabled)) c.enabled(enabled.get());
            float[] exposure = {c.exposure()};
            if (ImGui.dragFloat("Exposure", exposure, 0.01f, 0f, 8f)) c.exposure(exposure[0]);
            float[] contrast = {c.contrast()};
            if (ImGui.dragFloat("Contrast", contrast, 0.01f, 0f, 4f)) c.contrast(contrast[0]);
            float[] saturation = {c.saturation()};
            if (ImGui.dragFloat("Saturation", saturation, 0.01f, 0f, 4f)) c.saturation(saturation[0]);
            float[] brightness = {c.brightness()};
            if (ImGui.dragFloat("Brightness", brightness, 0.01f, -1f, 1f)) c.brightness(brightness[0]);
            float[] temperature = {c.temperature()};
            if (ImGui.sliderFloat("Temperature", temperature, -1f, 1f)) c.temperature(temperature[0]);
            float[] tint = {c.tint()};
            if (ImGui.sliderFloat("Tint", tint, -1f, 1f)) c.tint(tint[0]);
            float[] gamma = {c.gamma()};
            if (ImGui.dragFloat("Gamma", gamma, 0.01f, 0.01f, 4f)) c.gamma(gamma[0]);
            float[] lutIntensity = {c.lutIntensity()};
            if (ImGui.sliderFloat("LUT intensity", lutIntensity, 0f, 1f)) c.lutIntensity(lutIntensity[0]);
        }
    }
}
