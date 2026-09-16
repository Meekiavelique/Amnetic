package com.meekdev.amnetic.client.surface.internal;

import com.meekdev.amnetic.client.surface.ScreenSurface;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9 {
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class SurfaceScreen extends Screen {

    private final ScreenSurface owner;

    public SurfaceScreen(ScreenSurface owner) {
        super(Component.literal(owner.name()));
        this.owner = owner;
    }

    @Override
    public boolean isPauseScreen() {
        return owner.pausesGameValue();
    }

    //? if >=26.1 {
    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }
    *///?}

    @Override
    public void onClose() {
        super.onClose();
        owner.internalClosed();
    }

    @Override
    public void mouseMoved(double mx, double my) {
        owner.internalInput().mouseMoved((float) mx, (float) my);
        SurfaceRenderer.INSTANCE.hudMouseMoved((float) mx, (float) my);
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (owner.internalInput().mouseDown((float) event.x(), (float) event.y(), event.button())) return true;
        if (SurfaceRenderer.INSTANCE.hudMouseDown((float) event.x(), (float) event.y(), event.button())) return true;
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (owner.internalInput().mouseUp((float) event.x(), (float) event.y(), event.button())) return true;
        if (SurfaceRenderer.INSTANCE.hudMouseUp((float) event.x(), (float) event.y(), event.button())) return true;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (owner.internalInput().mouseDragged((float) event.x(), (float) event.y())) return true;
        return super.mouseDragged(event, dx, dy);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (owner.internalInput().mouseDown((float) mx, (float) my, button)) return true;
        if (SurfaceRenderer.INSTANCE.hudMouseDown((float) mx, (float) my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (owner.internalInput().mouseUp((float) mx, (float) my, button)) return true;
        if (SurfaceRenderer.INSTANCE.hudMouseUp((float) mx, (float) my, button)) return true;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (owner.internalInput().mouseDragged((float) mx, (float) my)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    *///?}

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (owner.internalInput().scroll((float) mx, (float) my, (float) dy)) return true;
        return super.mouseScrolled(mx, my, dx, dy);
    }

    //? if >=1.21.9 {
    @Override
    public boolean charTyped(CharacterEvent event) {
        if (owner.internalInput().charTyped(event.codepoint())) return true;
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() != GLFW.GLFW_KEY_ESCAPE) {
            if (owner.internalInput().keyPressed(event.key(), event.modifiers())) return true;
            if (owner.internalInput().focused() != null && event.key() != GLFW.GLFW_KEY_TAB) return true;
        }
        return super.keyPressed(event);
    }
    //?} else {
    /*@Override
    public boolean charTyped(char c, int modifiers) {
        if (owner.internalInput().charTyped(c)) return true;
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (key != GLFW.GLFW_KEY_ESCAPE) {
            if (owner.internalInput().keyPressed(key, modifiers)) return true;
            if (owner.internalInput().focused() != null && key != GLFW.GLFW_KEY_TAB) return true;
        }
        return super.keyPressed(key, scancode, modifiers);
    }
    *///?}
}
