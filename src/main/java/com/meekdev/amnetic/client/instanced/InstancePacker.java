package com.meekdev.amnetic.client.instanced;

import org.joml.Matrix4fc;
import org.joml.Vector4fc;

import java.nio.ByteBuffer;

public final class InstancePacker {

    ByteBuffer buf;

    InstancePacker() {}

    public InstancePacker putFloat(float v) {
        buf.putFloat(v);
        return this;
    }

    public InstancePacker putVec2(float x, float y) {
        buf.putFloat(x).putFloat(y);
        return this;
    }

    public InstancePacker putVec3(float x, float y, float z) {
        buf.putFloat(x).putFloat(y).putFloat(z);
        return this;
    }

    public InstancePacker putVec4(float x, float y, float z, float w) {
        buf.putFloat(x).putFloat(y).putFloat(z).putFloat(w);
        return this;
    }

    public InstancePacker putVec4(Vector4fc v) {
        return putVec4(v.x(), v.y(), v.z(), v.w());
    }

    public InstancePacker putMat4(Matrix4fc m) {
        buf.putFloat(m.m00()).putFloat(m.m01()).putFloat(m.m02()).putFloat(m.m03())
           .putFloat(m.m10()).putFloat(m.m11()).putFloat(m.m12()).putFloat(m.m13())
           .putFloat(m.m20()).putFloat(m.m21()).putFloat(m.m22()).putFloat(m.m23())
           .putFloat(m.m30()).putFloat(m.m31()).putFloat(m.m32()).putFloat(m.m33());
        return this;
    }
}
