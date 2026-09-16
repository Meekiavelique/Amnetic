package com.meekdev.amnetic.mixin.accessor;

//? if <1.21.5 {
/*import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LightTexture.class)
public interface LightTextureAccessor {

    @Accessor("lightTexture")
    DynamicTexture amnetic$getTexture();
}
*///?}
