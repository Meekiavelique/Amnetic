package com.meekdev.amnetic.client.gbuffer.internal;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;

final class BlockPbrAtlas {

    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic/BlockPbr");
    private static final int MIP_LEVELS = 4;

    private static int normalTex;
    private static int specularTex;
    private static TextureAtlasSprite stamp;
    private static boolean any;

    private BlockPbrAtlas() {}

    static boolean bind(int normalUnit, int specularUnit) {
        Minecraft mc = Minecraft.getInstance();
        TextureAtlas atlas = mc.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite missing = atlas.getSprite(MissingTextureAtlasSprite.getLocation());
        if (missing != stamp) {
            stamp = missing;
            build(mc.getResourceManager(), atlas, missing);
        }
        if (!any) return false;
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + normalUnit);
        GlStateManager._bindTexture(normalTex);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + specularUnit);
        GlStateManager._bindTexture(specularTex);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        return true;
    }

    private static void build(ResourceManager resources, TextureAtlas atlas, TextureAtlasSprite missing) {
        delete();
        Map<Identifier, Resource> found = resources.listResources("textures",
                id -> id.getPath().endsWith("_n.png") || id.getPath().endsWith("_s.png"));
        if (found.isEmpty()) return;

        int w = Math.round(missing.contents().width() / (missing.getU1() - missing.getU0()));
        int h = Math.round(missing.contents().height() / (missing.getV1() - missing.getV0()));
        normalTex = create(w, h);
        specularTex = create(w, h);

        int stitched = 0;
        for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
            String path = entry.getKey().getPath();
            boolean normal = path.endsWith("_n.png");
            String base = path.substring("textures/".length(), path.length() - "_n.png".length());
            TextureAtlasSprite sprite = atlas.getSprite(Identifier.fromNamespaceAndPath(entry.getKey().getNamespace(), base));
            if (sprite == missing) continue;
            try (InputStream in = entry.getValue().open(); NativeImage image = NativeImage.read(in)) {
                upload(normal ? normalTex : specularTex, sprite, image, normal);
                stitched++;
            } catch (Exception e) {
                LOGGER.warn("couldn't read {}", entry.getKey(), e);
            }
        }
        for (int tex : new int[]{normalTex, specularTex}) {
            GlStateManager._bindTexture(tex);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        }
        GlStateManager._bindTexture(0);
        any = stitched > 0;
        LOGGER.info("stitched {} block pbr maps into {}x{}", stitched, w, h);
    }

    private static int create(int w, int h) {
        int tex = GlStateManager._genTexture();
        GlStateManager._bindTexture(tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, MIP_LEVELS);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        unpackDefaults();
        ByteBuffer clear = MemoryUtil.memCalloc(w * h * 4);
        try {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, clear);
        } finally {
            MemoryUtil.memFree(clear);
        }
        return tex;
    }

    private static void upload(int tex, TextureAtlasSprite sprite, NativeImage image, boolean normal) {
        int sw = sprite.contents().width();
        int sh = sprite.contents().height();
        ByteBuffer pixels = MemoryUtil.memAlloc(sw * sh * 4);
        try {
            for (int y = 0; y < sh; y++) {
                for (int x = 0; x < sw; x++) {
                    int p = image.getPixelRGBA(x * image.getWidth() / sw, y * image.getWidth() / sw % image.getHeight());
                    pixels.put((byte) p).put((byte) (p >> 8)).put(normal ? (byte) (p >> 16) : 0).put((byte) 255);
                }
            }
            pixels.flip();
            GlStateManager._bindTexture(tex);
            unpackDefaults();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, sprite.getX(), sprite.getY(), sw, sh, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    private static void unpackDefaults() {
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
    }

    static void delete() {
        if (normalTex != 0) GlStateManager._deleteTexture(normalTex);
        if (specularTex != 0) GlStateManager._deleteTexture(specularTex);
        normalTex = 0;
        specularTex = 0;
        any = false;
    }
}
