package com.meekdev.amnetic.client.ui.inspector;

import com.meekdev.amnetic.client.ui.Inspector;
import imgui.ImGui;

public final class DemoInspector extends Inspector {

    public DemoInspector() {
        super("Example", "ImGui Demo", false);
    }

    @Override
    public void render() {
        ImGui.text("The ImGui reference demo is shown in its own window.");
        ImGui.showDemoWindow();
    }
}
