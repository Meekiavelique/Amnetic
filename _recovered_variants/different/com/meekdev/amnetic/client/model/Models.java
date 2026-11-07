package com.meekdev.amnetic.client.model;

import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.model.internal.ModelIR;
import com.meekdev.amnetic.client.model.internal.ModelRegistry;
import com.meekdev.amnetic.client.model.internal.ammesh.AmmeshCache;
import com.meekdev.amnetic.client.model.internal.ammesh.AmmeshReader;
import com.meekdev.amnetic.client.model.internal.ammesh.AmmeshScanner;
import com.meekdev.amnetic.client.model.internal.parse.BbmodelParser;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

public final class Models {
    private Models() {}

    public static Model load(ResourceLocation id) {
        ModelFormat format = ModelFormat.fromResourceLocation(id);

        if (format == ModelFormat.GLTF) {
            Optional<byte[]> cached = AmmeshCache.INSTANCE.getCachedByResourceLocationOnly(id);
            if (cached.isPresent()) {
                Model model = Model.pending(id.toString());
                register(model);
                model.completeLoad(AmmeshReader.read(cached.get()));
                return model;
            }
            Model model = Model.pending(id.toString());
            register(model);
            AmmeshScanner.convertAsync(id, () -> {
                Optional<byte[]> converted = AmmeshCache.INSTANCE.getCachedByResourceLocationOnly(id);
                converted.ifPresent(bytes -> model.completeLoad(AmmeshReader.read(bytes)));
            });
            return model;
        }

        byte[] bytes = readBytes(id);
        if (format == null) format = ModelFormat.fromMagic(bytes);
        if (format == null) throw new ModelLoadException("Could not detect model format for " + id);
        ModelIR ir = parse(bytes, format, id);
        return register(new Model(ir, new ModelConfig()));
    }

    public static Model load(byte[] bytes, ModelFormat format) {
        if (format == ModelFormat.GLTF) {
            throw new ModelLoadException(
                    "Raw glTF bytes must be converted to .ammesh first (use Models.load(ResourceLocation) " +
                            "for resource-backed models, or AmmeshConverter.convert for ad-hoc conversion)");
        }
        ModelIR ir = parse(bytes, format, null);
        return register(new Model(ir, new ModelConfig()));
    }

    public static Model register(Model model) {
        ModelRegistry.INSTANCE.register(model);
        return model;
    }

    public static Model fromIR(ModelIR ir) {
        return register(new Model(ir, new ModelConfig()));
    }

    public static void onFrame(Consumer<InstanceRenderContext> callback) {
        ModelRegistry.INSTANCE.onFrame(callback);
    }

    private static ModelIR parse(byte[] bytes, ModelFormat format, ResourceLocation source) {
        return switch (format) {
            case OBJ -> throw new ModelLoadException("OBJ parsing was removed; convert to .ammesh instead: " + source);
            case AMMESH -> AmmeshReader.read(bytes);
            case BBMODEL -> BbmodelParser.parse(bytes, String.valueOf(source));
            case GLTF -> throw new ModelLoadException("glTF must go through the .ammesh cache, not direct parse: " + source);
        };
    }

    private static byte[] readBytes(ResourceLocation id) {
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
        if (res.isEmpty()) throw new ModelLoadException("Model resource not found: " + id);
        try (InputStream is = res.get().open()) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new ModelLoadException("Failed to read model resource: " + id, e);
        }
    }
}
