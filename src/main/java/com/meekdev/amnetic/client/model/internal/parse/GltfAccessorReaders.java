package com.meekdev.amnetic.client.model.internal.parse;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import de.javagl.jgltf.model.AccessorData;
import de.javagl.jgltf.model.AccessorModel;

// reads glTF accessor data into plain float/int arrays, honoring component type and normalization
final class GltfAccessorReaders {

    private static final int COMPONENT_UNSIGNED_BYTE = 5121;
    private static final int COMPONENT_UNSIGNED_SHORT = 5123;
    private static final int COMPONENT_UNSIGNED_INT = 5125;
    private static final int COMPONENT_FLOAT = 5126;

    private GltfAccessorReaders() {
    }

    static float[] readFloatArray(AccessorModel accessor) {
        AccessorData data = accessor.getAccessorData();
        ByteBuffer buffer = data.createByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        int total = data.getTotalNumComponents();
        float[] out = new float[total];

        Class<?> componentType = data.getComponentType();
        boolean normalized = accessor.isNormalized();
        int gltfComponentType = accessor.getComponentType();

        if (componentType == Float.TYPE || componentType == Float.class || gltfComponentType == COMPONENT_FLOAT) {
            for (int i = 0; i < total; i++) {
                out[i] = buffer.getFloat();
            }
            return out;
        }
        if (componentType == Byte.TYPE || componentType == Byte.class) {
            boolean unsigned = gltfComponentType == COMPONENT_UNSIGNED_BYTE;
            for (int i = 0; i < total; i++) {
                int value = unsigned ? Byte.toUnsignedInt(buffer.get()) : buffer.get();
                out[i] = normalized ? (unsigned ? value / 255.0f : Math.max(-1.0f, value / 127.0f)) : (float) value;
            }
            return out;
        }
        if (componentType == Short.TYPE || componentType == Short.class) {
            boolean unsigned = gltfComponentType == COMPONENT_UNSIGNED_SHORT;
            for (int i = 0; i < total; i++) {
                int value = unsigned ? Short.toUnsignedInt(buffer.getShort()) : buffer.getShort();
                out[i] = normalized ? (unsigned ? value / 65535.0f : Math.max(-1.0f, value / 32767.0f)) : (float) value;
            }
            return out;
        }
        if (componentType == Integer.TYPE || componentType == Integer.class) {
            for (int i = 0; i < total; i++) {
                out[i] = gltfComponentType == COMPONENT_UNSIGNED_INT
                        ? (float) Integer.toUnsignedLong(buffer.getInt())
                        : (float) buffer.getInt();
            }
            return out;
        }
        throw new IllegalArgumentException("Unsupported float accessor component type: " + componentType);
    }

    static int[] readUnsignedIntArray(AccessorModel accessor) {
        AccessorData data = accessor.getAccessorData();
        ByteBuffer buffer = data.createByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        int total = data.getTotalNumComponents();
        int[] out = new int[total];

        Class<?> componentType = data.getComponentType();
        if (componentType == Byte.TYPE || componentType == Byte.class) {
            for (int i = 0; i < total; i++) {
                out[i] = Byte.toUnsignedInt(buffer.get());
            }
            return out;
        }
        if (componentType == Short.TYPE || componentType == Short.class) {
            for (int i = 0; i < total; i++) {
                out[i] = Short.toUnsignedInt(buffer.getShort());
            }
            return out;
        }
        if (componentType == Integer.TYPE || componentType == Integer.class) {
            for (int i = 0; i < total; i++) {
                out[i] = buffer.getInt();
            }
            return out;
        }
        throw new IllegalArgumentException("Unsupported int accessor component type: " + componentType);
    }
}
