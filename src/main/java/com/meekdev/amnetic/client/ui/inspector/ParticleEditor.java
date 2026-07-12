package com.meekdev.amnetic.client.ui.inspector;

import com.meekdev.amnetic.client.anim.Easing;
import com.meekdev.amnetic.client.particle.ParticleMaterial;
import com.meekdev.amnetic.client.particle.ParticleSimulation;
import com.meekdev.amnetic.client.particle.Particles;
import com.meekdev.amnetic.client.particle.SpawnShape;
import com.meekdev.amnetic.client.particle.editor.EffectDraft;
import com.meekdev.amnetic.client.particle.editor.EffectIO;
import com.meekdev.amnetic.client.ui.Inspector;
import com.meekdev.amnetic.client.ui.SpawnTarget;
import com.meekdev.amnetic.client.ui.widget.CurveEditor;
import com.meekdev.amnetic.client.ui.widget.GradientEditor;
import com.meekdev.amnetic.client.ui.widget.TexturePicker;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.random.RandomGenerator;

public final class ParticleEditor extends Inspector {

    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic/Particles");
    private static final String[] BLENDS = {"ALPHA", "ADDITIVE"};
    private static final String[] BILLBOARDS = {"SPHERICAL", "VELOCITY_STRETCHED"};
    private static final String[] AFF_TYPES = {"GRAVITY", "DRAG", "DRAG_PP", "WIND", "TURBULENCE", "ATTRACTOR", "VORTEX"};
    private static final String[] COLLIDERS = {"NONE", "WORLD", "PLANE"};
    private static final String[] RESPONSES = {"BOUNCE", "SLIDE", "STOP"};
    private static final String[] EASINGS;
    static {
        Easing[] e = Easing.values();
        EASINGS = new String[e.length];
        for (int i = 0; i < e.length; i++) EASINGS[i] = e[i].name();
    }

    private static final String[] EMIT_SHAPES = {"Sphere", "Cone up", "Point up"};
    private static final SpawnShape[] SHAPES = {
            (rng, off, dir) -> { randomUnit(rng, dir); off.set(0f); },
            (rng, off, dir) -> { dir.set((rng.nextFloat() * 2 - 1) * 0.3f, 1f, (rng.nextFloat() * 2 - 1) * 0.3f).normalize(); off.set(0f); },
            (rng, off, dir) -> { dir.set(0f, 1f, 0f); off.set(0f); },
    };

    private final SpawnTarget spawn = new SpawnTarget();

    private EffectDraft draft = new EffectDraft();
    private ParticleMaterial material;
    private String structuralSig = "";
    private boolean dirty = true;

    private final ImString name = new ImString(64);
    private final ImString texture = new ImString(256);
    private final ImInt libSel = new ImInt(0);

    private final ImBoolean emit = new ImBoolean(false);
    private final ImInt emitShape = new ImInt(1);
    private final int[] rate = {4};
    private final float[] speed = {0.5f, 2f};
    private float emitAcc;

    public ParticleEditor() {
        super("Renderer", "Particle Editor", true);
        name.set(draft.name);
    }

    @Override
    public void render() {
        if (material == null) {
            rebuild();
        } else if (dirty && !ImGui.isMouseDown(0)) {
            if (!draft.structuralSig().equals(structuralSig)) rebuild();
            else { draft.applyLive(material); dirty = false; }
        }

        library();
        ImGui.separator();
        if (ImGui.collapsingHeader("Material", ImGuiTreeNodeFlags.DefaultOpen)) material();
        if (ImGui.collapsingHeader("Over lifetime", ImGuiTreeNodeFlags.DefaultOpen)) lifetime();
        if (ImGui.collapsingHeader("Affectors")) affectors();
        if (ImGui.collapsingHeader("Collider")) collider();
        ImGui.separator();
        preview();
    }

    private void rebuild() {
        try {
            if (material != null) ParticleSimulation.INSTANCE.unregister(material);
            material = draft.build();
            structuralSig = draft.structuralSig();
        } catch (Throwable t) {
            LOGGER.warn("particle effect build failed", t);
            material = null;
        }
        dirty = false;
    }

    private void library() {
        List<String> saved = EffectIO.list();
        String[] items = saved.isEmpty() ? new String[]{"<none>"} : saved.toArray(new String[0]);
        ImGui.setNextItemWidth(180);
        ImGui.combo("##lib", libSel, items);
        ImGui.sameLine();
        if (ImGui.button("Load") && !saved.isEmpty()) {
            EffectDraft d = EffectIO.load(saved.get(Math.min(libSel.get(), saved.size() - 1)));
            if (d != null) { draft = d; syncFromDraft(); dirty = true; }
        }
        ImGui.sameLine();
        if (ImGui.button("Save")) EffectIO.save(draft);
        ImGui.sameLine();
        if (ImGui.button("New")) { draft = new EffectDraft(); syncFromDraft(); dirty = true; }
        ImGui.sameLine();
        if (ImGui.button("Duplicate")) { draft = draft.copy(); syncFromDraft(); dirty = true; }
        ImGui.sameLine();
        if (ImGui.button("Delete") && !saved.isEmpty()) EffectIO.delete(saved.get(Math.min(libSel.get(), saved.size() - 1)));
    }

    private void material() {
        if (ImGui.inputText("Name", name)) { draft.name = name.get(); }
        if (TexturePicker.draw("Texture (blank=soft dot)", texture)) { draft.texture = texture.get(); dirty = true; }

        draft.blend = combo("Blend", BLENDS, draft.blend);
        draft.billboard = combo("Billboard", BILLBOARDS, draft.billboard);

        ImBoolean em = new ImBoolean(draft.emissive);
        if (ImGui.checkbox("Emissive", em)) { draft.emissive = em.get(); dirty = true; }
        if (draft.emissive) {
            float[] es = {draft.emissiveStrength};
            if (ImGui.sliderFloat("Emissive strength", es, 0f, 5f)) { draft.emissiveStrength = es[0]; dirty = true; }
        }
        draft.softDepth = check("Soft depth", draft.softDepth);
        ImGui.sameLine(); draft.sorted = check("Sorted", draft.sorted);
        draft.depthWrite = check("Depth write", draft.depthWrite);
        ImGui.sameLine(); draft.lightmap = check("Lightmap", draft.lightmap);

        float[] life = {draft.life};
        if (ImGui.dragFloat("Life (s)", life, 0.02f, 0.05f, 30f)) { draft.life = life[0]; dirty = true; }
        float[] grav = {draft.gravity};
        if (ImGui.dragFloat("Gravity", grav, 0.05f, -50f, 50f)) { draft.gravity = grav[0]; dirty = true; }
        float[] drag = {draft.drag};
        if (ImGui.sliderFloat("Drag (retain/s)", drag, 0f, 1f)) { draft.drag = drag[0]; dirty = true; }
        draft.easing = combo("Size easing", EASINGS, draft.easing);

        ImBoolean tr = new ImBoolean(draft.trail);
        if (ImGui.checkbox("Trail", tr)) { draft.trail = tr.get(); dirty = true; }
        if (draft.trail) {
            int[] tp = {draft.trailPoints};
            if (ImGui.sliderInt("Trail points", tp, 1, 32)) { draft.trailPoints = tp[0]; dirty = true; }
            float[] tw = {draft.trailWidth};
            if (ImGui.sliderFloat("Trail width", tw, 0f, 1.5f)) { draft.trailWidth = tw[0]; dirty = true; }
            float[] tf = {draft.trailFade};
            if (ImGui.sliderFloat("Trail fade", tf, 0f, 1f)) { draft.trailFade = tf[0]; dirty = true; }
            int[] ti = {draft.trailInterval};
            if (ImGui.sliderInt("Trail sample interval", ti, 1, 8)) { draft.trailInterval = ti[0]; dirty = true; }
        }

        int[] flip = {draft.flipCols, draft.flipRows};
        if (ImGui.dragInt2("Flipbook cols/rows", flip, 1f, 1, 16)) { draft.flipCols = flip[0]; draft.flipRows = flip[1]; dirty = true; }
        if (draft.flipCols > 1 || draft.flipRows > 1) {
            float[] fps = {draft.flipFps};
            if (ImGui.dragFloat("Flipbook fps", fps, 0.5f, 0f, 60f)) { draft.flipFps = fps[0]; dirty = true; }
        }
    }

    private void lifetime() {
        ImGui.text("Size over life");
        if (CurveEditor.draw("size", draft.sizeKeys, 0f, 1f, 70f)) dirty = true;
        ImGui.text("Alpha over life");
        if (CurveEditor.draw("alpha", draft.alphaKeys, 0f, 1f, 70f)) dirty = true;
        ImGui.text("Color over life");
        if (GradientEditor.draw("color", draft.colorStops, 22f)) dirty = true;
    }

    private void affectors() {
        for (int i = 0; i < draft.affectors.size(); i++) {
            EffectDraft.Aff a = draft.affectors.get(i);
            ImGui.pushID(i);
            a.type = combo("Type", AFF_TYPES, a.type);
            switch (a.type) {
                case "GRAVITY" -> { float[] v = {a.p0}; if (ImGui.dragFloat("accel", v, 0.05f, -50f, 50f)) { a.p0 = v[0]; dirty = true; } }
                case "DRAG" -> { float[] v = {a.p0}; if (ImGui.sliderFloat("retain/s", v, 0f, 1f)) { a.p0 = v[0]; dirty = true; } }
                case "TURBULENCE" -> {
                    float[] v = {a.p0, a.p1};
                    if (ImGui.dragFloat2("scale / strength", v, 0.02f, 0f, 20f)) { a.p0 = v[0]; a.p1 = v[1]; dirty = true; }
                }
                case "WIND" -> {
                    float[] v = {(float) a.x, (float) a.y, (float) a.z};
                    if (ImGui.dragFloat3("accel xyz", v, 0.05f)) { a.x = v[0]; a.y = v[1]; a.z = v[2]; dirty = true; }
                }
                case "ATTRACTOR" -> {
                    float[] p = {(float) a.x, (float) a.y, (float) a.z};
                    if (ImGui.dragFloat3("pos xyz", p, 0.1f)) { a.x = p[0]; a.y = p[1]; a.z = p[2]; dirty = true; }
                    float[] s = {a.p0}; if (ImGui.dragFloat("strength", s, 0.05f, -50f, 50f)) { a.p0 = s[0]; dirty = true; }
                }
                case "VORTEX" -> {
                    float[] p = {(float) a.x, (float) a.y, (float) a.z};
                    if (ImGui.dragFloat3("center xyz (Y axis)", p, 0.1f)) { a.x = p[0]; a.y = p[1]; a.z = p[2]; dirty = true; }
                    float[] v = {a.p0, a.p1};
                    if (ImGui.dragFloat2("swirl / inward", v, 0.05f, -50f, 50f)) { a.p0 = v[0]; a.p1 = v[1]; dirty = true; }
                }
                default -> { }
            }
            if (ImGui.button("Remove")) { draft.affectors.remove(i); dirty = true; ImGui.popID(); break; }
            ImGui.popID();
            ImGui.separator();
        }
        if (ImGui.button("Add affector")) { draft.affectors.add(new EffectDraft.Aff()); dirty = true; }
    }

    private void collider() {
        draft.collider = combo("Type", COLLIDERS, draft.collider);
        if ("PLANE".equals(draft.collider)) {
            float[] y = {draft.planeY};
            if (ImGui.dragFloat("Plane Y", y, 0.1f)) { draft.planeY = y[0]; dirty = true; }
        }
        if (!"NONE".equals(draft.collider)) {
            draft.response = combo("Response", RESPONSES, draft.response);
            float[] r = {draft.restitution};
            if (ImGui.sliderFloat("Restitution", r, 0f, 1f)) { draft.restitution = r[0]; dirty = true; }
            float[] f = {draft.friction};
            if (ImGui.sliderFloat("Friction", f, 0f, 1f)) { draft.friction = f[0]; dirty = true; }
        }
    }

    private void preview() {
        spawn.controls();
        ImGui.checkbox("Emit", emit);
        ImGui.sameLine();
        if (ImGui.button("Burst")) emitNow(64);
        ImGui.combo("Emit shape", emitShape, EMIT_SHAPES);
        ImGui.sliderInt("Rate (/frame)", rate, 0, 64);
        ImGui.dragFloat2("Speed min/max", speed, 0.05f, 0f, 20f);
        ImGui.text("Live particles: " + Particles.liveCount());

        if (emit.get()) {
            emitAcc += rate[0];
            int n = (int) emitAcc;
            emitAcc -= n;
            if (n > 0) emitNow(n);
        }
    }

    private void emitNow(int count) {
        if (material == null || count <= 0) return;
        Vec3 p = spawn.position();
        Particles.burst(material, p.x, p.y, p.z, count, SHAPES[emitShape.get()], speed[0], speed[1]);
    }

    private void syncFromDraft() {
        name.set(draft.name);
        texture.set(draft.texture);
    }

    private boolean check(String label, boolean value) {
        ImBoolean b = new ImBoolean(value);
        if (ImGui.checkbox(label, b)) { dirty = true; return b.get(); }
        return value;
    }

    private String combo(String label, String[] items, String current) {
        int idx = 0;
        for (int i = 0; i < items.length; i++) if (items[i].equals(current)) { idx = i; break; }
        ImInt sel = new ImInt(idx);
        if (ImGui.combo(label, sel, items)) { dirty = true; return items[sel.get()]; }
        return current;
    }

    private static void randomUnit(RandomGenerator rng, Vector3f out) {
        float z = rng.nextFloat() * 2f - 1f;
        float a = rng.nextFloat() * 6.2831855f;
        float r = (float) Math.sqrt(Math.max(0f, 1f - z * z));
        out.set(r * (float) Math.cos(a), r * (float) Math.sin(a), z);
    }
}
