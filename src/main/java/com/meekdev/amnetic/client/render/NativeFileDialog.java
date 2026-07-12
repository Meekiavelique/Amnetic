package com.meekdev.amnetic.client.render;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class NativeFileDialog {

    private NativeFileDialog() {}

    public static String openImage() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.pointers(
                    stack.UTF8("*.png"), stack.UTF8("*.jpg"), stack.UTF8("*.jpeg"),
                    stack.UTF8("*.tga"), stack.UTF8("*.bmp"));
            return TinyFileDialogs.tinyfd_openFileDialog("Choose a texture", "", filters, "Image files", false);
        } catch (Throwable t) {
            return null; // natives missing or dialog failed
        }
    }
}
