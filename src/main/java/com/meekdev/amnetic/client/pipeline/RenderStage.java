package com.meekdev.amnetic.client.pipeline;

public enum RenderStage {
    SETUP("per-frame setup; runs before water"),

    GEOMETRY("opaque world geometry; deferred-lit; drawn under water"),

    LIGHTING("shadow bake + deferred surface lighting; under water"),

    SCREEN_SPACE("SSAO / SSGI / SSR; reads depth+normals; under water"),

    AFTER_WATER("over water; depth-tested against opaque geometry (particles, billboards)"),

    ATMOSPHERE("fullscreen atmospherics (god-rays); over water"),

    POST("fullscreen image grading (bloom, grade, dev post); over everything"),

    OVERLAY("screen-space overlays; after world post"),

    AFTER_HAND("fullscreen over world + first-person hand; before GUI"),

    BEFORE_GUI("fullscreen, just before the GUI draws"),

    AFTER_GUI("fullscreen over the entire final image, GUI included");

    private final String doc;

    RenderStage(String doc) {
        this.doc = doc;
    }

    public String doc() {
        return doc;
    }
}
