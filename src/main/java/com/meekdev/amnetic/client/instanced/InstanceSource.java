package com.meekdev.amnetic.client.instanced;

public interface InstanceSource {

    int buffer();

    int prepare(InstanceRenderContext ctx);
}
