package com.meekdev.amnetic.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.opengl.ARBParallelShaderCompile;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.KHRParallelShaderCompile;

public final class ProgramLink {
    private static final int COMPLETION_STATUS = 0x91B1;
    private static final long BLOCKING_GAP = 16_000_000L;

    private static final int QUEUED = 0, RUNNING = 1, DONE = 2, TAKEN_BACK = 3;

    private static Boolean parallel;
    private static long lastBlockingFinish;
    private static int started;
    private static final AtomicInteger CACHED = new AtomicInteger(), FAILED = new AtomicInteger();
    private static long waitedNanos, forcedNanos, checkNanos;
    private static final AtomicLong WORKER_NANOS = new AtomicLong();

    private final String name;
    private final List<Stage> stages;
    private final List<Attribute> attributes;
    private final boolean soon;
    final long order;
    private int program;
    private int[] shaders;
    private String key;
    private boolean done, failed, fromCache, taken;
    private String problem;
    private int failedStage = -1;

    private final AtomicInteger state = new AtomicInteger(-1);
    private final CountDownLatch built = new CountDownLatch(1);

    public record Stage(int type, String source, String label) {}

    private record Attribute(int index, String name) {}

    private static long submitted;

    private ProgramLink(String name, List<Stage> stages, List<Attribute> attributes, boolean soon) {
        this.name = name;
        this.stages = stages;
        this.attributes = attributes;
        this.soon = soon;
        this.order = submitted++;
    }

    boolean soon() {
        return soon;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static ProgramLink start(String name, String vertex, String fragment) {
        return builder(name).vertex(vertex, name).fragment(fragment, name).start();
    }

    public static boolean parallel() {
        if (parallel == null) {
            boolean worker = ProgramWorker.available();
            GLCapabilities caps = GL.getCapabilities();
            boolean khr = caps.GL_KHR_parallel_shader_compile;
            boolean arb = caps.GL_ARB_parallel_shader_compile;
            boolean driver = (khr || arb) && !"false".equals(System.getProperty("amnetic.parallelCompile"));
            if (driver && khr) KHRParallelShaderCompile.glMaxShaderCompilerThreadsKHR(0xFFFFFFFF);
            else if (driver) ARBParallelShaderCompile.glMaxShaderCompilerThreadsARB(0xFFFFFFFF);
            parallel = worker || driver;
        }
        return parallel;
    }

    public static String stats() {
        String how = ProgramWorker.available() ? ProgramWorker.threads() + " compile threads worked " + ms(WORKER_NANOS.get()) + " ms"
                : parallel() ? "the driver compiled in parallel" : "the driver compiled in place";
        return started + " programs, " + CACHED.get() + " from the program cache, " + FAILED.get() + " failed, " + how
                + ", the render thread spent " + ms(waitedNanos) + " ms, " + ms(forcedNanos) + " ms of it waiting on ones needed early, "
                + ms(checkNanos) + " ms checking shaders";
    }

    private static long ms(long nanos) {
        return nanos / 1_000_000L;
    }

    private void begin() {
        long t0 = System.nanoTime();
        started++;
        parallel();
        key = ProgramCache.enabled() ? ProgramCache.key(stages, attributes.toString()) : null;
        if (ProgramWorker.available()) {
            state.set(QUEUED);
            ProgramWorker.submit(this);
        } else if (key == null || !fromCacheNow()) {
            compileAndLink();
        }
        waitedNanos += System.nanoTime() - t0;
    }

    boolean claim() {
        return state.compareAndSet(QUEUED, RUNNING);
    }

    void buildOnWorker() {
        long t0 = System.nanoTime();
        try {
            build();
        } catch (RuntimeException e) {
            failed = true;
            problem = String.valueOf(e);
            program = 0;
            done = true;
        }
        GL11.glFinish();
        WORKER_NANOS.addAndGet(System.nanoTime() - t0);
        state.set(DONE);
        built.countDown();
    }

    private void build() {
        if (key != null && fromCacheNow()) return;
        compileAndLink();
        finish();
    }

    private boolean fromCacheNow() {
        int loaded = ProgramCache.load(key);
        if (loaded == 0) return false;
        program = loaded;
        fromCache = true;
        done = true;
        CACHED.incrementAndGet();
        return true;
    }

    private void compileAndLink() {
        program = GL20.glCreateProgram();
        shaders = new int[stages.size()];
        for (int i = 0; i < shaders.length; i++) {
            Stage stage = stages.get(i);
            int shader = GL20.glCreateShader(stage.type());
            GL20.glShaderSource(shader, stage.source());
            GL20.glCompileShader(shader);
            GL20.glAttachShader(program, shader);
            shaders[i] = shader;
        }
        for (Attribute a : attributes) GL20.glBindAttribLocation(program, a.index(), a.name());
        if (key != null) ProgramCache.retrievable(program);
        GL20.glLinkProgram(program);
    }

    public boolean ready() {
        int s = state.get();
        if (s >= 0) return s == DONE || s == TAKEN_BACK;
        if (done) return true;
        if (parallel()) {
            if (GL20.glGetProgrami(program, COMPLETION_STATUS) == GL11.GL_FALSE) return false;
        } else {
            long now = System.nanoTime();
            if (now - lastBlockingFinish < BLOCKING_GAP) return false;
            lastBlockingFinish = now;
        }
        long t0 = System.nanoTime();
        finish();
        waitedNanos += System.nanoTime() - t0;
        return true;
    }

    public int program() {
        await();
        if (failed) throw new RuntimeException(name + ": " + problem);
        taken = true;
        return program;
    }

    public boolean failed() {
        await();
        return failed;
    }

    public boolean fromCache() {
        return fromCache;
    }

    public String problem() {
        return problem;
    }

    public int failedStage() {
        return failedStage;
    }

    public void delete() {
        if (taken) return;
        if (state.get() >= 0) {
            if (state.compareAndSet(QUEUED, TAKEN_BACK)) {
                done = true;
                return;
            }
            awaitWorker();
        }
        if (shaders != null) {
            for (int shader : shaders) GL20.glDeleteShader(shader);
            shaders = null;
        }
        if (program != 0) GL20.glDeleteProgram(program);
        program = 0;
        done = true;
    }

    private void await() {
        int s = state.get();
        if (s == DONE || s == TAKEN_BACK || s < 0 && done) return;
        long t0 = System.nanoTime();
        if (s < 0) {
            finish();
        } else if (state.compareAndSet(QUEUED, TAKEN_BACK)) {
            build();
        } else {
            awaitWorker();
        }
        forcedNanos += System.nanoTime() - t0;
        waitedNanos += System.nanoTime() - t0;
    }

    private void awaitWorker() {
        try {
            built.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(name + ": interrupted while waiting for the compile thread", e);
        }
    }

    private void finish() {
        boolean linked = GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != GL11.GL_FALSE;
        if (!linked) {
            StringBuilder log = new StringBuilder();
            for (int i = 0; i < shaders.length; i++) {
                if (GL20.glGetShaderi(shaders[i], GL20.GL_COMPILE_STATUS) != GL11.GL_FALSE) continue;
                if (failedStage < 0) failedStage = i;
                log.append(stages.get(i).label()).append(": ").append(GL20.glGetShaderInfoLog(shaders[i])).append('\n');
            }
            if (failedStage < 0) log.append(GL20.glGetProgramInfoLog(program));
            problem = log.toString().strip();
            failed = true;
            FAILED.incrementAndGet();
        } else if (key != null) {
            ProgramCache.store(key, program);
        }
        for (int shader : shaders) {
            GL20.glDetachShader(program, shader);
            GL20.glDeleteShader(shader);
        }
        shaders = null;
        if (failed) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
        done = true;
    }

    public static final class Builder {
        private final String name;
        private final List<Stage> stages = new ArrayList<>();
        private final List<Attribute> attributes = new ArrayList<>();
        private boolean soon;

        private Builder(String name) {
            this.name = name;
        }

        public Builder vertex(String source, Object label) {
            return stage(GL20.GL_VERTEX_SHADER, source, label);
        }

        public Builder fragment(String source, Object label) {
            return stage(GL20.GL_FRAGMENT_SHADER, source, label);
        }

        public Builder stage(int type, String source, Object label) {
            stages.add(new Stage(type, source, String.valueOf(label)));
            return this;
        }

        public Builder attribute(int index, String attribute) {
            attributes.add(new Attribute(index, attribute));
            return this;
        }

        public Builder soon() {
            soon = true;
            return this;
        }

        public String check() {
            long t0 = System.nanoTime();
            parallel();
            String key = ProgramCache.enabled() ? ProgramCache.key(stages, "check") : null;
            String problem = null;
            if (key == null || !ProgramCache.checked(key)) {
                int[] shaders = new int[stages.size()];
                for (int i = 0; i < shaders.length; i++) {
                    shaders[i] = GL20.glCreateShader(stages.get(i).type());
                    GL20.glShaderSource(shaders[i], stages.get(i).source());
                    GL20.glCompileShader(shaders[i]);
                }
                for (int shader : shaders) {
                    if (problem == null && GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                        problem = GL20.glGetShaderInfoLog(shader);
                    }
                    GL20.glDeleteShader(shader);
                }
                if (problem == null && key != null) ProgramCache.markChecked(key);
            }
            waitedNanos += System.nanoTime() - t0;
            checkNanos += System.nanoTime() - t0;
            return problem;
        }

        public ProgramLink start() {
            ProgramLink link = new ProgramLink(name, List.copyOf(stages), List.copyOf(attributes), soon);
            link.begin();
            return link;
        }
    }
}
