package com.meekdev.amnetic.client.particle;

import com.meekdev.amnetic.client.anim.Easing;
import com.meekdev.amnetic.client.instanced.InstanceBatch;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public final class ParticleSimulation {

    public static final float STEP = 1f / 60f;

    private static final float MAX_FRAME_DT = 0.1f;
    private static final int MAX_PENDING = 200_000;
    private static final int TRAIL_MAX = 32; // ring-buffer capacity for per-particle trail history

    public static final ParticleSimulation INSTANCE = new ParticleSimulation();

    private final CopyOnWriteArrayList<ParticleMaterial> materials = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<SpawnRequest> intake = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<SpawnRequest> requestPool = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingCount = new AtomicInteger();

    private final ArrayDeque<Particle> pool = new ArrayDeque<>();
    private final ParticleContext ctx = new ParticleContext();
    private final Vector4f clip = new Vector4f();
    private Particle[] visibleBuf = new Particle[256];
    private long[] sortBuf = new long[256];
    private final float[] rgbaScratch = new float[4]; // gradient eval scratch (render thread only)
    private final Particle trailGhost = new Particle(); // reused for every trail ghost instance (written immediately)
    private float accumulator;
    private float simClock;
    private long lastNano;
    private int stepCount;
    private boolean prePhaseRegistered;
    private int meshCounter;

    private ParticleSimulation() {}


    synchronized void register(ParticleMaterial material) {
        materials.add(material);
        Identifier meshId = Identifier.fromNamespaceAndPath("amnetic", "particle_material_" + (meshCounter++));
        material.meshId = meshId;
        InstancedMesh.Builder<Particle> b = InstancedMesh.builder(ParticleLayout.LAYOUT, ParticleLayout.WRITER)
                .geometry(MeshData.texturedQuad())
                .shaders(material.billboardMode.vertexShader(), material.fragmentShaderId)
                .renderState(material.renderState())
                .phase(material.overlay ? InstancePhase.OVERLAY : InstancePhase.WORLD_TRANSLUCENT)
                .onRender((rctx, batch) -> packMaterial(material, rctx, batch));
        if (material.emissive) b.emissive(material.emissiveStrength);
        if (material.textureId != null) b.texture(material.textureId);
        if (material.texture2Id != null) b.extraSampler("Sampler1", material.texture2Id, 2, true);
        if (material.softDepth) b.extraSampler("DepthSampler", SceneDepth.ID, 1);
        b.register(meshId);

        if (!prePhaseRegistered) {
            InstanceMeshRegistry.INSTANCE.addPrePhaseCallback(InstancePhase.WORLD_TRANSLUCENT, this::preRender);
            prePhaseRegistered = true;
        }
    }


    public synchronized void clear() {
        for (ParticleMaterial m : materials) {
            if (m.meshId != null) InstanceMeshRegistry.INSTANCE.unregister(m.meshId);
            recycleLive(m);
        }
        materials.clear();
        SpawnRequest req;
        while ((req = intake.poll()) != null) recycle(req);
        pendingCount.set(0);
        meshCounter = 0;
    }

    public synchronized boolean unregister(ParticleMaterial material) {
        if (!materials.remove(material)) return false;
        if (material.meshId != null) InstanceMeshRegistry.INSTANCE.unregister(material.meshId);
        recycleLive(material);
        return true;
    }

    private void recycleLive(ParticleMaterial m) {
        for (int i = 0; i < m.liveCount; i++) {
            Particle p = m.live[i];
            if (p != null) {
                p.clear();
                pool.offer(p);
                m.live[i] = null;
            }
        }
        m.liveCount = 0;
    }

    SpawnRequest borrowRequest() {
        SpawnRequest r = requestPool.poll();
        return r != null ? r : new SpawnRequest();
    }

    void submit(SpawnRequest req) {
        if (pendingCount.get() >= MAX_PENDING) { recycle(req); return; }
        pendingCount.incrementAndGet();
        intake.add(req);
    }

    private void recycle(SpawnRequest req) {
        req.material = null;
        requestPool.offer(req);
    }

    // defensive copy so the debug UI can list/spawn from it
    public List<ParticleMaterial> materials() {
        return new ArrayList<>(materials);
    }

    public int liveCount() {
        int total = 0;
        for (ParticleMaterial m : materials) total += m.liveCount;
        return total;
    }


    private void preRender(InstanceRenderContext rctx) {
        drainIntake();

        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        if (mc.isPaused()) { lastNano = now; return; }

        float frameDt = lastNano == 0 ? 0f : (now - lastNano) / 1e9f;
        lastNano = now;
        if (frameDt > MAX_FRAME_DT) frameDt = MAX_FRAME_DT;

        simClock += frameDt;
        if (simClock > 3600f) simClock -= 3600f;
        ctx.set(simClock, rctx.world(), rctx.cameraPos());

        accumulator += frameDt;
        while (accumulator >= STEP) {
            step(STEP);
            accumulator -= STEP;
        }
    }

    private void drainIntake() {
        SpawnRequest req;
        while ((req = intake.poll()) != null) {
            pendingCount.decrementAndGet();
            addParticle(req);
            recycle(req);
        }
    }

    private void addParticle(SpawnRequest r) {
        ParticleMaterial m = r.material;
        if (m == null || m.liveCount >= m.liveCap) return;

        Particle p = pool.poll();
        if (p == null) p = new Particle();
        p.x = r.x; p.y = r.y; p.z = r.z;
        p.vx = r.vx; p.vy = r.vy; p.vz = r.vz;
        p.age = 0f; p.life = Math.max(r.life, 0.01f);
        p.size0 = r.size0; p.size1 = r.size1;
        p.r0 = r.r0; p.g0 = r.g0; p.b0 = r.b0;
        p.r1 = r.r1; p.g1 = r.g1; p.b1 = r.b1;
        p.a0 = r.a0; p.a1 = r.a1;
        p.aFadeIn = r.aFadeIn; p.aFadeOut = r.aFadeOut;
        p.gravity = r.gravity; p.drag = r.drag;
        p.dragStep = (float) Math.pow(Math.max(r.drag, 0f), STEP);
        p.rot = r.rot; p.rotSpeed = r.rotSpeed;
        p.seedX = r.seedX; p.seedY = r.seedY;
        p.sizeEasing = r.easing != null ? r.easing : Easing.EASE_OUT;
        p.brightness = 1f; p.lightTimer = 0;
        p.colliding = false;
        p.alive = true;

        p.trailHead = 0;
        p.trailCount = 0;
        if (m.trail) {
            int need = TRAIL_MAX * 3;
            if (p.trail == null || p.trail.length < need) p.trail = new double[need];
        }

        if (m.liveCount == m.live.length) {
            m.live = Arrays.copyOf(m.live, Math.min(m.liveCap, m.live.length * 2));
        }
        m.live[m.liveCount++] = p;
    }

    private void step(float dt) {
        stepCount++;
        ClientLevel level = ctx.world();
        for (ParticleMaterial m : materials) {
            Affector[] affectors = m.affectors;
            Collider collider = m.collider;
            Particle[] live = m.live;
            int n = m.liveCount;
            for (int i = 0; i < n; i++) {
                Particle p = live[i];
                for (Affector a : affectors) a.apply(p, dt, ctx);
                double ox = p.x, oy = p.y, oz = p.z;
                p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
                if (collider != null) collider.resolve(p, ox, oy, oz, dt, ctx);
                p.rot += p.rotSpeed * dt;
                p.age += dt;
                if (m.trail && p.trail != null && stepCount % m.trailInterval == 0) {
                    int slot = p.trailHead * 3;
                    p.trail[slot] = p.x; p.trail[slot + 1] = p.y; p.trail[slot + 2] = p.z;
                    p.trailHead = (p.trailHead + 1) % TRAIL_MAX;
                    if (p.trailCount < TRAIL_MAX) p.trailCount++;
                }
                if (m.lightmap && level != null && --p.lightTimer <= 0) {
                    p.brightness = sampleLight(level, m, p);
                    p.lightTimer = m.lightRefreshSteps;
                }
                if (p.age >= p.life) {
                    p.alive = false;
                    p.clear();
                    pool.offer(p);
                    live[i] = live[n - 1];
                    live[n - 1] = null;
                    n--; i--;
                }
            }
            m.liveCount = n;
        }
    }

    private float sampleLight(ClientLevel level, ParticleMaterial m, Particle p) {
        BlockPos pos = BlockPos.containing(p.x, p.y, p.z);
        int block = level.getBrightness(LightLayer.BLOCK, pos);
        int sky = Math.max(0, level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken());
        float b = Math.max(block, sky) / 15f;
        return m.lightMin + (1f - m.lightMin) * b;
    }

    public boolean captureSceneDepth() {
        for (ParticleMaterial m : materials) {
            if (m.softDepth && m.liveCount > 0) {
                return SceneDepth.update();
            }
        }
        return false;
    }


    private void packMaterial(ParticleMaterial m, InstanceRenderContext rctx, InstanceBatch<Particle> batch) {
        int n = m.liveCount;
        if (n == 0) return;

        Vec3 cam = rctx.cameraPos();
        Matrix4fc proj = rctx.projectionMatrix();
        Matrix4f projView = new Matrix4f(proj).mul(rctx.viewMatrix());
        float m00 = proj.m00(), m11 = proj.m11();

        if (visibleBuf.length < n) visibleBuf = new Particle[Integer.highestOneBit(n) * 2];

        int vis = 0;
        for (int i = 0; i < n; i++) {
            Particle p = m.live[i];
            float cx = (float) (p.x - cam.x), cy = (float) (p.y - cam.y), cz = (float) (p.z - cam.z);
            float t = p.ageFraction();
            float seed = p.seedX * 0.25f; // seedX is uniform [0,4); Track seeds want [0,1)
            float size = m.sizeTrack != null
                    ? m.sizeTrack.eval(t, seed)
                    : lerp(p.size0, p.size1, p.sizeEasing.apply(t));
            if (!visible(projView, cx, cy, cz, size, m00, m11)) continue;

            float light = m.lightmap ? p.brightness : 1f;
            p.rCx = cx; p.rCy = cy; p.rCz = cz;
            p.rVx = (float) p.vx; p.rVy = (float) p.vy; p.rVz = (float) p.vz;
            p.rSize = size;

            float cr, cg, cb, ca;
            if (m.colorGradient != null) {
                m.colorGradient.eval(t, rgbaScratch);
                cr = rgbaScratch[0]; cg = rgbaScratch[1]; cb = rgbaScratch[2]; ca = rgbaScratch[3];
            } else {
                cr = lerp(p.r0, p.r1, t); cg = lerp(p.g0, p.g1, t); cb = lerp(p.b0, p.b1, t);
                ca = lerp(p.a0, p.a1, t);
            }
            if (m.alphaTrack != null) ca = m.alphaTrack.eval(t, seed);

            p.rR = cr * light;
            p.rG = cg * light;
            p.rB = cb * light;
            p.rA = ca * alphaEnvelope(t, p.aFadeIn, p.aFadeOut);

            if (m.flipCols > 0) {
                int rows = Math.max(1, m.flipRows);
                int total = m.flipCols * rows;
                int frame = m.flipOverLife
                        ? Math.min((int) (t * total), total - 1)
                        : Math.floorMod((int) (p.age * m.flipFps), total);
                float sx = 1f / m.flipCols, sy = 1f / rows;
                p.rUvOffX = (frame % m.flipCols) * sx;
                p.rUvOffY = (frame / m.flipCols) * sy;
                p.rUvScaleX = sx; p.rUvScaleY = sy;
            } else {
                p.rUvOffX = 0f; p.rUvOffY = 0f; p.rUvScaleX = 1f; p.rUvScaleY = 1f;
            }
            visibleBuf[vis++] = p;
        }
        if (vis == 0) return;

        if (m.sorted) {
            packSorted(m, batch, vis, cam);
        } else {
            for (int i = 0; i < vis; i++) {
                batch.add(visibleBuf[i]);
                if (m.trail) emitTrail(batch, m, visibleBuf[i], cam);
            }
        }
    }

    private void packSorted(ParticleMaterial m, InstanceBatch<Particle> batch, int vis, Vec3 cam) {
        if (sortBuf.length < vis) sortBuf = new long[Integer.highestOneBit(vis) * 2];
        for (int i = 0; i < vis; i++) {
            Particle p = visibleBuf[i];
            float dist2 = p.rCx * p.rCx + p.rCy * p.rCy + p.rCz * p.rCz;
            sortBuf[i] = ((long) Float.floatToIntBits(dist2) << 32) | (i & 0xffffffffL);
        }
        Arrays.sort(sortBuf, 0, vis);
        for (int i = vis - 1; i >= 0; i--) {
            int idx = (int) (sortBuf[i] & 0xffffffffL);
            batch.add(visibleBuf[idx]);
            if (m.trail) emitTrail(batch, m, visibleBuf[idx], cam);
        }
    }

    // emits fading ghost billboards along the particle's recorded path. the single reusable ghost is
    // written into the batch immediately (the writer copies its fields) so no per-instance allocation
    private void emitTrail(InstanceBatch<Particle> batch, ParticleMaterial m, Particle p, Vec3 cam) {
        if (p.trail == null || p.trailCount < 2) return;
        int pts = Math.min(m.trailPoints, p.trailCount);
        Particle g = trailGhost;
        g.rVx = p.rVx; g.rVy = p.rVy; g.rVz = p.rVz;
        g.rUvOffX = p.rUvOffX; g.rUvOffY = p.rUvOffY; g.rUvScaleX = p.rUvScaleX; g.rUvScaleY = p.rUvScaleY;
        g.rR = p.rR; g.rG = p.rG; g.rB = p.rB;
        // the instance writer also reads these - copy so ghosts match the particle (else age==1, no rotation/seed)
        g.rot = p.rot; g.seedX = p.seedX; g.seedY = p.seedY; g.age = p.age; g.life = p.life;
        for (int k = 1; k <= pts; k++) {
            int hidx = ((p.trailHead - k) % TRAIL_MAX + TRAIL_MAX) % TRAIL_MAX;
            int slot = hidx * 3;
            float f = 1f - (float) k / (pts + 1); // taper size + alpha toward the tail
            g.rSize = p.rSize * m.trailWidth * f;
            g.rA = p.rA * m.trailFade * f;
            if (g.rSize <= 0f || g.rA <= 0.002f) continue;
            g.rCx = (float) (p.trail[slot] - cam.x);
            g.rCy = (float) (p.trail[slot + 1] - cam.y);
            g.rCz = (float) (p.trail[slot + 2] - cam.z);
            batch.add(g);
        }
    }

    private boolean visible(Matrix4fc projView, float cx, float cy, float cz, float size, float m00, float m11) {
        projView.transform(cx, cy, cz, 1f, clip);
        float w = clip.w;
        float r = size * 0.5f * 1.4142f;
        if (w <= 0f) return w > -r;
        float mx = r * m00, my = r * m11;
        if (clip.x < -w - mx || clip.x > w + mx) return false;
        if (clip.y < -w - my || clip.y > w + my) return false;
        if (clip.z < -w - r || clip.z > w + r) return false;
        return true;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    // smoothstep fade in over the first fadeIn fraction of life, fade out over the last fadeOut
    // fraction, 1 in between. both 0 means no envelope
    private static float alphaEnvelope(float t, float fadeIn, float fadeOut) {
        float e = 1f;
        if (fadeIn > 1e-4f) { float x = Math.min(t / fadeIn, 1f); e *= x * x * (3f - 2f * x); }
        if (fadeOut > 1e-4f) { float x = Math.min((1f - t) / fadeOut, 1f); e *= x * x * (3f - 2f * x); }
        return e;
    }

    static final class SpawnRequest {
        ParticleMaterial material;
        double x, y, z, vx, vy, vz;
        float life, size0, size1;
        float r0, g0, b0, r1, g1, b1;
        float a0, a1;
        float aFadeIn, aFadeOut;
        float gravity, drag, rot, rotSpeed;
        float seedX, seedY;
        Easing easing;
    }
}