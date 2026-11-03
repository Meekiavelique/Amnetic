package com.meekdev.amnetic.client.render;
//? if >=26.3 {
import com.mojang.blaze3d.systems.RenderSystem;
//?}

public final class NativeCommandBatch {
    private static int depth;
    private static boolean pending;

    private NativeCommandBatch() {}

    public static void begin() { depth++; }

    public static boolean defer() {
        if (depth == 0) return false;
        pending = true;
        return true;
    }

    public static void end() {
        if (depth == 0) throw new IllegalStateException("Unbalanced native command batch");
        depth--;
        //? if >=26.3 {
        if (depth == 0 && pending) {
            pending = false;
            RenderSystem.getDevice().createCommandEncoder().submit();
        }
        //?}
    }
}
