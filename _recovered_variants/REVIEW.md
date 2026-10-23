# Review of recovered variants (Amnetic)

All variants are an older Minecraft-API snapshot (ResourceLocation, GuiGraphics, PoseStack) of classes the repo has since ported and extended. None is newer; nothing merged. Many .vhash alternates follow the same pattern.

| verdict | file | reason |
|---|---|---|
| OLDER | com/meekdev/amnetic/client/AmneticClient.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 16 lines the variant lacks, variant has 7 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/bloom/internal/BloomRenderer.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 6 lines the variant lacks, variant has 6 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/compat/BlockQuads.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 7 lines the variant lacks, variant has 8 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/decal/internal/DecalRenderer.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 5 lines the variant lacks, variant has 9 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/emissive/BlockEmissive.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 1 lines the variant lacks, variant has 1 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/emissive/internal/BlockEmissiveGeometry.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 3 lines the variant lacks, variant has 2 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/emissive/internal/BlockEmissiveSource.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 4 lines the variant lacks, variant has 4 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/framebuffer/internal/RegisteredColorTexture.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 4 lines the variant lacks, variant has 5 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/gbuffer/internal/GBufferNormalFill.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 3 lines the variant lacks, variant has 7 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/gbuffer/internal/GBufferTargets.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 52 lines the variant lacks, variant has 8 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/geometry/PosedMesh.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 5 lines the variant lacks, variant has 2 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/geometry/internal/CapturingConsumer.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 9 lines the variant lacks, variant has 10 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/grade/internal/ColorGradePass.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 2 lines the variant lacks, variant has 2 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/instanced/ExtraSampler.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 17 lines the variant lacks, variant has 5 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/instanced/InstancedMesh.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 23 lines the variant lacks, variant has 0 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/instanced/internal/InstanceMeshEntry.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 32 lines the variant lacks, variant has 6 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/instanced/internal/InstanceMeshRegistry.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 7 lines the variant lacks, variant has 2 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/light/LightStyles.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 1 lines the variant lacks, variant has 1 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/light/internal/DeferredLightingPass.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 19 lines the variant lacks, variant has 5 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/light/internal/LightVolumePass.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 3 lines the variant lacks, variant has 3 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/light/internal/VolumetricPass.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 4 lines the variant lacks, variant has 4 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/material/ShadingModel.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 22 lines the variant lacks, variant has 0 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/material/internal/ShadingModelRegistry.vc9c8986.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 3 lines the variant lacks, variant has 3 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/model/MaterialLook.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 8 lines the variant lacks, variant has 2 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/model/internal/FlatProgram.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 4 lines the variant lacks, variant has 4 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/model/internal/FlatProgram.v4c9f4cb.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 2 lines the variant lacks, variant has 2 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/model/internal/GpuModel.java | repo adds motion-vector history (lastPose/lastWorld), dithered materials, 144-byte instances; variant is the older 80-byte layout |
| OLDER | com/meekdev/amnetic/client/model/internal/ModelRegistry.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 43 lines the variant lacks, variant has 3 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/model/internal/ModelShader.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 38 lines the variant lacks, variant has 5 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/model/internal/ModelShader.vcf46f9d.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 35 lines the variant lacks, variant has 2 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/model/internal/ModelShadowProgram.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 2 lines the variant lacks, variant has 2 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/model/internal/ModelShadowProgram.v7ef9a34.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 4 lines the variant lacks, variant has 4 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/model/internal/OffscreenModelRenderer.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 5 lines the variant lacks, variant has 1 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/model/internal/ammesh/AmmeshReader.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 1 lines the variant lacks, variant has 1 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/particle/BillboardMode.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 3 lines the variant lacks, variant has 3 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/particle/Colliders.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 1 lines the variant lacks, variant has 1 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/particle/ParticleSimulation.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 1 lines the variant lacks, variant has 1 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/particle/SceneDepth.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 3 lines the variant lacks, variant has 6 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/particle/editor/EffectDraft.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 3 lines the variant lacks, variant has 3 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/pipeline/Pipeline.java | repo has PassProfiler-based cpu/gpu profiling; variant older profiling path |
| OLDER | com/meekdev/amnetic/client/post/internal/PostEffectEntry.v703c068.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 2 lines the variant lacks, variant has 2 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/post/internal/PostPipeline.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 1 lines the variant lacks, variant has 1 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/post/internal/PostPipeline.vf76fcc8.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 9 lines the variant lacks, variant has 8 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/post/internal/PostProgram.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 8 lines the variant lacks, variant has 8 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/post/internal/WorldDepthSnapshot.v114335b.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 1 lines the variant lacks, variant has 1 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/render/CameraSnapshot.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 3 lines the variant lacks, variant has 1 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/render/Geometry.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 4 lines the variant lacks, variant has 4 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/render/GlState.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 16 lines the variant lacks, variant has 1 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/render/ImageOps.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 3 lines the variant lacks, variant has 3 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/render/ImportedTextures.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 2 lines the variant lacks, variant has 2 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/render/LevelCamera.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 4 lines the variant lacks, variant has 6 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/render/MeshPipeline.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 7 lines the variant lacks, variant has 7 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/render/MeshPipeline.v190ec2b.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 8 lines the variant lacks, variant has 8 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/render/ShaderProgram.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 19 lines the variant lacks, variant has 3 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/scene/CameraFeed.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 1 lines the variant lacks, variant has 1 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/scene/CaptureSurface.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 1 lines the variant lacks, variant has 1 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/scene/PlanarReflection.v902a0e3.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 1 lines the variant lacks, variant has 1 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/scene/internal/CaptureColorTexture.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 2 lines the variant lacks, variant has 5 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/scene/internal/CaptureManager.java | repo has ~126 extra lines |
| OLDER | com/meekdev/amnetic/client/shadow/internal/CaptureConsumer.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 11 lines the variant lacks, variant has 12 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/shadow/internal/EntityModelCapture.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 8 lines the variant lacks, variant has 17 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/shadow/internal/PointShadowArray.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 1 lines the variant lacks, variant has 1 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/shadow/internal/ShadowDepthProgram.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 2 lines the variant lacks, variant has 2 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/shadow/internal/ShadowMapPass.java | repo has cascade staggering/caching and SunOccluders; variant uses older single-occluder path |
| OLDER | com/meekdev/amnetic/client/shadow/internal/SpotShadowAtlas.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 1 lines the variant lacks, variant has 1 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/shadow/internal/SunShadowCascades.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 5 lines the variant lacks, variant has 4 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/ssao/internal/SsaoPass.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 115 lines the variant lacks, variant has 74 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/ssgi/internal/SsgiPass.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 5 lines the variant lacks, variant has 5 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/ssr/internal/SsrPass.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 5 lines the variant lacks, variant has 5 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/subsurface/Subsurface.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 1 lines the variant lacks, variant has 1 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/subsurface/internal/SubsurfacePass.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 10 lines the variant lacks, variant has 5 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/surface/internal/SurfaceRenderer.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 2 lines the variant lacks, variant has 2 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/surface/internal/SurfaceScreen.java | variant uses older GuiGraphics/mouse API; repo uses GuiGraphicsExtractor/MouseButtonEvent |
| OLDER | com/meekdev/amnetic/client/surface/internal/UiBatcher.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 3 lines the variant lacks, variant has 3 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/surface/internal/WorldSurfaceRenderer.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 3 lines the variant lacks, variant has 3 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/surface/material/SurfaceMaterial.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 2 lines the variant lacks, variant has 2 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/client/taa/TaaSettings.java | variant is a markdown implementation plan, not Java; repo has the real class |
| OLDER | com/meekdev/amnetic/client/ui/TexturePreview.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 6 lines the variant lacks, variant has 2 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/mixin/CameraMixin.java | variant hooks older Camera.setup; repo hooks update()/culling projection + orthographic |
| OLDER | com/meekdev/amnetic/mixin/CameraStateMixin.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 19 lines the variant lacks, variant has 22 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/mixin/GameRendererMixin.java | variant targets older GameRenderer API (getFov/PoseStack); repo reworked for CameraRenderState |
| OLDER | com/meekdev/amnetic/mixin/ItemInHandRendererMixin.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 3 lines the variant lacks, variant has 3 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/mixin/LivingEntityRendererMixin.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 41 lines the variant lacks, variant has 26 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/mixin/LivingEntityRendererMixin.v77fa599.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 41 lines the variant lacks, variant has 26 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/mixin/SurfaceKeyboardMixin.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 7 lines the variant lacks, variant has 2 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/mixin/WeatherEffectRendererMixin.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 2 lines the variant lacks, variant has 3 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/mixin/WindowGlContextMixin.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 12 lines the variant lacks, variant has 8 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/mixin/accessor/CameraInvoker.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 9 lines the variant lacks, variant has 0 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/mixin/accessor/LevelRendererAccessor.java | older-MC-API snapshot (ResourceLocation/older vanilla signatures); repo has 5 lines the variant lacks, variant has 0 lines that are old-API equivalents |
| OLDER | com/meekdev/amnetic/mixin/accessor/LightTextureAccessor.java | variant accessor targets vanilla LightTexture that no longer exists in the repo MC version |
| OLDER | different/com/meekdev/amnetic/mixin/sodium/SodiumMixinPlugin.java | no counterpart; no sodium mixin config or mixin package in repo references it; obsolete |
| OLDER | new/com/meekdev/amnetic/bench/AutoRun.java | dev benchmark harness: no class in repo references it, not registered in fabric.mod.json; Scenes.java uses removed ResourceLocation (could be revived by hand) |
| OLDER | new/com/meekdev/amnetic/bench/BenchClient.java | dev benchmark harness: no class in repo references it, not registered in fabric.mod.json; Scenes.java uses removed ResourceLocation (could be revived by hand) |
| OLDER | new/com/meekdev/amnetic/bench/BenchRunner.java | dev benchmark harness: no class in repo references it, not registered in fabric.mod.json; Scenes.java uses removed ResourceLocation (could be revived by hand) |
| OLDER | new/com/meekdev/amnetic/bench/BenchScene.java | dev benchmark harness: no class in repo references it, not registered in fabric.mod.json; Scenes.java uses removed ResourceLocation (could be revived by hand) |
| OLDER | new/com/meekdev/amnetic/bench/GlDebug.java | dev benchmark harness: no class in repo references it, not registered in fabric.mod.json; Scenes.java uses removed ResourceLocation (could be revived by hand) |
| OLDER | new/com/meekdev/amnetic/bench/LogCapture.java | dev benchmark harness: no class in repo references it, not registered in fabric.mod.json; Scenes.java uses removed ResourceLocation (could be revived by hand) |
| OLDER | new/com/meekdev/amnetic/bench/Scenes.java | dev benchmark harness: no class in repo references it, not registered in fabric.mod.json; Scenes.java uses removed ResourceLocation (could be revived by hand) |
| OLDER | new/com/meekdev/amnetic/bench/TextureDump.java | dev benchmark harness: no class in repo references it, not registered in fabric.mod.json; Scenes.java uses removed ResourceLocation (could be revived by hand) |
| OLDER | new/com/meekdev/amnetic/client/surface/reactive/SignalTest.java | markdown implementation plan, not Java; reactive Signal/Computed/Effect/Motion already exist in repo; repo has no src/test |
| SAME | com/meekdev/amnetic/client/compute/ComputeShader.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/decal/Decal.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/decal/Decals.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/emissive/EmissiveSources.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/grade/ColorGradeSettings.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/light/LightSettings.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/material/ShadingModel.v823e67e.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/material/internal/ShadingModelRegistry.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/model/ItemModels.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/model/ModelConfig.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/model/ModelFormat.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/model/Models.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/model/ViewModels.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/model/internal/ModelIR.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/model/internal/ammesh/AmmeshCache.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/model/internal/ammesh/AmmeshConverter.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/model/internal/ammesh/AmmeshReader.v732ce6e.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/model/internal/ammesh/AmmeshScanner.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/model/internal/ammesh/AmmeshWriter.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/particle/ParticleMaterial.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/post/PostEffectConfig.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/post/PostEffectHandle.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/post/PostEffects.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/post/internal/PostEffectEntry.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/post/internal/PostEffectRegistry.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/post/internal/WorldDepthSnapshot.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/scene/PerspectiveCapture.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/scene/PlanarReflection.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/scene/internal/CaptureTarget.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/surface/Surfaces.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/surface/draw/UiDraw.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/surface/text/Fonts.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/surface/text/SdfFont.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/surface/widget/Button.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/surface/widget/Checkbox.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/surface/widget/ContextMenu.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/surface/widget/Dropdown.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/surface/widget/KeybindField.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/surface/widget/ProgressBar.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/surface/widget/RadioButton.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/surface/widget/Stepper.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/surface/widget/Tabs.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/surface/widget/Text.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/surface/widget/TextArea.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/client/surface/widget/TreeView.java | identical to repo modulo comments/whitespace |
| SAME | com/meekdev/amnetic/mixin/EntityRenderStateMixin.java | identical to repo modulo comments/whitespace |
