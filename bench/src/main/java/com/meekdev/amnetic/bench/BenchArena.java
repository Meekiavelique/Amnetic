package com.meekdev.amnetic.bench;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

public final class BenchArena {

    public static final int HALF = 24;
    public static final int HEIGHT = 12;

    private static BlockPos origin;

    private BenchArena() {}

    public static Vec3 center() {
        if (origin != null) return Vec3.atBottomCenterOf(origin).add(0, 1, 0);
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.position() : Vec3.ZERO;
    }

    public static void time(long dayTime) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput().withPermission(4), "time set " + dayTime));
    }

    public static boolean build() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return false;
        BlockPos o = mc.player.blockPosition();
        origin = o;
        List<String> commands = commands(o);
        server.execute(() -> {
            var source = server.createCommandSourceStack().withSuppressedOutput().withPermission(4);
            for (String c : commands) server.getCommands().performPrefixedCommand(source, c);
        });
        return true;
    }

    private static List<String> commands(BlockPos o) {
        int x = o.getX(), y = o.getY(), z = o.getZ();
        List<String> c = new ArrayList<>();
        c.add("gamerule doDaylightCycle false");
        c.add("gamerule doWeatherCycle false");
        c.add("gamerule doMobSpawning false");
        c.add("time set 6000");
        c.add("weather clear");
        c.add(fill(x - HALF, y, z - HALF, x + HALF, y + HEIGHT, z + HALF, "air"));
        c.add(fill(x - HALF, y - 1, z - HALF, x + HALF, y - 1, z + HALF, "smooth_stone"));
        c.add(fill(x - HALF, y, z - HALF, x + HALF, y + 5, z - HALF, "stone_bricks"));
        c.add(fill(x - HALF, y, z + HALF, x + HALF, y + 5, z + HALF, "stone_bricks"));
        c.add(fill(x - HALF, y, z - HALF, x - HALF, y + 5, z + HALF, "stone_bricks"));
        c.add(fill(x + HALF, y, z - HALF, x + HALF, y + 5, z + HALF, "stone_bricks"));
        c.add(fill(x - 6, y - 1, z - 6, x + 6, y - 1, z + 6, "water"));
        c.add(fill(x - 7, y - 2, z - 7, x + 7, y - 2, z + 7, "polished_andesite"));
        String[] lamps = {"glowstone", "sea_lantern", "shroomlight", "redstone_lamp[lit=true]", "jack_o_lantern"};
        int n = 0;
        for (int dx = -HALF + 4; dx <= HALF - 4; dx += 8) {
            for (int dz = -HALF + 4; dz <= HALF - 4; dz += 8) {
                if (Math.abs(dx) <= 8 && Math.abs(dz) <= 8) continue;
                String lamp = lamps[n++ % lamps.length];
                c.add(fill(x + dx, y, z + dz, x + dx, y + 2, z + dz, "polished_blackstone"));
                c.add(fill(x + dx, y + 3, z + dz, x + dx, y + 3, z + dz, lamp));
            }
        }
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4;
            int cx = x + (int) Math.round(Math.cos(ang) * 10);
            int cz = z + (int) Math.round(Math.sin(ang) * 10);
            c.add(fill(cx, y, cz, cx, y + 2, cz, "stone_bricks"));
        }
        c.add(fill(x + 14, y, z + 2, x + 14, y + 1, z + 2, "stone_bricks"));
        c.add(fill(x + 17, y, z + 2, x + 17, y + 1, z + 2, "stone_bricks"));
        c.add(fill(x + 14, y, z + 5, x + 14, y + 1, z + 5, "stone_bricks"));
        c.add(fill(x + 17, y, z + 5, x + 17, y + 1, z + 5, "stone_bricks"));
        c.add(fill(x + 14, y + 2, z + 2, x + 17, y + 2, z + 5, "smooth_stone_slab"));
        c.add(fill(x - 10, y, z + 10, x - 8, y + 3, z + 12, "glass"));
        c.add(fill(x + 8, y, z - 12, x + 10, y + 3, z - 10, "gold_block"));
        for (int i = 0; i < 8; i++) {
            int ex = x - 12 + i * 3;
            c.add("summon armor_stand " + ex + " " + y + " " + (z - 14) + " {NoGravity:1b,ShowArms:1b}");
            c.add("summon pig " + ex + " " + y + " " + (z + 14) + " {NoAI:1b}");
        }
        return c;
    }

    private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " " + block;
    }
}
