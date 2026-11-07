package com.meekdev.amnetic.client.particle;

import com.meekdev.amnetic.client.instanced.InstanceLayout;
import com.meekdev.amnetic.client.instanced.InstanceWriter;

public final class ParticleLayout {
    public static final InstanceLayout LAYOUT = InstanceLayout.builder()
            .vec3(2)
            .vec3(3)
            .float1(4)
            .float1(5)
            .vec4(6)
            .vec4(7)
            .vec4(8)
            .build();

    public static final InstanceWriter<Particle> WRITER = (p, packer) ->
            packer.putVec3(p.rCx, p.rCy, p.rCz)
                  .putVec3(p.rVx, p.rVy, p.rVz)
                  .putFloat(p.rSize)
                  .putFloat(p.rot)
                  .putVec4(p.rR, p.rG, p.rB, p.rA)
                  .putVec4(p.seedX, p.seedY, p.rAge, 0f)
                  .putVec4(p.rUvOffX, p.rUvOffY, p.rUvScaleX, p.rUvScaleY);

    private ParticleLayout() {}
}
