package com.example;

import com.example.compute.ComputeSelfTest;
import com.example.emissive.EmissiveDemo;
import com.example.entityfx.EntityEffectDemo;
import com.example.geometry.MeshParticlesDemo;
import com.example.geometry.MeshTapDemo;
import com.example.instanced.InstanceCullDemo;
import com.example.camera.HandheldCamera;
import com.example.material.ShadingDemo;
import com.example.mirror.MirrorFeature;
import com.example.model.B1Demo;
import com.example.model.JulienDemo;
import com.example.model.ModelDemo;
import com.example.model.StressDemo;
import com.example.post.CrtVhsDemo;
import com.example.reflective.DiamondReflectionDemo;
import com.example.surface.HudDemo;
import com.example.surface.ReactivityDemo;
import com.example.surface.ScreenDemo;
import com.example.surface.WorldUiDemo;
import com.meekdev.amnetic.client.surface.Surfaces;
import net.minecraft.resources.Identifier;
import com.meekdev.amnetic.client.Quality;
import com.meekdev.amnetic.client.camera.CameraEffects;
import com.meekdev.amnetic.client.grade.ColorGrade;
import net.fabricmc.api.ClientModInitializer;

public class ExamplesClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModelDemo.registerClient();
        B1Demo.registerClient();
        JulienDemo.registerClient();
        MirrorFeature.registerClient();
        ReactivityDemo.init();
        HudDemo.init();
        Surfaces.defaultFont(Identifier.fromNamespaceAndPath("example", "fonts/heavitas.ttf"));
        ScreenDemo.init();
        WorldUiDemo.init();
        StressDemo.init();
        InstanceCullDemo.init();
        EmissiveDemo.init();

    }
}
