# Tweens

The animation system is made up of a few small parts that fit together.

- **`Animations`** is the front desk. You ask it for a tween or a timeline, and it quietly drives everything you start, once per frame.

- **`Tween`** is a single value going from one place to another over time. A number, a position, a color, an angle. It carries its own easing, delay, and repeat settings.

- **`Timeline`** is like a little conductor. It plays tweens in order, side by side, or staggered, and can fire callbacks along the way.

- **`Easing`** is the shape of the motion. Whether it starts slow, ends slow, bounces, or just moves in a straight line.

- **`Interpolators`** are how two values get blended. There are built-ins for the common types, and you can teach it your own.

A few things to keep in mind. Time is in real seconds, not ticks. Started tweens advance while the world is rendering. And the value type is whatever you want, as long as there is an interpolator for it.

---

## `Animations`. The front desk

This is where you make things. Build a tween, point its `onUpdate` at whatever you want to move, and `start()` it. From there the engine takes care of it and drops it when it is done.

```java
import com.meekdev.amnetic.client.anim.Animations;
import com.meekdev.amnetic.client.anim.Easing;

// fade a value from 0 to 1 over half a second
Animations.tween(0f, 1f, 0.5f)
    .ease(Easing.SMOOTH)
    .onUpdate(v -> material.setOpacity(v))
    .start();

// pulse forever, back and forth
Animations.tween(0.8f, 1.2f, 0.6f)
    .ease(Easing.SINE_IN_OUT)
    .repeatForever().yoyo(true)
    .onUpdate(s -> glow.setScale(s))
    .start();
```

### Reference

```java
// make a tween (start, end, duration in seconds)
Tween<Float> tween(float start, float end, float duration);
Tween<Float> tweenAngle(float startDeg, float endDeg, float duration); // shortest way around
Tween<Vector3fc> tween(Vector3fc start, Vector3fc end, float duration);
Tween<Vec3> tween(Vec3 start, Vec3 end, float duration);
Tween<Vector4fc> tweenColor(Vector4fc start, Vector4fc end, float duration);
Tween<Quaternionfc> tween(Quaternionfc start, Quaternionfc end, float duration);
<T> Tween<T> tween(T start, T end, float duration, Interpolator<T> interpolator); // anything else

// make a timeline
Timeline timeline();

// the engine
void update(); // pumped once per frame by Amnetic
int activeCount(); // how many tweens and timelines are running
void clear(); // stop and drop everything
```

---

## `Tween`. One value over time

A tween is full of small switches you can flip before you start it. Set the easing, give it a delay, make it repeat, ping-pong with yoyo, or hang callbacks off the start, every frame, and the end. Once it is running you can still pause it, cancel it, or jump it to a different time.

```java
import com.meekdev.amnetic.client.anim.Animations;
import com.meekdev.amnetic.client.anim.Easing;

Tween<Float> t = Animations.tween(0f, 100f, 1.5f)
    .ease(Easing.CUBIC_IN_OUT)
    .delay(0.25f)
    .repeat(3).yoyo(true)
    .onStart(() -> sound.play())
    .onUpdate(v -> light.setIntensity(v))
    .onComplete(() -> sound.stop())
    .start();

// later, from your own logic
t.pause();
t.speed(2f);
t.seek(0.5f);
t.cancel();
```

You do not have to hand a tween to the engine. If you own your own clock, build one and step it yourself with `update(dt)`. That is exactly how the camera drives its impulses.

```java
Tween<Float> clock = new Tween<>(0f, 1f, duration, Interpolators.FLOAT);
// every frame, with your own delta:
clock.update(dt);
float v = clock.value();
boolean finished = clock.isDone();
```

### Reference

```java
// configure (before start)
Tween<T> ease(EasingFunction easing);
Tween<T> delay(float seconds);
Tween<T> repeat(int count); // extra runs after the first
Tween<T> repeatForever();
Tween<T> yoyo(boolean yoyo); // alternate direction each run
Tween<T> speed(float multiplier);
Tween<T> onStart(Runnable cb);
Tween<T> onUpdate(Consumer<T> cb);
Tween<T> onComplete(Runnable cb);

// control
Tween<T> start(); // hand it to the engine
void pause(); void resume(); void cancel();
void seek(float seconds);
boolean update(float dt); // drive it yourself; returns true when done

// read
T value();
float progress(); // 0..1, before easing
boolean isDone();
```

---

## `Timeline`. Sequencing

A timeline lines tweens up on a shared clock. `append` puts something after everything so far. `with` runs it next to the last thing you appended. `stagger` spreads a list out by a fixed step. `call` drops a callback at the current spot. Timelines are themselves drivable, so a timeline can hold another timeline.

```java
import com.meekdev.amnetic.client.anim.Animations;

Animations.timeline()
    .append(fadeIn) // first
    .append(0.2f, slideUp) // a beat later
    .with(brighten) // alongside slideUp
    .stagger(0.05f, letterPops) // each one a little after the last
    .call(() -> spawnBurst()) // fire a callback here
    .start();
```

### Reference

```java
// build
Timeline append(Updatable item);
Timeline append(float gap, Updatable item);
Timeline with(Updatable item); // parallel to the last append
Timeline stagger(float step, Iterable<? extends Updatable> items);
Timeline call(Runnable callback);
Timeline add(float offset, Updatable item); // place at an explicit time
Timeline loop(boolean loop);
Timeline speed(float multiplier);
Timeline onComplete(Runnable cb);

// control
Timeline start();
void pause(); void resume(); void cancel();
void seek(float seconds);
boolean isDone();
```

(`Tween` and `Timeline` are both `Updatable`, so you pass either one to a timeline.)

---

## `Easing` and `Interpolators`

`Easing` is the curve. The four originals are still here, plus the usual family. Anything that takes an easing also takes a plain `t -> ...` lambda, so a custom curve is one line.

```java
import com.meekdev.amnetic.client.anim.Easing;

Animations.tween(0f, 1f, 1f).ease(Easing.BACK_OUT); // a little overshoot
Animations.tween(0f, 1f, 1f).ease(t -> t * t * t); // your own curve
```

```java
// built-in curves
LINEAR, EASE_IN, EASE_OUT, SMOOTH,
QUAD_IN_OUT, CUBIC_IN, CUBIC_OUT, CUBIC_IN_OUT,
SINE_IN, SINE_OUT, SINE_IN_OUT, EXPO_OUT,
BACK_OUT, ELASTIC_OUT, BOUNCE_OUT
```

`Interpolators` decides how two values blend. Use a built-in, or write an `Interpolator<T>` for your own type and pass it to `Animations.tween(start, end, duration, interpolator)`.

```java
// built-in interpolators
Interpolator<Float>        FLOAT;
Interpolator<Float>        ANGLE; // shortest path in degrees
Interpolator<Vector3fc>    VEC3;
Interpolator<Vec3>         VEC3D; // Minecraft world coordinates
Interpolator<Vector4fc>    COLOR; // RGBA
Interpolator<Quaternionfc> QUATERNION; // slerp

// and the shared math helpers
float lerp(float a, float b, float t);
float lerpAngle(float a, float b, float t);
Vec3  lerp(Vec3 a, Vec3 b, float t);
float clamp01(float v);
```

---

## Notes and Status

- Anything you `start()` is driven by the engine once per frame, pumped from the end of world rendering. So started tweens advance while you are in a world, not on the title screen. If you need an animation off in a menu, drive a detached tween yourself with `update(dt)`.

- The frame delta is measured for you and clamped, so a lag spike will not teleport an animation across its whole range in a single jump.

- Callbacks (`onUpdate`, `onComplete`, and the timeline's `call`) run on the render thread, same as the tween itself. If you trigger a `start()` from a tick or network thread, make sure the state it reads is safe to touch.

- `Easing` used to live with the particles. It now lives in `com.meekdev.amnetic.client.anim`, and both the particle and camera systems share it. The camera's impulses are built on a self-driven `Tween` internally; the `CameraDirector` blends keep their own clock and use `Easing` and `Interpolators` directly rather than this engine.

- The value type is open. If there is an `Interpolator<T>` for it, you can tween it.

---

## See Also

- [Camera](Camera). The impulse and kick effects are built on top of this engine.

- [Particles](Particles). Particle size curves use the same `Easing`.

- [Post-Processing](Post-Processing). Drive a uniform with a tween for a fade or a pulse.
