# Render Pipeline

Amnetic runs its rendering through one explicit, ordered pipeline instead of scattering work across render
events. Every pass declares which **stage** it runs in, and the stage says exactly where it sits relative to
vanilla's opaque -> water -> hand -> GUI draws. Picking a stage *is* picking your order, so the usual "it draws
behind water" / "the AO darkens my particles" surprises can't happen by accident.

There are two ways to plug in: **stages** (fullscreen or world-space passes at a fixed point in the frame) and
**layers** (process the hand or GUI in isolation, separately from the world).

## Stages

A stage is a named point in the frame. Register a pass and it runs there, in `order` (lower first):

```java
import com.meekdev.amnetic.client.pipeline.*;

PassHandle h = Pipeline.add(RenderStage.AFTER_WATER, ctx -> renderMyBillboards(ctx));
Pipeline.add(RenderStage.POST, 20, myGradePass); // optional int = order within the stage
Pipeline.add(RenderStage.POST, 20, "my grade", myGradePass); // optional label for the profiler
h.setEnabled(false);
h.remove();
```

The label overload exists because lambdas otherwise show up in the profiler under an unreadable synthetic
class name. `Pipeline.has(stage)` tells you whether a stage has any registered pass, so callers can skip
per-frame work entirely when nothing is listening.

A pass is just `RenderPass`  - a functional interface  - so a lambda and a class are interchangeable:

```java
@FunctionalInterface
public interface RenderPass {
    void render(FrameContext ctx);
    default boolean enabled() { return true; }
}
```

`FrameContext` is the shared per-frame bundle: `camera()`, `fabric()` (the Fabric level-render context, null at
GUI-area stages), and `opaqueDepth()` (the pre-translucent depth snapshot gl id, or 0). Pull what you need from
it and assume GL state is clean on entry  - the pipeline resets state after every stage, so you never clean up
after yourself or your neighbours. A pass that throws is logged and skipped; it can't kill the frame.

### The stages, in frame order

| Stage | For | vs water / GUI |
| --- | --- | --- |
| `SETUP` | per-frame setup; opaque depth + scene-colour snapshot | before water |
| `GEOMETRY` | opaque world geometry, deferred-lit (gbuffer, instanced meshes, models, decals) | under water |
| `LIGHTING` | shadow bake + deferred surface lighting | under water |
| `SCREEN_SPACE` | SSAO, SSGI, SSR | under water |
| `AFTER_WATER` | world-space billboards/geometry over water: particles, custom over-water geo | over water |
| `ATMOSPHERE` | fullscreen atmospherics (volumetric god-rays) | over water |
| `POST` | fullscreen image grading: bloom, colour grade, dev CRT/VHS post | over the world |
| `OVERLAY` | screen-space overlays, after world post | before the hand |
| `AFTER_HAND` | fullscreen over world + first-person hand | before the GUI |
| `BEFORE_GUI` | fullscreen, just before the GUI draws | before the GUI |
| `AFTER_GUI` | fullscreen over the entire final image, GUI included | after everything |

Want a blood splatter that sorts in front of water? `AFTER_WATER`. Want a CRT warp that covers the HUD too?
`AFTER_GUI`. Want a grade on the world but not the GUI? `POST`. Each `RenderStage` constant carries a `doc()`
string describing exactly this.

## Layers (per-layer isolation)

Stages cover "where in the frame". **Layers** cover "process this part of the screen on its own". Register a
`LayerPass` and the pipeline renders that layer into its own transparent texture, hands it to you, and
composites your result back over the scene  - so you can blur only the HUD, tint only the held item, etc.,
without touching the world.

```java
public enum RenderLayer { HAND, GUI }

Pipeline.addLayer(RenderLayer.HAND, (ctx, layer) -> {
    // layer.inputTexture() = the captured hand (RGBA, transparent where it drew nothing)
    // the layer's framebuffer is already bound as the draw target (layer.width()/height())
    myShader.begin();
    GlState.bindTexture(0, layer.inputTexture());
    myShader.setSampler("Layer", 0);
    myShader.draw(); // a fullscreen pass that writes the processed layer
});
```

Layers are **zero-cost until used**: a layer with no registered pass is never captured, and normal rendering is
untouched. Internally the hand/GUI draw is redirected into an off-screen target (via the same
`getMainRenderTarget()` redirect the scene-capture system uses), processed, then alpha-composited back.

`Pipeline.removeLayer(layer, pass)` unregisters a layer pass, and `Pipeline.hasLayer(layer)` reports whether a
layer has any pass registered (i.e. whether it will be captured this frame).

## How it's wired

The concrete vanilla hooks -> stages mapping lives in one place (`AmneticClient`):

- `BEFORE_TRANSLUCENT_TERRAIN` -> snapshot depth/colour, run `SETUP`.
- `END_MAIN` -> `GEOMETRY`, `LIGHTING`, `SCREEN_SPACE`.
- `renderLevel` TAIL -> `AFTER_WATER`, `ATMOSPHERE`, `POST`.
- `renderItemInHand` TAIL -> `AFTER_HAND`, immediately after the first-person hand draws (and the `HAND`
  layer capture ends). This is why an `AFTER_HAND` fullscreen pass really does cover the hand.
- GUI-area hooks -> `OVERLAY`, `BEFORE_GUI`, `AFTER_GUI`, plus the `GUI` layer capture.

`registerDefaultPasses()` declares every built-in pass and its stage/order  - the single source of truth for
render order. Adding or moving a built-in pass is a one-line change there; third-party mods slot into the same
stages and layers through `Pipeline.add` / `Pipeline.addLayer`.

## Profiling

Every pass is timed through `PassProfiler`, keyed by stage and label. CPU timing (`System.nanoTime` around the
pass) is cheap and always on. GPU timing uses `GL_TIME_ELAPSED` queries, which force driver serialization
around every pass  - so GPU timers only run while the editor is open, and cost nothing otherwise.

## State contract

The pipeline owns the shared frame resources (the opaque-depth snapshot, scene-colour snapshot, main-target
binding / depth override) and resets GL state to a clean baseline between stages. Passes assume clean state and
never touch those globals  - that is what keeps the GpuDevice/GlStateManager desync bugs from creeping back in,
including for third-party fullscreen post effects.
