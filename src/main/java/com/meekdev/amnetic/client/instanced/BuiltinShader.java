package com.meekdev.amnetic.client.instanced;

import org.joml.Matrix4fc;
import org.joml.Vector4fc;

public final class BuiltinShader<T> {

    public static final BuiltinShader<TransformColor> TRANSFORM_COLOR = new BuiltinShader<>(
            InstanceLayout.TRANSFORM_COLOR,
            (inst, p) -> p.putMat4(inst.transform).putVec4(inst.color),
            "transform_color"
    );

    public static final BuiltinShader<Transform> TRANSFORM = new BuiltinShader<>(
            InstanceLayout.TRANSFORM,
            (inst, p) -> p.putMat4(inst.transform),
            "transform"
    );

    public record TransformColor(Matrix4fc transform, Vector4fc color) {}

    public record Transform(Matrix4fc transform) {}

    private final InstanceLayout layout;
    private final InstanceWriter<T> writer;
    private final String shaderId;

    private BuiltinShader(InstanceLayout layout, InstanceWriter<T> writer, String shaderId) {
        this.layout = layout;
        this.writer = writer;
        this.shaderId = shaderId;
    }

    public InstanceLayout layout() { return layout; }
    public InstanceWriter<T> writer() { return writer; }
    public String shaderId() { return shaderId; }
}
