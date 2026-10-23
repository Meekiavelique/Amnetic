package com.meekdev.amnetic.bench;

import java.util.function.Consumer;
import net.minecraft.world.phys.Vec3;

public record BenchScene(String name, String description, Consumer<Vec3> start, Runnable stop) {
}
