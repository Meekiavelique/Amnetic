package com.example;

import com.example.mirror.MirrorFeature;
import com.example.model.B1Demo;
import com.example.model.JulienDemo;
import com.example.model.ModelDemo;
import net.fabricmc.api.ModInitializer;

public class ExamplesMod implements ModInitializer {

    @Override
    public void onInitialize() {
        MirrorFeature.register();
        ModelDemo.register();
        B1Demo.register();
        JulienDemo.register();
    }
}
