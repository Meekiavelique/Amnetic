package com.meekdev.amnetic.bench;

import com.meekdev.amnetic.client.bloom.Bloom;
import com.meekdev.amnetic.client.decal.Decal;
import com.meekdev.amnetic.client.decal.Decals;
import com.meekdev.amnetic.client.emissive.BlockEmissive;
import com.meekdev.amnetic.client.grade.ColorGrade;
import com.meekdev.amnetic.client.instanced.BuiltinShader;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.LightSettings;
import com.meekdev.amnetic.client.light.Lights;
import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.ModelMaterial;
import com.meekdev.amnetic.client.model.Models;
import com.meekdev.amnetic.client.particle.ParticleMaterial;
import com.meekdev.amnetic.client.particle.ParticleSimulation;
import com.meekdev.amnetic.client.particle.Particles;
import com.meekdev.amnetic.client.particle.Shapes;
import com.meekdev.amnetic.client.post.PostEffectHandle;
import com.meekdev.amnetic.client.post.PostEffects;
import com.meekdev.amnetic.client.shadow.ShadowSettings;
import com.meekdev.amnetic.client.shadow.Shadows;
import com.meekdev.amnetic.client.ssao.Ssao;
import com.meekdev.amnetic.client.ssgi.Ssgi;
import com.meekdev.amnetic.client.ssr.Ssr;
import com.meekdev.amnetic.client.surface.HudSurface;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.taa.Taa;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class Scenes {

    private static final Map<String, BenchScene> SCENES = new LinkedHashMap<>();
    private static final List<Light> LIGHTS = new ArrayList<>();
    private static final List<Decal> DECALS = new ArrayList<>();
    private static final List<String> FAILED = new ArrayList<>();

    private static boolean emissiveCubes;
    private static boolean plainCubes;
    private static int cubeCount;
    private static boolean modelsActive;
    private static boolean modelsEmissive;
    private static Boolean appliedEmissive;
    private static int modelGrid;
    private static boolean particles;
    private static Vec3 center = Vec3.ZERO;
    private static Model model;
    private static ParticleMaterial sparks;
    private static PostEffectHandle post;
    private static HudSurface hud;

    private Scenes() {}

    public static Map<String, BenchScene> all() {
        if (SCENES.isEmpty()) register();
        return SCENES;
    }

    public static void resetAll() {
        Bloom.disable();
        BlockEmissive.disable();
        Shadows.disable();
        Ssao.disable();
        Ssr.disable();
        Ssgi.disable();
        Taa.disable();
        ColorGrade.disable();
        LightSettings.defaults().volumetric(false).lightVolumes(true);
        for (Light l : LIGHTS) l.remove();
        LIGHTS.clear();
        for (Decal d : DECALS) d.remove();
        DECALS.clear();
        emissiveCubes = false;
        plainCubes = false;
        modelsActive = false;
        particles = false;
        ParticleSimulation.INSTANCE.clear();
        if (post != null) { post.unregister(); post = null; }
        if (hud != null) { hud.remove(); hud = null; }
        BenchArena.time(6000);
    }

    private static void register() {
        add("baseline", "every Amnetic feature off", c -> {});
        add("bloom_blocks", "bloom + emissive blocks (arena lamps)", c -> {
            Bloom.enable();
            BlockEmissive.enable();
        });
        add("bloom_instanced", "bloom + 4096 emissive instanced cubes", c -> {
            Bloom.enable();
            cubes(64, true);
        });
        add("bloom_models", "bloom + 256 emissive models", c -> {
            Bloom.enable();
            models(16, true);
        });
        add("lights_64", "64 point lights, light volumes, night", c -> {
            BenchArena.time(18000);
            pointLights(c, 64);
        });
        add("lights_64_fullscreen", "64 point lights, fullscreen loop (volumes off)", c -> {
            BenchArena.time(18000);
            LightSettings.defaults().lightVolumes(false);
            pointLights(c, 64);
        });
        add("shadows", "8 spot + 4 point shadow casters, entities in range", c -> {
            BenchArena.time(18000);
            Shadows.enable();
            ShadowSettings.defaults().entityShadows(true);
            shadowLights(c);
        });
        add("ssao", "screen-space ambient occlusion", c -> Ssao.enable());
        add("ssr", "screen-space reflections over the pool", c -> Ssr.enable());
        add("ssgi", "screen-space global illumination", c -> Ssgi.enable());
        add("taa", "temporal anti-aliasing + sharpen", c -> Taa.enable());
        add("color_grade", "color grading", c -> {
            ColorGrade.enable();
            ColorGrade.settings().contrast(1.1f).saturation(1.15f).temperature(0.1f);
        });
        add("volumetric", "volumetric light with god rays", c -> {
            BenchArena.time(18000);
            LightSettings.defaults().volumetric(true);
            LIGHTS.add(Lights.spot(c.add(0, 8, 0), new Vector3f(0, -1, 0), 25f, 40f, 1f, 0.9f, 0.7f, 20f, 3f)
                    .castsShadow(false).godray(1.2f));
        });
        add("decals", "96 decals", c -> decals(c, 96));
        add("particles", "additive emissive particles, 20k cap", c -> {
            Bloom.enable();
            particles = true;
        });
        add("post_effect", "raw-GL post effect (two passes, custom blocks)", c -> {
            post = PostEffects.register(new ResourceLocation("amneticbench", "wave"));
            post.enable();
        });
        add("surfaces", "HUD surface with 200 rounded rects and text", c -> hudSurface());
        add("instanced_stress", "16384 plain instanced cubes", c -> cubes(128, false));
        add("models_stress", "1024 models", c -> models(32, false));
        add("everything", "bloom, blocks, lights, shadows, ssao, ssr, taa, grade, particles, cubes, models, post", c -> {
            Bloom.enable();
            BlockEmissive.enable();
            Shadows.enable();
            Ssao.enable();
            Ssr.enable();
            Taa.enable();
            ColorGrade.enable();
            pointLights(c, 32);
            shadowLights(c);
            cubes(32, true);
            models(8, true);
            decals(c, 32);
            particles = true;
            post = PostEffects.register(new ResourceLocation("amneticbench", "wave"));
            post.enable();
        });
        add("baseline_end", "every Amnetic feature off, rerun once everything is warm", c -> {});
    }

    private static void add(String name, String description, Consumer<Vec3> start) {
        SCENES.put(name, new BenchScene(name, description, c -> {
            center = c;
            start.accept(c);
        }, Scenes::resetAll));
    }

    public static void init() {
        guard("models", () -> {
            model = Models.load(new ResourceLocation("amnetic", "models/pillow.glb"));
            Models.onFrame(Scenes::renderModels);
        });
        guard("instanced cubes", () -> {
            registerCubes("cubes", true);
            registerCubes("plain_cubes", false);
        });
        guard("particles", () -> sparks = Particles.material()
                .shader(new ResourceLocation("amnetic", "particle/default_soft"))
                .blend(ParticleMaterial.Blend.ADDITIVE)
                .emissive(1.5f)
                .life(1.5f)
                .size(0.25f, 0f)
                .color(1f, 0.7f, 0.2f, 1f, 0.1f, 0f)
                .gravity(-3f)
                .liveCap(20000)
                .register());
    }

    private static void guard(String what, Runnable init) {
        try {
            init.run();
        } catch (RuntimeException e) {
            FAILED.add(what + ": " + e.getMessage());
        }
    }

    public static List<String> failures() {
        return FAILED;
    }

    private static void renderModels(com.meekdev.amnetic.client.instanced.InstanceRenderContext ctx) {
        if (!modelsActive || model == null || !model.isReady()) return;
        if (appliedEmissive == null || appliedEmissive != modelsEmissive) {
            for (ModelMaterial m : model.materials()) {
                if (modelsEmissive) m.setEmissive(1f, 0.45f, 0.15f);
                else m.setEmissive(0f, 0f, 0f);
            }
            appliedEmissive = modelsEmissive;
        }
        int half = modelGrid / 2;
        for (int x = 0; x < modelGrid; x++) {
            for (int z = 0; z < modelGrid; z++) {
                Vec3 p = center.add((x - half) * 2.5, 2, (z - half) * 2.5);
                model.render(p, (x * 37 + z * 11) % 360, 0.8f);
            }
        }
    }

    public static void frame() {
        if (particles && sparks != null) {
            for (int i = 0; i < 4; i++) {
                double a = Math.random() * Math.PI * 2;
                Particles.burst(sparks, center.x + Math.cos(a) * 10, center.y + 1, center.z + Math.sin(a) * 10,
                        60, Shapes.sphereVolume(0.4f), 1f, 5f);
            }
        }
    }

    private static void cubes(int side, boolean emissive) {
        cubeCount = side;
        if (emissive) emissiveCubes = true; else plainCubes = true;
    }

    private static void models(int side, boolean emissive) {
        modelGrid = side;
        modelsActive = true;
        modelsEmissive = emissive;
    }

    private static void registerCubes(String id, boolean emissive) {
        InstancedMesh.Builder<BuiltinShader.TransformColor> b = InstancedMesh.builder(BuiltinShader.TRANSFORM_COLOR)
                .geometry(MeshData.unitCube())
                .phase(InstancePhase.WORLD_LAST);
        if (emissive) b.emissive(2f);
        b.onRender((ctx, batch) -> {
            if (emissive ? !emissiveCubes : !plainCubes) return;
            int half = cubeCount / 2;
            for (int x = 0; x < cubeCount; x++) {
                for (int z = 0; z < cubeCount; z++) {
                    double wx = center.x + (x - half) * 0.7;
                    double wz = center.z + (z - half) * 0.7;
                    double wy = center.y + 6 + Math.sin((x + z) * 0.3) * 0.5;
                    float hue = (x * 7 + z * 3) % 16 / 16f;
                    batch.add(new BuiltinShader.TransformColor(ctx.worldToModel(wx, wy, wz).scale(0.3f),
                            new Vector4f(1f, hue, 1f - hue, 1f)));
                }
            }
        }).register(new ResourceLocation("amneticbench", id));
    }

    private static void pointLights(Vec3 c, int count) {
        for (int i = 0; i < count; i++) {
            double a = i / (double) count * Math.PI * 2;
            double r = 6 + (i % 4) * 4;
            float hue = (i % 6) / 6f;
            LIGHTS.add(Lights.point(c.add(Math.cos(a) * r, 1.5 + (i % 3), Math.sin(a) * r),
                    1f, 0.4f + hue * 0.6f, 1f - hue, 8f, 2f));
        }
    }

    private static void shadowLights(Vec3 c) {
        for (int i = 0; i < 8; i++) {
            double a = i / 8.0 * Math.PI * 2;
            Vec3 p = c.add(Math.cos(a) * 14, 7, Math.sin(a) * 14);
            Vector3f dir = new Vector3f((float) (c.x - p.x), -6f, (float) (c.z - p.z)).normalize();
            LIGHTS.add(Lights.spot(p, dir, 20f, 35f, 1f, 0.95f, 0.85f, 24f, 4f).castsShadow(true));
        }
        for (int i = 0; i < 4; i++) {
            double a = i / 4.0 * Math.PI * 2 + 0.4;
            LIGHTS.add(Lights.point(c.add(Math.cos(a) * 9, 2.5, Math.sin(a) * 9), 0.6f, 0.8f, 1f, 12f, 3f)
                    .castsShadow(true));
        }
    }

    private static void decals(Vec3 c, int count) {
        ResourceLocation scorch = new ResourceLocation("amnetic", "textures/decal/scorch.png");
        for (int i = 0; i < count; i++) {
            double a = i * 2.39996;
            double r = 2 + Math.sqrt(i) * 2;
            DECALS.add(Decals.box(scorch, new Vec3(c.x + Math.cos(a) * r, c.y - 1, c.z + Math.sin(a) * r),
                    new Vector3f(0, 1, 0), 1.5f, 1.5f, 1f));
        }
    }

    private static void hudSurface() {
        hud = Surfaces.hud().onDraw(d -> {
            for (int i = 0; i < 200; i++) {
                float x = 10 + (i % 20) * 22;
                float y = 40 + (i / 20) * 22;
                d.roundedRect(x, y, 18, 18, 4, 0x80000000 | (int) ((i * 2654435761L) & 0xFFFFFF));
            }
            d.text("Amnetic bench surface", 12, 12, 16, 0xFFFFFFFF);
        });
    }
}
