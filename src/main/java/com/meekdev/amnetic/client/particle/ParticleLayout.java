package com.meekdev.amnetic.client.particle;

import com.meekdev.amnetic.client.instanced.InstanceLayout;
import com.meekdev.amnetic.client.instanced.InstanceWriter;

public final class ParticleLayout {

    public static final InstanceLayout LAYOUT = InstanceLayout.builder()
            .vec3(2) // center
            .vec3(3) // velocity
            .float1(4) // size
            .float1(5) // rotation
            .vec4(6) // color
            .vec4(7) // seed + age
            .vec4(8) // uvRect: offset.xy, scale.xy (flipbook sub-rect; (0,0,1,1) = whole texture)
            .build();

    public static final InstanceWriter<Particle> WRITER = (p, packer) ->
            packer.putVec3(p.rCx, p.rCy, p.rCz)
                  .putVec3(p.rVx, p.rVy, p.rVz)
                  .putFloat(p.rSize)
                  .putFloat(p.rot)
                  .putVec4(p.rR, p.rG, p.rB, p.rA)
                  .putVec4(p.seedX, p.seedY, p.ageFraction(), 0f)
                  .putVec4(p.rUvOffX, p.rUvOffY, p.rUvScaleX, p.rUvScaleY);

    private ParticleLayout() {}
}
