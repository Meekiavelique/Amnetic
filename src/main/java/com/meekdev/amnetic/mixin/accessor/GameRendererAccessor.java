package com.meekdev.amnetic.mixin.accessor;

//? if >=26.2 {
/*import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// the global settings getter went private in 26.2
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Accessor("globalSettingsUniform")
    GlobalSettingsUniform amnetic$getGlobalSettingsUniform();
}
*///?}
