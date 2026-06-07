package com.meekdev.amnetic.client.particle;

import java.util.random.RandomGenerator;
import org.joml.Vector3f;

@FunctionalInterface
public interface SpawnShape {
    /**
     * @param rng       random source
     * @param outOffset receives the spawn position offset from the emitter origin (blocks)
     * @param outDir    receives the initial unit direction of travel
     */
    void sample(RandomGenerator rng, Vector3f outOffset, Vector3f outDir);
}
