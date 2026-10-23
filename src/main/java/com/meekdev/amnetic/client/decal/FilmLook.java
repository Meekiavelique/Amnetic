package com.meekdev.amnetic.client.decal;

import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

// how a film decal shades: a thin liquid layer over the surface plus whatever it leaves behind when it dries
// absorption follows beer lambert, so a thin film tints the floor and a deep pool goes nearly opaque
public final class FilmLook {

    // per world unit of thickness, higher absorbs more
    final Vector3f absorption = new Vector3f(30f, 170f, 210f);
    // absorption once the liquid has fully aged, blended by the age channel
    final Vector3f agedAbsorption = new Vector3f(80f, 190f, 220f);
    // what fully dried residue multiplies the surface by
    final Vector3f stainTint = new Vector3f(0.42f, 0.2f, 0.15f);
    // light scattered back out of the liquid, scaled by how much it absorbed and by the local light
    final Vector3f scatter = new Vector3f(0.10f, 0.01f, 0.005f);
    float maxThickness = 0.05f;
    float edgeThickness = 0.0015f;
    float edgeSoftness = 0.0006f;
    float edgeNoise = 0.35f;
    float edgeNoiseScale = 9f;
    float stainThreshold = 0.06f;
    float ring = 1.4f;
    float normalStrength = 18f;
    float rim = 2f;
    float flowSpeed = 1.2f;
    float rippleStrength = 0.25f;
    float parallax = 1f;
    float specular = 1f;
    float wetRoughness = 0.06f;
    float dryRoughness = 0.85f;
    float reflectance = 0.02f;
    // pixel art mode
    boolean pixelArt;
    float texelsPerUnit = 16f;
    // roles: 0 highlight, 1 light body, 2 body, 3 dark body, 4 edge, 5 aged body, 6 aged dark and dried edge, 7 dried stain
    final float[] palette = new float[24];
    float jitter = 1f;
    int outline = 1;
    boolean core;
    float highlights;
    Identifier patternWet;
    Identifier patternDry;

    public FilmLook absorption(float r, float g, float b) { absorption.set(r, g, b); return this; }
    public FilmLook agedAbsorption(float r, float g, float b) { agedAbsorption.set(r, g, b); return this; }
    public FilmLook stainTint(float r, float g, float b) { stainTint.set(r, g, b); return this; }
    public FilmLook scatter(float r, float g, float b) { scatter.set(r, g, b); return this; }
    // thickness the 8 bit field can hold, thicker values clamp
    public FilmLook maxThickness(float v) { maxThickness = Math.max(1e-4f, v); return this; }
    // where the liquid edge sits and how soft it is, in world units of thickness
    public FilmLook edge(float thickness, float softness) { edgeThickness = Math.max(0f, thickness); edgeSoftness = Math.max(1e-6f, softness); return this; }
    // breaks up the outline so it isn't a smooth blob, amount 0..1 and features per world unit
    public FilmLook edgeNoise(float amount, float scale) { edgeNoise = Math.max(0f, amount); edgeNoiseScale = Math.max(0.01f, scale); return this; }
    public FilmLook stainThreshold(float v) { stainThreshold = Math.max(0f, v); return this; }
    // how much darker dried residue gets along its outline
    public FilmLook ring(float v) { ring = Math.max(0f, v); return this; }
    // slope gain from thickness to normals, and how much steeper it gets at the rim (the meniscus)
    public FilmLook normals(float strength, float rim) { normalStrength = Math.max(0f, strength); this.rim = Math.max(0f, rim); return this; }
    // ripples on cells flagged as flowing
    public FilmLook flow(float speed, float ripple) { flowSpeed = speed; rippleStrength = Math.max(0f, ripple); return this; }
    // view dependent offset so a deep pool reads as having volume, 0 turns it off
    public FilmLook parallax(float v) { parallax = Math.max(0f, v); return this; }
    public FilmLook specular(float v) { specular = Math.max(0f, v); return this; }
    public FilmLook roughness(float wet, float dry) { wetRoughness = clamp01(wet); dryRoughness = clamp01(dry); return this; }
    // normal incidence reflectance of the wet surface, water is about 0.02
    public FilmLook reflectance(float v) { reflectance = clamp01(v); return this; }

    // pixel art instead of the smooth liquid: the film snaps to a world grid of texels (16 per block matches
    // minecraft's textures), shades with the palette below in posterised steps and gets crisp outlines
    public FilmLook pixelArt(boolean v) { pixelArt = v; return this; }
    public FilmLook texelsPerUnit(float v) { texelsPerUnit = Math.max(1f, v); return this; }
    // how the pixel art is shaded: jitter 0..1 is per texel shade noise like vanilla textures, outline 0 none,
    // 1 a darker edge on the shadow side, 2 an outline all around, core darkens thick parts, highlights 0..1 is
    // how many lit texels sit just inside the lit edge
    public FilmLook pixelStyle(float jitter, int outline, boolean core, float highlights) {
        this.jitter = Math.max(0f, Math.min(1f, jitter));
        this.outline = Math.max(0, Math.min(2, outline));
        this.core = core;
        this.highlights = Math.max(0f, Math.min(1f, highlights));
        return this;
    }

    // block style textures the pixel film shows instead of palette shades, sampled 1 texel per world texel
    // (wrapping, so they should tile), wet for fresh liquid and dry for aged liquid and stains. the edge colour
    // still comes from the palette. a resource pack can replace them. null clears it
    public FilmLook pattern(Identifier wet, Identifier dry) { patternWet = wet; patternDry = dry; return this; }

    // 8 colours as 0xRRGGBB: highlight, light body, body, dark body, edge, aged body, aged dark and dried edge, dried stain
    public FilmLook palette(int... rgb) {
        for (int i = 0; i < 8 && i < rgb.length; i++) {
            palette[i * 3] = ((rgb[i] >> 16) & 255) / 255f;
            palette[i * 3 + 1] = ((rgb[i] >> 8) & 255) / 255f;
            palette[i * 3 + 2] = (rgb[i] & 255) / 255f;
        }
        return this;
    }

    public FilmLook copy() {
        FilmLook c = new FilmLook();
        c.absorption.set(absorption); c.agedAbsorption.set(agedAbsorption); c.stainTint.set(stainTint); c.scatter.set(scatter);
        c.maxThickness = maxThickness; c.edgeThickness = edgeThickness; c.edgeSoftness = edgeSoftness;
        c.edgeNoise = edgeNoise; c.edgeNoiseScale = edgeNoiseScale; c.stainThreshold = stainThreshold; c.ring = ring;
        c.normalStrength = normalStrength; c.rim = rim; c.flowSpeed = flowSpeed; c.rippleStrength = rippleStrength;
        c.parallax = parallax; c.specular = specular; c.wetRoughness = wetRoughness; c.dryRoughness = dryRoughness; c.reflectance = reflectance;
        c.pixelArt = pixelArt; c.texelsPerUnit = texelsPerUnit;
        c.jitter = jitter; c.outline = outline; c.core = core; c.highlights = highlights;
        c.patternWet = patternWet; c.patternDry = patternDry;
        System.arraycopy(palette, 0, c.palette, 0, palette.length);
        return c;
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    public Vector3f absorption() { return absorption; }
    public Vector3f agedAbsorption() { return agedAbsorption; }
    public Vector3f stainTint() { return stainTint; }
    public Vector3f scatter() { return scatter; }
    public float maxThickness() { return maxThickness; }
    public float edgeThickness() { return edgeThickness; }
    public float edgeSoftness() { return edgeSoftness; }
    public float edgeNoise() { return edgeNoise; }
    public float edgeNoiseScale() { return edgeNoiseScale; }
    public float stainThreshold() { return stainThreshold; }
    public float ring() { return ring; }
    public float normalStrength() { return normalStrength; }
    public float rim() { return rim; }
    public float flowSpeed() { return flowSpeed; }
    public float rippleStrength() { return rippleStrength; }
    public float parallax() { return parallax; }
    public float specular() { return specular; }
    public float wetRoughness() { return wetRoughness; }
    public float dryRoughness() { return dryRoughness; }
    public float reflectance() { return reflectance; }
    public boolean pixelArt() { return pixelArt; }
    public float texelsPerUnit() { return texelsPerUnit; }
}
