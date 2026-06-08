# Camera

The camera system is made up of three parts.

- **`AmneticCamera`** is like a camera that you can ask questions to. You can ask where the camera is and what it is looking at.

- **`CameraEffects`** is like a special effects box. You can add things like shake or other movements to the camera.

- **`CameraDirector`** is like a movie director. It can take control of the camera. Move it around to make a scene look really cool.

Some things to keep in mind when working with the camera. Positions are in world space, rotations are in degrees, and screen coordinates are in pixels.

---

## `AmneticCamera`. Query and math

This camera is like a helper. You can ask it questions about the frame.

```java
import com.meekdev.amnetic.client.camera.AmneticCamera;

// put a label above something on the screen
Vector2f s = AmneticCamera.worldToScreen(entity.position());
if (s != null && AmneticCamera.isVisible(entity.getBoundingBox())) {
    drawLabel(s.x, s.y, name);
}

// pick something on the screen
HitResult hit = AmneticCamera.pickFromScreen(mouseX, mouseY, 64);

// make the sound quieter when it is far away
float gain = (float) (1.0 - Math.min(1.0, AmneticCamera.distanceTo(source) / 32.0));

// do something when the player looks at something
if (AmneticCamera.isLookingAt(shrine, 8f)) { ... }
```

### Reference

```java
boolean isReady();

// get the camera position and direction
Vec3 position(); Vec3 forward(); Vec3 up(); Vec3 right();
float yaw(); float pitch();
float fov(); float near(); float far();

// get the camera matrices
Matrix4f projection(); Matrix4f view(); Matrix4f viewProjection(); Matrix4f inverseViewProjection();

// convert between world and screen
Vector2f worldToScreen(Vec3 world);
Ray screenToRay(double sx, double sy);
Vector3f worldToNdc(Vec3 world);
Vec3 ndcToWorld(float nx, float ny, float nz);

// check if something is visible
boolean isVisible(Vec3 point);
boolean isVisible(AABB box);
boolean isVisible(Vec3 center, double radius);

// get the distance or direction to something
double distanceTo(Vec3 p); double distanceSquaredTo(Vec3 p);
Vec3 directionTo(Vec3 p);
float angleTo(Vec3 p);
boolean isLookingAt(Vec3 p, float toleranceDegrees);

// pick something in the world
HitResult pick(double maxDistance);
HitResult pickFromScreen(double sx, double sy, double maxDistance);
```

---

## `CameraEffects`. Additive effects

These effects are like special things you can add to the camera. They can make the camera shake or move around. You just call them and they fade away on their own.

```java
import com.meekdev.amnetic.client.camera.CameraEffects;
import com.meekdev.amnetic.client.anim.Easing;

CameraEffects.shake(0.6f);
CameraEffects.kick(3f, 0f, 0f, 0.25f);
CameraEffects.fovPunch(8f, 0.3f);
CameraEffects.springTo(new Vec3(0, -0.2, 0), 120f, 18f);
```

### Reference

```java
// add an offset to the camera for this frame
void addPositionOffset(Vec3 offset);
void addPositionOffset(double x, double y, double z);
void addRotationOffset(float pitch, float yaw, float roll);
void addFovOffset(float degrees);

// add a special effect to the camera
void impulse(Vec3 positionDelta, float pitch, float yaw, float roll, float fov, float durationS, Easing easing);
void kick(float pitch, float yaw, float roll, float durationS);
void fovPunch(float fovDegrees, float durationS);
void shake(float trauma);
void shake(float trauma, float positionScale, float rotationScale);
void shakeDecay(float perSecond);
void springTo(Vec3 targetOffset, float stiffness, float damping);
void clear();

// add a custom modifier to the camera
void addModifier(CameraModifier modifier);
void removeModifier(CameraModifier modifier);
void clearModifiers();
```

---

## Building your own effect. `CameraModifier`

You can make your own special effects by creating a `CameraModifier`. Amnetic calls it once every frame and gives you a `CameraFrame`. You add your own offsets inside it.

```java
import com.meekdev.amnetic.client.camera.*;

class Turbulence implements CameraModifier {
    private float amp;
    private volatile float target;

    void setTarget(float t) { target = t; }

    @Override
    public void modify(CameraFrame f) {
        float step = 2.2f * f.dt();
        amp += Math.max(-step, Math.min(step, target - amp));
        if (amp < 1e-3f) return;

        float t = f.time();
        f.addRotation(noise(t, 17) * 4.5f * amp, noise(t, 41) * 4.5f * amp, noise(t, 73) * 9f * amp);
        f.addPositionLocal(noise(t, 91) * 0.15f * amp, noise(t, 113) * 0.15f * amp, 0);
    }

    // ... your own noise(t, seed) ...
}

Turbulence turb = new Turbulence();
CameraEffects.addModifier(turb);
```

`CameraFrame` API:

```java
float dt(); float time();
Vec3 cameraPosition(); float yaw(); float pitch(); float fov();
void addPosition(Vec3 offset);
void addPosition(double x, double y, double z);
void addPositionLocal(double right, double up, double forward);
void addRotation(float pitch, float yaw, float roll);
void addFov(float degrees);
```

---

## `CameraDirector`. Cinematics

The `CameraDirector` is like a movie director. It can take control of the camera. Move it around to make a scene look really cool. Call `release` when you are done so control goes back to the player.

```java
import com.meekdev.amnetic.client.camera.CameraDirector;
import com.meekdev.amnetic.client.anim.Easing;

CameraDirector.moveTo(new Vec3(x, y + 30, z - 40), new Vec3(x, y, z), 70f, 3f, Easing.SMOOTH);
CameraDirector.orbit(new Vec3(x, y, z), 25.0, 12f);
CameraDirector.release(1.0f);
```

### Reference

```java
boolean isActive();
void moveTo(Vec3 target, Vec3 lookAt, Float fov, float durationS, Easing easing);
void orbit(Vec3 center, double radius, float speedDegPerSecond);
void followPath(List<Keyframe> keyframes, Easing easing);
void lockTo(Vec3 target);
void lockTo(Supplier<Vec3> target);
void lockTo(Entity target);
void release(float durationS);
void releaseNow();

record Keyframe(Vec3 position, Vec3 lookAt, float time);
```

---

## Notes and Status

- The read side does not need a mixin. The `AmneticCamera` reads the `Camera` plus the projection and view matrices that Amnetic already captures each frame.

- The write side is a `Camera` mixin called `CameraMixin`. It applies the composed offsets, or a director override, at the end of `Camera.update`. It also folds the FOV contribution into `Camera.getFov`. The roll is applied by rotating the camera quaternion about its forward axis, because the vanilla `setRotation` only does yaw and pitch. If a Minecraft update breaks the write side, this mixin is the place to look.

- Threading is important here. The `modify` function and the offset application run on the render thread. Effect triggers, like `shake` or arm style flags or modifier targets, are commonly called from tick or network threads. Pass scalars through `volatile` fields like the examples do.

- `Easing` lives in `com.meekdev.amnetic.client.anim` and is shared with the particle and animation systems. The impulse and director blends run on the [tween engine](Tweens) internally, so the curve set is the full family (`LINEAR`, `EASE_IN`, `EASE_OUT`, `SMOOTH`, and the rest), plus any custom `t -> ...` curve.

---

## See Also

- [Particles](Particles). Pair bursts with shake or impulse for impacts.

- [Post-Processing](Post-Processing). Fullscreen effects driven by camera or game state.

- [Vanilla Rendering Internals](Vanilla-Rendering-Index). How the camera and projection are built each frame.
