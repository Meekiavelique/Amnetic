# Particles

Particles handle world effects like smoke, sparks, and magic. You write the fragment shader to define the look. You add affectors to control the movement. Amnetic handles everything else. It simulates every particle on the CPU and draws each material in a single instanced draw call.

If you prefer visual tools, use the Particle Editor. It builds materials through an in-game UI. You get live previews and can save directly to JSON.

### Trails

Calling `trail(points, width, fade, sampleInterval)` on the builder gives each particle a motion trail. The system records a ring buffer of recent positions and emits fading billboard ghosts along that path. This reuses the standard billboard pipeline so it costs no extra draw calls. The width and fade arguments taper the ghosts toward the tail. The sample interval dictates the simulation steps between recorded points. Higher intervals create longer but coarser trails. Use this for sparks or magic projectiles.

## How Particles Work

A particle effect relies on three components.

The material defines the visuals. It holds the fragment shader, texture, blend mode, default lifecycle envelope, and affectors. You build this once and save the handle.

The spawns create the actual particles. Calling `spawn` makes one particle. Calling `burst` creates a shaped puff. You give them a position and velocity. The material fills in the remaining properties unless you explicitly override them.

The simulation runs every frame. It moves particles, ages them, kills the dead ones, and draws the survivors as camera-facing quads.

Simulation starts automatically when you build your first material. There is no initialization method to call.

The entire system is time-based. Velocities are measured in blocks per second and lifetimes in seconds. A lag spike will not teleport particles across the map because the maximum simulation step caps at 0.1 seconds.

The `liveCap` limit applies per material. Spawns exceeding the cap are dropped silently. Always check `liveCount()` if you emit massive volumes. Both `spawn` and `burst` are thread-safe.

## Example 1: Smoke

Build the material once. You provide the fragment shader and Amnetic pairs it with a built-in billboard vertex shader.

```java
ParticleMaterial smoke = Particles.material()
    .shader(Identifier.fromNamespaceAndPath("mymod", "particle/smoke"))
    .texture(Identifier.fromNamespaceAndPath("mymod", "textures/particle/smoke.png"))
    .blend(Blend.ALPHA).sorted(true).softDepth(true).lightmap(true)
    .life(3f).size(0.5f, 1.5f).alpha(0.8f, 0f).easing(Easing.EASE_OUT)
    .affector(Affectors.drag(0.5f))
    .affector(Affectors.gravity(-0.4f))
    .register();
```

Spawn into it. The last three numbers represent the velocity in blocks per second.

```java
Particles.spawn(smoke, x, y, z, 0.0, 0.6, 0.0);
```

You can use the callback to override the material defaults for a specific particle.

```java
Particles.spawn(smoke, x, y, z, 0.0, 0.6, 0.0,
    o -> o.life(5f).size(0.3f, 2.2f).color(0.6f, 0.6f, 0.65f));
```

The fragment shader decides the final look.

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

## Example 2: Burst

The `burst` method emits multiple particles from a single point. The shape argument gives each particle a spawn offset and a travel direction. Speed is randomized between your minimum and maximum values. The callback runs once per particle and provides the overrides object, a random number generator, and the particle index.

```java
Particles.burst(smoke, x, y, z,
    30,
    Shapes.sphereSurface(),
    0.3f, 3.0f,
    (o, rng, i) -> {
        float s = 1.5f + rng.nextFloat() * 0.8f;
        o.size(s * 0.6f, s).life(10f + rng.nextFloat() * 5f)
         .rotation(rng.nextFloat() * 6.28f)
         .color(0.85f, 0.95f, 0.7f);
    });
```

## Curves and Gradients

Size, color, and alpha default to a linear interpolation from start to end. If you need something more complex, use a track. Tracks allow sparks to pop and shrink or smoke to change color as it cools. A track simply evaluates a value at a specific lifetime fraction.

You can create a constant track for a single value. You can use a random track to pick a value once per particle and keep it stable. You can use a curve track to follow keyframes. You can also use a random blend between two different curves.

A `Curve` defines keyframes on a 0.0 to 1.0 axis. Each keyframe can specify an easing function into the next segment.

```java
Curve pop = Curve.builder()
    .key(0.0f, 0.0f)
    .key(0.2f, 1.0f, Easing.BACK_OUT)
    .key(1.0f, 0.0f)
    .build();
```

A `Gradient` does the exact same thing for multi-stop RGBA colors over a particle's life.

```java
Gradient fire = Gradient.builder()
    .stop(0.0f, 1.0f, 0.9f, 0.4f, 1.0f)
    .stop(0.6f, 1.0f, 0.2f, 0.0f, 1.0f)
    .stop(1.0f, 0.1f, 0.1f, 0.1f, 0.0f)
    .build();
```

Pass these into the material builder. A track or gradient completely overrides the default size, color, or alpha settings.

```java
ParticleMaterial ember = Particles.material()
    .shader(Identifier.fromNamespaceAndPath("mymod", "particle/ember"))
    .blend(Blend.ADDITIVE)
    .life(2f)
    .size(pop)
    .color(fire)
    .register();
```

A track evaluates based on normalized particle age. It sits at 0 on spawn and hits 1 at death. The fragment shader sees this exact same value as `vAge`.

Random tracks evaluate per particle rather than per frame. They seed from the particle itself so the value stays constant without flickering.

Gradients drive alpha alongside color, and a gradient replaces the `alpha(start, end)` lerp entirely. If you want a soft fade in and out without baking transparency into every single gradient stop, use `alphaInOut(fadeInFrac, fadeOutFrac)`. That is a separate smoothstep envelope over the first and last fractions of the lifetime and it multiplies on top of whatever the gradient or alpha track produced.

Tracks exist at the material level. If you only need a one-off tweak for a specific spawn, use the overrides callback instead.

## Flipbooks

If your texture is a grid of frames, you can play it as a flipbook. Amnetic walks the grid automatically and feeds the current frame's UV coordinates to the shader. The `quadUV` variable will already point to the correct cell.

```java
.flipbook(4, 4, 30f)
.flipbookOverLife(4, 4)
```

Frames read left to right and top to bottom. Cell (0,0) is the top-left corner of the texture.

Calling `flipbook` loops the animation on a clock based on the FPS you provide. Calling `flipbookOverLife` ties the animation frame directly to the particle age so it plays exactly once over the entire lifetime.

Your shader code does not need to change. Just sample your texture at `quadUV` and the engine handles the sub-rect mapping.

## The Fragment Shader Contract

You only need to write the fragment shader. The built-in billboard vertex shader provides several inputs automatically.

* `quadUV` is a vec2 representing the quad coordinate from 0 to 1.
* `vColor` is a vec4 containing the particle color. The alpha channel already includes the lifecycle envelope.
* `seed` is a vec2 holding a per-particle random value between 0 and 4. Use this to break up uniformity across identical particles.
* `vAge` is a float representing normalized age from 0 to 1.
* `TextureSampler` binds the material texture to unit 0.
* `DepthSampler` binds scene depth to unit 1. This is only available if you enable soft depth.
* `ProjectionMatrix` and `ViewMatrix` are provided for depth calculations.
* `Time` is a float representing the Minecraft GameTime as a fraction of the day.

Write your final pixel to `out vec4 fragColor`. Billboards are translucent and disable depth writes by default; `depthWrite(true)` on the builder turns them back on.

## Soft Particles, Depth and Water

Enabling `softDepth(true)` binds the scene depth buffer as `DepthSampler`. This lets you fade the harsh edges where a particle sprite intersects a solid surface. Compare your fragment depth to the sampled scene depth and fade the alpha based on the difference. The Vanilla Depth and Fog documentation covers the exact math for this.

The engine captures this depth buffer before drawing water. This means it only contains opaque geometry. Particles will correctly hide behind solid blocks but render smoothly over water instead of clipping through it.

## Colliders

Particles can collide with the world. Attach a collider to a material and every particle will react to surfaces. They can bounce, slide, stick, or die on contact. You also get an optional callback when they first hit something.

A collider consists of a target shape and a response. Target shapes include block worlds or flat horizontal planes. The response decomposes the velocity into normal and tangential vectors. Restitution controls the bounce intensity. Friction controls the sliding resistance.

```java
ParticleMaterial sparks = Particles.material()
    .shader(Identifier.fromNamespaceAndPath("mymod", "particle/spark"))
    .life(2f)
    .affector(Affectors.gravity(9.8f))
    .collider(Colliders.world(
        CollisionResponse.bounce(0.6f, 0.2f)
            .onHit((p, nx, ny, nz, ctx) -> { /* run hit logic */ })))
    .register();
```

Amnetic provides several ready-made responses. You can use bounce, slide, stop, or die. You can chain modifiers onto these responses like `withRestitution`, `withFriction`, `killOnContact`, and `onHit`.

```java
Colliders.plane(64.0, CollisionResponse.die());
Colliders.world(CollisionResponse.bounce(0.9f).killOnContact());
```

The `onHit` callback fires once per new contact. It passes you the surface normal. The contact flag resets when the particle leaves the surface, so a bouncing particle triggers the callback again on every landing. If you write custom affectors, you can check `p.colliding` to see if a particle hit geometry during the current frame.

## Reference

### Particles

`material()` starts the builder.
`spawn()` creates a single particle. Velocities are measured in blocks per second.
`burst()` spawns multiple particles using a shape and speed range.
`liveCount()` returns the total number of active particles across all materials.

### ParticleMaterial.Builder

The `shader` and `register` methods are strictly required. Everything else falls back to a default.

`displayName(String)` names the material in the editor and debug listings.
`shader(Identifier)` sets the fragment shader.
`texture(Identifier)` sets the material texture bound to TextureSampler.
`texture2(Identifier)` binds a second texture as Sampler1 on unit 2.
`blend(Blend)` accepts either ALPHA or ADDITIVE.
`sorted(boolean)` forces back-to-front sorting each frame.
`softDepth(boolean)` binds scene depth to allow occlusion by solids instead of water.
`depthWrite(boolean)` re-enables depth writes. Off by default.
`overlay(boolean)` draws the material in the OVERLAY phase, on top of the world.
`emissive()` or `emissive(float)` writes the bloom-feeding emissive target, default strength 1.0.
`billboard(BillboardMode)` accepts SPHERICAL or VELOCITY_STRETCHED.
`lightmap(boolean)` tints the particle using the world light.
`lightMin(float)` sets the darkest lightmap tint. Defaults to 0.15.
`lightRefreshSteps(int)` sets simulation steps between light samples. Defaults to 4.
`liveCap(int)` limits the maximum live particles for this specific material.
`life(float)` sets the base lifetime in seconds.
`size(float)` sets the full quad width in blocks. Corners sit at plus and minus half of it.
`color(r, g, b)` sets the base color. The six-argument form lerps from a start to an end color.
`alpha(float, float)` controls the start and end opacity.
`alpha(Track)` drives opacity from a track, replacing the start-end lerp.
`alphaInOut(fadeInFrac, fadeOutFrac)` is a smoothstep fade envelope that multiplies on top.
`gravity(float)` applies downward acceleration. Negative values rise.
`drag(float)` determines the velocity fraction kept per second.
`easing(Easing)` sets the curve for the size transition.
`flipbook(cols, rows, fps)` plays a sprite sheet at a fixed frame rate.
`flipbookOverLife(cols, rows)` maps the sprite sheet across the particle lifetime.
`affector(Affector)` applies a continuous force.
`collider(Collider)` attaches surface collision rules.
`register()` finalizes the builder and returns the usable material.

### ParticleOverrides

The per-spawn callback object. It seeds from the material defaults, so you only set what differs.

`life`, `size`, `color`, `gravity`, `drag`, and `easing` mirror the builder methods.
`alpha(constant)` or `alpha(start, end)` sets the opacity.
`alphaInOut(fadeInFrac, fadeOutFrac)` sets the fade envelope.
`rotation(radians)` sets the initial billboard rotation.
`spin(radiansPerSecond)` sets the rotation speed.

### Track, Curve, and Gradient

`Track.constant(v)` returns a static value.
`Track.lerp(start, end)` interpolates straight from start to end over the lifetime.
`Track.random(min, max)` returns a stable random value per particle.
`Track.curve(Curve)` follows a keyframed path.
`Track.randomBetween(Curve, Curve)` blends randomly between two separate curves.
`Curve.linear(a, b)` interpolates straight from start to end.
`Gradient.builder()` lets you add color stops with RGBA values across a 0 to 1 axis.

### Shapes

Spawn shapes for `burst`. Each one gives a particle its offset from the burst center and its travel direction.

`point()` spawns at the center with a uniformly random direction.
`sphereSurface()` is the same as point. Every particle flies outward on a random unit direction.
`sphereVolume(radius)` offsets particles uniformly through a solid sphere.
`cone(axis, halfAngleDeg)` sends particles out within a cone around the axis.
`box(halfX, halfY, halfZ)` offsets particles through a box and sends them straight up.
`disc(axis, radius)` offsets particles across a flat disc and sends them along the axis.

### Affectors

`gravity(accel)` applies vertical acceleration. Positive falls, negative rises.
`gravity()` with no arguments reads each particle's own gravity value, set by the builder or overrides.
`drag(retention)` applies velocity damping.
`dragPerParticle()` reads each particle's own drag value instead of a shared constant.
`wind(ax, ay, az)` applies directional acceleration.
`wind(Supplier<Vector3fc>)` samples the acceleration each step, for wind that changes over time.
`turbulence(scale, strength)` applies curl noise to the velocity.
`attractor(px, py, pz, strength)` pulls particles toward a specific world point.
`vortex(px, py, pz, strength)` swirls particles around a vertical axis through that point.
`vortex(px, py, pz, axisX, axisY, axisZ, strength, inward)` is the full form with a custom axis and an inward pull.
`force(VectorField)` builds an affector from any custom per-particle acceleration field.

### Colliders and Responses

`Colliders.world(response)` checks collisions against actual world blocks.
`Colliders.plane(y, response)` checks collisions against an infinite flat plane. This is mathematically cheaper.
`CollisionResponse.bounce(restitution)` and `bounce(restitution, friction)` reflect the particle.
`CollisionResponse.slide(friction)` zeroes the velocity into the surface and applies friction along it.
`CollisionResponse.stop()` freezes the particle in place.
`CollisionResponse.die()` kills the particle immediately upon contact.

## Status

The simulation ticks and draws in the `WORLD_TRANSLUCENT` instancing phase, driven by the AFTER_WATER pipeline stage, so particles sort in front of water and glass. Materials built with `overlay(true)` draw in the `OVERLAY` phase instead, on top of the whole frame. It runs on the exact same pipeline as instanced rendering. Soft depth materials grab the scene depth before translucent terrain draws. If your particles lack a soft fade or get hidden by water incorrectly, check that depth capture first.

The Particle Editor saves each effect as a Gson `EffectDraft` JSON file at `<gameDir>/amnetic/particles/<name>.json`, and loads them back from the same folder.

## See Also

Instanced Rendering covers the pipeline particles run on.
Camera covers screen shake and impulses for impact effects.
Vanilla Depth and Fog explains the depth math needed for soft particles.
Writing Shaders covers standard GLSL conventions.