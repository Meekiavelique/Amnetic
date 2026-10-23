package com.example.material;

import com.meekdev.amnetic.client.material.ShadingModel;
import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.ModelMaterial;
import com.meekdev.amnetic.client.model.Models;
import com.meekdev.amnetic.client.ssgi.Ssgi;
import net.minecraft.resources.Identifier;

public final class ShadingDemo {

    private static final Identifier B1_MODEL = Identifier.fromNamespaceAndPath("example", "models3d/b1_bis.glb");
    private static final Identifier JULIEN_MODEL = Identifier.fromNamespaceAndPath("example", "models3d/julienzed.gltf");

    private ShadingDemo() {}

    public static void init() {
        ShadingModel celShaded = ShadingModel.pbr().fragment(
                "float lum = dot(s.radiance, vec3(0.299, 0.587, 0.114));\n" +
                "        float bands = 3.0;\n" +
                "        float quant = floor(lum * bands + 0.5) / bands;\n" +
                "        return s.albedo * quant;");

        Model b1 = Models.load(B1_MODEL);
        for (ModelMaterial m : b1.materials()) {
            m.setShadingModel(celShaded);
        }

        Model julien = Models.load(JULIEN_MODEL);
        for (ModelMaterial m : julien.materials()) {
            m.setEmissive(3.0f, 2.2f, 0.4f);
        }

        Ssgi.enable();
        Ssgi.settings()
                .scale(0.6f)
                .radius(1.6f)
                .intensity(0.65f)
                .historyBlend(0.92f)
                .maxHistoryFrames(24);
    }
}
