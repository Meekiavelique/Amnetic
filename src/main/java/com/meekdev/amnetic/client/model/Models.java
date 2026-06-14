package com.meekdev.amnetic.client.model;

import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.model.internal.ModelIR;
import com.meekdev.amnetic.client.model.internal.ModelRegistry;
import com.meekdev.amnetic.client.model.internal.parse.GltfParser;
import com.meekdev.amnetic.client.model.internal.parse.ObjParser;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

public final class Models {

    private Models() {}

    public static Model load(Identifier id) {
        byte[] bytes = readBytes(id);
        ModelFormat format = ModelFormat.fromIdentifier(id);
        if (format == null) format = ModelFormat.fromMagic(bytes);
        if (format == null) throw new ModelLoadException("Could not detect model format for " + id);
        ModelIR ir = parse(bytes, format, id);
        return register(new Model(ir));
    }

    public static Model load(byte[] bytes, ModelFormat format) {
        ModelIR ir = parse(bytes, format, null);
        return register(new Model(ir));
    }

    public static Model register(Model model) {
        ModelRegistry.INSTANCE.register(model);
        return model;
    }

    public static Model fromIR(ModelIR ir) {
        return register(new Model(ir));
    }

    public static void onFrame(Consumer<InstanceRenderContext> callback) {
        ModelRegistry.INSTANCE.onFrame(callback);
    }

    private static ModelIR parse(byte[] bytes, ModelFormat format, Identifier source) {
        return switch (format) {
            case OBJ -> ObjParser.parse(bytes, source);
            case GLTF -> GltfParser.parse(bytes, source);
        };
    }

    private static byte[] readBytes(Identifier id) {
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
        if (res.isEmpty()) throw new ModelLoadException("Model resource not found: " + id);
        try (InputStream is = res.get().open()) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new ModelLoadException("Failed to read model resource: " + id, e);
        }
    }
}
