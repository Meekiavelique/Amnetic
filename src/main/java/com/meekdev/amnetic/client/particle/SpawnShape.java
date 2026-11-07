package com.meekdev.amnetic.client.particle;

import java.util.random.RandomGenerator;
import org.joml.Vector3f;

@FunctionalInterface
public interface SpawnShape {
    void sample(RandomGenerator rng, Vector3f outOffset, Vector3f outDir);
}
