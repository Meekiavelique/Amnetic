# Editor

Amnetic ships an in-game ImGui overlay for tuning the renderer live: inspectors for lights, shadows, TAA, post FX and the rest, plus in-world gizmos for selecting and moving lights and decals with the mouse. It's a development tool, not a player-facing screen; everything it changes is the same runtime state your code changes through the public APIs.

Host mods can plug into it too. A custom panel is one small class and one call:

```java
import com.meekdev.amnetic.client.ui.AmneticEditor;
import com.meekdev.amnetic.client.ui.Inspector;
import imgui.ImGui;

AmneticEditor.register(new Inspector("MyMod", "Portals", false) {
    @Override
    public void render() {
        ImGui.text("active portals: " + PortalManager.count());
        // any imgui-java calls
    }
});
```

That adds a **MyMod** menu to the editor's menu bar with a **Portals** entry that opens your window.

---

## The ImGuiMC dependency

The editor is built on **ImGuiMC** (mod id `imguimc`), which provides the ImGui context and renders the overlay. Amnetic only compiles against it; it is **not** bundled and **not** required. At client init, `AmneticEditorBridge` checks `FabricLoader.isModLoaded("imguimc")`:

- If ImGuiMC is present, the editor initializes and hooks ImGuiMC's post-render event.
- If it's absent, nothing editor-related is created. The toggle keybind still exists, and the first press logs a one-time hint to install ImGuiMC. Everything else in Amnetic works normally.

So you can ship a mod that uses Amnetic without pulling ImGui onto your players, and drop ImGuiMC into your dev environment when you want the tooling (Amnetic's own dev setup does exactly this via `localRuntime`).

Note for host mods: because the classes in `com.meekdev.amnetic.client.ui` reference ImGui types directly, only touch `AmneticEditor.register(...)` and friends from code paths that run when `imguimc` is loaded, or guard the call with your own `isModLoaded` check.

---

## Toggling it

The editor binds a vanilla keybind, `key.amnetic.editor`, default **Right Shift** (rebindable in Options > Controls, Misc category). Pressing it flips the overlay on and off. In code, `AmneticEditor.toggle()` and `AmneticEditor.isEnabled()` do the same.

If an editor frame ever throws, the overlay logs to `Amnetic/Editor` and disables itself rather than breaking rendering; toggle it back on with the key. A single failing inspector window just closes itself instead of taking the whole overlay down.

---

## Menu and windows

When enabled, the editor draws a main menu bar across the top of the screen. Each `Inspector` declares a **group** (the menu name) and a **title** (the menu item and window title); inspectors with the same group share a menu, in registration order. Clicking a menu item toggles that inspector's window. Windows are normal ImGui windows: movable, resizable (default 440x520), closable with their own close button.

The built-in inspectors:

| Group    | Title           | Open by default | What it controls                                  |
|----------|-----------------|-----------------|---------------------------------------------------|
| Renderer | Lights          | yes             | Live [light](Deferred-Lights) list and properties |
| Renderer | Shadows         | no              | [Shadow](Shadows) settings                        |
| Renderer | SSAO            | no              | [SSAO](Screen-Space-Effects) settings             |
| Renderer | SSGI            | no              | [SSGI](Screen-Space-Effects) settings             |
| Renderer | TAA             | no              | [TAA](TAA) enable, feedback, sharpness            |
| Renderer | Decals          | no              | Live [decal](Decals) list and properties          |
| Renderer | Particles       | no              | [Particle](Particles) system settings             |
| Renderer | Particle Editor | yes             | Authoring tool for particle effects               |
| Renderer | Post FX         | no              | [Bloom](Bloom) and [color grade](Color-Grading)   |
| Renderer | GBuffer         | no              | [GBuffer](Shading-Models) debug views             |
| Info     | Profiler        | no              | Pass timings                                      |
| Info     | Stats           | no              | Frame statistics                                  |
| Info     | Device Info     | no              | GPU and capability info                           |
| Example  | ImGui Demo      | no              | The stock ImGui demo window                       |

A consistent dark theme (`EditorTheme`) is applied every frame, so registered inspectors inherit the same styling without doing anything.

---

## Custom inspectors

`Inspector` is a tiny abstract class:

```java
public abstract class Inspector {
    protected Inspector(String group, String title, boolean openByDefault) { ... }
    public abstract void render(); // imgui-java calls; already inside a begun window
}
```

`render()` is called each frame the window is open, between `ImGui.begin` and `ImGui.end`, with the editor font and theme active. Exceptions are caught per inspector: a throwing panel logs and closes itself without affecting others.

`AmneticEditor.register(inspector)` appends it to the list. Register after Amnetic's client init has run (the call is a no-op if the editor never initialized, i.e. ImGuiMC is missing, so it's always safe).

---

## Gizmos and selection

With the overlay open, lights and decals are drawn as clickable markers over the world:

- **`LightGizmo`** draws a colored dot per enabled light plus a wireframe of its shape: range rings for point lights, the cone and base circle for spots, the rect/disc outline for area lights, the segment for tubes, and a direction line for anything aimable. Clicking a dot selects the light; clicking empty space clears the selection.
- **`DecalGizmo`** draws each decal's oriented projection box and a center dot; clicking the dot selects the decal.
- **`GizmoLayer`** draws the transform gizmo on whatever is selected: X/Y/Z move arrows, a center square for camera-plane free move, an aim handle (for aimable objects: spots, directionals, area lights, decals) and a resize handle (light range, decal size). Handles are picked and dragged purely in screen space; no world raycasting is involved. ImGui panels win the mouse, so clicking through a window never moves an object.

Selection is shared: clicking a gizmo highlights the object's row in the Lights or Decals inspector, and selecting a row there highlights the gizmo. The plumbing is the `scene` package: `Selectable` (one listable, inspectable object), `Transformable` (what the transform gizmo may manipulate, with `aimable()`/`resizable()` capability flags), `EditorSelection` (the single global selection, keyed by object identity), and `SelectableRegistry` (rebuilds the adapter list from live lights and decals every frame). `LightSelectable` and `DecalSelectable` are the two built-in adapters.

Each gizmo layer has a public `enabled` flag (`GizmoLayer.enabled`, `LightGizmo.enabled`, `DecalGizmo.enabled`) if you want to switch one off.

### `Gizmos` helpers

The projection math the gizmos use is public and reusable from your own inspectors:

```java
// world position -> screen pixels, null if behind the camera
float[] xy = Gizmos.project(cam, tmp, wx, wy, wz, displayW, displayH);

// packed ABGR color for ImDrawList
int color = Gizmos.col(1f, 0.85f, 0.2f, 1f);

// draw a world-space circle (center + two basis vectors) as line segments
Gizmos.ring(drawList, cam, tmp, w, h, color, cx, cy, cz, ax, ay, az, bx, by, bz, 24);
```

`cam` is `CameraSnapshot.current()` and `tmp` a scratch `Vector4f` you reuse across calls. Draw into `ImGui.getBackgroundDrawList()` to appear over the world but behind the editor panels.

---

## Limitations

**Requires ImGuiMC.** Without the `imguimc` mod there is no overlay at all; the keybind just logs a hint. Amnetic does not bundle it.

**Nothing persists.** The editor edits live runtime state. Values are not saved anywhere; a restart is back to whatever your code sets. Treat it as a tuning tool and copy the numbers you like into code.

**Selection covers lights and decals only.** `SelectableRegistry` enumerates those two object kinds; there is currently no registration point for host mods to add their own selectables or gizmos, beyond drawing manually with the `Gizmos` helpers.

**Custom transform gizmos are screen-space.** Dragging moves objects relative to the camera, which is convenient but not snapped to blocks or surfaces. Use the inspector's numeric fields for exact positions.

---

## Reference

### `AmneticEditor`

```java
static void register(Inspector inspector); // add a panel; no-op if editor unavailable
static void toggle(); // flip the overlay
static boolean isEnabled();
```

`init()` is called by Amnetic itself (via `AmneticEditorBridge`) when ImGuiMC is present.

### `Inspector`

```java
protected Inspector(String group, String title, boolean openByDefault);
String group();  String title();  ImBoolean open();
abstract void render();
```

### `Gizmos`

```java
static float[] project(CameraSnapshot cam, Vector4f tmp,
        double wx, double wy, double wz, float w, float h); // null if behind camera
static int col(float r, float g, float b, float a); // packed ABGR
static void ring(ImDrawList dl, CameraSnapshot cam, Vector4f tmp, float w, float h,
        int color, double cx, double cy, double cz,
        float ax, float ay, float az, float bx, float by, float bz, int segments);
```

### Scene framework (`ui.scene`)

```java
interface Selectable {
    Object target(); // selection identity
    String category();  String displayName();
    default Transformable transform() { return null; } // null = not movable
    void renderInspector(); // property controls
    default void remove() {}
}

interface Transformable {
    double posX(); double posY(); double posZ();
    void setPosition(double x, double y, double z);
    default boolean aimable() { return false; } // direction handle
    default boolean resizable() { return false; } // extent handle
    // dirX/dirY/dirZ, setDirection, extent, setExtent
}

final class EditorSelection {
    static void set(Selectable s);  static void setTarget(Object t);  static void clear();
    static boolean is(Object t);    static boolean is(Selectable s);
    static boolean has();           static Object target();
}
```

---

## See Also

- [Deferred Lights](Deferred-Lights). What the Lights inspector and light gizmos edit.
- [Decals](Decals). What the Decals inspector and decal gizmos edit.
- [TAA](TAA), [Bloom](Bloom), [Color Grading](Color-Grading). The renderer settings exposed as inspectors.
- [Shader Hot Reload](Shader-Hot-Reload). The other half of the dev loop: edit shaders while the editor is open.
