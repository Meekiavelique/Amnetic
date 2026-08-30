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
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

public final class Models {

    private Models() {}

    /**
     * glTF sources are never parsed on the calling thread, they go through the background-converted
     * .ammesh cache (see AmmeshScanner). if conversion hasn't finished this returns a not-ready
     * Model the renderer silently skips until it completes
     */
    public static Model load(Identifier id) {
        ModelFormat format = ModelFormat.fromIdentifier(id);

        if (format == ModelFormat.GLTF) {
            // even on a cache hit go through the pending/completeLoad path instead of building the Model
            // (and its GPU upload) here. Models.load can be called off the render thread, and GpuModel
            // construction only happens in ModelRegistry's per-frame render-thread drain
            Optional<byte[]> cached = AmmeshCache.INSTANCE.getCachedByIdentifierOnly(id);
            if (cached.isPresent()) {
                Model model = Model.pending(id.toString());
                register(model);
                model.completeLoad(AmmeshReader.read(cached.get()));
                return model;
            }
            Model model = Model.pending(id.toString());
            register(model);
            AmmeshScanner.convertAsync(id, () -> {
                Optional<byte[]> converted = AmmeshCache.INSTANCE.getCachedByIdentifierOnly(id);
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
                    "Raw glTF bytes must be converted to .ammesh first (use Models.load(Identifier) " +
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

    private static ModelIR parse(byte[] bytes, ModelFormat format, Identifier source) {
        return switch (format) {
            case OBJ -> throw new ModelLoadException("OBJ parsing was removed; convert to .ammesh instead: " + source);
            case AMMESH -> AmmeshReader.read(bytes);
            case BBMODEL -> BbmodelParser.parse(bytes, String.valueOf(source));
            case GLTF -> throw new ModelLoadException("glTF must go through the .ammesh cache, not direct parse: " + source);
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
