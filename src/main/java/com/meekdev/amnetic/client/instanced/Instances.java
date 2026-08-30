package com.meekdev.amnetic.client.instanced;

import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;

public final class Instances {

    private Instances() {
    }

    public static void onPrePhase(InstancePhase phase, Consumer<InstanceRenderContext> callback) {
        InstanceMeshRegistry.INSTANCE.addPrePhaseCallback(phase, callback);
    }

    public static void onPostPhase(InstancePhase phase, Consumer<InstanceRenderContext> callback) {
        InstanceMeshRegistry.INSTANCE.addPostPhaseCallback(phase, callback);
    }

    public static void render(Identifier id, InstanceRenderContext context) {
        InstanceMeshRegistry.INSTANCE.render(id, context);
    }
}
