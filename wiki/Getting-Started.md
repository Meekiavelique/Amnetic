# Getting Started

This page covers adding Amnetic to your mod and registering a basic post-processing effect.

---

## Adding the dependency

Amnetic is not yet published to a public Maven repository. Until then, clone the repository and publish to your local Maven cache:

```bash
git clone https://github.com/meekdev/amnetic
cd amnetic
./gradlew publishToMavenLocal
```

In your mod's `build.gradle`:

```groovy
repositories {
    mavenLocal()
}

dependencies {
    modImplementation "com.meekdev:amnetic:1.0-SNAPSHOT"
}
```

Amnetic targets the client side only. 

---

## Project layout

Amnetic is a library, not a mod entrypoint. You do not need to call any initialization method. Registration happens lazily when you call `PostEffects.register()`.
All registration should happen on the client thread. 

---

## Writing a pipeline JSON

Before registering an effect you need a pipeline descriptor. Place it at:

```
assets/[your_namespace]/post_effect/[effect_name].json
```

A minimal one-pass effect looks like this:

```json
{
    "targets": {
        "swap": {}
    },
    "passes": [
        {
            "vertex_shader": "minecraft:core/screenquad",
            "fragment_shader": "mymod:post/my_effect",
            "inputs": [
                { "sampler_name": "In", "target": "minecraft:main" }
            ],
            "output": "swap",
            "uniforms": {}
        },
        {
            "vertex_shader": "minecraft:core/screenquad",
            "fragment_shader": "minecraft:post/blit",
            "inputs": [{ "sampler_name": "In", "target": "swap" }],
            "output": "minecraft:main",
            "uniforms": {
                "BlitConfig": [
                    { "name": "ColorModulate", "type": "vec4", "value": [1.0, 1.0, 1.0, 1.0] }
                ]
            }
        }
    ]
}
```

Place your fragment shader at:

```
assets/[your_namespace]/shaders/post/my_effect.fsh
```

See [Writing Shaders](Writing-Shaders) for the full shader format and conventions.

---

## Registering an effect

Import from the `com.meekdev.amnetic.client.post` package:

```java
import com.meekdev.amnetic.client.post.PostEffects;
import com.meekdev.amnetic.client.post.PostEffectHandle;
import com.meekdev.amnetic.client.post.RenderPhase;
```

**Always active:**

```java
PostEffectHandle handle = PostEffects.register(Identifier.of("mymod", "my_effect"));
```

**Conditional:**

```java
PostEffectHandle handle = PostEffects.register(
    Identifier.of("mymod", "my_effect"),
    () -> SomeClientState.isActive()
);
```

**Full configuration:**

```java
PostEffectHandle handle = PostEffects.register(
    Identifier.of("mymod", "my_effect"),
    cfg -> cfg
        .when(() -> SomeClientState.isActive())
        .phase(RenderPhase.POST_WORLD)
        .fadeIn(20)
        .fadeOut(10)
        .uniform("MyConfig", 0.5f)
);
```

The returned `PostEffectHandle` lets you reconfigure or disable the effect at any point after registration.

---

## Controlling an effect at runtime

```java
handle.disable();
handle.enable();
handle.setCondition(() -> player.isUnderwater());
handle.setFade(20, 10);
handle.uniform("MyConfig", newValue);
handle.unregister(); // removes the effect permanently
```

See [Post-Processing](Post-Processing) for the complete handle API.

---

## Convenience shortcuts

For common cases, Amnetic provides some methods that skip the configuration builder:

```java
// blur
PostEffects.blur(Identifier.of("mymod", "blur"), 2.0f);
PostEffects.blur(Identifier.of("mymod", "blur"), () -> computeRadius());

// vignette
PostEffects.vignette(Identifier.of("mymod", "vignette"), 0.6f);
```

These still return a `PostEffectHandle` and can be further configured after the call.
