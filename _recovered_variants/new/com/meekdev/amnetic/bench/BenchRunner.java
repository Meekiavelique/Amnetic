package com.meekdev.amnetic.bench;

import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.meekdev.amnetic.client.camera.CameraDirector;
import com.mojang.blaze3d.platform.NativeImage;
import com.meekdev.amnetic.client.pipeline.PassProfiler;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;

public final class BenchRunner {

    public static final BenchRunner INSTANCE = new BenchRunner();

    private static final float WARMUP_SECONDS = 3f;
    private static final float FIRST_WARMUP_SECONDS = 12f;
    private static final int SHOT_SETTLE_FRAMES = 45;
    private static final double SHOT_SETTLE_SECONDS = 1.6;
    private static final double ORBIT_RADIUS = 18.0;

    private enum Phase { IDLE, SHOT, WARMUP, MEASURE, SHOW }

    public record PassStat(String label, double cpuMs, double gpuMs) {}

    public record Result(String scene, String description, int frames, double avgMs, double p50Ms, double p95Ms,
                         double p99Ms, double maxMs, double onePercentLowFps, int glErrors, int warnings,
                         int errors, List<String> logSamples, List<PassStat> passes) {}

    private final List<BenchScene> queue = new ArrayList<>();
    private final List<Result> results = new ArrayList<>();
    private final List<Double> frames = new ArrayList<>();
    private final Map<String, double[]> passAccum = new LinkedHashMap<>();
    private Phase phase = Phase.IDLE;
    private BenchScene current;
    private int index;
    private float seconds;
    private long phaseStart;
    private long lastFrame;
    private int glErrors;
    private Vec3 center = Vec3.ZERO;

    private Runnable onFinished;
    private int shotFrames;
    private boolean shotRequested;
    private Path shotDir;
    private boolean hadHiddenGui;

    private BenchRunner() {}

    public void onFinished(Runnable callback) {
        this.onFinished = callback;
    }

    public boolean running() {
        return phase == Phase.SHOT || phase == Phase.WARMUP || phase == Phase.MEASURE;
    }

    public void run(List<BenchScene> scenes, float measureSeconds) {
        stop();
        queue.clear();
        queue.addAll(scenes);
        results.clear();
        seconds = Math.max(2f, measureSeconds);
        index = -1;
        center = BenchArena.center();
        shotDir = Minecraft.getInstance().gameDirectory.toPath().resolve("amnetic-bench")
                .resolve("shots-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        hadHiddenGui = Minecraft.getInstance().options.hideGui;
        Minecraft.getInstance().options.hideGui = true;
        Minecraft.getInstance().options.pauseOnLostFocus = false;
        LogCapture.INSTANCE.install();
        if (Boolean.getBoolean("amnetic.bench.gldebug")) GlDebug.install();
        Pipeline.gpuTimings(true);
        chat("Amnetic bench: " + queue.size() + " scene(s), " + (int) seconds + "s each", ChatFormatting.AQUA);
        for (String f : Scenes.failures()) chat("init failed, " + f, ChatFormatting.RED);
        next();
    }

    public void show(BenchScene scene) {
        stop();
        center = BenchArena.center();
        current = scene;
        Scenes.resetAll();
        scene.start().accept(center);
        phase = Phase.SHOW;
        chat("showing " + scene.name() + ": " + scene.description(), ChatFormatting.AQUA);
    }

    public void stop() {
        boolean wasRunning = running();
        if (current != null) current.stop().run();
        current = null;
        CameraDirector.releaseNow();
        AmneticCamera.clearPose();
        Pipeline.gpuTimings(false);
        if (wasRunning) Minecraft.getInstance().options.hideGui = hadHiddenGui;
        phase = Phase.IDLE;
        if (wasRunning && !results.isEmpty()) finish();
    }

    public void onFrame() {
        Scenes.frame();
        if (!running()) return;
        long now = System.nanoTime();
        double elapsed = (now - phaseStart) / 1.0e9;
        if (phase == Phase.SHOT) {
            Minecraft.getInstance().options.hideGui = true;
            if (Minecraft.getInstance().screen != null) Minecraft.getInstance().setScreen(null);
            if (++shotFrames >= SHOT_SETTLE_FRAMES && elapsed >= SHOT_SETTLE_SECONDS) shotRequested = true;
            return;
        }
        if (phase == Phase.WARMUP) {
            if (elapsed >= warmupSeconds()) beginMeasure(now);
            lastFrame = now;
            return;
        }
        frames.add((now - lastFrame) / 1.0e6);
        lastFrame = now;
        glErrors += drainGlErrors();
        for (Map.Entry<RenderStage, List<PassProfiler.Entry>> stage : PassProfiler.INSTANCE.snapshot().entrySet()) {
            for (PassProfiler.Entry e : stage.getValue()) {
                double[] acc = passAccum.computeIfAbsent(stage.getKey() + " / " + e.label, k -> new double[4]);
                acc[0] += e.lastMs;
                acc[1]++;
                if (e.lastGpuMs >= 0) {
                    acc[2] += e.lastGpuMs;
                    acc[3]++;
                }
            }
        }
        if (elapsed >= seconds) {
            record();
            next();
        }
    }

    private void next() {
        if (current != null) current.stop().run();
        index++;
        if (index >= queue.size()) {
            current = null;
            CameraDirector.releaseNow();
            AmneticCamera.clearPose();
            Pipeline.gpuTimings(false);
            Minecraft.getInstance().options.hideGui = hadHiddenGui;
            phase = Phase.IDLE;
            finish();
            return;
        }
        current = queue.get(index);
        Scenes.resetAll();
        GlDebug.scene(current.name());
        current.start().accept(center);
        CameraDirector.releaseNow();
        Vec3 eye = center.add(-13, 6, -13);
        Vec3 look = center.add(0, 1, 0).subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
        AmneticCamera.setPose(eye, yaw, pitch);
        shotFrames = 0;
        shotRequested = false;
        phase = Phase.SHOT;
        phaseStart = System.nanoTime();
        chat("[" + (index + 1) + "/" + queue.size() + "] " + current.name(), ChatFormatting.GRAY);
    }

    public void onClientTick() {
        if (phase != Phase.SHOT || !shotRequested) return;
        shotRequested = false;
        Minecraft mc = Minecraft.getInstance();
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(shotDir);
            image.writeToFile(shotDir.resolve(String.format(Locale.ROOT, "%02d-%s.png", index, current.name())));
            if (Boolean.getBoolean("amnetic.bench.dump")) {
                String prefix = String.format(Locale.ROOT, "%02d-%s", index, current.name());
                List<String> lines = TextureDump.dump(shotDir.resolve("dump"), prefix);
                Files.write(shotDir.resolve("dump").resolve(prefix + ".txt"), lines);
            }
        } catch (IOException e) {
            chat("screenshot failed: " + e.getMessage(), ChatFormatting.RED);
        }
        AmneticCamera.clearPose();
        CameraDirector.orbit(center.add(0, 4, 0), ORBIT_RADIUS, 360f / (warmupSeconds() + seconds));
        phase = Phase.WARMUP;
        phaseStart = System.nanoTime();
    }

    private float warmupSeconds() {
        return index == 0 ? FIRST_WARMUP_SECONDS : WARMUP_SECONDS;
    }

    private void beginMeasure(long now) {
        phase = Phase.MEASURE;
        phaseStart = now;
        frames.clear();
        passAccum.clear();
        drainGlErrors();
        glErrors = 0;
        LogCapture.INSTANCE.reset();
    }

    private void record() {
        double[] f = frames.stream().mapToDouble(Double::doubleValue).toArray();
        if (f.length == 0) return;
        double[] sorted = f.clone();
        Arrays.sort(sorted);
        double avg = Arrays.stream(f).average().orElse(0);
        int worst = Math.max(1, sorted.length / 100);
        double worstAvg = 0;
        for (int i = sorted.length - worst; i < sorted.length; i++) worstAvg += sorted[i];
        worstAvg /= worst;
        List<PassStat> passes = new ArrayList<>();
        for (Map.Entry<String, double[]> e : passAccum.entrySet()) {
            double[] a = e.getValue();
            double cpu = a[1] > 0 ? a[0] / a[1] : 0;
            double gpu = a[3] > 0 ? a[2] / a[3] : -1;
            if (cpu < 0.005 && gpu < 0.005) continue;
            passes.add(new PassStat(e.getKey(), cpu, gpu));
        }
        passes.sort(Comparator.comparingDouble((PassStat p) -> Math.max(p.cpuMs(), p.gpuMs())).reversed());
        Result r = new Result(current.name(), current.description(), f.length, avg,
                pct(sorted, 0.50), pct(sorted, 0.95), pct(sorted, 0.99), sorted[sorted.length - 1],
                1000.0 / worstAvg, glErrors, LogCapture.INSTANCE.warnings(), LogCapture.INSTANCE.errors(),
                LogCapture.INSTANCE.samples(), passes);
        results.add(r);
        ChatFormatting color = r.glErrors() > 0 || r.errors() > 0 ? ChatFormatting.RED : ChatFormatting.WHITE;
        chat(String.format(Locale.ROOT, "  %s: %.1f fps avg, %.1f fps 1%% low, %.2f ms p99%s",
                r.scene(), 1000.0 / r.avgMs(), r.onePercentLowFps(), r.p99Ms(),
                r.glErrors() > 0 || r.errors() > 0 ? "  (" + r.glErrors() + " GL / " + r.errors() + " log errors)" : ""), color);
    }

    private void finish() {
        if (onFinished != null) {
            Runnable callback = onFinished;
            onFinished = null;
            Minecraft.getInstance().execute(callback);
        }
        if (results.isEmpty()) return;
        try {
            Path file = write();
            chat("report: " + file, ChatFormatting.GREEN);
        } catch (IOException e) {
            chat("could not write report: " + e.getMessage(), ChatFormatting.RED);
        }
    }

    private Path write() throws IOException {
        Minecraft mc = Minecraft.getInstance();
        Path dir = mc.gameDirectory.toPath().resolve("amnetic-bench");
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = dir.resolve("bench-" + stamp + ".md");
        StringBuilder sb = new StringBuilder();
        sb.append("# Amnetic bench ").append(stamp).append("\n\n");
        sb.append("- GPU: ").append(GL11.glGetString(GL11.GL_RENDERER)).append('\n');
        sb.append("- GL: ").append(GL11.glGetString(GL11.GL_VERSION)).append('\n');
        sb.append("- resolution: ").append(mc.getWindow().getWidth()).append('x').append(mc.getWindow().getHeight()).append('\n');
        sb.append("- render distance: ").append(mc.options.getEffectiveRenderDistance()).append('\n');
        sb.append("- measure: ").append((int) seconds).append("s after ").append((int) WARMUP_SECONDS).append("s warmup, camera orbit r=")
                .append((int) ORBIT_RADIUS).append('\n');
        if (shotDir != null) sb.append("- screenshots: ").append(shotDir).append('\n');
        for (String f : Scenes.failures()) sb.append("- init failed: ").append(f).append('\n');
        sb.append("- center: ").append(String.format(Locale.ROOT, "%.1f %.1f %.1f", center.x, center.y, center.z)).append("\n\n");

        Result base = results.stream().filter(r -> r.scene().startsWith("baseline"))
                .min(Comparator.comparingDouble(Result::avgMs)).orElse(null);
        sb.append("| scene | fps avg | ms avg | ms p95 | ms p99 | fps 1% low | ms max | vs baseline | GL err | log warn/err |\n");
        sb.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Result r : results) {
            String delta = base == null || r == base ? "" : String.format(Locale.ROOT, "%+.2f ms", r.avgMs() - base.avgMs());
            sb.append(String.format(Locale.ROOT, "| %s | %.1f | %.2f | %.2f | %.2f | %.1f | %.2f | %s | %d | %d/%d |\n",
                    r.scene(), 1000.0 / r.avgMs(), r.avgMs(), r.p95Ms(), r.p99Ms(), r.onePercentLowFps(), r.maxMs(),
                    delta, r.glErrors(), r.warnings(), r.errors()));
        }
        for (Result r : results) {
            sb.append("\n## ").append(r.scene()).append("\n\n").append(r.description()).append(", ")
                    .append(r.frames()).append(" frames\n\n");
            if (!r.passes().isEmpty()) {
                sb.append("| pass | cpu ms | gpu ms |\n|---|---:|---:|\n");
                for (PassStat p : r.passes().subList(0, Math.min(10, r.passes().size()))) {
                    sb.append(String.format(Locale.ROOT, "| %s | %.3f | %s |\n", p.label(), p.cpuMs(),
                            p.gpuMs() < 0 ? "-" : String.format(Locale.ROOT, "%.3f", p.gpuMs())));
                }
            }
            for (String s : r.logSamples()) sb.append("\n    ").append(s);
            if (!r.logSamples().isEmpty()) sb.append('\n');
        }
        List<String> traces = GlDebug.traces();
        if (!traces.isEmpty()) {
            sb.append("\n## first GL errors\n\n```\n");
            for (String t : traces) sb.append(t).append('\n');
            sb.append("```\n");
        }
        Files.writeString(file, sb.toString());
        return file;
    }

    private static int drainGlErrors() {
        int n = 0;
        while (n < 64 && GL11.glGetError() != GL11.GL_NO_ERROR) n++;
        return n;
    }

    private static double pct(double[] sorted, double q) {
        int i = (int) Math.min(sorted.length - 1, Math.floor(q * (sorted.length - 1)));
        return sorted[i];
    }

    static void chat(String message, ChatFormatting color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(message).withStyle(color), false);
    }
}
