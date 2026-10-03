package com.meekdev.amnetic.client.post;

import com.meekdev.amnetic.client.post.internal.PostEffectEntry;
import com.meekdev.amnetic.client.post.internal.PostEffectRegistry;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import net.minecraft.resources.ResourceLocation;

public final class PostEffects {

    private PostEffects() {}

    public static PostEffectHandle register(ResourceLocation id) {
        PostEffectEntry entry = PostEffectRegistry.INSTANCE.register(id);
        return new PostEffectHandle(entry);
    }

    public static PostEffectHandle register(ResourceLocation id, Consumer<PostEffectConfig> configurator) {
        PostEffectEntry entry = PostEffectRegistry.INSTANCE.register(id);
        configurator.accept(new PostEffectConfig(entry));
        PostEffectRegistry.INSTANCE.resort(); // the configurator may have set a priority
        return new PostEffectHandle(entry);
    }

    public static PostEffectHandle register(ResourceLocation id, BooleanSupplier condition) {
        return register(id, cfg -> cfg.when(condition));
    }

    public static PostEffectHandle blur(ResourceLocation id, float radius) {
        return register(id, cfg -> cfg
                .uniform("Radius", radius)
        );
    }

    public static PostEffectHandle blur(ResourceLocation id, DoubleSupplier radius) {
        return register(id, cfg -> cfg
                .uniform("Radius", radius)
        );
    }

    public static PostEffectHandle vignette(ResourceLocation id, float intensity) {
        return register(id, cfg -> cfg
                .uniform("Intensity", intensity)
        );
    }

    public static PostEffectHandle vignette(ResourceLocation id, DoubleSupplier intensity) {
        return register(id, cfg -> cfg
                .uniform("Intensity", intensity)
        );
    }

    public static PostEffectHandle conditionalWithFade(ResourceLocation id, BooleanSupplier condition, int fadeInTicks, int fadeOutTicks) {
        return register(id, cfg -> cfg
                .when(condition)
                .fadeIn(fadeInTicks)
                .fadeOut(fadeOutTicks)
        );
    }
}
