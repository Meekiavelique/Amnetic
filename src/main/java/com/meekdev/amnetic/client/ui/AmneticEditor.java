package com.meekdev.amnetic.client.ui;

import com.meekdev.amnetic.client.ui.inspector.DecalsInspector;
import com.meekdev.amnetic.client.ui.inspector.DemoInspector;
import com.meekdev.amnetic.client.ui.inspector.DeviceInfoInspector;
import com.meekdev.amnetic.client.ui.inspector.GBufferInspector;
import com.meekdev.amnetic.client.ui.inspector.LightsInspector;
import com.meekdev.amnetic.client.ui.inspector.ParticleEditor;
import com.meekdev.amnetic.client.ui.inspector.ParticlesInspector;
import com.meekdev.amnetic.client.ui.inspector.PostFxInspector;
import com.meekdev.amnetic.client.ui.inspector.ProfilerInspector;
import com.meekdev.amnetic.client.ui.inspector.ShadowsInspector;
import com.meekdev.amnetic.client.ui.inspector.SsaoInspector;
import com.meekdev.amnetic.client.ui.inspector.SsgiInspector;
import com.meekdev.amnetic.client.ui.inspector.TaaInspector;
import com.meekdev.amnetic.client.ui.inspector.StatsInspector;
import foundry.imgui.api.ImGuiMC;
import foundry.imgui.api.ImGuiMCEvents;
import imgui.ImFont;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AmneticEditor {

    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic/Editor");

    private static AmneticEditor instance;

    private static final float FONT_SIZE = 16f;

    private final List<Inspector> inspectors = new ArrayList<>();
    private boolean enabled;
    private ImFont font;

    private AmneticEditor() {
        inspectors.add(new LightsInspector());
        inspectors.add(new ShadowsInspector());
        inspectors.add(new SsaoInspector());
        inspectors.add(new SsgiInspector());
        inspectors.add(new TaaInspector());
        inspectors.add(new DecalsInspector());
        inspectors.add(new ParticlesInspector());
        inspectors.add(new ParticleEditor());
        inspectors.add(new PostFxInspector());
        inspectors.add(new GBufferInspector());
        inspectors.add(new ProfilerInspector());
        inspectors.add(new StatsInspector());
        inspectors.add(new DeviceInfoInspector());
        inspectors.add(new DemoInspector());
    }

    public static void init() {
        if (instance != null) return;
        instance = new AmneticEditor();
        ImGuiMCEvents.INSTANCE.postRenderImGuiEvent(instance::render);
        LOGGER.info("Amnetic editor ready (toggle with the configured keybind)");
    }

    public static void register(Inspector inspector) {
        if (instance != null && inspector != null) instance.inspectors.add(inspector);
    }

    public static void toggle() {
        if (instance != null) instance.enabled = !instance.enabled;
    }

    public static boolean isEnabled() {
        return instance != null && instance.enabled;
    }

    private void render() {
        if (!enabled) return;
        EditorTheme.apply();
        ImFont f = font();
        boolean pushed = f != null;
        if (pushed) ImGui.pushFont(f, FONT_SIZE);
        try {
            renderMenuBar();
            renderWindows();
            // gizmo handles claim the click first (start a drag), the marker gizmos then skip
            // select/deselect while a drag is active so grabbing a handle doesn't clear the selection
            GizmoLayer.draw();
            LightGizmo.draw();
            DecalGizmo.draw();
        } catch (Throwable t) {
            LOGGER.error("editor frame failed, disabling overlay", t);
            enabled = false;
        } finally {
            if (pushed) ImGui.popFont();
        }
    }

    private ImFont font() {
        if (font == null) {
            font = ImGuiMC.getFont(ImGuiMC.FONT_DEFAULT, false, false);
        }
        return font;
    }

    private void renderMenuBar() {
        if (!ImGui.beginMainMenuBar()) return;

        Map<String, List<Inspector>> groups = new LinkedHashMap<>();
        for (Inspector insp : inspectors) {
            groups.computeIfAbsent(insp.group(), g -> new ArrayList<>()).add(insp);
        }
        for (Map.Entry<String, List<Inspector>> e : groups.entrySet()) {
            if (ImGui.beginMenu(e.getKey())) {
                for (Inspector insp : e.getValue()) {
                    ImBoolean open = insp.open();
                    if (ImGui.menuItem(insp.title(), "", open.get())) {
                        open.set(!open.get());
                    }
                }
                ImGui.endMenu();
            }
        }
        ImGui.endMainMenuBar();
    }

    private void renderWindows() {
        for (Inspector insp : inspectors) {
            ImBoolean open = insp.open();
            if (!open.get()) continue;
            ImGui.setNextWindowSize(440f, 520f, ImGuiCond.FirstUseEver);
            boolean shown = ImGui.begin(insp.title(), open);
            try {
                if (shown) insp.render();
            } catch (Throwable t) {
                LOGGER.error("inspector '{}' failed, closing it", insp.title(), t);
                open.set(false);
            } finally {
                ImGui.end();
            }
        }
    }
}
