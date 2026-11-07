package com.meekdev.amnetic.client;

import com.meekdev.amnetic.client.decal.Decals;
import com.meekdev.amnetic.client.gbuffer.internal.EntityMotion;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferNormalFill;
import com.meekdev.amnetic.client.grade.internal.ColorGradePass;
import com.meekdev.amnetic.client.light.internal.DeferredLightingPass;
import com.meekdev.amnetic.client.post.internal.PostEffectRegistry;
import com.meekdev.amnetic.client.render.ProgramLink;
import com.meekdev.amnetic.client.render.ScreenPass;
import com.meekdev.amnetic.client.render.ShaderProgram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//? if >=26.2 {
//?} else {
/*import net.minecraft.client.Minecraft;
*///?}

public final class ShaderWarmup {
    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/ShaderWarmup");
    private static int ticks, warmed;
    private static boolean touched, announced;

    private ShaderWarmup() {}

    public static void tick() {
        //? if >=26.2 {
        return;
        //?} else {
        /*
        if (Minecraft.getInstance().gui.overlay() != null) return;
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
            worked = PostEffectRegistry.INSTANCE.warm();
        } catch (RuntimeException ignored) {
            worked = true;
        }
        if (ShaderProgram.warm()) worked = true;
        if (worked) {
            warmed++;
            announced = false;
        } else if (!announced && warmed > 0) {
            announced = true;
            LOG.info("linked shader programs ahead of use: {}", ProgramLink.stats());
        }
        *///?}
    }
}
