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
        .add(new Text("Shield").color(0xFFFFFFFF).px(9))
        .add(new ProgressBar(0.62f).width(120).height(6)));
```

Worked examples live in `examples/src/main/java/com/example/surface/`: `HudDemo`, `ScreenDemo`, `WorldUiDemo`, and `ReactivityDemo`.

## The three surfaces

| Factory | Class | Behaviour |
|---|---|---|
| `Surfaces.hud()` | `HudSurface` | Always-on overlay drawn before the GUI |
| `Surfaces.screen(name)` | `ScreenSurface` | A modal screen that takes mouse and keyboard while open |
| `Surfaces.world(w, h)` | `WorldSurface` | A panel in the world, sized in metres |

`Surfaces.defaultFont(id)` sets the font text widgets use when they do not pick their own; `Surfaces.defaultFont()` reads it back.

Every surface exposes `root()` for your content and `overlay()` for anything that must float above it. Both are a `Stack`.

**`HudSurface`**

| Method | Meaning |
|---|---|
| `interactive(boolean)` | Give the HUD the pointer. Off by default, so a HUD never steals clicks unless you ask |
| `onDraw(Consumer<UiDraw>)` | Custom immediate-mode drawing after the widget tree |
| `setVisible(boolean)` / `isVisible()` | Show or hide the whole surface |
| `remove()` | Unregister it from the renderer |

**`ScreenSurface`**

| Method | Meaning |
|---|---|
| `open()` / `close()` / `isOpen()` | Show, dismiss, and query the modal |
| `dim(argb)` | Darken the world behind it |
| `pausesGame(boolean)` | Pause singleplayer while open |
| `name()` | The name given at construction |

**`WorldSurface`**

| Method | Meaning |
|---|---|
| `at(x, y, z)` | World position |
| `facing(fx, fy, fz)` | Surface normal |
| `billboard(boolean)` | Always turn to face the camera |
| `curve(radians)` | Bend the panel around its vertical axis |
| `resolution(pxPerMetre)` | Canvas density, minimum 16 |
| `maxDistance(metres)` | Cull and stop interacting past this range |
| `alwaysOnTop(boolean)` | Draw over geometry instead of depth-testing |
| `setVisible(boolean)` / `isVisible()` / `remove()` | Lifecycle |

## Layout

A `Widget` is a retained node with a computed rect. Layout runs top-down every frame in GUI-scaled pixels.

| Method | Meaning |
|---|---|
| `size(w, h)`, `width(w)`, `height(h)` | Absolute pixels. Leave unset to derive from content |
| `grow(weight)` | Divide a container's leftover space along its main axis |
| `anchor(Anchor)` | One of the nine-point `Anchor` enum values |
| `offset(dx, dy)` | Nudge from the anchor |
| `padding(p)` | Inset a container's content box |
| `visible(boolean)`, `opacity(0..1)` | Visibility. `opacity` multiplies down the subtree |
| `add(child)`, `removeChild(child)`, `remove()`, `children()`, `parentWidget()` | Tree mutation and traversal |

`add` returns the *parent* so chains build a tree, which means you keep a reference to a child by constructing it into a variable first.

Containers decide how children flow:

| Container | Flow |
|---|---|
| `Column` | Vertical. `gap(px)`, `alignStart()`, `alignCenter()`, `alignEnd()` |
| `Row` | Horizontal. Same alignment API plus `wrap(boolean)` |
| `Stack` | Free placement, every child anchored independently |
| `Grid` | Fixed column count. `gap(px)`, `add(child, colSpan)`, `span(child, colSpan)` |
| `Scroll` | A `Column` that clips overflow and takes the wheel |
| `SplitPane` | Two panes with a draggable divider. `horizontal(boolean)`, `minSizes(a, b)`, `dividerColors(idle, active)` |

### Animation and transforms

`animateLayout(true)` makes a widget spring to new layout positions instead of teleporting, which is one switch that works inside every container. `animateLayout(stiffness, damping)` tunes it; the default is `60, 9`.

Every widget also carries transform channels applied around its centre. They are deliberately generic, so a spring on scale is a bounce and clock noise on translate is a shake:

```java
widget.translate(dx, dy);     // translateXValue() / translateYValue()
widget.scaleChannel(1.05f);   // scaleValue()
widget.rotate(0.1f);          // rotationValue()
```

Hit-testing inverts the transform, so the pointer follows.

## Widgets

### Constructors

Most widgets take their initial state as a constructor argument rather than a setter, so there is no invalid intermediate state:

| Widget | Constructor |
|---|---|
| `Text` | `Text(String)` or `Text(Signal<String>)`, which re-renders when the signal changes |
| `Button` | `Button(String label)` |
| `Checkbox` | `Checkbox(boolean initial)` or `Checkbox(boolean initial, String label)` |
| `Toggle` | `Toggle(boolean initial)` |
| `Slider` | `Slider(float initial)` |
| `ProgressBar` | `ProgressBar(float initial)` |
| `Grid` | `Grid(int cols)` |
| `RadioGroup` | `RadioGroup(int initialSelected)` |
| `Dropdown` | `Dropdown(Stack overlay, List<String> options, int initialIndex)` |
| `Column`, `Row`, `Stack`, `Scroll`, `Spinner` | no-arg |

`Text(Signal<String>)` is the shortest path to reactive UI: bind it once and never touch the widget again.

```java
Signal<String> status = new Signal<>("idle");
column.add(new Text(status));
status.set("running");   // the label updates itself
```

### Panel

The styling primitive most other things sit on.

| Method | Meaning |
|---|---|
| `background(argb)` | Flat fill |
| `gradient(topArgb, bottomArgb)` | Vertical gradient fill |
| `rounding(r)` | Corner radius |
| `border(width, argb)` | Outline |
| `shadow(softness)` / `shadow(softness, argb)` | Drop shadow |
| `clipContent(boolean)` | Clip children to the panel rect |
| `blurBehind(blurPx)` | Blur whatever the scene drew behind the panel |
| `material(SurfaceMaterial)` | Fill with a custom fragment shader |

### Text

| Method | Meaning |
|---|---|
| `text(s)`, `px(size)`, `color(argb)`, `font(id)` | Content and basic style |
| `center()`, `right()` | Horizontal alignment |
| `wrap(boolean)`, `ellipsis(boolean)` | Overflow behaviour |
| `outline(width, color)` | Outline around every glyph |
| `glow(radius, color)` | Soft glow |
| `textShadow(dx, dy, color)` | Offset shadow |
| `layer(dx, dy, edgeOffset, softness, color)` | A raw extra SDF layer, the primitive the three above are built from |
| `glyphFx(GlyphFx)` | Per-glyph animation hook |

### Inputs

| Widget | Key API |
|---|---|
| `Button` | `label`, `onClick(Runnable)`, `colors(normal, hover)`, `rounding`, `textColor`, `textShadow`, `border(width, color[, hoverColor])`, `font`, `material` |
| `Checkbox` | `label`, `onChange(Consumer<Boolean>)`, `colors(box, checked)`, `textColor`, `px` |
| `Toggle` | `onChange(Consumer<Boolean>)`, `colors(off, on)` |
| `Slider` | `onChange(Consumer<Float>)`, `colors(track, fill)`, `knobColor`, `rounding` |
| `Stepper` | `onChange(Consumer<Float>)`, `px`, `textColor` |
| `TextField` | `placeholder`, `onSubmit(Consumer<String>)`, `colors(bg, text)`, `accent`, `rounding`, `px` |
| `TextArea` | `placeholder`, `px` |
| `Dropdown` | `onChange(Consumer<Integer>)`, `attach(overlay)`, `isOpen()`, `colors`, `accent`, `rounding`, `px` |
| `RadioGroup` | `option(label)`, `onChange(Consumer<Integer>)`, `select(index)`, `selected` signal |
| `RadioButton` | `label`, `colors(ring, active)`, `textColor`, `px` |
| `ColorPicker` | `onChange(Consumer<Integer>)` |
| `KeybindField` | `onChange(Consumer<Integer>)`, `isArmed()`, `accent`, `px`, static `keyName(code)` |

### Display and containers

| Widget | Key API |
|---|---|
| `ProgressBar` | `set(0..1)`, `label`, `colors(track, fill)`, `textColor`, `rounding`, `px` |
| `Spinner` | `color`, `segments(n)`, `speed(radiansPerSecond)` |
| `Image` | `tint(argb)`, `flipV(boolean)` |
| `Tabs` | `addTab(title, page)`, `select(index)`, `onChange(Consumer<Integer>)`, `accent`, `gap`, `px` |
| `TreeView` | `indent`, `rowHeight`, `textColor`, `px`; nodes are `TreeNode` with `add(child)`, `expand(boolean)`, `childNodes()` |
| `ListView<T>` | `items(List<T>)`, virtualized so only visible rows are built |
| `Toasts` | `push(text, seconds)`, `push(widget, seconds)`, `column()`, `dispose()` |

### Popups

These overlap other content, so they need somewhere above the tree to live. Each takes the surface's `overlay()` layer:

```java
Tooltip.text(button, "Writes the config to disk", hud.overlay());
Tooltip.install(target, customContent, hud.overlay());

new Dropdown(screen.overlay(), List.of("Low", "Medium", "Ultra"), 1);

ContextMenu.open(screen.overlay(), mouseX, mouseY, List.of(
        new ContextMenu.Item("Copy", () -> copy(), true),
        new ContextMenu.Item("Paste", () -> paste(), false)));

Dialog.confirm(screen, "Delete?", "This cannot be undone", () -> del(), null);
Dialog.prompt(screen, "Rename", "new name", name -> rename(name));
```

Forgetting the layer is not fatal: the widget logs a warning and skips the popup.

### 3D viewports

`Viewport` renders arbitrary 3D content into a rect: `onFrame(FrameHook)`, `orbitInput(boolean)`, `pitchLimits(min, max)`, `zoomLimits(min, max)`, `dragSensitivity(degPerPx)`.

`ModelViewport` is the turntable preset for showing off a `Model`: `autoSpin(degreesPerSecond)`, `play(clip)`, `light(block, sky)`, `fov(degrees)`, and `view()` for the underlying `ModelView`.

## Reactivity

A `Signal<T>` holds a value, a `Computed<T>` derives one, and an `Effect` re-runs a body whenever anything it read changes. Dependencies are tracked automatically by *reading* them, so nothing needs declaring.

```java
Signal<Integer> health = new Signal<>(20);
Computed<String> label = new Computed<>(() -> health.get() + " HP");
new Effect(() -> text.text(label.get()));

health.set(18);                  // the effect re-runs, the text updates
health.update(h -> h - 2);       // read-modify-write
int now = health.peek();         // read WITHOUT subscribing
```

`peek()` is the escape hatch: use it inside an effect when you need a value but do not want a dependency on it. `set` is a no-op when the value is equal, so redundant writes cost nothing.

`Motion<T>` animates toward a target:

```java
Motion<Float> m = Motion.spring(0f, 60f, 9f);        // stiffness, damping
Motion<Float> t = Motion.tween(0f, 0.3f, easing);    // seconds, easing
m.target(1f);
m.follow(someSignal);
m.onSettle(() -> done());
Signal<Float> current = m.value();
boolean moving = m.inFlight();
m.dispose();
```

`Fx` wraps the common cases: `Fx.spring(setter, initial, stiffness, damping)`, `Fx.tween(setter, from, to, seconds[, easing])`, `Fx.fadeIn(widget, seconds)`, `Fx.fadeOut(widget, seconds)`, `Fx.perFrame(body)`, `Fx.timed(seconds, body)`.

`Reactive` is the clock the whole graph runs on: `Reactive.clock()` is a `Signal<Float>` of elapsed seconds, `Reactive.dt()` the last frame delta, `Reactive.tick(dt)` advances it, and `Reactive.flush()` drains pending effects. The client frame drives `tick` for you; the surface renderer also pumps it while a menu is open with no world loaded.

## Text rendering

Text is rendered from signed-distance-field atlases, so it stays sharp at any scale and in a world surface viewed up close. `Fonts.get(id)` loads one and `Fonts.chain(primary, fallbacks...)` builds a fallback chain for glyphs the primary lacks. Glyphs are rasterised into the atlas on demand as they are first used.

`SdfFont` exposes the metrics if you are laying out text yourself: `glyph(codepoint)`, `baked(codepoint)`, `kern(cp1, cp2)`, `ascentPx()`, `capPx()`, `bakePx()`, `atlasSize()`, `texture()`, and `sdfUnitsPerGuiPx(px)`.

`GlyphFx` is a per-glyph hook:

```java
public interface GlyphFx {
    void apply(int glyphIndex, float time, GlyphPose pose);
}
```

`GlyphPose` carries `dx`, `dy`, `scale`, and `alpha`, all writable. That is the whole contract, so wave, jitter, stagger, and typewriter reveals are all expressed as offsets, scale, and fade per glyph.

```java
text.glyphFx((i, time, pose) -> {
    pose.dy = (float) Math.sin(time * 4 + i * 0.4) * 2f;
    pose.alpha = Math.min(1f, Math.max(0f, time * 4 - i * 0.2f));
});
```

## Materials

`SurfaceMaterial.load(id)` compiles a fragment shader you can apply to a `Panel` or `Button` background.

| Method | Meaning |
|---|---|
| `uniforms()` | The uniforms the shader declares, as `Uniform(name, glslType, hint, hintRange, defaultValue)` |
| `set(name, floats...)` | Push a value |
| `setColor(name, argb)` | Push a colour as a vec4 |
| `setTexture(name, glTextureId)` | Bind a sampler |
| `bind(name, Signal<Float>)` | Wire a uniform to a signal so it follows the reactive graph |
| `blend()` | `MIX`, `ADD`, or `PREMUL` |
| `broken()` | True if it failed to compile, so you can fall back |
| `dispose()` | Release it |

The renderer captures the scene into a texture before drawing, so materials can sample what is behind them. That is what `blurBehind` is built on.

## Custom drawing

`HudSurface.onDraw(UiDraw)` hands you the immediate-mode API the widgets themselves use.

| Method | Draws |
|---|---|
| `rect`, `roundedRect`, `gradient`, `border`, `shadow` | Boxes |
| `image(glTextureId, x, y, w, h, argb)` | A texture |
| `blurBehind(x, y, w, h, radius, blurPx, tint)` | Blurred scene behind a rect |
| `material(mat, x, y, w, h, radius)` | A shader fill |
| `text`, `textCentered`, `textLeftCentered` | Text |
| `textStyled(s, x, baselineY, px, argb, edgeOffset, softness)` | Text with raw SDF edge control |
| `textWidth(s, px)`, `lineHeight(px)` | Metrics |
| `font(id)`, `currentFont()` | Font selection |
| `pushClip`/`popClip`, `pushTransform`/`popTransform` | Nestable clip and transform stacks |
| `interrupt(Runnable)` | Flush the batch, run raw GL, then resume |
| `width()`, `height()` | Canvas size |

## Input

Each surface owns an `InputRouter` that walks the widget tree. Hover, press, drag, focus, and tab cycling are handled for you; `hovered`, `pressed`, and `focused` are public flags on every widget.

Drag and drop is two calls, `draggable(payload)` on the source and `dropTarget(consumer)` on the destination. A drag begins after five pixels of movement and the dragged widget is drawn semi-transparent under the cursor.

Scroll events bubble from the hit widget upward, so a button inside a `Scroll` still scrolls it.

To build a custom widget, subclass `Widget` and override the hooks: `drawSelf(UiDraw, alpha)`, `drawAfterChildren(UiDraw)`, `contentWidth()`, `contentHeight(forWidth)`, `placeChildren()`, `interactive()`, `focusable()`, and the `onMouseDown`/`onMouseUp`/`onMouseDrag`/`onScroll`/`onChar`/`onKey`/`onFocusLost` handlers.

Methods prefixed `internal` (`internalTree`, `internalInput`, `internalDrawCallback`, `internalClosed`) are engine-side hooks the renderer calls. They are public only because the renderer lives in another package; treat them as private. The same goes for the `*Value` readback getters (`dimValue`, `billboardValue`, `opacityValue` and friends), which exist so the renderer can read what you set.

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
* `GlyphFx` cannot rotate or recolour a glyph, only offset, scale, and fade it.

## See Also

* [Framebuffers](Framebuffers) for the targets world surfaces render into
* [Models](Models) for the models a `ModelViewport` displays
* [Writing Shaders](Writing-Shaders) for authoring a `SurfaceMaterial`
* [Render Pipeline](Render-Pipeline) for the stage the renderer hooks
