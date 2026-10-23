# TAA + Specular AA + CAS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Full-frame temporal anti-aliasing (with projection jitter), specular AA in deferred lighting, and a CAS sharpen pass, per `docs/superpowers/specs/2026-07-07-taa-design.md`.

**Architecture:** A `client/taa` package following the `client/ssr` pattern (facade + settings + internal passes). Jitter is injected in the existing `GameRendererMixin` projection Redirect (the single `ProjectionMatrixBuffer.getBuffer` site in `renderLevel`). TAA resolve runs at `RenderStage.POST` priority 5 (before Bloom 10), CAS at priority 30 (after Color Grade 20). Specular AA is a small edit to `light/deferred.fsh`.

**Tech Stack:** Java 21 / Fabric mod (MC 26.1.2), LWJGL GL 3.3–4.3 shaders, JOML, existing Amnetic helpers (`ScreenPass`, `Framebuffers`, `ShaderProgram`, `GlState`, `MainTargetFramebuffer`).

## Global Constraints

- No test infrastructure exists in Amnetic (`src/test` absent) — each task verifies with `./gradlew compileJava` (or `build`) and the final task verifies in-game via pun's `runClient`.
- Shaders: fullscreen passes reuse `amnetic:shaders/util/fullscreen.vsh` (`out vec2 vUV`).
- Follow existing pass conventions exactly: singleton `extends ScreenPass`, settings object passed via `render(Settings)`, fail-soft (missing resources → return false / pass-through).
- History/aux framebuffers via `Framebuffers.screen(name, spec)`; main-target writes via `MainTargetFramebuffer.bind()/restore(prevFbo)`.
- Commit after each task with the given message.

---

### Task 1: TaaSettings + Taa facade + jitter state

**Files:**
- Create: `src/main/java/com/meekdev/amnetic/client/taa/TaaSettings.java`
- Create: `src/main/java/com/meekdev/amnetic/client/taa/Taa.java`
- Create: `src/main/java/com/meekdev/amnetic/client/taa/internal/TaaJitter.java`

**Interfaces:**
- Produces: `Taa.settings() -> TaaSettings`, `Taa.render()`, `Taa.renderSharpen()`, `Taa.dispose()` (render methods stubbed until Tasks 2/4 fill them in).
- Produces: `TaaSettings.isEnabled()/enabled(boolean)`, `feedback() -> float [0..0.98]` (default 0.9), `sharpness() -> float [0..1]` (default 0.4, CAS strength).
- Produces: `TaaJitter.INSTANCE.beginFrame(int width, int height)` (advances Halton index, computes NDC jitter), `applyTo(Matrix4f proj)` (premultiplies clip-space translation in place), `jitterX()/jitterY()` (current NDC offsets), `unjitter(Matrix4f viewProj, Matrix4f dest)` (premultiplies inverse translation), `reset()`.

- [ ] **Step 1: Write the three classes**

`TaaSettings.java` — mirror `SsrSettings` style:

```java
package com.meekdev.amnetic.client.taa;

public final class TaaSettings {

    private boolean enabled = true;
    private float feedback = 0.9f;
    private float sharpness = 0.4f;

    TaaSettings() {}

    public TaaSettings enabled(boolean v) { this.enabled = v; return this; }
    public boolean isEnabled() { return enabled; }

    public TaaSettings feedback(float v) { this.feedback = Math.max(0f, Math.min(0.98f, v)); return this; }
    public float feedback() { return feedback; }

    public TaaSettings sharpness(float v) { this.sharpness = Math.max(0f, Math.min(1f, v)); return this; }
    public float sharpness() { return sharpness; }
}
```

`Taa.java` — mirror `Ssr` facade (render bodies wired in Tasks 2/4):

```java
package com.meekdev.amnetic.client.taa;

import com.meekdev.amnetic.client.taa.internal.CasPass;
import com.meekdev.amnetic.client.taa.internal.TaaPass;

public final class Taa {

    private static final TaaSettings SETTINGS = new TaaSettings();

    private Taa() {}

    public static TaaSettings settings() { return SETTINGS; }

    public static void enable() { SETTINGS.enabled(true); }

    public static void disable() { SETTINGS.enabled(false); }

    public static void render() { TaaPass.INSTANCE.render(SETTINGS); }

    public static void renderSharpen() { CasPass.INSTANCE.render(SETTINGS); }

    public static void dispose() {
        TaaPass.INSTANCE.dispose();
        CasPass.INSTANCE.dispose();
    }
}
```

`TaaJitter.java`:

```java
package com.meekdev.amnetic.client.taa.internal;

import org.joml.Matrix4f;

// Sub-pixel projection jitter for TAA. Halton(2,3), 8-frame cycle, expressed as a clip-space
// translation premultiplied onto the projection so every consumer of the frame's projection
// (terrain, entities, Amnetic geometry, FrameView capture) sees the same jitter.
public final class TaaJitter {

    public static final TaaJitter INSTANCE = new TaaJitter();

    private static final int CYCLE = 8;
    private static final float[] HALTON_X = new float[CYCLE];
    private static final float[] HALTON_Y = new float[CYCLE];
    static {
        for (int i = 0; i < CYCLE; i++) {
            HALTON_X[i] = halton(i + 1, 2) - 0.5f;
            HALTON_Y[i] = halton(i + 1, 3) - 0.5f;
        }
    }

    private final Matrix4f scratch = new Matrix4f();
    private int frame;
    private float jitterX, jitterY; // NDC units

    private TaaJitter() {}

    private static float halton(int index, int base) {
        float f = 1f, r = 0f;
        while (index > 0) {
            f /= base;
            r += f * (index % base);
            index /= base;
        }
        return r;
    }

    public void beginFrame(int width, int height) {
        frame = (frame + 1) % CYCLE;
        jitterX = HALTON_X[frame] * 2f / Math.max(1, width);
        jitterY = HALTON_Y[frame] * 2f / Math.max(1, height);
    }

    public float jitterX() { return jitterX; }
    public float jitterY() { return jitterY; }

    public void applyTo(Matrix4f proj) {
        scratch.translation(jitterX, jitterY, 0f).mul(proj, proj);
    }

    public Matrix4f unjitter(Matrix4f viewProj, Matrix4f dest) {
        return scratch.translation(-jitterX, -jitterY, 0f).mul(viewProj, dest);
    }

    public void reset() { jitterX = 0f; jitterY = 0f; }
}
```

- [ ] **Step 2: Compile**

Run: `cd /home/meek/Desktop/DEV/Amnetic && ./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL (Taa references TaaPass/CasPass — those are created as minimal stubs in this task if compile requires them; see note). To keep this task self-contained, create the two pass stubs now:

`internal/TaaPass.java` and `internal/CasPass.java` minimal stubs (filled in Tasks 2/4):

```java
package com.meekdev.amnetic.client.taa.internal;

import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.ScreenPass;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.client.taa.TaaSettings;
import net.minecraft.resources.Identifier;

public final class TaaPass extends ScreenPass {

    public static final TaaPass INSTANCE = new TaaPass();

    private TaaPass() { super("TAA"); }

    private TaaSettings settings;

    public void render(TaaSettings s) { this.settings = s; dispatch(); }

    @Override protected boolean enabled() { return settings != null && settings.isEnabled(); }

    @Override protected ShaderProgram createProgram() {
        return new ShaderProgram(
                Identifier.fromNamespaceAndPath("amnetic", "shaders/util/fullscreen.vsh"),
                Identifier.fromNamespaceAndPath("amnetic", "shaders/taa/taa.fsh"));
    }

    @Override protected boolean record(CameraSnapshot cam, ShaderProgram program) { return true; }
}
```

(CasPass identical shape: `super("CAS")`, shader `shaders/taa/cas.fsh`.)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/meekdev/amnetic/client/taa
git commit -m "feat(taa): settings, facade and Halton jitter state"
```

---

### Task 2: Jitter injection in GameRendererMixin

**Files:**
- Modify: `src/main/java/com/meekdev/amnetic/mixin/GameRendererMixin.java:63-76`

**Interfaces:**
- Consumes: `TaaJitter.INSTANCE.beginFrame/applyTo/reset`, `Taa.settings().isEnabled()`.

- [ ] **Step 1: Edit the Redirect**

Replace the body of `amnetic$captureBobbedProjection` so the world projection is jittered in place before both the FrameView capture and the GPU upload (skip during scene captures so reflection probes stay stable):

```java
    private GpuBufferSlice amnetic$captureBobbedProjection(ProjectionMatrixBuffer buffer, Matrix4f projectionMatrix) {
        if (!CaptureManager.INSTANCE.isCapturing()) {
            if (Taa.settings().isEnabled()) {
                Minecraft mc = Minecraft.getInstance();
                TaaJitter.INSTANCE.beginFrame(
                        mc.getMainRenderTarget().width, mc.getMainRenderTarget().height);
                TaaJitter.INSTANCE.applyTo(projectionMatrix);
            } else {
                TaaJitter.INSTANCE.reset();
            }
            FrameView.INSTANCE.setProjection(projectionMatrix);
        }
        return buffer.getBuffer(projectionMatrix);
    }
```

Add imports: `com.meekdev.amnetic.client.taa.Taa`, `com.meekdev.amnetic.client.taa.internal.TaaJitter`.

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/meekdev/amnetic/mixin/GameRendererMixin.java
git commit -m "feat(taa): jitter world projection at the single upload site"
```

---

### Task 3: TAA resolve shader + full TaaPass

**Files:**
- Create: `src/main/resources/assets/amnetic/shaders/taa/taa.fsh`
- Create: `src/main/resources/assets/amnetic/shaders/taa/blit.fsh`
- Rewrite: `src/main/java/com/meekdev/amnetic/client/taa/internal/TaaPass.java`

**Interfaces:**
- Consumes: `TaaJitter.INSTANCE.unjitter(...)`, `CameraSnapshot` fields, `Framebuffers.screen`, `MainTargetFramebuffer`.
- Produces: resolved anti-aliased image written back to the main target each frame.

- [ ] **Step 1: Write `taa.fsh`**

Uniforms: `ColorSampler` (current frame), `History`, `DepthSampler`, `PrevViewProj` (unjittered), `InvViewProj` (unjittered current, inverted), `EyeDelta`, `ZeroToOne`, `Feedback`. Resolve = reproject via depth reconstruction (same pattern as `ssr/temporal.fsh` but full-screen, sky included), Catmull-Rom history sampling, 3×3 YCoCg variance clipping, velocity-scaled feedback, inverse-luminance-weighted blend. (Complete shader source is written at implementation time; it must include `#include "amnetic:shaders/common/screen.glsl"`-style reconstruct or a local copy since `screen.glsl` also defines unrelated helpers — use a local `reconstruct()` to avoid pulling `betterNormal`'s `DepthSampler` coupling.)

`blit.fsh` — trivial textured copy (`uniform sampler2D ColorSampler; FragColor = texture(ColorSampler, vUV);`).

- [ ] **Step 2: Rewrite `TaaPass.record`**

Mirror `SsrPass`: full-res capture fb (RGBA8 color + depthTexture), two RGBA16F history fbs, ping-pong; store prev unjittered view-proj + prev eye; `Feedback = historyValid ? s.feedback() : 0`; after resolve, bind main target and draw resolved via blit program. `onDispose` frees everything and clears `historyValid`.

- [ ] **Step 3: Compile + build jar**

Run: `./gradlew build -x test -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/assets/amnetic/shaders/taa src/main/java/com/meekdev/amnetic/client/taa/internal/TaaPass.java
git commit -m "feat(taa): full-screen temporal resolve pass"
```

---

### Task 4: CAS sharpen shader + CasPass

**Files:**
- Create: `src/main/resources/assets/amnetic/shaders/taa/cas.fsh`
- Rewrite: `src/main/java/com/meekdev/amnetic/client/taa/internal/CasPass.java`

**Interfaces:**
- Consumes: `TaaSettings.sharpness()`; `Framebuffers.captureColor()`.
- Produces: sharpened main target after color grade.

- [ ] **Step 1: Write `cas.fsh`** — AMD CAS-style adaptive sharpen: cross-neighborhood min/max, per-pixel amplification `sqrt(clamp(min(mn, 1-mx)/mx))`, developer knob mapped `Sharpness ∈ [0..1]` to peak `-1/(8..5)` weights.

- [ ] **Step 2: Rewrite `CasPass.record`** — capture main color, bind main, draw with `Sharpness` uniform. `enabled()` = settings enabled AND sharpness > 0.

- [ ] **Step 3: Compile**: `./gradlew compileJava -q` → BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/assets/amnetic/shaders/taa/cas.fsh src/main/java/com/meekdev/amnetic/client/taa/internal/CasPass.java
git commit -m "feat(taa): CAS adaptive sharpen pass"
```

---

### Task 5: Specular AA in deferred lighting

**Files:**
- Modify: `src/main/resources/assets/amnetic/shaders/light/deferred.fsh` (~line 306–316, where `rough` is read from `GMaterialSampler`)

- [ ] **Step 1: Widen roughness by screen-space normal variance** (Tokuyoshi/Kaplanyan kernel roughness), immediately after `rough` is final:

```glsl
    // Specular AA: widen roughness by the pixel-footprint normal variance so distant/curved
    // surfaces stop sparkling instead of aliasing the highlight.
    vec3 nDx = dFdx(N), nDy = dFdy(N);
    float nVar = 0.25 * (dot(nDx, nDx) + dot(nDy, nDy));
    rough = clamp(sqrt(rough * rough + min(2.0 * nVar, 0.18)), 0.0, 1.0);
```

- [ ] **Step 2: Build**: `./gradlew build -x test -q` → BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/assets/amnetic/shaders/light/deferred.fsh
git commit -m "feat(light): specular AA via normal-variance roughness widening"
```

---

### Task 6: Pipeline + editor wiring

**Files:**
- Modify: `src/main/java/com/meekdev/amnetic/client/AmneticClient.java` (registerDefaultPasses + AmneticResources block)
- Create: `src/main/java/com/meekdev/amnetic/client/ui/inspector/TaaInspector.java`
- Modify: `src/main/java/com/meekdev/amnetic/client/ui/AmneticEditor.java:43-44`

- [ ] **Step 1: Register passes**

```java
        Pipeline.add(RenderStage.POST, 5, "TAA", ctx -> Taa.render());
        Pipeline.add(RenderStage.POST, 10, "Bloom", ctx -> Bloom.render(ctx.fabric()));
        Pipeline.add(RenderStage.POST, 20, "Color Grade", ctx -> ColorGrade.render());
        Pipeline.add(RenderStage.POST, 30, "CAS Sharpen", ctx -> Taa.renderSharpen());
```

and `AmneticResources.register(Taa::dispose);` next to the other disposals.

- [ ] **Step 2: TaaInspector** — mirror `SsaoInspector`: enable checkbox, feedback slider (0–0.98), sharpness slider (0–1). Add `inspectors.add(new TaaInspector());` in `AmneticEditor`.

- [ ] **Step 3: Build**: `./gradlew build -x test -q` → BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/meekdev/amnetic/client/AmneticClient.java src/main/java/com/meekdev/amnetic/client/ui
git commit -m "feat(taa): pipeline registration and editor inspector"
```

---

### Task 7: In-game verification via pun

- [ ] **Step 1:** `cd /home/meek/Desktop/DEV/Amnetic && ./gradlew build -x test` then launch pun's `runClient`.
- [ ] **Step 2:** Verify: distant shimmer gone in motion; no ghost trail behind a moving entity; no GUI wobble; textures crisp (CAS); toggling TAA in the Amnetic editor inspector works; GPU profiler shows TAA+CAS ≲ 1 ms.
- [ ] **Step 3:** Fix anything found, commit fixes.
