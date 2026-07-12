package com.meekdev.amnetic.client.pipeline;

import com.meekdev.amnetic.client.pipeline.internal.GpuTimer;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.ui.AmneticEditor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** the render pipeline driver: one explicit ordered place where every pass declares which RenderStage it
 *  runs in. AmneticClient drives the vanilla hooks and asks the pipeline to runStage each stage in frame
 *  order; the pipeline runs that stage's passes by order, isolates each pass so a thrown exception can't
 *  kill the frame, and resets GL state to a clean baseline after the stage so nothing leaks between passes.
 *  third-party mods register the same way as internal passes:
 *  <pre>{@code
 *  PassHandle h = Pipeline.add(RenderStage.AFTER_WATER, ctx -> renderMyBillboards(ctx));
 *  }</pre> */
public final class Pipeline {

    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic/Pipeline");

    private static final Map<RenderStage, CopyOnWriteArrayList<PassHandle>> STAGES = new EnumMap<>(RenderStage.class);
    private static final Map<RenderLayer, CopyOnWriteArrayList<LayerPass>> LAYERS = new EnumMap<>(RenderLayer.class);

    static {
        for (RenderStage s : RenderStage.values()) {
            STAGES.put(s, new CopyOnWriteArrayList<>());
        }
        for (RenderLayer l : RenderLayer.values()) {
            LAYERS.put(l, new CopyOnWriteArrayList<>());
        }
    }

    private Pipeline() {}

    // register a pass in a stage at the default order (0)
    public static PassHandle add(RenderStage stage, RenderPass pass) {
        return add(stage, 0, pass);
    }

    // register a pass in a stage; lower order runs earlier, ties keep registration order
    public static PassHandle add(RenderStage stage, int order, RenderPass pass) {
        return add(stage, order, null, pass);
    }

    // same but with an explicit label for the profiler; lambdas otherwise show up under an unreadable
    // synthetic class name
    public static PassHandle add(RenderStage stage, int order, String label, RenderPass pass) {
        PassHandle handle = new PassHandle(stage, pass, order, label);
        CopyOnWriteArrayList<PassHandle> list = STAGES.get(stage);
        // keep the list sorted by order so runStage doesn't re-sort every frame
        int i = 0;
        while (i < list.size() && list.get(i).order <= order) i++;
        list.add(i, handle);
        return handle;
    }

    static void remove(PassHandle handle) {
        handle.removed = true;
        STAGES.get(handle.stage).remove(handle);
    }

    // register a pass that processes an isolated screen layer (hand / GUI). the layer is captured into its
    // own transparent texture, the pass processes it, and the pipeline composites it back over the scene.
    // layers with no registered pass are never captured, so this is zero-cost until used
    public static void addLayer(RenderLayer layer, LayerPass pass) {
        LAYERS.get(layer).add(pass);
    }

    public static void removeLayer(RenderLayer layer, LayerPass pass) {
        LAYERS.get(layer).remove(pass);
    }

    public static boolean hasLayer(RenderLayer layer) {
        return !LAYERS.get(layer).isEmpty();
    }

    public static List<LayerPass> layerPasses(RenderLayer layer) {
        return LAYERS.get(layer);
    }

    // run a screen-space stage (GUI-area hooks) with no level-render context; passes get the current camera
    public static void runStage(RenderStage stage) {
        if (STAGES.get(stage).isEmpty()) return;
        runStage(stage, new FrameContext(CameraSnapshot.current(), null, 0));
    }

    // true if a stage has any registered pass, lets callers skip work entirely when nothing is registered
    public static boolean has(RenderStage stage) {
        return !STAGES.get(stage).isEmpty();
    }

    // run every enabled pass in a stage, in order. never throws - a failing pass is logged and skipped
    public static void runStage(RenderStage stage, FrameContext ctx) {
        List<PassHandle> list = STAGES.get(stage);
        if (list.isEmpty()) return;
        // GL_TIME_ELAPSED queries force driver serialization around every pass, only pay that when the
        // profiler inspector is actually open. CPU timing (nanoTime) is cheap and stays on
        boolean gpuProfile = AmneticEditor.isEnabled();
        for (PassHandle h : list) {
            if (h.removed || !h.enabled) continue;
            RenderPass pass = h.pass;
            if (!safeEnabled(pass, stage)) continue;
            String label = h.label() != null ? h.label() : pass.getClass().getSimpleName();
            long start = System.nanoTime();
            if (gpuProfile) {
                if (h.gpuTimer == null) h.gpuTimer = new GpuTimer();
                h.gpuTimer.begin();
            }
            try {
                pass.render(ctx);
            } catch (Exception e) {
                LOGGER.error("pass in stage {} threw; skipping it this frame", stage, e);
            } finally {
                if (gpuProfile && h.gpuTimer != null) {
                    h.gpuTimer.end();
                    float gpuMs = h.gpuTimer.lastMs();
                    if (gpuMs >= 0f) PassProfiler.INSTANCE.recordGpu(stage, label, gpuMs);
                }
                PassProfiler.INSTANCE.record(stage, label, System.nanoTime() - start);
            }
        }
        // clean baseline after the stage so the next stage (and vanilla) start from known state
        GlState.endFullscreen();
    }

    private static boolean safeEnabled(RenderPass pass, RenderStage stage) {
        try {
            return pass.enabled();
        } catch (Exception e) {
            LOGGER.error("pass.enabled() in stage {} threw; skipping it", stage, e);
            return false;
        }
    }
}
