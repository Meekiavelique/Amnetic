package com.meekdev.amnetic.client.post;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.nio.ByteBuffer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.joml.Vector4f;
import org.joml.Vector4fc;

public sealed interface UniformValue {

    void write(ByteBuffer buffer, int offset);

    record FloatUniform(float value) implements UniformValue {
        public void write(ByteBuffer buffer, int offset) {
            buffer.putFloat(offset, value);
        }
    }

    record IntUniform(int value) implements UniformValue {
        public void write(ByteBuffer buffer, int offset) {
            buffer.putInt(offset, value);
        }
    }

    record Vec2Uniform(Vector2fc value) implements UniformValue {
        public void write(ByteBuffer buffer, int offset) {
            buffer.putFloat(offset, value.x()).putFloat(offset + 4, value.y());
        }
    }

    record Vec3Uniform(Vector3fc value) implements UniformValue {
        public void write(ByteBuffer buffer, int offset) {
            buffer.putFloat(offset, value.x()).putFloat(offset + 4, value.y()).putFloat(offset + 8, value.z());
        }
    }

    record IVec3Uniform(Vector3ic value) implements UniformValue {
        public void write(ByteBuffer buffer, int offset) {
            buffer.putInt(offset, value.x()).putInt(offset + 4, value.y()).putInt(offset + 8, value.z());
        }
    }

    record Vec4Uniform(Vector4fc value) implements UniformValue {
        public void write(ByteBuffer buffer, int offset) {
            buffer.putFloat(offset, value.x()).putFloat(offset + 4, value.y())
                    .putFloat(offset + 8, value.z()).putFloat(offset + 12, value.w());
        }
    }

    record Matrix4x4Uniform(Matrix4fc value) implements UniformValue {
        public void write(ByteBuffer buffer, int offset) {
            value.get(offset, buffer);
        }
    }

    static UniformValue parse(String type, JsonElement value) {
        return switch (type) {
            case "float" -> new FloatUniform(value.getAsFloat());
            case "int" -> new IntUniform(value.getAsInt());
            case "vec2" -> new Vec2Uniform(new Vector2f(f(value, 0), f(value, 1)));
            case "vec3" -> new Vec3Uniform(new Vector3f(f(value, 0), f(value, 1), f(value, 2)));
            case "ivec3" -> {
                JsonArray a = value.getAsJsonArray();
                yield new IVec3Uniform(new Vector3i(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
            }
            case "vec4" -> new Vec4Uniform(new Vector4f(f(value, 0), f(value, 1), f(value, 2), f(value, 3)));
            case "matrix4x4" -> {
                float[] m = new float[16];
                for (int i = 0; i < 16; i++) m[i] = f(value, i);
                yield new Matrix4x4Uniform(new Matrix4f().set(m));
            }
            default -> throw new IllegalArgumentException("unknown uniform type " + type);
        };
    }

    private static float f(JsonElement array, int index) {
        return array.getAsJsonArray().get(index).getAsFloat();
    }
}
