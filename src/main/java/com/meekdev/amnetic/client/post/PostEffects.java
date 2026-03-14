package com.meekdev.amnetic.client.post;

import com.meekdev.amnetic.client.post.internal.PostEffectEntry;
import com.meekdev.amnetic.client.post.internal.PostEffectRegistry;
import net.minecraft.util.Identifier;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

public final class PostEffects {

    private PostEffects() {}

    public static PostEffectHandle register(Identifier id) {
        PostEffectEntry entry = PostEffectRegistry.INSTANCE.register(id);
        return new PostEffectHandle(entry);
    }

    public static PostEffectHandle register(Identifier id, Consumer<PostEffectConfig> configurator) {
        PostEffectEntry entry = PostEffectRegistry.INSTANCE.register(id);
        configurator.accept(new PostEffectConfig(entry));
        return new PostEffectHandle(entry);
    }

    public static PostEffectHandle register(Identifier id, BooleanSupplier condition) {
        return register(id, cfg -> cfg.when(condition));
    }

    public static PostEffectHandle blur(Identifier id, float radius) {
        return register(id, cfg -> cfg
                .uniform("Radius", radius)
        );
    }

    public static PostEffectHandle blur(Identifier id, DoubleSupplier radius) {
        return register(id, cfg -> cfg
                .uniform("Radius", radius)
        );
    }

    public static PostEffectHandle vignette(Identifier id, float intensity) {
        return register(id, cfg -> cfg
                .uniform("Intensity", intensity)
        );
    }

    public static PostEffectHandle vignette(Identifier id, DoubleSupplier intensity) {
        return register(id, cfg -> cfg
                .uniform("Intensity", intensity)
        );
    }

    public static PostEffectHandle conditionalWithFade(Identifier id, BooleanSupplier condition, int fadeInTicks, int fadeOutTicks) {
        return register(id, cfg -> cfg
                .when(condition)
                .fadeIn(fadeInTicks)
                .fadeOut(fadeOutTicks)
        );
    }
}
