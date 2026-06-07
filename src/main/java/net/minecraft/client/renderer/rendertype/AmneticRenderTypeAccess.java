package net.minecraft.client.renderer.rendertype;

public final class AmneticRenderTypeAccess {

    private AmneticRenderTypeAccess() {}

    public static RenderType create(String name, RenderSetup setup) {
        return RenderType.create(name, setup);
    }
}
