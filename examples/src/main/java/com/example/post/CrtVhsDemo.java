package com.example.post;

import com.meekdev.amnetic.client.post.PostEffects;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;

public final class CrtVhsDemo {

    private CrtVhsDemo() {}

    public static void init() {
        final long start = System.nanoTime();
        PostEffects.register(
                Identifier.fromNamespaceAndPath("example", "crt_vhs"),
                cfg -> cfg.uniformVec4("AmneticParams",
                        () -> new Vector4f((float) ((System.nanoTime() - start) / 1.0e9), 0f, 0f, 0f))
        ).enable();
    }
}
