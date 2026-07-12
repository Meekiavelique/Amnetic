# Quality

Amnetic ships four screen-space effects that each carry a dozen knobs: [SSAO, SSGI, SSR](Screen-Space-Effects) and [Bloom](Bloom). Tuning them one by one is fine when you're chasing a look, but most of the time you just want "make it pretty" or "make it fast". `Quality` is that: one static call that enables or disables the whole stack and sets every knob to a coherent, tested combination.

```java
import com.meekdev.amnetic.client.Quality;

// once, at client init (or any time later)
Quality.balanced();
```

That's the entire API. There's no state, no registration, no per-frame call. Each preset is a plain static method that writes into the same global settings objects you'd otherwise touch yourself (`Ssao.settings()`, `Ssgi.settings()`, `Ssr.settings()`, `Bloom.settings()`, `ModelLighting.INSTANCE`). Amnetic's own [render pipeline](Render-Pipeline) already drives the passes every frame, so flipping a preset takes effect on the next frame with nothing else to wire up.

A preset is a starting point, not a mode. After calling one, every system stays fully configurable through its own settings:

```java
Quality.balanced();
Ssr.settings().maxSteps(96).reflectivity(0.5f); // tweak on top of the preset
```

---

## The presets

| | `off()` | `low()` | `balanced()` | `ultra()` |
|---|---|---|---|---|
| SSAO | disabled | on, half res | on, half res | on, full res |
| SSGI | disabled | disabled | on, half res | on, full res |
| SSR | disabled | disabled | on, 3/4 res, 48 steps | on, full res, 96 steps |
| Bloom | disabled | on, 4 levels | on, 6 levels | on, 7 levels |
| Tonemap / exposure | on / 1.0 | on / 1.0 | on / 1.0 | on / 1.05 |

`off()` is the cheapest, vanilla-ish look. `low()` keeps cheap ambient occlusion and emissive bloom for low-end machines or high-FPS play. `balanced()` is the intended default: AO, GI, half-res temporal reflections, bloom. `ultra()` runs everything at full resolution with more ray steps and a wider bloom.

### Exact values

Every knob each preset writes, per system. A blank cell means the preset does not touch that knob (see the next section for why that matters).

**SSAO** (`Ssao.settings()`)

| Knob | `low()` | `balanced()` | `ultra()` |
|---|---|---|---|
| `scale` | 0.5 | 0.5 | 1.0 |
| `intensity` | 0.9 | 1.0 | 1.0 |
| `radius` | 0.6 | 0.7 | 0.8 |
| `temporal` | true | true | true |
| `bias`, `power`, `feedback` | | | |

**SSGI** (`Ssgi.settings()`) - `low()` disables it entirely.

| Knob | `balanced()` | `ultra()` |
|---|---|---|
| `scale` | 0.5 | 1.0 |
| `intensity` | 0.6 | 0.7 |
| `radius` | 1.5 | 1.8 |
| `historyBlend`, `maxHistoryFrames` | | |

**SSR** (`Ssr.settings()`) - `low()` disables it entirely.

| Knob | `balanced()` | `ultra()` |
|---|---|---|
| `intensity` | 1.0 | 1.0 |
| `reflectivity` | 0.3 | 0.3 |
| `maxSteps` | 48 | 96 |
| `stride` | 0.4 | 0.3 |
| `maxDistance` | 48 | 64 |
| `thickness` | 0.6 | 0.5 |
| `edgeFade` | 0.12 | 0.1 |
| `resolution` | 0.75 | 1.0 |
| `temporal` | true | true |
| `feedback` | 0.85 | 0.88 |

**Bloom** (`Bloom.settings()`) - every preset that enables bloom sets `all(false)`, i.e. emissive-only mode.

| Knob | `low()` | `balanced()` | `ultra()` |
|---|---|---|---|
| `all` | false | false | false |
| `levels` | 4 | 6 | 7 |
| `intensity` | 0.9 | 0.9 | 1.0 |
| `threshold` | 0.75 | 0.75 | 0.7 |
| `knee` | | 0.5 | 0.5 |
| `scale`, `occlude` | | | |

**ModelLighting** (`ModelLighting.INSTANCE`)

| Knob | `low()` | `balanced()` | `ultra()` |
|---|---|---|---|
| `tonemap` | true | true | true |
| `exposure` | 1.0 | 1.0 | 1.05 |

`off()` calls `disable()` on SSAO, SSGI, SSR and Bloom, and resets `ModelLighting` to tonemap on / exposure 1.0 so an earlier `ultra()` doesn't leave the scene brighter.

---

## Presets vs. manual settings

There is no merge logic and no memory. A preset is a sequence of ordinary setter calls on the global settings objects, exactly as if you had typed them yourself. Two consequences:

**A preset overwrites the knobs it sets.** If you hand-tuned `Ssr.settings().maxSteps(200)` and later call `Quality.balanced()`, your 200 becomes 48. This includes changes made through the in-game editor: the SSAO and SSGI inspectors and the Bloom section of the Post FX inspector in `AmneticEditor` edit these same global settings objects, so applying a preset stomps editor tweaks too.

**A preset leaves alone the knobs it doesn't set.** The blank cells in the tables above survive a preset call. `Ssao.settings().bias(...)`, `Ssgi.settings().historyBlend(...)`, `Bloom.settings().scale(...)` and `occlude(...)` are never touched by any preset, so those tweaks persist across preset changes. Values on a system a preset merely disables also persist - `Quality.low()` disables SSR but doesn't reset it, so re-enabling SSR later gets you whatever values it had before.

The practical rule: call the preset first, then layer your own tweaks on top. If you flip presets at runtime (a video-settings screen, say), reapply your tweaks after each preset call.

Presets are safe to call at any time on the client thread. Everything they write is either free to change per frame or, like `Bloom.levels()`, triggers a lazy buffer rebuild on the next frame - so don't call a preset every frame, but calling one from a settings screen or a keybind is fine.

## The editor

The in-game editor does not expose presets. There is no Quality dropdown; the editor gives you per-system inspectors (SSAO, SSGI and others) plus a Bloom header inside Renderer > Post FX, each editing individual knobs. SSR has no inspector at all, so it is tunable only from code. Presets are code-only too. Since editor and presets write to the same settings objects, you can call a preset in code and then fine-tune the result live in the editor.

---

## Limitations

**Not the whole stack.** Despite covering the screen-space effects, `Quality` does not touch [shadows](Shadows), [deferred lights](Deferred-Lights), or TAA. Those keep whatever configuration they have across every preset, including `off()`.

**No way to read the current preset.** `Quality` has no getter and keeps no state; once applied, a preset is indistinguishable from hand-set values. If your mod has a settings screen, track the selection yourself.

**Presets always pick emissive-only bloom.** Every preset sets `Bloom.settings().all(false)`. If you want whole-scene HDR bloom, set `all(true)` after the preset call. (Note that plain `Bloom.enable()` outside of presets does the opposite and sets `all(true)`.)

**Global, last-writer-wins.** The settings objects are shared across all mods in the client. If two mods each apply a preset, the last call wins.

---

## Reference

### `Quality`

```java
static void off(); // disable SSAO, SSGI, SSR, bloom; reset lighting to defaults
static void low(); // cheap half-res AO + emissive bloom
static void balanced(); // AO + GI + half-res temporal SSR + bloom
static void ultra(); // full-res AO/GI/SSR, more ray steps, wider bloom
```

All methods are static, return nothing, and take effect on the next frame. Exact values per preset are in the tables above.

---

## See Also

- [Screen-Space-Effects](Screen-Space-Effects). SSAO, SSGI and SSR in depth, and every knob the presets set.
- [Bloom](Bloom). The bloom pipeline and its settings.
- [Render Pipeline](Render-Pipeline). The stages where these passes run; presets only change settings, the pipeline does the driving.
