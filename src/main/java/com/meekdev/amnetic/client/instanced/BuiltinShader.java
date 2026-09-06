package com.meekdev.amnetic.client.instanced;

import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

public final class BuiltinShader<T> {

    public static final BuiltinShader<TransformColor> TRANSFORM_COLOR = new BuiltinShader<>(
            InstanceLayout.TRANSFORM_COLOR,
            (inst, p) -> p.putMat4(inst.transform).putVec4(inst.color),
            "transform_color",
            true
    );

    public static final BuiltinShader<Transform> TRANSFORM = new BuiltinShader<>(
            InstanceLayout.TRANSFORM,
            (inst, p) -> p.putMat4(inst.transform),
            "transform",
            false
    );

    public static final BuiltinShader<TexturedBillboard> TEXTURED_BILLBOARD = new BuiltinShader<>(
            InstanceLayout.TEXTURED_BILLBOARD,
            (inst, p) -> p.putVec3(inst.center).putFloat(inst.size).putVec4(inst.color).putVec4(inst.uv),
            "textured_billboard",
            false
    );

    public record TransformColor(Matrix4fc transform, Vector4fc color) {}

    public record Transform(Matrix4fc transform) {}

    public record TexturedBillboard(Vector3fc center, float size, Vector4fc color, Vector4fc uv) {
        public TexturedBillboard(Vector3fc center, float size, Vector4fc color) {
            this(center, size, color, new Vector4f(0f, 0f, 1f, 1f));
        }
    }

    private final InstanceLayout layout;
    private final InstanceWriter<T> writer;
    private final String shaderId;
    private final boolean flatVariant;

    private BuiltinShader(InstanceLayout layout, InstanceWriter<T> writer, String shaderId, boolean flatVariant) {
        this.layout = layout;
        this.writer = writer;
        this.shaderId = shaderId;
        this.flatVariant = flatVariant;
    }

    public InstanceLayout layout() { return layout; }
    public InstanceWriter<T> writer() { return writer; }
    public String shaderId() { return shaderId; }
    public boolean hasFlatVariant() { return flatVariant; }
}
