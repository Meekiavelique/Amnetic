package com.meekdev.amnetic.client.post;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.UniformValue;
import net.minecraft.entity.player.PlayerEntity;
import org.joml.*;

import java.lang.Math;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class UniformSuppliers {

    private UniformSuppliers() {}

    public static Supplier<List<UniformValue>> constant(float value) {
        List<UniformValue> cached = List.of(new UniformValue.FloatValue(value));
        return () -> cached;
    }

    public static Supplier<List<UniformValue>> constant(float x, float y) {
        List<UniformValue> cached = List.of(new UniformValue.Vec2fValue(new Vector2f(x, y)));
        return () -> cached;
    }

    public static Supplier<List<UniformValue>> gameTime() {
        return () -> {
            long ticks = MinecraftClient.getInstance().world != null
                    ? MinecraftClient.getInstance().world.getTime()
                    : 0L;
            return List.of(new UniformValue.FloatValue(ticks));
        };
    }

    public static Supplier<List<UniformValue>> partialTick() {
        return () -> {
            float t = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true);
            return List.of(new UniformValue.FloatValue(t));
        };
    }

    public static Supplier<List<UniformValue>> screenWidth() {
        return () -> List.of(new UniformValue.FloatValue(
                MinecraftClient.getInstance().getWindow().getFramebufferWidth()
        ));
    }

    public static Supplier<List<UniformValue>> screenHeight() {
        return () -> List.of(new UniformValue.FloatValue(
                MinecraftClient.getInstance().getWindow().getFramebufferHeight()
        ));
    }

    public static Supplier<List<UniformValue>> screenSize() {
        return () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            return List.of(new UniformValue.Vec2fValue(new Vector2f(
                    mc.getWindow().getFramebufferWidth(),
                    mc.getWindow().getFramebufferHeight()
            )));
        };
    }

    public static Supplier<List<UniformValue>> playerHealth() {
        return () -> {
            PlayerEntity player = MinecraftClient.getInstance().player;
            float hp = player != null ? player.getHealth() : 0f;
            return List.of(new UniformValue.FloatValue(hp));
        };
    }

    public static Supplier<List<UniformValue>> playerHealthNorm() {
        return () -> {
            PlayerEntity player = MinecraftClient.getInstance().player;
            float hp = player != null ? player.getHealth() / player.getMaxHealth() : 0f;
            return List.of(new UniformValue.FloatValue(hp));
        };
    }

    public static Supplier<List<UniformValue>> playerAir() {
        return () -> {
            PlayerEntity player = MinecraftClient.getInstance().player;
            float air = player != null ? player.getAir() : 0f;
            return List.of(new UniformValue.FloatValue(air));
        };
    }

    public static Supplier<List<UniformValue>> playerAirNorm() {
        return () -> {
            PlayerEntity player = MinecraftClient.getInstance().player;
            if (player == null) return List.of(new UniformValue.FloatValue(0f));
            float norm = (float) player.getAir() / player.getMaxAir();
            return List.of(new UniformValue.FloatValue(norm));
        };
    }

    public static Supplier<List<UniformValue>> sinTime(float speed) {
        return () -> {
            float t = (System.currentTimeMillis() / 1000f) * speed;
            return List.of(new UniformValue.FloatValue((float) Math.sin(t)));
        };
    }

    public static Supplier<List<UniformValue>> cosTime(float speed) {
        return () -> {
            float t = (System.currentTimeMillis() / 1000f) * speed;
            return List.of(new UniformValue.FloatValue((float) Math.cos(t)));
        };
    }

    public static Supplier<List<UniformValue>> pingPong(float min, float max, float speed) {
        return () -> {
            float t = (System.currentTimeMillis() / 1000f) * speed;
            float range = max - min;
            float ping = (float) Math.abs((t % (range * 2)) - range);
            return List.of(new UniformValue.FloatValue(min + ping));
        };
    }

    public static Supplier<List<UniformValue>> ofFloat(DoubleSupplier supplier) {
        return () -> List.of(new UniformValue.FloatValue((float) supplier.getAsDouble()));
    }

    public static Supplier<List<UniformValue>> ofInt(IntSupplier supplier) {
        return () -> List.of(new UniformValue.IntValue(supplier.getAsInt()));
    }

    public static Supplier<List<UniformValue>> ofVec2(Supplier<Vector2fc> supplier) {
        return () -> List.of(new UniformValue.Vec2fValue(supplier.get()));
    }

    public static Supplier<List<UniformValue>> ofVec3(Supplier<Vector3fc> supplier) {
        return () -> List.of(new UniformValue.Vec3fValue(supplier.get()));
    }

    public static Supplier<List<UniformValue>> ofVec4(Supplier<Vector4fc> supplier) {
        return () -> List.of(new UniformValue.Vec4fValue(supplier.get()));
    }

    public static Supplier<List<UniformValue>> ofMat4(Supplier<Matrix4fc> supplier) {
        return () -> List.of(new UniformValue.Matrix4fValue(supplier.get()));
    }
}
