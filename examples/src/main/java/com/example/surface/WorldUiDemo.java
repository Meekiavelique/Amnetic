package com.example.surface;

import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.WorldSurface;
import com.meekdev.amnetic.client.surface.material.MaterialTextures;
import com.meekdev.amnetic.client.surface.material.SurfaceMaterial;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import com.meekdev.amnetic.client.surface.widget.Button;
import com.meekdev.amnetic.client.surface.widget.Column;
import com.meekdev.amnetic.client.surface.widget.Panel;
import com.meekdev.amnetic.client.surface.widget.Row;
import com.meekdev.amnetic.client.surface.widget.Text;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class WorldUiDemo {

    private static WorldSurface panel;
    private static boolean jDown;

    private WorldUiDemo() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;
            boolean down = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_J);
            if (down && !jDown) {
                if (panel != null) {
                    panel.remove();
                    panel = null;
                } else {
                    spawn(mc.player.getEyePosition(), mc.player.getLookAngle());
                }
            }
            jDown = down;
        });
    }

    private static void spawn(Vec3 eye, Vec3 look) {
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        Vec3 pos = eye.add(flat.scale(2.5));

        panel = Surfaces.world(2.0f, 1.2f)
                .at(pos.x, pos.y, pos.z)
                .facing((float) -flat.x, 0, (float) -flat.z)
                .curve(0.5f)
                .resolution(300)
                .maxDistance(6f);

        SurfaceMaterial distort = SurfaceMaterial.load(
                Identifier.fromNamespaceAndPath("example", "shaders/surface/distort.fsh"));
        distort.setTexture("noise", MaterialTextures.noise());

        Signal<String> status = new Signal<>("aim and left-click");

        Panel card = new Panel();
        card.gradient(0xEE151B24, 0xEE0D1118).rounding(24).border(2f, 0x50FFFFFF).padding(24);

        Column col = new Column();
        col.gap(16);
        col.add(new Text("TERMINAL").px(40).center().color(0xFFB0E0FF));
        col.add(new Text(status).px(22).center().color(0xA0FFFFFF));

        Row buttons = new Row();
        buttons.gap(16);
        buttons.add(new Button("OPEN").px(24).onClick(() -> status.set("opened")).grow(1));
        buttons.add(new Button("LOCK").px(24).onClick(() -> status.set("locked")).grow(1));
        col.add(buttons.height(56));

        Panel shader = new Panel();
        shader.material(distort).rounding(18).height(120);
        col.add(shader);

        card.add(col);
        panel.root().add(card);
    }
}
