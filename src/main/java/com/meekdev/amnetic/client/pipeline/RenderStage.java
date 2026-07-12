package com.meekdev.amnetic.client.pipeline;

/** the fixed, ordered list of render stages in a single frame. stages are anchored to vanilla's draw
 *  points (opaque terrain, then translucent terrain/water, then GUI), so picking a stage is picking where
 *  a pass sits relative to water - which makes the "it appears behind water / it darkens particles" class
 *  of ordering bug impossible to hit by accident. register with Pipeline.add; within a stage, passes run
 *  in ascending order, ties broken by registration order */
public enum RenderStage {

    // per-frame setup: camera capture, target allocation, opaque depth + scene-colour snapshots. before water
    SETUP("per-frame setup; runs before water"),

    // opaque world geometry that should be deferred-lit and sit under water: gbuffer fill, instanced meshes, models, decals
    GEOMETRY("opaque world geometry; deferred-lit; drawn under water"),

    // shadow bake + deferred surface lighting (consumes the gbuffer). under water
    LIGHTING("shadow bake + deferred surface lighting; under water"),

    // screen-space passes that read depth/normal/colour and composite onto the lit scene: SSAO, SSGI, SSR. under water
    SCREEN_SPACE("SSAO / SSGI / SSR; reads depth+normals; under water"),

    // world-space geometry that must sort in front of translucents: particles, over-water billboards. over water, depth-tested against opaque geometry
    AFTER_WATER("over water; depth-tested against opaque geometry (particles, billboards)"),

    // fullscreen additive atmospherics over the whole scene: volumetric god-rays. over water
    ATMOSPHERE("fullscreen atmospherics (god-rays); over water"),

    // fullscreen image grading, applied last to the scene: bloom, colour grade, dev CRT/VHS post
    POST("fullscreen image grading (bloom, grade, dev post); over everything"),

    // screen-space overlays drawn after world post, before the first-person hand, e.g. UI-space meshes
    OVERLAY("screen-space overlays; after world post"),

    // fullscreen, after the first-person hand is drawn but before the GUI. covers world + hand
    AFTER_HAND("fullscreen over world + first-person hand; before GUI"),

    // fullscreen, immediately before the GUI/HUD draws
    BEFORE_GUI("fullscreen, just before the GUI draws"),

    // fullscreen, after everything including the GUI/HUD. covers the entire final image
    AFTER_GUI("fullscreen over the entire final image, GUI included");

    private final String doc;

    RenderStage(String doc) {
        this.doc = doc;
    }

    // human-readable description of what this stage is for and where it sits relative to water
    public String doc() {
        return doc;
    }
}
