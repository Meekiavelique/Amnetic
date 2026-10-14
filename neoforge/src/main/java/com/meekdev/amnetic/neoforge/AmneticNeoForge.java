package com.meekdev.amnetic.neoforge;

import com.meekdev.amnetic.Amnetic;
import com.meekdev.amnetic.client.AmneticClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = Amnetic.MOD_ID, dist = Dist.CLIENT)
public final class AmneticNeoForge {

    public AmneticNeoForge() {
        new Amnetic().onInitialize();
        new AmneticClient().onInitializeClient();
    }
}
