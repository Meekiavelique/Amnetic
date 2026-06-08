# Particles

Particles are world effects like smoke, sparks, dust or magic. You write the fragment shader that decides how they look, and you add affectors that decide how they move. Amnetic does the rest: it simulates every particle on the CPU and draws each material in one instanced draw call.

---

## How Particles Work

A particle effect is three things:

1. **A Particle Material**. One look: its fragment shader, texture, blend mode, default envelope (life, size, color, alpha) and the affectors that move it. You build it once and keep the handle.

2. **Spawns**. `spawn` makes one particle, `burst` makes a shaped puff. Each spawn takes a position and a velocity; the material fills in the rest unless you override it.

3. **The Simulation**. Runs every frame: moves each particle, ages it, drops the dead ones, and draws the rest as camera-facing quads.

Key facts:

- It starts on its own when you build your first material. There is no init call.

- It is time-based. Velocities are blocks per second and lifetimes are seconds. A lag spike will not jump particles far because the frame time is capped at 0.1 seconds.

- `liveCap` is per material. Spawns past the cap are dropped, so check `liveCount()` if you emit a lot.

- `spawn` and `burst` are safe to call from any thread.

---

## Example 1. Smoke

Build the material once. You provide the fragment shader; Amnetic pairs it with its own billboard vertex shader.

```java
ParticleMaterial smoke = Particles.material()
    .shader(Identifier.of("mymod", "particle/smoke"))                 // assets/mymod/shaders/particle/smoke.fsh
    .texture(Identifier.of("mymod", "textures/particle/smoke.png"))
    .blend(Blend.ALPHA).sorted(true).softDepth(true).lightmap(true)
    .life(3f).size(0.5f, 1.5f).alpha(0.8f, 0f).easing(Easing.EASE_OUT)
    .affector(Affectors.drag(0.5f))
    .affector(Affectors.gravity(-0.4f))                              // negative rises
    .register();
```

Spawn into it. The last three numbers are the velocity in blocks per second:

```java
Particles.spawn(smoke, x, y, z, 0.0, 0.6, 0.0);
```

The callback overrides the material defaults for that one particle:

```java
Particles.spawn(smoke, x, y, z, 0.0, 0.6, 0.0,
    o -> o.life(5f).size(0.3f, 2.2f).color(0.6f, 0.6f, 0.65f));
```

The fragment shader decides the look:

```glsl
#version 330 core
uniform sampler2D TextureSampler;
in vec2 quadUV;
in vec4 vColor;
in float vAge;
out vec4 fragColor;

void main() {
    vec4 tex = texture(TextureSampler, quadUV);
    fragColor = vec4(tex.rgb * vColor.rgb, tex.a * vColor.a);
    if (fragColor.a < 0.01) discard;
}
```

---

## Example 2. Burst

`burst` emits `count` particles from a point. The shape gives each one a spawn offset and a travel direction; the speed is random between `speedMin` and `speedMax`. The callback runs per particle with the overrides object, the RNG and the index.

```java
Particles.burst(smoke, x, y, z,
    30,                          // count
    Shapes.sphereSurface(),      // outward in every direction
    0.3f, 3.0f,                  // speed range, blocks per second
    (o, rng, i) -> {
        float s = 1.5f + rng.nextFloat() * 0.8f;
        o.size(s * 0.6f, s).life(10f + rng.nextFloat() * 5f)
         .rotation(rng.nextFloat() * 6.28f)
         .color(0.85f, 0.95f, 0.7f);
    });
```

---

## The Fragment Shader Contract

You write only the fragment shader. Amnetic's billboard vertex shader gives it these inputs:

- `quadUV` (vec2). Quad coordinate, 0 to 1.
- `vColor` (vec4). The particle color in rgb; the alpha is already enveloped over the life from `alpha(start, end)`.
- `seed` (vec2). Per-particle random value, 0 to 4. Use it to decorrelate identical particles.
- `vAge` (float). Normalized age, 0 at spawn to 1 at death. Use it for extra age-driven shaping.
- `TextureSampler`. The material texture, unit 0.
- `DepthSampler`. Scene depth, unit 1. Only bound when `softDepth(true)`.
- `ProjectionMatrix`, `ViewMatrix`. For depth math.
- `Time` (float). Minecraft GameTime, a 0 to 1 fraction of the 24000-tick day.

Output one `out vec4 fragColor`. The billboard is translucent and does not write depth.

---

## Soft Particles, Depth and Water

With `softDepth(true)` Amnetic binds the scene depth as `DepthSampler` so you can fade the edge where a sprite meets a surface. Compare the fragment depth to the sampled scene depth and fade by the difference (the math is on the Vanilla Depth and Fog page).

That depth is captured before water is drawn, so it is opaque-only. The effect: particles are hidden by solid blocks but draw over water instead of being clipped behind it. You get this for free on any `softDepth` material.

---

## Reference

### `Particles`

```java
ParticleMaterial.Builder material();                                  // begin a material
void spawn(ParticleMaterial m, double x,y,z, double vx,vy,vz);        // velocities in blocks/second
void spawn(..., Consumer<ParticleOverrides> overrides);              // override the envelope per spawn
void burst(ParticleMaterial m, double x,y,z,
           int count, SpawnShape shape, float speedMin, float speedMax);
void burst(..., BurstCustomizer customizer);                         // customize(o, rng, index)
int  liveCount();                                                    // live particles, all materials
```

### `ParticleMaterial.Builder`

`shader(...)` and `register()` are required; everything else has a default.

- `shader(Identifier)`. The fragment shader, `assets/<ns>/shaders/<path>.fsh`.
- `texture(Identifier)`. Material texture, bound to `TextureSampler`.
- `blend(Blend)`. `ALPHA` or `ADDITIVE`.
- `sorted(boolean)`. Back-to-front sort each frame. Use with `ALPHA`.
- `softDepth(boolean)`. Bind scene depth as `DepthSampler`; occlude by solids, not water.
- `billboard(BillboardMode)`. `SPHERICAL` (default) or `VELOCITY_STRETCHED` for sparks.
- `lightmap(boolean)`, `lightMin(float)`, `lightRefreshSteps(int)`. Tint by world light, with an ambient floor and a re-sample interval.
- `liveCap(int)`. Max live particles for this material.
- `life(float seconds)`.
- `size(float)` or `size(float start, float end)`. Billboard half-extent, eased over life.
- `color(r,g,b)` or `color(sr,sg,sb, er,eg,eb)`. 0 to 1, start to end.
- `alpha(float start, float end)`. Becomes `vColor.a`.
- `gravity(float)`. Acceleration; negative rises.
- `drag(float)`. Velocity fraction kept per second (not a damping amount).
- `easing(Easing)`. The size curve.
- `affector(Affector)`. Add a force; call repeatedly.
- `register()`. Finalize and start drawing. Returns the `ParticleMaterial`.

### `ParticleOverrides` (in a spawn or burst callback)

Same fields as the material defaults: `life`, `size`, `color`, `alpha`, `gravity`, `drag`, `easing`, plus `rotation(radians)` and `spin(radiansPerSecond)`.

### `Affectors`

- `gravity()` / `gravity(accel)`. Downward (or upward if negative) acceleration.
- `drag(retention)` / `dragPerParticle()`. Shared retention fraction, or each particle's own.
- `wind(ax, ay, az)` / `wind(Supplier<Vector3fc>)`. Constant or dynamic acceleration.
- `turbulence(scale, strength)`. Curl-style noise.
- `attractor(px, py, pz, strength)`. Pull toward a point.
- `force(VectorField)`. Custom per-particle force.

### `Shapes`

- `point()`. Origin, random direction.
- `sphereSurface()`. Origin, outward in every direction.
- `sphereVolume(radius)`. Random point in a sphere, outward.
- `cone(axis, halfAngleDeg)`. Within a cone around the axis.
- `box(hx, hy, hz)`. Inside a box, travelling up.
- `disc(axis, radius)`. On a disc, along its axis.

### Enums

- `Blend`: `ALPHA`, `ADDITIVE`.
- `BillboardMode`: `SPHERICAL`, `VELOCITY_STRETCHED`.
- `Easing` (in `com.meekdev.amnetic.client.anim`): `LINEAR`, `EASE_OUT`, `EASE_IN`, `SMOOTH`, and the rest of the family. See [Tweens](Tweens).

---

## Status

The simulation ticks and draws from the `WORLD_LAST` phase, after terrain, entities and translucent water. It uses the same pipeline as instanced rendering. Soft-depth materials snapshot the scene depth before translucent terrain; if particles show no soft fade or are wrongly hidden by water, that capture is the first place to check.

---

## See Also

- [Instanced Rendering](Instanced-Rendering). The pipeline particles are built on.
- [Camera](Camera). Pair bursts with shake or impulse for impacts.
- [Vanilla Depth and Fog](Vanilla-Depth-and-Fog). The depth math for soft particles.
- [Writing Shaders](Writing-Shaders). GLSL conventions and moj_import.
