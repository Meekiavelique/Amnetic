# Backport triage: 26.1.2 → 1.21.1 / 1.20.1

Generated from imports in `src/main/java` (374 files, ~37.5k lines), then spot-checked by hand.

| Bucket | Files | Lines | Meaning |
|---|---:|---:|---|
| PORTABLE | 151 | 9,862 | No Minecraft/Mojang/Fabric imports. Copy as-is. |
| MECHANICAL | 145 | 19,316 | Same classes on old versions, maybe renamed or moved (`Identifier`→`ResourceLocation`, `blaze3d.opengl.GlStateManager`→`blaze3d.platform.GlStateManager`). |
| ADAPT | 21 | 1,539 | Class exists but the API differs (`RenderTarget`, `Camera`, `VertexConsumer`, `PostChain`, `RenderSystem`...). Port by hand. |
| REWRITE | 36 | 5,957 | Uses types that don't exist before 1.21.2–1.21.11. |
| MIXIN | 21 | 878 | All need re-targeting. |

About 7.5k lines really need porting, and about 19k lines are find/replace. Shaders are fine: only one MC-loaded core shader (`shaders/core/worldmesh.vsh`), and everything else is standalone GLSL compiled by Amnetic.

## REWRITE is smaller than it looks

Most of the 36 REWRITE files are blocked by one of a few small seams. Build one shim per seam and most of them become MECHANICAL.

| Seam | Files | What it really is | Old-version replacement |
|---|---:|---|---|
| **GL texture id** (`GlTexture`, `GpuTexture`) | ~12 | `tex.getTexture() instanceof GlTexture gl ? gl.glId() : 0` | `AbstractTexture.getId()`, `RenderTarget.getColorTextureId()/getDepthTextureId()`. One `TextureIds` helper. |
| **Level render hook** (`LevelRenderContext`/`LevelRenderEvents`) | 7 | Fabric 26.1 event context | Fabric `WorldRenderEvents`/`WorldRenderContext` (same idea, different names and stages) |
| **Camera snapshot** (`CameraRenderState`) | 4 | Camera pos/rot/matrices after extraction | Read from `Camera` + `RenderSystem`/`GameRenderer` matrices at the hook point |
| **Post chain** (`UniformValue`, `PostChainConfig`, `FrameGraphBuilder`, `Std140*`, `ShaderManager`) | 8 (~1.1k lines) | Drives vanilla `PostChain` with UBO uniforms | **Genuine rewrite.** Pre-1.21.2 post chains are JSON program files with `Uniform` setters. Better: move `post/` onto Amnetic's own raw-GL fullscreen pass, which the other passes already use, so it no longer depends on vanilla post at all. |
| **Mesh pipelines** (`RenderPipeline`, `RenderSetup`, `AmneticRenderTypeAccess`) | 2 | Custom `RenderType` from a blaze3d pipeline | `RenderType.create(... CompositeState ...)` with a `ShaderInstance`. `AmneticRenderTypeAccess` (package hack in `net.minecraft`) goes away. |
| **Entity state** (`EntityRenderState`, `SubmitNodeCollector`) | 2 + 3 mixins | Hook render-state extraction / submit | Hook `EntityRenderer.render` / `LivingEntityRenderer.render` directly (no extract step before 1.21.2) |
| **Block models** (`BlockStateModel`, `BlockStateModelPart`, `ChunkSectionLayer`) | 2 | Quads of a block for emissive/cutout shadow | `BakedModel.getQuads(state, dir, rand)` + `ItemBlockRenderTypes` |
| **Scene capture** (`CaptureManager`) | 1 (198 lines) | Re-renders the level from another camera | Hardest single file. `LevelRenderer.renderLevel` signature differs a lot across all three versions. |
| **Surface screen input** (`KeyEvent`, `CharacterEvent`, `MouseButtonEvent`, `GuiGraphicsExtractor`) | 1 | 1.21.9+ input records | Old `keyPressed(int,int,int)` / `charTyped(char,int)` / `GuiGraphics` |

## Public API that leaks version types (fix before porting)

These would break users of the library across versions, so hide them behind Amnetic types:

- `PostEffectConfig.uniformRaw` / `PostEffectHandle.uniformRaw` / all of `UniformSuppliers` return `Supplier<List<UniformValue>>`.
- `Bloom.render(LevelRenderContext)`, `ModelRegistry.flush(LevelRenderContext)`, `FrameContext`.
- `geometry/PosedMesh` takes `EntityRenderState`.

## Mixins

| Mixin | 1.21.1 | 1.20.1 |
|---|---|---|
| `WindowGlContextMixin` | same | same |
| `MinecraftMainTargetMixin`, `SurfaceClickMixin`, `LevelRendererBlockChangeMixin`, `FrustumMixin` | re-check names | re-check names |
| `GameRendererMixin`, `CameraMixin`, `accessor.CameraInvoker`, `ItemInHandRendererMixin`, `SurfaceKeyboardMixin`, `accessor.LevelRendererAccessor` | exists, signatures differ | exists, signatures differ |
| `CameraStateMixin` | target `renderLevel`, no camera state | same |
| `WeatherEffectRendererMixin` | **no class**: hook `LevelRenderer.renderSnowAndRain` | same |
| `EntityRendererMixin` (`extractRenderState`), `EntityRenderStateMixin`, `LivingEntityRendererMixin` (`submit`) | **no targets**: hook `render(...)` | same |
| `GlCommandEncoderMixin`, `accessor.GlGpuBufferAccessor` | **no targets**: delete (no pipeline state to patch) | same |
| `accessor.PostEffectPassAccessor`, `accessor.PostEffectProcessorAccessor`, `accessor.ShaderLoaderAccessor` | delete if `post/` moves to raw GL | same |

## Build / dependency gotchas

- **LWJGL assimp version must match the game's LWJGL.** The build pins `lwjgl-assimp 3.4.1`. 1.21.1 ships LWJGL 3.3.3 and 1.20.1 ships 3.3.1. Bundling a mismatched assimp crashes at native load.
- **Java:** 1.21.1 = Java 21 (fine). 1.20.1 = Java 17: set `compatibilityLevel` in `amnetic.mixins.json`, and 2 files use `getFirst/getLast/reversed`. `instanceof` pattern variables are fine on 17.
- **Mappings:** 26.1 is unobfuscated; old versions need Loom with official mojmap (`mappings loom.officialMojangMappings()`) so names mostly line up.
- **ImGuiMC** (`imguimc-fabric-26.1`, compileOnly): check that builds exist for 1.21.1/1.20.1, or gate the editor UI off.
- **Vertex API:** 1.20.1 uses `vertex().color().endVertex()`, 1.21+ uses `addVertex().setColor()`. This affects `CapturingConsumer` and `shadow/internal/CaptureConsumer`.
- **1.20.1 only:** `DeltaTracker` doesn't exist (pass `float partialTick`). `SectionRenderDispatcher` is `ChunkRenderDispatcher`.

## Suggested order

1. On 26.1, pull the seams above into a `compat` interface layer (texture ids, level hook, camera snapshot, entity hook, block quads, input) and move `post/` to raw GL. No behaviour change, so you can verify it on the current version.
2. Set up Stonecutter with 26.1 as the main version and add 1.21.1. Implement the layer, then re-target the mixins.
3. Add 1.20.1 on top: vertex API, Java 17, `DeltaTracker`, LWJGL pin.

---

## Per-file detail

### REWRITE

| File | Lines | Blocking types |
|---|---:|---|
| `client/AmneticClient.java` | 236 | LevelRenderContext, LevelRenderEvents, ResourceManagerHelper, SimpleSynchronousResourceReloadListener |
| `client/bloom/Bloom.java` | 35 | LevelRenderContext |
| `client/bloom/internal/BloomRenderer.java` | 265 | LevelRenderContext |
| `client/decal/internal/DecalRenderer.java` | 229 | AbstractTexture, GlTexture, SimpleTexture |
| `client/dev/PostShaderReload.java` | 38 | ShaderManager |
| `client/emissive/internal/BlockEmissiveGeometry.java` | 146 | BakedQuad, BlockStateModel, BlockStateModelPart |
| `client/emissive/internal/BlockEmissiveSource.java` | 214 | AbstractTexture, GlTexture |
| `client/entityfx/internal/EntityEffectPipeline.java` | 56 | AmneticRenderTypeAccess, BlendFunction, ColorTargetState, CompareOp, DefaultVertexFormat, DepthStencilState, RenderPipeline, RenderSetup, RenderType, UniformType, VertexFormat |
| `client/framebuffer/internal/GlFramebuffer.java` | 286 | GlTexture, GpuTexture, RenderTarget |
| `client/framebuffer/internal/WrappedGlTexture.java` | 12 | GlTexture, TextureFormat |
| `client/gbuffer/internal/GBufferTargets.java` | 175 | GlTexture, GpuTexture, RenderTarget |
| `client/geometry/PosedMesh.java` | 58 | EntityRenderState, Model |
| `client/grade/internal/ColorGradePass.java` | 94 | AbstractTexture, GlTexture, SimpleTexture |
| `client/instanced/internal/InstanceMeshEntry.java` | 461 | AbstractTexture, GlTexture, SimpleTexture |
| `client/instanced/internal/InstanceMeshRegistry.java` | 242 | CameraRenderState, LevelRenderContext |
| `client/instanced/internal/MainTargetFramebuffer.java` | 71 | GlTexture, GpuTexture, RenderTarget |
| `client/light/internal/DeferredLightingPass.java` | 271 | GlTexture, SimpleTexture |
| `client/model/internal/ModelRegistry.java` | 553 | CameraRenderState, LevelRenderContext |
| `client/particle/SceneDepth.java` | 103 | AbstractTexture, GlTexture, GpuTexture, RenderTarget, TextureTarget |
| `client/pipeline/FrameContext.java` | 32 | LevelRenderContext |
| `client/post/PostEffectConfig.java` | 147 | RenderTarget, UniformValue |
| `client/post/PostEffectHandle.java` | 175 | RenderTarget, UniformValue |
| `client/post/UniformSuppliers.java` | 145 | UniformValue |
| `client/post/internal/PipelineBuilder.java` | 70 | PostChainConfig, UniformValue |
| `client/post/internal/PostEffectEntry.java` | 424 | FrameGraphBuilder, GraphicsResourceAllocator, LevelTargetBundle, PostChain, PostChainConfig, Projection, ProjectionMatrixBuffer, RenderTarget, ResourceHandle, ShaderManager, UniformValue |
| `client/post/internal/PostEffectRegistry.java` | 73 | GraphicsResourceAllocator, RenderTarget |
| `client/post/internal/UniformBufferWriter.java` | 88 | GlBuffer, GpuBuffer, PostChain, PostPass, Std140Builder, Std140SizeCalculator, UniformValue |
| `client/render/CameraSnapshot.java` | 59 | CameraRenderState, RenderSystem |
| `client/render/MeshPipeline.java` | 94 | AmneticRenderTypeAccess, BlendFunction, ColorTargetState, CompareOp, DefaultVertexFormat, DepthStencilState, RenderPipeline, RenderSetup, RenderType, UniformType, VertexFormat |
| `client/scene/internal/CaptureManager.java` | 198 | Camera, CameraRenderState, DeltaTracker, Frustum, GameRenderer, LevelRenderer, RenderTarget, SectionRenderDispatcher, TextureFilteringMethod |
| `client/scene/internal/CaptureTarget.java` | 66 | GlTexture, GpuTexture, RenderTarget, TextureTarget |
| `client/shadow/internal/CutoutGeometry.java` | 108 | BakedQuad, BlockStateModel, BlockStateModelPart, ChunkSectionLayer |
| `client/shadow/internal/EntityModelCapture.java` | 48 | EntityModel, EntityRenderDispatcher, EntityRenderState, EntityRenderer, LivingEntityRenderState, LivingEntityRenderer |
| `client/shadow/internal/ShadowMapPass.java` | 566 | AbstractTexture, GlTexture, GpuTexture |
| `client/surface/internal/SurfaceScreen.java` | 82 | CharacterEvent, GuiGraphicsExtractor, KeyEvent, MouseButtonEvent, Screen |
| `client/ui/TexturePreview.java` | 37 | GpuTextureView, RenderSystem |

### ADAPT

| File | Lines | Blocking types |
|---|---:|---|
| `client/camera/AmneticCamera.java` | 273 | Camera, Frustum |
| `client/camera/internal/Orthographic.java` | 37 | RenderSystem |
| `client/entityfx/EntityEffect.java` | 120 | RenderType |
| `client/framebuffer/internal/RegisteredColorTexture.java` | 30 | AbstractTexture, RenderSystem |
| `client/geometry/internal/CapturingConsumer.java` | 76 | VertexConsumer |
| `client/model/HandModels.java` | 183 | ItemStack |
| `client/model/ModelView.java` | 216 | RenderSystem |
| `client/model/internal/GlReaper.java` | 28 | RenderSystem |
| `client/pipeline/internal/LayerRedirect.java` | 21 | RenderTarget |
| `client/post/PostEffectContext.java` | 41 | PostChain |
| `client/post/internal/DepthSnapshot.java` | 48 | RenderTarget, TextureTarget |
| `client/post/internal/PostRenderDepthSnapshot.java` | 18 | RenderTarget |
| `client/post/internal/WorldDepthSnapshot.java` | 24 | RenderTarget |
| `client/render/ImportedTextures.java` | 80 | DynamicTexture |
| `client/scene/CaptureContext.java` | 32 | Camera |
| `client/scene/internal/CaptureColorTexture.java` | 26 | AbstractTexture, RenderTarget |
| `client/scene/internal/ReflectionMath.java` | 56 | RenderSystem |
| `client/shadow/internal/CaptureConsumer.java` | 36 | VertexConsumer |
| `client/ui/AmneticEditorBridge.java` | 52 | KeyMappingHelper |
| `client/ui/SpawnTarget.java` | 48 | Camera |
| `client/ui/inspector/GBufferInspector.java` | 94 | RenderTarget |

### MIXIN

| File | Lines | Blocking types |
|---|---:|---|
| `mixin/CameraMixin.java` | 119 | Camera, DeltaTracker |
| `mixin/CameraStateMixin.java` | 46 | CameraRenderState, ChunkSectionsToRender, DeltaTracker, GpuBufferSlice, GraphicsResourceAllocator, LevelRenderer |
| `mixin/EntityRenderStateMixin.java` | 60 | EntityRenderState |
| `mixin/EntityRendererMixin.java` | 40 | EntityRenderState, EntityRenderer |
| `mixin/FrustumMixin.java` | 17 | Frustum |
| `mixin/GameRendererMixin.java` | 152 | CameraRenderState, CrossFrameResourcePool, DeltaTracker, GameRenderer, GpuBufferSlice, ProjectionMatrixBuffer |
| `mixin/GlCommandEncoderMixin.java` | 19 | RenderPipeline |
| `mixin/ItemInHandRendererMixin.java` | 25 | ItemInHandRenderer, ItemStack, SubmitNodeCollector |
| `mixin/LevelRendererBlockChangeMixin.java` | 25 | LevelRenderer |
| `mixin/LivingEntityRendererMixin.java` | 72 | CameraRenderState, EntityModel, LivingEntityRenderState, LivingEntityRenderer, RenderType, RenderTypes, SubmitNodeCollector |
| `mixin/MinecraftMainTargetMixin.java` | 28 | RenderTarget |
| `mixin/SurfaceClickMixin.java` | 28 |  |
| `mixin/SurfaceKeyboardMixin.java` | 44 | CharacterEvent, KeyEvent, KeyboardHandler |
| `mixin/WeatherEffectRendererMixin.java` | 19 | WeatherEffectRenderer, WeatherRenderState |
| `mixin/WindowGlContextMixin.java` | 48 |  |
| `mixin/accessor/CameraInvoker.java` | 36 | Camera, Frustum |
| `mixin/accessor/GlGpuBufferAccessor.java` | 12 | GlBuffer |
| `mixin/accessor/LevelRendererAccessor.java` | 32 | Camera, LevelRenderer, SectionRenderDispatcher |
| `mixin/accessor/PostEffectPassAccessor.java` | 15 | GpuBuffer, PostPass |
| `mixin/accessor/PostEffectProcessorAccessor.java` | 15 | PostChain, PostPass |
| `mixin/accessor/ShaderLoaderAccessor.java` | 26 | Projection, ProjectionMatrixBuffer, ShaderManager |

<details><summary>MECHANICAL (145 files)</summary>

- `Amnetic.java`
- `client/AmneticDataGenerator.java`
- `client/anim/Animations.java`
- `client/anim/Interpolators.java`
- `client/camera/CameraDirector.java`
- `client/camera/CameraEffects.java`
- `client/camera/CameraFrame.java`
- `client/camera/Ray.java`
- `client/camera/effect/Kick.java`
- `client/camera/effect/Spring.java`
- `client/camera/internal/CameraController.java`
- `client/camera/internal/Director.java`
- `client/compat/Sodium.java`
- `client/compute/ComputeCapabilities.java`
- `client/compute/ComputeShader.java`
- `client/compute/ComputeTexture.java`
- `client/decal/Decal.java`
- `client/decal/Decals.java`
- `client/dev/ShaderHotReload.java`
- `client/emissive/BlockEmissive.java`
- `client/emissive/EmissiveContext.java`
- `client/emissive/EmissiveSources.java`
- `client/entityfx/EntityEffects.java`
- `client/entityfx/EntityTextureOverride.java`
- `client/entityfx/SurfaceConfig.java`
- `client/entityfx/internal/EffectUniforms.java`
- `client/entityfx/internal/EntityEffectRegistry.java`
- `client/entityfx/internal/SceneColorSnapshot.java`
- `client/entityfx/internal/TextureOverrideRegistry.java`
- `client/entityfx/internal/TextureOverrideTarget.java`
- `client/framebuffer/Framebuffer.java`
- `client/gbuffer/internal/GBufferNormalFill.java`
- `client/geometry/EntityMeshTap.java`
- `client/geometry/internal/MeshTapRegistry.java`
- `client/grade/ColorGradeSettings.java`
- `client/ibl/internal/EnvProbe.java`
- `client/instanced/ExtraSampler.java`
- `client/instanced/InstanceLayout.java`
- `client/instanced/InstanceRenderContext.java`
- `client/instanced/InstancedMesh.java`
- `client/instanced/Instances.java`
- `client/instanced/RenderState.java`
- `client/instanced/internal/CompiledShader.java`
- `client/instanced/internal/GpuCuller.java`
- `client/instanced/internal/InstanceBuffer.java`
- `client/instanced/internal/MinecraftRenderContext.java`
- `client/light/Light.java`
- `client/light/LightSettings.java`
- `client/light/LightStyles.java`
- `client/light/Lights.java`
- `client/light/internal/LightBuffer.java`
- `client/light/internal/LightVolumePass.java`
- `client/light/internal/VolumetricPass.java`
- `client/material/ShadingModel.java`
- `client/material/internal/ShadingModelRegistry.java`
- `client/model/ItemModels.java`
- `client/model/Model.java`
- `client/model/ModelBlockEntity.java`
- `client/model/ModelConfig.java`
- `client/model/ModelFormat.java`
- `client/model/ModelMaterial.java`
- `client/model/Models.java`
- `client/model/SceneView.java`
- `client/model/ViewModels.java`
- `client/model/WorldModels.java`
- `client/model/internal/FlatProgram.java`
- `client/model/internal/GpuModel.java`
- `client/model/internal/ModelIR.java`
- `client/model/internal/ModelShader.java`
- `client/model/internal/ModelShadowProgram.java`
- `client/model/internal/ModelTexture.java`
- `client/model/internal/OffscreenModelRenderer.java`
- `client/model/internal/ammesh/AmmeshCache.java`
- `client/model/internal/ammesh/AmmeshConverter.java`
- `client/model/internal/ammesh/AmmeshReader.java`
- `client/model/internal/ammesh/AmmeshScanner.java`
- `client/model/internal/ammesh/AmmeshWriter.java`
- `client/model/internal/parse/AssimpParser.java`
- `client/model/internal/parse/GltfParser.java`
- `client/particle/BillboardMode.java`
- `client/particle/Colliders.java`
- `client/particle/ParticleContext.java`
- `client/particle/ParticleMaterial.java`
- `client/particle/ParticleSimulation.java`
- `client/particle/editor/EffectDraft.java`
- `client/particle/editor/EffectIO.java`
- `client/pipeline/internal/LayerSystem.java`
- `client/post/PostEffects.java`
- `client/render/Geometry.java`
- `client/render/GlState.java`
- `client/render/ImageOps.java`
- `client/render/OverlayRender.java`
- `client/render/ScreenPass.java`
- `client/render/ShaderProgram.java`
- `client/scene/CameraFeed.java`
- `client/scene/CaptureSurface.java`
- `client/scene/PerspectiveCapture.java`
- `client/scene/PerspectiveView.java`
- `client/scene/PlanarReflection.java`
- `client/shadow/internal/EntityOccluders.java`
- `client/shadow/internal/OccluderCache.java`
- `client/shadow/internal/OccluderCollector.java`
- `client/shadow/internal/OccluderEntry.java`
- `client/shadow/internal/OccluderMesh.java`
- `client/shadow/internal/PointShadowArray.java`
- `client/shadow/internal/ShadowDepthProgram.java`
- `client/shadow/internal/SpotShadowAtlas.java`
- `client/shadow/internal/SunShadowCascades.java`
- `client/ssao/internal/SsaoPass.java`
- `client/ssgi/internal/SsgiPass.java`
- `client/ssr/internal/SsrPass.java`
- `client/subsurface/Subsurface.java`
- `client/subsurface/internal/SubsurfacePass.java`
- `client/surface/ScreenSurface.java`
- `client/surface/Surfaces.java`
- `client/surface/draw/UiDraw.java`
- `client/surface/internal/SurfaceRenderer.java`
- `client/surface/internal/UiBatcher.java`
- `client/surface/internal/WorldSurfaceRenderer.java`
- `client/surface/material/SurfaceMaterial.java`
- `client/surface/text/Fonts.java`
- `client/surface/text/SdfFont.java`
- `client/surface/widget/Button.java`
- `client/surface/widget/Checkbox.java`
- `client/surface/widget/ContextMenu.java`
- `client/surface/widget/Dropdown.java`
- `client/surface/widget/KeybindField.java`
- `client/surface/widget/ProgressBar.java`
- `client/surface/widget/RadioButton.java`
- `client/surface/widget/Stepper.java`
- `client/surface/widget/Tabs.java`
- `client/surface/widget/Text.java`
- `client/surface/widget/TextArea.java`
- `client/surface/widget/TextField.java`
- `client/surface/widget/TreeView.java`
- `client/taa/Taa.java`
- `client/taa/internal/CasPass.java`
- `client/taa/internal/TaaPass.java`
- `client/ui/DecalGizmo.java`
- `client/ui/inspector/DecalsInspector.java`
- `client/ui/inspector/LightsInspector.java`
- `client/ui/inspector/ParticleEditor.java`
- `client/ui/inspector/ParticlesInspector.java`
- `client/ui/scene/DecalSelectable.java`
- `client/ui/widget/TexturePicker.java`

</details>

<details><summary>PORTABLE (151 files)</summary>

- `client/Quality.java`
- `client/anim/Easing.java`
- `client/anim/EasingFunction.java`
- `client/anim/Interpolator.java`
- `client/anim/Timeline.java`
- `client/anim/Tween.java`
- `client/anim/internal/TweenRegistry.java`
- `client/anim/internal/Updatable.java`
- `client/bloom/BloomSettings.java`
- `client/camera/CameraModifier.java`
- `client/camera/effect/Shake.java`
- `client/camera/internal/FrameView.java`
- `client/compute/ShaderStorageBuffer.java`
- `client/entityfx/internal/SurfaceTarget.java`
- `client/framebuffer/ColorFormat.java`
- `client/framebuffer/DepthMode.java`
- `client/framebuffer/FramebufferException.java`
- `client/framebuffer/FramebufferSpec.java`
- `client/framebuffer/Framebuffers.java`
- `client/framebuffer/PingPongBuffer.java`
- `client/framebuffer/internal/Attachment.java`
- `client/framebuffer/internal/FramebufferRegistry.java`
- `client/gbuffer/GBuffer.java`
- `client/geometry/internal/MeshTapTarget.java`
- `client/grade/ColorGrade.java`
- `client/instanced/BuiltinShader.java`
- `client/instanced/InstanceBatch.java`
- `client/instanced/InstancePacker.java`
- `client/instanced/InstancePhase.java`
- `client/instanced/InstanceWriter.java`
- `client/instanced/MeshData.java`
- `client/instanced/internal/CullTargets.java`
- `client/light/FalloffCurve.java`
- `client/light/LightType.java`
- `client/light/internal/LightRegistry.java`
- `client/material/internal/MaterialParams.java`
- `client/model/Animator.java`
- `client/model/Meshes.java`
- `client/model/ModelInstance.java`
- `client/model/ModelLighting.java`
- `client/model/ModelLoadException.java`
- `client/model/TextureFilter.java`
- `client/model/internal/GlUploadQueue.java`
- `client/model/internal/MeshSimplifier.java`
- `client/model/internal/ModelLod.java`
- `client/model/internal/ammesh/AmmeshFormat.java`
- `client/model/internal/parse/BbmodelParser.java`
- `client/model/internal/parse/CoplanarSeparation.java`
- `client/model/internal/parse/GltfAccessorReaders.java`
- `client/particle/Affector.java`
- `client/particle/Affectors.java`
- `client/particle/Collider.java`
- `client/particle/CollisionCallback.java`
- `client/particle/CollisionResponse.java`
- `client/particle/Particle.java`
- `client/particle/ParticleLayout.java`
- `client/particle/ParticleOverrides.java`
- `client/particle/Particles.java`
- `client/particle/Shapes.java`
- `client/particle/SpawnShape.java`
- `client/particle/track/Curve.java`
- `client/particle/track/Gradient.java`
- `client/particle/track/Track.java`
- `client/pipeline/Layer.java`
- `client/pipeline/LayerPass.java`
- `client/pipeline/PassHandle.java`
- `client/pipeline/PassProfiler.java`
- `client/pipeline/Pipeline.java`
- `client/pipeline/RenderLayer.java`
- `client/pipeline/RenderPass.java`
- `client/pipeline/RenderStage.java`
- `client/pipeline/internal/GpuTimer.java`
- `client/post/RenderPhase.java`
- `client/post/internal/CameraState.java`
- `client/render/AfterWorldRender.java`
- `client/render/AmneticResources.java`
- `client/render/CullFrustum.java`
- `client/render/FailureGuard.java`
- `client/render/NativeFileDialog.java`
- `client/render/RenderThread.java`
- `client/scene/CaptureResult.java`
- `client/shadow/ShadowSettings.java`
- `client/shadow/Shadows.java`
- `client/shadow/internal/OccluderMeshCache.java`
- `client/ssao/Ssao.java`
- `client/ssao/SsaoSettings.java`
- `client/ssgi/Ssgi.java`
- `client/ssgi/SsgiSettings.java`
- `client/ssr/Ssr.java`
- `client/ssr/SsrSettings.java`
- `client/subsurface/SubsurfaceSettings.java`
- `client/surface/Anchor.java`
- `client/surface/Fx.java`
- `client/surface/HudSurface.java`
- `client/surface/WorldSurface.java`
- `client/surface/internal/InputRouter.java`
- `client/surface/material/MaterialTextures.java`
- `client/surface/reactive/Computed.java`
- `client/surface/reactive/Effect.java`
- `client/surface/reactive/Motion.java`
- `client/surface/reactive/Reactive.java`
- `client/surface/reactive/Signal.java`
- `client/surface/reactive/internal/Tracking.java`
- `client/surface/text/GlyphFx.java`
- `client/surface/text/GlyphPose.java`
- `client/surface/widget/ColorPicker.java`
- `client/surface/widget/Column.java`
- `client/surface/widget/Dialog.java`
- `client/surface/widget/Grid.java`
- `client/surface/widget/Image.java`
- `client/surface/widget/ListView.java`
- `client/surface/widget/ModelViewport.java`
- `client/surface/widget/Panel.java`
- `client/surface/widget/RadioGroup.java`
- `client/surface/widget/Row.java`
- `client/surface/widget/Scroll.java`
- `client/surface/widget/Slider.java`
- `client/surface/widget/Spinner.java`
- `client/surface/widget/SplitPane.java`
- `client/surface/widget/Stack.java`
- `client/surface/widget/Toasts.java`
- `client/surface/widget/Toggle.java`
- `client/surface/widget/Tooltip.java`
- `client/surface/widget/Viewport.java`
- `client/surface/widget/Widget.java`
- `client/taa/TaaSettings.java`
- `client/taa/internal/TaaJitter.java`
- `client/ui/AmneticEditor.java`
- `client/ui/EditorTheme.java`
- `client/ui/GizmoLayer.java`
- `client/ui/Gizmos.java`
- `client/ui/Inspector.java`
- `client/ui/LightGizmo.java`
- `client/ui/inspector/DemoInspector.java`
- `client/ui/inspector/DeviceInfoInspector.java`
- `client/ui/inspector/PostFxInspector.java`
- `client/ui/inspector/ProfilerInspector.java`
- `client/ui/inspector/ShadowsInspector.java`
- `client/ui/inspector/SsaoInspector.java`
- `client/ui/inspector/SsgiInspector.java`
- `client/ui/inspector/StatsInspector.java`
- `client/ui/inspector/TaaInspector.java`
- `client/ui/scene/EditorSelection.java`
- `client/ui/scene/LightSelectable.java`
- `client/ui/scene/Selectable.java`
- `client/ui/scene/SelectableRegistry.java`
- `client/ui/scene/Transformable.java`
- `client/ui/widget/CurveEditor.java`
- `client/ui/widget/GradientEditor.java`
- `client/ui/widget/Widgets.java`
- `net/minecraft/client/renderer/rendertype/AmneticRenderTypeAccess.java`

</details>
