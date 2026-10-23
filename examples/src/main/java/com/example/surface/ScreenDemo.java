package com.example.surface;

import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.Models;
import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.ScreenSurface;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import com.meekdev.amnetic.client.surface.widget.Button;
import com.meekdev.amnetic.client.surface.widget.Checkbox;
import com.meekdev.amnetic.client.surface.widget.Column;
import com.meekdev.amnetic.client.surface.widget.Dropdown;
import com.meekdev.amnetic.client.surface.widget.Grid;
import com.meekdev.amnetic.client.surface.widget.ModelViewport;
import com.meekdev.amnetic.client.surface.widget.Panel;
import com.meekdev.amnetic.client.surface.widget.Row;
import com.meekdev.amnetic.client.surface.widget.Slider;
import com.meekdev.amnetic.client.surface.widget.Tabs;
import com.meekdev.amnetic.client.surface.widget.Text;
import com.meekdev.amnetic.client.surface.widget.TextField;
import com.meekdev.amnetic.client.surface.widget.Toasts;
import com.meekdev.amnetic.client.surface.widget.Tooltip;
import com.meekdev.amnetic.client.surface.widget.Widget;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ScreenDemo {

    private static final int BTN = 0xFF6E6E6E;
    private static final int BTN_HOVER = 0xFF7E7E8E;
    private static final int BORDER = 0xFF000000;
    private static final int BORDER_HOVER = 0xFFFFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_SHADOW = 0xFF3F3F3F;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_EDGE = 0xFF373737;
    private static final int CONTAINER = 0xFFC6C6C6;

    private static ScreenSurface screen;
    private static Toasts toasts;
    private static boolean f10Down;

    private ScreenDemo() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean down = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_F10);
            if (down && !f10Down) {
                if (screen == null) build();
                if (screen.isOpen()) screen.close();
                else screen.open();
            }
            f10Down = down;
        });
    }

    private static Button mcButton(String label) {
        return new Button(label)
                .rounding(0)
                .colors(BTN, BTN_HOVER)
                .border(1.5f, BORDER, BORDER_HOVER)
                .textColor(TEXT)
                .textShadow(TEXT_SHADOW)
                .px(13);
    }

    private static Text mcText(String s, float px) {
        return new Text(s).px(px).color(TEXT).textShadow(1, 1, TEXT_SHADOW);
    }

    private static void mcToast(String message) {
        Panel p = new Panel();
        p.background(0xF0202020).rounding(0).border(1.5f, 0xFF5A5A5A).padding(8);
        p.add(mcText(message, 12));
        toasts.push(p, 2.5f);
    }

    private static void build() {
        screen = Surfaces.screen("minecraft style");
        toasts = new Toasts(screen.overlay(), Anchor.BOTTOM_RIGHT);

        Panel card = new Panel();
        card.background(0xE0181818).rounding(0).border(2f, 0xFF000000)
                .padding(14)
                .anchor(Anchor.CENTER).size(440, 430);

        Column col = new Column().gap(10);
        col.add(mcText("MINECRAFT STYLE", 20).center());

        Tabs tabs = new Tabs();
        tabs.accent(0xFFFFFFFF);
        tabs.addTab("OPTIONS", optionsTab());
        tabs.addTab("INVENTORY", inventoryTab());
        tabs.addTab("CHAT", chatTab());
        tabs.addTab("MODEL", modelTab());
        col.add(tabs.grow(1));

        card.add(col);
        screen.root().add(card);
    }

    private static Widget optionsTab() {
        Column c = new Column().gap(8).padding(10);

        Signal<Float> music = new Signal<>(0.7f);
        Row musicRow = new Row().gap(8).alignCenter();
        Text musicLabel = mcText("Music: 70%", 12);
        Slider musicSlider = new Slider(0.7f)
                .rounding(0).colors(0xFF000000, BTN).knobColor(0xFFDDDDDD);
        musicSlider.onChange(v -> {
            music.set(v);
            musicLabel.text("Music: " + Math.round(v * 100) + "%");
        });
        musicRow.add(musicSlider.grow(1).height(16));
        musicRow.add(musicLabel.width(90));
        c.add(musicRow.height(20));

        Row fovRow = new Row().gap(8).alignCenter();
        Text fovLabel = mcText("FOV: 90", 12);
        Slider fov = new Slider(0.6f).rounding(0).colors(0xFF000000, BTN).knobColor(0xFFDDDDDD);
        fov.onChange(v -> fovLabel.text("FOV: " + Math.round(30 + v * 100)));
        fovRow.add(fov.grow(1).height(16));
        fovRow.add(fovLabel.width(90));
        c.add(fovRow.height(20));

        Row diffRow = new Row().gap(8).alignCenter();
        diffRow.add(mcText("Difficulty", 12).width(90));
        Dropdown difficulty = new Dropdown(screen.overlay(),
                List.of("Peaceful", "Easy", "Normal", "Hard"), 2);
        difficulty.rounding(0).accent(0xFFFFFFFF);
        difficulty.onChange(i -> mcToast("difficulty set"));
        diffRow.add(difficulty.grow(1));
        c.add(diffRow.height(24));

        c.add(new Checkbox(true, "Fullscreen"));
        c.add(new Checkbox(false, "VSync"));

        Row bottom = new Row().gap(8);
        Button done = mcButton("Done").onClick(() -> screen.close());
        Tooltip.text(done, "closes the screen", screen.overlay());
        bottom.add(mcButton("Reset").onClick(() -> mcToast("options reset")).grow(1));
        bottom.add(done.grow(1));
        c.add(bottom.height(26));
        return c;
    }

    private static Widget inventoryTab() {
        Column c = new Column().gap(8).padding(10);

        Panel container = new Panel();
        container.background(CONTAINER).rounding(0).border(2f, 0xFF000000).padding(8);

        Column inner = new Column().gap(6);
        Text title = new Text("Chest").px(11).color(0xFF404040);
        inner.add(title);

        Grid slots = new Grid(9).gap(2);
        String[] items = {"RUBY", "JADE", "GOLD"};
        int[] colors = {0xFFAA3344, 0xFF33AA66, 0xFFDDAA33};
        for (int i = 0; i < 27; i++) {
            Panel slot = new Panel();
            slot.background(SLOT).rounding(0).border(1f, SLOT_EDGE).height(20);
            if (i < 3) {
                Panel item = new Panel();
                item.background(colors[i]).rounding(0).size(12, 12);
                item.anchor(Anchor.CENTER);
                item.draggable(items[i]);
                slot.add(item);
            }
            final Panel target = slot;
            slot.dropTarget(payload -> {
                Panel item = new Panel();
                item.background(0xFF888888).rounding(0).size(12, 12);
                item.anchor(Anchor.CENTER);
                item.draggable(payload);
                target.add(item);
                mcToast(payload + " moved");
            });
            slots.add(slot);
        }
        inner.add(slots);
        container.add(inner);
        c.add(container);
        c.add(mcText("drag the colored items between slots", 10));
        return c;
    }

    private static Widget modelTab() {
        Column c = new Column().gap(8).padding(10);

        Model model = Models.load(Identifier.fromNamespaceAndPath("example", "models3d/refrigerator.glb"));
        ModelViewport viewport = new ModelViewport(model, 512);
        viewport.autoSpin(25f).light(0.9f, 1.0f);

        Panel frame = new Panel();
        frame.background(SLOT).rounding(0).border(2f, 0xFF000000).padding(4);
        frame.add(viewport);
        c.add(frame.grow(1));

        c.add(mcText("drag to orbit, scroll to zoom", 10).center());
        return c;
    }

    private static Widget chatTab() {
        Column c = new Column().gap(8).padding(10);

        Signal<String> chat = new Signal<>("<system> chat style demo");
        Text log = new Text(chat).px(11).color(TEXT).textShadow(1, 1, TEXT_SHADOW).wrap(true);
        Panel logPanel = new Panel();
        logPanel.background(0x80000000).rounding(0).padding(6);
        logPanel.add(log);
        c.add(logPanel.grow(1));

        TextField input = new TextField("")
                .placeholder("press here to chat...")
                .colors(0xC0000000, TEXT).rounding(0).accent(0xFFFFFFFF);
        Runnable sendAction = () -> {
            String msg = input.value.peek();
            if (!msg.isEmpty()) {
                chat.update(prev -> prev + "\n<you> " + msg);
                input.value.set("");
            }
        };
        Button send = mcButton("Send").onClick(sendAction);
        input.onSubmit(s -> sendAction.run());
        Row inputRow = new Row().gap(6);
        inputRow.add(input.grow(1));
        inputRow.add(send.width(70));
        c.add(inputRow.height(24));
        return c;
    }
}
