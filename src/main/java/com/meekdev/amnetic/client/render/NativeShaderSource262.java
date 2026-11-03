package com.meekdev.amnetic.client.render;
//? if >=26.2 && <26.3 {
/*public interface NativeShaderSource262 extends com.mojang.blaze3d.shaders.ShaderSource, AutoCloseable {
    final class CachedIncludeSource {}
    String getShader(net.minecraft.resources.Identifier id, com.mojang.blaze3d.shaders.ShaderType type);
    static String adapt(String source) {
        if (com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().backendName().equalsIgnoreCase("OpenGL"))
            return source.replace("gl_VertexIndex", "gl_VertexID").replace("gl_InstanceIndex", "gl_InstanceID");
        return source;
    }
    @Override default String get(net.minecraft.resources.Identifier id, com.mojang.blaze3d.shaders.ShaderType type) { return adapt(getShader(id,type)); }
    CachedIncludeSource getInclude(net.minecraft.resources.Identifier id);
    @Override void close();
}
*///? }
