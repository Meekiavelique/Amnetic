# Amnetic Editor Rework — Design

Goal: fix the three pain points — manipulation-by-numbers, clutter/focus, look & feel — with a
**selection-centric**, fully modular editor.

## Architecture (selection-centric)
- `EditorSelection` (singleton): the one currently-selected object.
- `Selectable { displayName(); category(); }` — implemented (via thin adapters) by lights, decals, effects.
- `Transformable { pos get/set; dir get/set; extent get/set; caps() }` — what the gizmo manipulates;
  `caps()` declares movable/aimable/resizable so only relevant handles show.
- `SelectableRegistry`: enumerates live objects (wraps LightRegistry, Decals, Particles).
- Reworked `AmneticEditor`: ONE window — Outliner (left, grouped list, click-selects, add/remove) +
  Inspector (right, renders the selection via a per-category PropertyView). Tabs: **Scene** (globals:
  shadows/SSAO/SSGI/volumetrics/post) and **World** (the outliner). Gizmo overlay reads/writes the same
  selection.

## Transform gizmos (`GizmoLayer` + `Handle`s)
- `Handle` interface: `draw(drawList)`, `hitTest(mx,my)->dist`, `onDrag(dx,dy,cam)`. Each kind independent:
  `MoveAxisHandle(x/y/z)`, `MovePlaneHandle`, `AimHandle`, `RingResizeHandle(kind)`.
- Input via imgui mouse; respects `WantCaptureMouse` (panels win). Hover = nearest handle in px threshold;
  active handle holds the drag until release.
- Math: screen-space mouse delta projected onto each handle's projected axis (uses `Gizmos.project()`),
  no world raycast. Free-move on a camera-facing plane at object depth.

## Look & feel
- `EditorTheme`: one cohesive dark palette (spacing, rounding, padding, frame/grab/hover, accent, font).
- `Widgets` (static lib): `vec3`, `slider`, `colorRGB/RGBA`, `enumCombo`, `toggle`, `section`, `help`.
  Two-column label|control rows. Every panel rebuilt on these for consistency.

## Modularity contract
- New manipulable type = implement `Selectable`+`Transformable` + declare caps; gizmo/outliner/inspector
  pick it up automatically.
- New handle kind = one `Handle` class.
- `Widgets`/`EditorTheme` standalone; no inspector depends on another.

## Build stages (each usable on its own)
1. **Shell + selection**: interfaces, `EditorSelection`, `SelectableRegistry`, `EditorTheme`, `Widgets`,
   reworked one-window Outliner+Inspector + Scene tab.
2. **Transform gizmos**: `GizmoLayer` + handles driving the selection.
3. **Migrate + polish**: move existing per-object editing into PropertyViews on `Widgets`; retire old panels.
</content>
