# Native shaders on Minecraft 26.2 and 26.3

Shaders still use **GLSL**, not HLSL or a separate Vulkan language. Minecraft compiles the GLSL through its selected backend; Vulkan consumes the resulting SPIR-V internally. You do not need to ship hand-written SPIR-V for `NativeScreenProgram`.

Amnetic's native render paths submit commands through Minecraft's GPU device. Minecraft selects OpenGL or Vulkan; shaders must compile through that device for either backend. Minecraft 26.2 uses `com.mojang.blaze3d` APIs, while 26.3 uses `com.mojang.renderpearl` APIs. Stonecutter adapts the shared source.

This guide describes the native shader adapter and its current limits. It does not mean every older Amnetic rendering API has been migrated.

## Fullscreen fragment shaders

Put a shader at `assets/example/shaders/effects/tint.fsh`:

```glsl
#version 450
in vec2 vUV;
out vec4 FragColor;
uniform sampler2D Scene;
uniform float Strength;
uniform vec3 Tint;
void main() {
    vec4 color = texture(Scene, vUV);
    FragColor = vec4(mix(color.rgb, color.rgb * Tint, Strength), color.a);
}
```

`NativeScreenProgram` generates the fullscreen triangle, assigns varying/output locations, and converts loose numeric uniforms into an explicit `std140` block named `EffectParams`. Keep each uniform declaration on its own line. Supported numeric types are `float`, `int`, `uint`, `bool`, `vec2`, `vec3`, `vec4`, `ivec2`, `ivec3`, `ivec4`, and `mat4`, including fixed-size numeric arrays. Other types require extending the adapter.

Bind every declared sampler before drawing. Use `setTexture(name, GpuTextureView)` for textures and `setTexels(name, GpuBufferSlice)` for `samplerBuffer` inputs. Set numeric values using `setFloat`, `setInt`, `setVector`, or `setMatrix`. Array elements use names such as `Transforms[0]`. Declare pipeline color formats and blending to match the destination attachments.

The adapter is an internal rendering API. Existing JSON post-effect examples for 26.1.2 are a separate path and must not be assumed to use this adapter.

## Instanced shaders

Use explicit vertex input locations matching `InstanceLayout`. Use `gl_VertexIndex` and `gl_InstanceIndex`; the adapter also translates older `gl_VertexID` and `gl_InstanceID` source. Match vertex/fragment varying names and types. A `mat4` varying occupies four locations.

Texture bindings must use resource identifiers or native texture views. A raw OpenGL texture ID has no meaning on Vulkan. The native `extraSampler` overload accepts a supplier of `GpuTextureView`; the owner must keep that view valid until the draw completes.

## Depth and coordinates

Vanilla native world rendering uses reversed depth: the far/sky clear value is `0`, and visible world geometry uses `GREATER_THAN_OR_EQUAL`. Reconstruct positions using the current inverse view-projection matrix and the device's zero-to-one convention. Do not hard-code OpenGL's `depth * 2 - 1` conversion.

Iris can change the depth convention when a shader pack is active. The lighting integration queries Iris's reverse-depth API and runs its composition after shader-pack rendering. A shader-pack depth attachment and projection must describe the same scene.

Do not flip the entire GUI projection based on the backend name. ImGui input, framebuffer scaling, projection and scissor conversion must agree; otherwise controls appear outside their windows or mouse hit targets stop matching pixels.

## Resource ownership and submission

- Own and close textures, their views, buffers, and samplers; close views before their backing textures.
- Treat Minecraft's main attachments and borrowed texture views as borrowed resources.
- Recreate size-dependent targets after resizing, and invalidate temporal histories after resizing, camera cuts and world transitions.
- Never sample an attachment while writing into that same attachment. Capture or ping-pong it first.
- Include the required texture usage flags. Clearing a color texture requires render-attachment usage.
- Submit command encoders. Closing a render pass alone does not submit its commands.

26.2 caches compiled pipelines on its device. 26.3 exposes owned compiled pipeline objects that must be closed. The compatibility adapter accounts for this difference.

## Backend limits

Direct `GL11`/`GL30` calls, GLFW context access, raw GL framebuffers, and OpenGL compute interoperability cannot be used in a Vulkan render path. Use capability checks and native command buffers; do not silently count a disabled effect as working.

The native API currently lacks the array-texture support needed by Amnetic's older shadow implementation. Local shadows use a 2D atlas with six columns for point-light cube faces and one row per light; spot shadows use a 4×4 atlas. Filtering must stay inside each tile.



## Temporal effects and color fallback

TAA reprojects with the same jittered matrices that produced the scene depth. Both TAA and SSGI store current-camera surface distance in the alpha channel of their RGBA16F history; reprojection compares it against the expected previous-camera distance. Read history distance with an unfiltered texel fetch so silhouette depths are not averaged together. Reject invalid positions, offscreen samples and disocclusions before filtering history color. TAA's output pass restores alpha to one.

SSGI uses a rotating 16-sample kernel, half-float raw GI and history, neighborhood bounds to limit stale indirect color, and the configured history frame cap for reset ramp-up. Radius, intensity, resolution changes and large camera jumps invalidate history. Native SSGI records trace, temporal resolve and composite in one command submission; native TAA records resolve and output together. These changes reduce submissions; frame-time improvements require measurement in game.

When a populated material G-buffer is unavailable, lighting uses the captured scene color for surface tint. Never interpret a white placeholder texture as published material albedo. This preserves scene tint with Iris, but shaderpack exposure, tone mapping and unavailable material data can still change the result. Sodium terrain remains owned by Sodium; Amnetic's composition consumes scene attachments rather than replacing its chunk renderer. Shaderpack parity and native Vulkan runtime coverage remain validation tasks.


Local godrays use the native half-float scatter targets and point/spot shadow atlases. Global strength, density, anisotropy, step budget and shadow toggles modify the corresponding per-light controls. Zero steps, strength or density skips the effect. With Iris active, shaft composition runs after shaderpack output and before scene depth is cleared; its temporal reconstruction follows the pack's depth convention. Large camera jumps and target resizing discard history. Directional/sun godrays are not implemented by this local-light ray marcher.

Light temperature is retained per light in kelvin (1000�40000 K), converts to the same RGB light-buffer values used for surface lighting and volumetric scattering, and is editable in both light inspectors. Direct color edits override the resulting RGB while retaining the last selected kelvin value.


Native local shadow depth uses nearest sampling before the Poisson depth comparisons, with atlas taps constrained to their tiles. Point filtering scales with face resolution, point receivers apply normal bias, and their perspective-depth bias decreases with distance to limit detached shadows. Tile clear and geometry draws share one submission. The point atlas allocates rows only for selected shadow lights; an unused local shadow atlas is released. Visual seam, bias and penumbra quality still require scene testing.


## Automatic shader reload in development

Amnetic watches development shader resources in the background. Saving a `.vsh`, `.fsh`, `.glsl`, `.comp`, `.geom`, `.tesc`, or `.tese` file synchronizes the resource and invalidates shaders on the client thread. Pending edits are processed when client ticking resumes, including when returning to a paused game. Includes invalidate their dependent native programs too. Native screen programs compile a replacement before releasing the previous pipeline; an invalid edit logs a compiler error and keeps the last valid pipeline until the next change. Compatible numeric uniform values and existing texture bindings are retained.

This watcher is for unpacked development resources, not edits inside a packaged mod JAR. Changing Java code, vertex layouts, attachment formats or resource ownership still requires rebuilding. Explicit resource reloads and custom native pipelines should call `ShaderProgram.invalidateAll()` or register a `ShaderHotReload.onReload` callback as appropriate.

## Selecting the backend

Use the same shader source on OpenGL and Vulkan and test both. Minecraft's graphics-backend setting requires a game restart to replace its GPU device. Amnetic follows the active device; changing a preference does not safely hot-swap a live OpenGL device for Vulkan.

## Directional shadows

The native path uses four horizontal atlas slots for directional cascades and stabilizes their projection to shadow texels. The first enabled directional light requesting shadows owns these cascades. Point and spot lights retain their separate atlases. Native directional illumination and cascades are implemented, but visual validation of alignment and player shadows is still pending. Custom model and instanced shadow casters remain migration work.
