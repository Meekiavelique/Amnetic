package com.meekdev.amnetic.client.entityfx;

import com.meekdev.amnetic.client.entityfx.internal.EntityEffectRegistry;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

public final class EntityEffects {

    private EntityEffects() {}

    public static EntityEffect surface(LivingEntity entity, Identifier vsh, Identifier fsh,
                                       Consumer<SurfaceConfig> configurator) {
        SurfaceConfig cfg = new SurfaceConfig();
        if (configurator != null) configurator.accept(cfg);
        EntityEffect effect = new EntityEffect(entity, vsh, fsh, cfg.replaceBody, cfg.skin,
                cfg.sceneColor, cfg.sceneDepth, cfg.samplers);
        cfg.constants.forEach(effect::setUniform);
        cfg.suppliers.forEach(effect::setUniform);
        EntityEffectRegistry.INSTANCE.put(entity, effect);
        return effect;
    }
}
