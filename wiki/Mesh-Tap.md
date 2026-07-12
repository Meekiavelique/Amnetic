# Mesh Tap

Tap an entity and you get its posed triangles back every frame it renders. You read the position, UV, and normal of every vertex of the model, already deformed into the current animation pose  - the same geometry the renderer submits for drawing.

You can use this for a lot of things. Spray particles off a surface, draw custom outlines, dissolve a body vertex by vertex, or scatter a model into pieces.

Two classes handle everything. `EntityMeshTap` registers the tap and returns a `PosedMesh` handle. The `PosedMesh` holds the captured vertex arrays and the lifecycle controls.

## The pipeline

When the game submits an entity model for rendering, a mesh tap runs a second pass over it: after the animation pose is set up, the model is rendered once more into Amnetic's capturing consumer, which dumps every vertex into three flat float arrays for you to read. It is a duplicate write of the same posed geometry, not an interception of the real draw. If a zombie swings its arms, your arrays reflect that exact swing.

The data is camera-relative. Positions are measured from the camera, not the world origin or the entity's feet. To get a world position, just add the camera position back. The renderer works this way natively so there is no extra math to undo.

You only get the base body mesh. Armor, elytra, and held items are separate render passes. They do not show up in the tap.

The vertex count stays at 0 until the entity actually renders a frame. If an entity is culled, off-screen, or in an unloaded chunk, the tap captures nothing that frame; after the first capture, the count and arrays simply keep the last rendered frame's data. Always check `if (vertexCount() > 0)` before doing anything with the arrays.

The arrays update every single frame. Read them immediately. Do not cache them expecting the pose to stay static.

## What the data looks like

You get three flat arrays indexed by the vertex number. The vertices arrive in draw order. This usually means quads coming through four vertices at a time.

| Accessor | Layout | Element `i` for vertex `v` |
|---|---|---|
| `positions()` | `x, y, z` per vertex | `i = v * 3` -> `pos[i], pos[i+1], pos[i+2]` |
| `uvs()` | `u, v` per vertex | `i = v * 2` -> `uv[i], uv[i+1]` |
| `normals()` | `nx, ny, nz` per vertex | `i = v * 3` -> `nrm[i], nrm[i+1], nrm[i+2]` |

Positions are camera-relative block coordinates. UVs are texture coordinates from 0 to 1. Normals are unit-length surface directions showing which way the face points.

The arrays size themselves to the largest mesh they have seen so far. This means the array length is almost always longer than the actual live data. Do not iterate over `positions().length`. Loop against `vertexCount()` instead.

### Stride versus decimation

To read every vertex, you step your loop by 1 and multiply by 3 for positions or 2 for UVs. That is the hardcoded array layout.

The particle demo below uses a constant called `VERTEX_STRIDE = 18`. That is totally different. That 18 is a decimation step. The loop skips vertices so it does not spawn a particle on every single polygon. It is just a knob to thin out the visual effect. It has nothing to do with memory layout. Inside the loop, you still index the arrays using `v * 3`.

## Tapping an entity

Calling `EntityMeshTap.tap` replaces any existing tap for that specific entity.

```java
import com.meekdev.amnetic.client.geometry.EntityMeshTap;
import com.meekdev.amnetic.client.geometry.PosedMesh;

PosedMesh mesh = EntityMeshTap.tap(player);

// Later, on the client tick:
int n = mesh.vertexCount();
if (n > 0) {
    float[] pos = mesh.positions();
    float[] nrm = mesh.normals();
    float[] uv  = mesh.uvs();
    
    for (int v = 0; v < n; v++) {
        int p = v * 3;
        // Read pos[p], pos[p+1], pos[p+2]
    }
}

mesh.remove();
```

## Example 1: Inspect the vertices

This is a debug tap on the local player. Hitting F8 toggles it. Every second it logs the vertex count and the first vertex position. You can watch the mesh populate once the player renders.

```java
private static PosedMesh tap;
private static boolean f8Down;
private static int ticks;

public static void init() {
    ClientTickEvents.END_CLIENT_TICK.register(mc -> {
        if (mc.player == null) {
            if (tap != null) { tap.remove(); tap = null; }
            return;
        }

        boolean down = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_F8);
        if (down && !f8Down) {
            if (tap == null) {
                tap = EntityMeshTap.tap(mc.player);
            } else {
                tap.remove();
                tap = null;
            }
        }
        f8Down = down;

        if (tap != null && ++ticks % 20 == 0) {
            int n = tap.vertexCount();
            if (n > 0) {
                float[] p = tap.positions();
                float[] uv = tap.uvs();
                LOG.info("[MeshTap] {} verts | v0 pos=({}, {}, {}) uv=({}, {})",
                        n, p[0], p[1], p[2], uv[0], uv[1]);
            } else {
                LOG.info("[MeshTap] 0 verts captured. Not rendered yet?");
            }
        }
    });
}
```

The important takeaways here are the F8 edge detection and the zero guard before touching the arrays. The zero branch handles the window between registering the tap and the entity's first rendered frame.

## Example 2: Particles off the surface

This is your starting point for dissolves and outlines. It spawns an END_ROD particle at every Nth vertex and pushes it outward along the normal. Hitting F9 toggles it.

```java
private static final int VERTEX_STRIDE = 18;

private static PosedMesh tap;
private static boolean f9Down;

public static void init() {
    ClientTickEvents.END_CLIENT_TICK.register(mc -> {
        if (mc.player == null) { stop(); return; }
        boolean down = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_F9);
        if (down && !f9Down) {
            if (isActive()) stop(); else start(mc.player);
        }
        f9Down = down;
        emit();
    });
}

private static void start(Entity entity) { tap = EntityMeshTap.tap(entity); }
private static void stop()               { if (tap != null) { tap.remove(); tap = null; } }
private static boolean isActive()        { return tap != null && !tap.isRemoved(); }

private static void emit() {
    if (!isActive()) return;
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null) return;

    int n = tap.vertexCount();
    if (n == 0) return;

    Camera cam = mc.gameRenderer.getMainCamera();
    Vec3 c = cam.position();
    float[] pos = tap.positions();
    float[] nrm = tap.normals();

    for (int v = 0; v < n; v += VERTEX_STRIDE) {
        int pi = v * 3;

        double wx = c.x + pos[pi];
        double wy = c.y + pos[pi + 1];
        double wz = c.z + pos[pi + 2];

        double speed = 0.02;
        mc.level.addParticle(ParticleTypes.END_ROD, wx, wy, wz,
                nrm[pi] * speed, nrm[pi + 1] * speed, nrm[pi + 2] * speed);
    }
}
```

`cam.position()` gets the world camera. Add that to the camera-relative tapped position to get your actual world coordinates.

`pi = v * 3` finds the array offset. Both position and normal use the same multiplier.

Multiplying the normal by `speed` pushes the particle away from the surface. Flip the sign if you want them to fly inward.

The `v += VERTEX_STRIDE` loop skips vertices to prevent particle spam. It does not change how you index the arrays inside the loop.

## Lifecycle

The `EntityMeshTap.tap` method creates the handle. The registry then starts capturing data on the next frame.

The registry ticks automatically. It cleans up any taps attached to removed entities. You do not strictly need to clean up dead entities, but calling `remove()` manually stops the capture overhead early.

Calling `mesh.remove()` unregisters the tap. You can call it multiple times without crashing.

Calling `EntityMeshTap.clear()` drops everything at once. The client stop event calls this automatically to prevent cross-world memory leaks.

If an entity is off-screen or culled, the capture skips that frame. Both the arrays and `vertexCount()` keep the previous rendered frame's values; the count only reads zero before the very first capture. Treat the data as "last time the entity rendered," not "this exact frame."

## Threading and timing

The capture runs on the render thread when the entity model is submitted for rendering, as an extra pass over the posed model.

You should read the data on the client tick after the frame finishes. This guarantees you see the most recent full capture.

Client ticks and rendering share the render thread, so a tick never observes a capture in progress  - that scheduling, not any locking, is what keeps you from reading a half-written frame. (A volatile flag additionally gates `vertexCount()` until the first capture completes.)

## Performance checklist

Copying vertex streams is per-frame work. Tapping one player is cheap. Tapping fifty zombies will tank your framerate. If the registry is empty, the overhead is zero.

Big models have thousands of vertices. Decimate your loops if you are doing heavy math or spawning particles.

The arrays grow in place and overwrite their contents every frame. Do not hold references to them expecting the data to stick around. Copy what you need.

Never loop over `array.length`. Always use `vertexCount()`.

A zero vertex count is normal. Expect it.

Armor and held items are missing. You only get the base body.

## Reference

```java
static PosedMesh tap(Entity entity);
static void clear();
```

```java
Entity entity();
int vertexCount();
float[] positions();
float[] uvs();
float[] normals();
boolean isRemoved();
void remove();
```

Positions are camera-relative. World position is `camera.position() + (x, y, z)`.