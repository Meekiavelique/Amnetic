# Uniforms

This page explains how Amnetic's uniform system works, and provides a full reference for `UniformSuppliers`.

---

## How uniform blocks work

Minecraft's post-processing pipeline uses GLSL uniform buffer objects (UBOs) declared with `layout(std140)`. A uniform block groups one or more variables under a single named interface block:

```glsl
layout(std140) uniform MyConfig {
    float Strength;
    vec4  Color;
};
```

In the pipeline JSON, the same grouping is expressed as a named entry under `"uniforms"`:

```json
"uniforms": {
    "MyConfig": [
        { "name": "Strength", "type": "float", "value": 0.0 },
        { "name": "Color",    "type": "vec4",  "value": [1.0, 0.0, 0.0, 1.0] }
    ]
}
```

The key `"MyConfig"` is the **block name**. It is the same string in both the JSON and the GLSL declaration.

---

## The block-name convention

**All `.uniform()` calls in Amnetic take the block name as the first argument, not the name of an individual variable inside the block.**

```java
cfg.uniform("MyConfig", 0.5f);
cfg.uniform("Strength", 0.5f);
```

The first line is correct. The second line is wrong because `"Strength"` is a member name, not a block name.

When you override a block, you replace the **entire block's value list**. All members must be accounted for in the order they appear in the JSON.

For a block declared as:

```json
"MyConfig": [
    { "name": "Strength", "type": "float", "value": 0.0 },
    { "name": "Color",    "type": "vec4",  "value": [1.0, 0.0, 0.0, 1.0] }
]
```

You cannot override just `Strength`. You must supply both values:

```java
cfg.uniformRaw("MyConfig", () -> List.of(
    new FloatValue(myStrength),
    new Vec4Value(r, g, b, a)
));
```

For blocks with a single member, a typed convenience overload is available:

Single float member:

```java
cfg.uniform("Intensity", 0.8f);
```

Single vec4 member:

```java
cfg.uniformVec4("TintBlock", () -> new Vector4f(r, g, b, a));
```

---

## Reserved block names

The following block names are used by Amnetic or minecraft and should not be used for custom data:

| Block name | Source | Contents                                                            |
|---|---|---------------------------------------------------------------------|
| `Intensity` | Amnetic fade system | `float Value` - current fade intensity 0..1                         |
| `SamplerInfo` | Minecraft  | `vec2 OutSize`, `vec2 InSize` - output and input texture dimensions |
| `BlitConfig` | Minecraft blit shader | `vec4 ColorModulate`                                                |

---

## UniformSuppliers reference

`UniformSuppliers` is a utility class providing factory methods for commonly needed uniform value suppliers. All methods are static.

### Constant values

```java
UniformSuppliers.constant(float value)
UniformSuppliers.constant(float x, float y)
```

Returns a supplier that always yields the given value. Useful for setting a uniform to a fixed value without hardcoding it in the JSON.

### Time

```java
UniformSuppliers.gameTime()
UniformSuppliers.partialTick()
```

`gameTime()` increases monotonically with world ticks. It is suitable for driving time-based animations in shaders.

`partialTick()` is the interpolation factor between the last tick and the current frame. Use it when you need frame-rate-independent smooth animation.

### Screen dimensions

```java
UniformSuppliers.screenWidth()
UniformSuppliers.screenHeight()
UniformSuppliers.screenSize()
```

These update every frame and reflect the current window size. Note that the engine also automatically provides `SamplerInfo.OutSize` and `SamplerInfo.InSize` in every shader, so `screenSize()` is mainly useful when you need the screen dimensions in a custom named block.

### Player state

```java
UniformSuppliers.playerHealth()
UniformSuppliers.playerHealthNorm()
UniformSuppliers.playerAir()
UniformSuppliers.playerAirNorm()
```

All player state suppliers return `0.0` when no local player exists (on the main menu).

### Animated values

```java
UniformSuppliers.sinTime(float speed)
UniformSuppliers.cosTime(float speed)
```

Returns a `DoubleSupplier` yielding `sin(gameTime * speed)` or `cos(gameTime * speed)`. The result is in the range `-1.0..1.0`.

```java
UniformSuppliers.pingPong(float min, float max, float speed)
```

Returns a `DoubleSupplier` that oscillates linearly between `min` and `max` at the given speed. Unlike `sinTime`, the transitions are linear rather than sinusoidal.

### Wrapping arbitrary suppliers

If none of the built-in suppliers fits your use case, you can wrap any supplier:

```java
UniformSuppliers.ofFloat(DoubleSupplier supplier)
UniformSuppliers.ofInt(IntSupplier supplier)
UniformSuppliers.ofVec2(Supplier<Vector2fc> supplier)
UniformSuppliers.ofVec3(Supplier<Vector3fc> supplier)
UniformSuppliers.ofVec4(Supplier<Vector4fc> supplier)
UniformSuppliers.ofMat4(Supplier<Matrix4fc> supplier)
```

These are pass-through wrappers. They exist so that you can pass any lambda or method reference where a typed supplier is expected.

---

## Custom uniforms

Suppliers do not have to come from `UniformSuppliers`. You can pass any lambda, method reference, or object that implements the supplier interface. This means uniforms can be driven by anything in your mod:

Driven by a field:

```java
private float myStrength = 0.5f;

PostEffects.register(Identifier.of("mymod", "effect"), cfg -> cfg
    .uniform("MyConfig", () -> myStrength)
);
```

```java
cfg.uniformVec4("TintBlock", () -> new Vector4f(
    MyState.getRed(),
    MyState.getGreen(),
    MyState.getBlue(),
    1.0f
));
```

Driven by a keybind or toggle:

```java
private boolean active = false;

cfg.uniform("FadeConfig", () -> active ? 1.0f : 0.0f);
```

Multi-member block override:

```java
cfg.uniformRaw("MyConfig", () -> List.of(
    new UniformValue.FloatValue(myStrength),
    new UniformValue.Vec4fValue(new Vector4f(r, g, b, 1.0f))
));
```

The supplier is called every frame the effect is active. Return whatever value is appropriate for that frame.
