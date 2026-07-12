# Shader Hot Reload

Editing a shader normally means rebuilding resources or at least a full F3+T resource reload. In a development run, Amnetic short-circuits that: save a shader file in your editor and the change is live in-game about a tick later, no reload screen, no restart. Broken saves don't crash anything either; a pass that fails to compile backs itself off and comes back the moment you save a fix.

For your own mod it's one line at client init:

```java
import com.meekdev.amnetic.client.dev.ShaderHotReload;

@Override
public void onInitializeClient() {
    ShaderHotReload.watchMod("mymodid");
}
```

That's safe to ship: outside a development environment `watchMod` is a no-op, so there is nothing to strip for release builds. Amnetic calls `watchMod("amnetic")` for itself, so its own shaders are always live in dev.

---

## How it works

In a dev run the game doesn't read shaders from `src/main/resources`; the `ResourceManager` reads the Gradle classpath copy under `build/resources/main`. Editing the source file alone changes nothing until the next build. Hot reload bridges the two:

1. `watchMod(id)` finds the mod's classpath resource roots, keeps the ones that are plain directories ending in `build/resources/main` (jars aren't hot-editable), and maps each back to its project's `src/main/resources`. A background `WatchService` thread then watches every directory under that source root.
2. When a file with a shader extension (`vsh`, `fsh`, `glsl`, `comp`, `geom`, `tesc`, `tese`) is created or modified, it's queued as dirty. Directories created at runtime are picked up and watched too, so adding a brand new shader folder mid-session works.
3. On the client tick, dirty files that have been **quiet for one full tick** are copied over their `build/resources/main` twin. The one-tick debounce lets editors that save in multiple writes settle before the copy, so you never compile a half-written file.
4. After a batch of files has synced, every live `ShaderProgram` is invalidated via `ShaderProgram.invalidateAll()` (a weak registry of all instances), and each recompiles lazily the next time it's used. Registered `onReload` callbacks then fire so systems that hand-compile their own GL programs can rebuild.

Since `ShaderProgram` inlines `#include` directives at compile time, editing a shared `.glsl` include reloads every program that pulls it in; `glsl` is a watched extension for exactly that reason.

---

## The failure guard

Every Amnetic screen pass runs behind a `FailureGuard`. A save with a syntax error makes the pass's lazy recompile throw; the guard logs a warning and skips that frame. After **60 consecutive failed frames** (about a second) it declares the pass dead and disables it, so a broken shader degrades to "that effect is off" instead of an error spammed every frame or a crash.

The guard remembers the hot-reload **generation** it died at. Every successful reload batch bumps the generation, so the moment you save again, any dead pass whose generation is stale revives itself and retries with the new source. The loop is just: save, see the error in the log, fix, save, effect comes back. No restart, no manual re-enable.

---

## Hooking the reload

`ShaderProgram` instances take care of themselves. Anything that compiles GL programs by hand needs to be told:

```java
ShaderHotReload.onReload(() -> {
    // called on the render thread after changed sources have been synced
    myRawGlProgram.close();
    myRawGlProgram = null; // rebuild lazily on next use
});
```

The contract: callbacks fire on the render thread, after the changed files are already in place under `build/resources/main`, once per reload batch (not per file). A throwing callback is caught and logged without affecting the others. There is no unregister; register once at init for objects that live for the session.

Amnetic's own hand-compiled systems (instanced mesh shaders, the shadow pass, model rendering, geometry helpers) already hook this internally, so everything documented in this wiki hot-reloads out of the box. You only need `onReload` for GL programs your own mod compiles outside of `ShaderProgram`.

### Compute shaders

A [`ComputeShader`](Compute-Shaders) is compiled once at `load()` and can't be swapped in place. If you want live compute edits, recreate the instance from a callback:

```java
ShaderHotReload.onReload(() -> {
    if (myCompute != null) { myCompute.close(); myCompute = null; }
});
// ... and load it lazily where you dispatch it
```

---

## Limitations

**Dev only.** Everything is gated on `FabricLoader.isDevelopmentEnvironment()`. In production `watchMod` returns immediately; there is no way (and no reason) to hot-reload from a shipped jar.

**Standard Gradle layout assumed.** The source root is derived by walking up from `build/resources/main` to the project directory and appending `src/main/resources`. Custom source-set layouts won't be found, in which case `watchMod` silently does nothing for that root.

**Only Amnetic-loaded shaders.** The reload path is `ShaderProgram.invalidateAll()` plus the callbacks. Vanilla JSON post effects and core shaders go through Minecraft's own pipeline caches and are not touched; use F3+T for those.

**One tick of latency.** The debounce means a save takes effect on the next quiet client tick, not instantly. In practice that's under 100 ms and you won't notice.

**Deleted files aren't un-synced.** Deleting a shader source doesn't remove the copy in `build/resources/main`; only creates and modifies are handled. A clean build sorts that out.

---

## Reference

### `ShaderHotReload`

```java
static void watchMod(String modId); // start watching a mod's shader sources; no-op outside dev
static void onReload(Runnable callback); // fires on the render thread after each reload batch
static long generation(); // bumped after every reload batch; what FailureGuard revives on
```

### `ShaderProgram` (the reload-relevant part)

```java
static void invalidateAll(); // mark every live program stale; each recompiles on next begin()
void invalidate(); // mark one program stale
```

Every `ShaderProgram` registers itself in a weak live-instance registry on construction, so `invalidateAll` reaches all of them and abandoned programs drop out on their own.

### `FailureGuard`

```java
FailureGuard(String name, int maxConsecutive); // screen passes use 60
boolean alive(); // false while dead; auto-revives when generation() has moved on
void success(); // resets the consecutive-failure counter
void fail(Logger log, Throwable e); // count a failure; kills the guard at the threshold
```

---

## See Also

- [Writing Shaders](Writing-Shaders). The shader sources this system keeps live.
- [Compute Shaders](Compute-Shaders). Compiled at `load()`; recreate them from an `onReload` callback.
- [Render Pipeline](Render-Pipeline). The passes the failure guard protects.
