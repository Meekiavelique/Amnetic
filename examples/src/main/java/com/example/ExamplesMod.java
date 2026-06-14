package com.example;

import com.example.mirror.MirrorFeature;
import net.fabricmc.api.ModInitializer;

public class ExamplesMod implements ModInitializer {

    @Override
    public void onInitialize() {
        MirrorFeature.register();
    }
}
