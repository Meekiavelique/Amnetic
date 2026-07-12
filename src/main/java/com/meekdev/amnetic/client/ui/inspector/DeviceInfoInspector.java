package com.meekdev.amnetic.client.ui.inspector;

import com.meekdev.amnetic.client.compute.ComputeCapabilities;
import com.meekdev.amnetic.client.ui.Inspector;
import imgui.ImGui;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLCapabilities;

public final class DeviceInfoInspector extends Inspector {

    public DeviceInfoInspector() {
        super("Info", "Device Info", false);
    }

    @Override
    public void render() {
        ImGui.text("Vendor:   " + str(GL11.GL_VENDOR));
        ImGui.text("Renderer: " + str(GL11.GL_RENDERER));
        ImGui.text("Version:  " + str(GL11.GL_VERSION));
        ImGui.text("GLSL:     " + str(GL20.GL_SHADING_LANGUAGE_VERSION));
        ImGui.separator();

        GLCapabilities caps = GL.getCapabilities();
        cap("OpenGL 4.3", caps.OpenGL43);
        cap("OpenGL 4.6", caps.OpenGL46);
        cap("Compute shaders", ComputeCapabilities.isComputeAvailable());
        cap("ARB_compute_shader", caps.GL_ARB_compute_shader);
        cap("Shader storage buffers (SSBO)", caps.GL_ARB_shader_storage_buffer_object);
        cap("Direct state access", caps.GL_ARB_direct_state_access);
        cap("Bindless textures", caps.GL_ARB_bindless_texture);
        cap("Multi-draw indirect", caps.GL_ARB_multi_draw_indirect);
        cap("Buffer storage", caps.GL_ARB_buffer_storage);
    }

    private static void cap(String label, boolean supported) {
        if (supported) {
            ImGui.textColored(0.4f, 0.9f, 0.4f, 1f, "yes");
        } else {
            ImGui.textColored(0.9f, 0.4f, 0.4f, 1f, "no ");
        }
        ImGui.sameLine();
        ImGui.text(label);
    }

    private static String str(int name) {
        String s = GL11.glGetString(name);
        return s == null ? "?" : s;
    }
}
