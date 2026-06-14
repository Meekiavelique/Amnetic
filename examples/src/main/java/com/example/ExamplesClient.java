package com.example;

import com.example.compute.ComputeSelfTest;
import com.example.entityfx.EntityEffectDemo;
import com.example.geometry.MeshParticlesDemo;
import com.example.geometry.MeshTapDemo;
import com.example.mirror.MirrorFeature;
import net.fabricmc.api.ClientModInitializer;

public class ExamplesClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MirrorFeature.registerClient();
        ComputeSelfTest.runOnce();
        EntityEffectDemo.init();
        MeshTapDemo.init();
        MeshParticlesDemo.init();
    }
}
