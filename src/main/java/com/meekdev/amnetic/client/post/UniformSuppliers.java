package com.meekdev.amnetic.client.post;

import org.joml.*;

import java.lang.Math;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.world.entity.player.Player;

public final class UniformSuppliers {

    private UniformSuppliers() {}

    public static Supplier<List<UniformValue>> constant(float value) {
        List<UniformValue> cached = List.of(new UniformValue.FloatUniform(value));
        return () -> cached;
    }

    public static Supplier<List<UniformValue>> constant(float x, float y) {
        List<UniformValue> cached = List.of(new UniformValue.Vec2Uniform(new Vector2f(x, y)));
        return () -> cached;
    }

    public static Supplier<List<UniformValue>> gameTime() {
        return () -> {
            long ticks = Minecraft.getInstance().level != null
                    ? Minecraft.getInstance().level.getGameTime()
                    : 0L;
            return List.of(new UniformValue.FloatUniform(ticks));
        };
    }

    public static Supplier<List<UniformValue>> partialTick() {
        return () -> {
            float t = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
            return List.of(new UniformValue.FloatUniform(t));
        };
    }

    public static Supplier<List<UniformValue>> screenWidth() {
        return () -> List.of(new UniformValue.FloatUniform(
                Minecraft.getInstance().getWindow().getWidth()
        ));
    }

    public static Supplier<List<UniformValue>> screenHeight() {
        return () -> List.of(new UniformValue.FloatUniform(
                Minecraft.getInstance().getWindow().getHeight()
        ));
    }

    public static Supplier<List<UniformValue>> screenSize() {
        return () -> {
            Minecraft mc = Minecraft.getInstance();
            return List.of(new UniformValue.Vec2Uniform(new Vector2f(
                    mc.getWindow().getWidth(),
                    mc.getWindow().getHeight()
            )));
        };
    }

    public static Supplier<List<UniformValue>> playerHealth() {
        return () -> {
            Player player = Minecraft.getInstance().player;
            float hp = player != null ? player.getHealth() : 0f;
            return List.of(new UniformValue.FloatUniform(hp));
        };
    }

    public static Supplier<List<UniformValue>> playerHealthNorm() {
        return () -> {
            Player player = Minecraft.getInstance().player;
            float hp = player != null ? player.getHealth() / player.getMaxHealth() : 0f;
            return List.of(new UniformValue.FloatUniform(hp));
        };
    }

    public static Supplier<List<UniformValue>> playerAir() {
        return () -> {
            Player player = Minecraft.getInstance().player;
            float air = player != null ? player.getAirSupply() : 0f;
            return List.of(new UniformValue.FloatUniform(air));
        };
    }

    public static Supplier<List<UniformValue>> playerAirNorm() {
        return () -> {
            Player player = Minecraft.getInstance().player;
            if (player == null) return List.of(new UniformValue.FloatUniform(0f));
            float norm = (float) player.getAirSupply() / player.getMaxAirSupply();
            return List.of(new UniformValue.FloatUniform(norm));
        };
    }

    public static Supplier<List<UniformValue>> sinTime(float speed) {
        return () -> {
            float t = (System.currentTimeMillis() / 1000f) * speed;
            return List.of(new UniformValue.FloatUniform((float) Math.sin(t)));
        };
    }

    public static Supplier<List<UniformValue>> cosTime(float speed) {
        return () -> {
            float t = (System.currentTimeMillis() / 1000f) * speed;
            return List.of(new UniformValue.FloatUniform((float) Math.cos(t)));
        };
    }

    public static Supplier<List<UniformValue>> pingPong(float min, float max, float speed) {
        return () -> {
            float t = (System.currentTimeMillis() / 1000f) * speed;
            float range = max - min;
            float ping = (float) Math.abs((t % (range * 2)) - range);
            return List.of(new UniformValue.FloatUniform(min + ping));
        };
    }

    public static Supplier<List<UniformValue>> ofFloat(DoubleSupplier supplier) {
        return () -> List.of(new UniformValue.FloatUniform((float) supplier.getAsDouble()));
    }

    public static Supplier<List<UniformValue>> ofInt(IntSupplier supplier) {
        return () -> List.of(new UniformValue.IntUniform(supplier.getAsInt()));
    }

    public static Supplier<List<UniformValue>> ofVec2(Supplier<Vector2fc> supplier) {
        return () -> List.of(new UniformValue.Vec2Uniform(supplier.get()));
    }

    public static Supplier<List<UniformValue>> ofVec3(Supplier<Vector3fc> supplier) {
        return () -> List.of(new UniformValue.Vec3Uniform(supplier.get()));
    }

    public static Supplier<List<UniformValue>> ofVec4(Supplier<Vector4fc> supplier) {
        return () -> List.of(new UniformValue.Vec4Uniform(supplier.get()));
    }

    public static Supplier<List<UniformValue>> ofMat4(Supplier<Matrix4fc> supplier) {
        return () -> List.of(new UniformValue.Matrix4x4Uniform(supplier.get()));
    }
}
