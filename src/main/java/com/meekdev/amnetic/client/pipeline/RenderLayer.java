package com.meekdev.amnetic.client.pipeline;

/** a screen layer that can be isolated and processed on its own, separately from the world. register a
 *  LayerPass with Pipeline.addLayer and the pipeline renders that layer into its own transparent texture,
 *  hands it to your pass, and composites the result back over the scene - so you can blur just the HUD,
 *  tint just the held item, etc without touching the world. the world itself is the main target and goes
 *  through RenderStages, so it isn't a layer here */
public enum RenderLayer {

    // the first-person held item / arm
    HAND,

    // the GUI / HUD drawn over the world
    GUI
}
