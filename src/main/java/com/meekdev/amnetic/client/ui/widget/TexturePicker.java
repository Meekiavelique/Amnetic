package com.meekdev.amnetic.client.ui.widget;

import com.meekdev.amnetic.client.render.ImportedTextures;
import com.meekdev.amnetic.client.render.NativeFileDialog;
import imgui.ImGui;
import imgui.type.ImString;
import net.minecraft.resources.Identifier;

public final class TexturePicker {

    private TexturePicker() {}

    public static boolean draw(String label, ImString target) {
        boolean changed = ImGui.inputText(label, target);
        ImGui.sameLine();
        if (ImGui.button("File…##" + label)) {
            String path = NativeFileDialog.openImage();
            if (path != null && !path.isBlank()) {
                Identifier id = ImportedTextures.idForPath(path);
                if (id != null) { target.set(id.toString()); changed = true; }
            }
        }
        ImGui.sameLine();
        String popup = "texpick_" + label;
        if (ImGui.button("Browse##" + label)) ImGui.openPopup(popup);

        if (ImGui.beginPopup(popup)) {
            ImGui.textDisabled("Imported (" + ImportedTextures.dir() + ")");
            ImGui.separator();
            var files = ImportedTextures.list();
            if (files.isEmpty()) {
                ImGui.textDisabled("Drop .png files into that folder.");
            } else {
                for (String f : files) {
                    if (ImGui.selectable(f)) {
                        Identifier id = ImportedTextures.idFor(f);
                        if (id != null) { target.set(id.toString()); changed = true; }
                        ImGui.closeCurrentPopup();
                    }
                }
            }
            ImGui.endPopup();
        }
        return changed;
    }
}
