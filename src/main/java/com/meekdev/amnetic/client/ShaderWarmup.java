package com.meekdev.amnetic.client;

import com.meekdev.amnetic.client.decal.Decals;
import com.meekdev.amnetic.client.gbuffer.internal.EntityMotion;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferNormalFill;
import com.meekdev.amnetic.client.grade.internal.ColorGradePass;
import com.meekdev.amnetic.client.light.internal.DeferredLightingPass;
import com.meekdev.amnetic.client.post.internal.PostEffectRegistry;
import com.meekdev.amnetic.client.render.ScreenPass;
import com.meekdev.amnetic.client.render.ShaderProgram;

// compiles shaders a little at a time from the first ticks on, so nothing links mid game.
// a first link can take seconds on some drivers, which shows up as a freeze the first time an effect appears
public final class ShaderWarmup {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("Amnetic/ShaderWarmup");
    private static int ticks, warmed;
    private static boolean touched, announced;

    private ShaderWarmup() {}

    public static void tick() {
        //? if >=26.2 {
        return;
        //?} else {
        /*
        if (net.minecraft.client.Minecraft.getInstance().gui.overlay() != null) return;
        if (!touched) {
            touched = true;
            Decals.touch();
            ColorGradePass.INSTANCE.hashCode();
            DeferredLightingPass.INSTANCE.hashCode();
            GBufferNormalFill.INSTANCE.hashCode();
            EntityMotion.INSTANCE.hashCode();
        }
        if (ticks++ % 40 == 0) ScreenPass.prepareAll();
        boolean worked;
        try {
            worked = PostEffectRegistry.INSTANCE.warmOne();
        } catch (RuntimeException ignored) {
            worked = true;
        }
        if (!worked) worked = ShaderProgram.warmOne();
        if (worked) {
            warmed++;
            announced = false;
        } else if (!announced && warmed > 0) {
            announced = true;
            LOG.info("warmed {} shader programs ahead of use", warmed);
        }
        *///?}
    }
}
