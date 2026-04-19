package com.meekdev.amnetic.client.instanced;

@FunctionalInterface
public interface InstanceWriter<T> {
    void write(T instance, InstancePacker packer);
}
