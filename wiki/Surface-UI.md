# Surface UI

Surface is a retained-mode UI toolkit that draws through Amnetic's own renderer rather than through vanilla's GUI stack. It gives you SDF text, shader materials, blur-behind, spring animation, and fine-grained reactivity, on three kinds of canvas: a HUD overlay, a modal screen, and a panel that lives in the world.

```java
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.HudSurface;
import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.widget.Column;
import com.meekdev.amnetic.client.surface.widget.Text;
import com.meekdev.amnetic.client.surface.widget.ProgressBar;

HudSurface hud = Surfaces.hud();
hud.root().add(new Column()
        .gap(4)
        .padding(8)
        .anchor(Anchor.TOP_LEFT)
        .offset(10, 10)
        .add(new Text("Shield"))
        .add(new ProgressBar().width(120).height(6)));
```

Worked examples live in `examples/src/main/java/com/example/surface/`: `HudDemo`, `ScreenDemo`, `WorldUiDemo`, and `ReactivityDemo`.

## The three surfaces

| Factory | Class | Behaviour |
|---|---|---|
| `Surfaces.hud()` | `HudSurface` | Always-on overlay drawn before the GUI. Passive by default; call `interactive(true)` to give it the pointer |
| `Surfaces.screen(name)` | `ScreenSurface` | A modal screen. `open()` shows it and takes mouse and keyboard, `close()` dismisses it. `dim(argb)` darkens the world behind, `pausesGame(true)` pauses singleplayer |
| `Surfaces.world(w, h)` | `WorldSurface` | A panel in the world, sized in metres. `at(x, y, z)`, `facing(...)`, `billboard(true)`, `curve(radians)`, `resolution(pxPerMetre)`, `maxDistance(metres)` |

Every surface exposes `root()` for your content and `overlay()` for anything that must float above it. `Surfaces.defaultFont(id)` sets the font text widgets use when they do not pick their own.

## Layout

A `Widget` is a retained node with a computed rect. Layout runs top-down every frame in GUI-scaled pixels.

Sizing is `size(w, h)`, `width(w)`, `height(h)` in pixels, or `grow(weight)` to divide a container's leftover space. Leaving a dimension unset derives it from content. Placement inside a parent is `anchor(Anchor)` from the nine-point enum plus `offset(dx, dy)`, and `padding(p)` insets a container's content box.

Containers decide how children flow:

| Container | Flow |
|---|---|
| `Column` / `Row` | Sequential along one axis with `gap(px)` and `alignStart/Center/End()`. `grow` divides leftover space |
| `Stack` | Free placement, every child anchored independently |
| `Grid` | Fixed column count |
| `Scroll` | A `Column` that clips overflow and takes the wheel |
| `SplitPane` | Two panes with a draggable divider |

`animateLayout(true)` makes a widget spring to new layout positions instead of teleporting, which is one switch that works inside every container. `animateLayout(stiffness, damping)` tunes it; the default is `60, 9`.

Every widget also carries transform channels applied around its centre: `translate(dx, dy)`, `scaleChannel(s)`, and `rotate(radians)`. They are deliberately generic, so a spring on scale is a bounce and clock noise on translate is a shake. Hit-testing inverts the transform, so the pointer follows.

## Widgets

`Button` `Checkbox` `ColorPicker` `ContextMenu` `Dialog` `Dropdown` `Image` `KeybindField` `ListView` `ModelViewport` `Panel` `ProgressBar` `RadioButton` `RadioGroup` `Slider` `Spinner` `Stepper` `Tabs` `Text` `TextArea` `TextField` `Toasts` `Toggle` `Tooltip` `TreeView` `Viewport`

Setters are fluent and covariant, so a chain keeps the subtype:

```java
new Button("Apply")
        .onClick(() -> apply())
        .colors(0xFF2A2F36, 0xFF3A424C)
        .rounding(4)
        .textColor(0xFFFFFFFF)
        .width(90);
```

`Viewport` renders arbitrary 3D content into a rect with orbit controls, and `ModelViewport` is the turntable preset for showing off a `Model`.

`Tooltip`, `Dropdown`, and `ContextMenu` overlap other content, so they need somewhere above the tree to live. Each takes the surface's `overlay()` layer:

```java
Tooltip.text(button, "Writes the config to disk", hud.overlay());
new Dropdown(screen.overlay(), List.of("Low", "Medium", "Ultra"), 1);
ContextMenu.open(screen.overlay(), mouseX, mouseY, items);
```

Forgetting the layer is not fatal: the widget logs a warning and skips the popup.

## Reactivity

Surface ships a fine-grained reactive core. A `Signal<T>` holds a value, a `Computed<T>` derives one, and an `Effect` re-runs a body whenever anything it read changes. Dependencies are tracked automatically by reading them, so nothing needs declaring.

```java
import com.meekdev.amnetic.client.surface.reactive.Signal;
import com.meekdev.amnetic.client.surface.reactive.Computed;
import com.meekdev.amnetic.client.surface.reactive.Effect;

Signal<Integer> health = new Signal<>(20);
Computed<String> label = new Computed<>(() -> health.get() + " HP");
new Effect(() -> text.text(label.get()));

health.set(18);   // the effect re-runs, the text updates
```

`set` is a no-op when the value is equal, so redundant writes cost nothing.

`Motion<T>` animates toward a target. `Motion.spring(initial, stiffness, damping)` is the house style and `Motion.tween(initial, seconds, easing)` is the fixed-duration alternative. `value()` exposes the current value as a `Signal`, `target(v)` retargets mid-flight, `follow(signal)` chases another signal, and `onSettle(cb)` fires when it comes to rest.

`Fx` wraps the common cases: `Fx.spring(setter, ...)`, `Fx.tween(setter, from, to, seconds)`, `Fx.fadeIn(widget, seconds)`, `Fx.fadeOut(widget, seconds)`, `Fx.perFrame(body)`, and `Fx.timed(seconds, body)`.

## Text

Text is rendered from signed-distance-field atlases, so it stays sharp at any scale and in a world surface viewed up close. `Fonts.get(id)` loads one and `Fonts.chain(primary, fallbacks...)` builds a fallback chain for glyphs the primary lacks. Glyphs are rasterised into the atlas on demand as they are first used.

`GlyphFx` is a per-glyph hook: it receives each glyph's pose and can offset, scale, rotate, or recolour it individually, which is what wave, jitter, and typewriter effects are built from.

## Materials and drawing

`SurfaceMaterial.load(id)` compiles a fragment shader you can apply to a widget's background. The material parses its own uniform declarations, so `uniforms()` reports what it exposes, `set(name, floats...)` and `setColor(name, argb)` push values, and `bind(name, signal)` wires a uniform to a `Signal` so it follows the reactive graph. Blending is `MIX`, `ADD`, or `PREMUL`.

The renderer captures the scene into a texture before drawing, so materials can sample what is behind them. That is what blur-behind panels are built on.

For custom drawing, `onDraw(UiDraw)` hands you the immediate-mode drawing API used by the widgets themselves: rects, rounded rects, clipping, transforms, and text.

## Input

Each surface owns an `InputRouter` that walks the widget tree. Hover, press, drag, focus, and tab cycling are handled for you; `hovered`, `pressed`, and `focused` are readable flags on every widget.

Drag and drop is two calls: `draggable(payload)` on the source and `dropTarget(consumer)` on the destination. A drag begins after five pixels of movement and the dragged widget is drawn semi-transparent under the cursor.

Scroll events bubble from the hit widget upward, so a button inside a `Scroll` still scrolls it.

## How it draws

`SurfaceRenderer` registers one pass at `RenderStage.BEFORE_GUI` priority 50. Each frame it captures the scene colour, lays out and draws every visible HUD surface and open screen surface into a batched vertex stream, and flushes once. `WorldSurfaceRenderer` handles world panels separately, rendering each to its own target and placing it in the world.

A surface that throws during draw is caught and hidden rather than taking down the frame, with a warning naming it.

## Limitations

* Sizing is absolute pixels or flex weight. There is no percentage-of-parent unit, so a layout tuned at one GUI scale needs rechecking at another.
* Layout mode is fixed by the container's class, so a widget cannot change how it arranges children at runtime without rebuilding that part of the tree.
* Drawing order is tree order. There is no z-index, which is why overlapping widgets are reparented into `overlay()`.
* Hit-testing is gated by ancestor rects, so a widget drawn outside its parent's bounds is not clickable. This is the reason popups live in the overlay layer.
* The whole tree is laid out every frame with no dirty-flagging, so very large trees pay a per-frame measurement cost.
* Hover is a polled flag, not an enter/leave event.

## See Also

* [Framebuffers](Framebuffers) for the targets world surfaces render into
* [Models](Models) for the models a `ModelViewport` displays
* [Writing Shaders](Writing-Shaders) for authoring a `SurfaceMaterial`
* [Render Pipeline](Render-Pipeline) for the stage the renderer hooks
