package com.meekdev.amnetic.client.ui.inspector;

import com.meekdev.amnetic.client.decal.Decal;
import com.meekdev.amnetic.client.decal.Decals;
import com.meekdev.amnetic.client.ui.DecalGizmo;
import com.meekdev.amnetic.client.ui.Inspector;
import com.meekdev.amnetic.client.ui.SpawnTarget;
import com.meekdev.amnetic.client.ui.scene.EditorSelection;
import com.meekdev.amnetic.client.ui.widget.TexturePicker;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class DecalsInspector extends Inspector {

    private final SpawnTarget spawn = new SpawnTarget();
    private final ImString textureId = new ImString("minecraft:textures/block/stone.png", 256);
    private final ImString normalId = new ImString("", 256);
    private final float[] size = {1f, 1f, 0.5f};
    private final float[] rough = {0.7f};
    private final float[] metal = {0f};
    private final ImBoolean relight = new ImBoolean(false);

    public DecalsInspector() {
        super("Renderer", "Decals", false);
    }

    @Override
    public void render() {
        spawn.controls();
        ImBoolean giz = new ImBoolean(DecalGizmo.enabled);
        if (ImGui.checkbox("Show decal gizmos", giz)) DecalGizmo.enabled = giz.get();
        TexturePicker.draw("Texture", textureId);
        ImGui.dragFloat3("Width/Height/Depth", size, 0.05f, 0f, 64f);
        ImGui.checkbox("Relightable (write gbuffer)", relight);
        if (relight.get()) {
            TexturePicker.draw("Normal map (blank = flat)", normalId);
            ImGui.sliderFloat("Roughness", rough, 0f, 1f);
            ImGui.sliderFloat("Metallic", metal, 0f, 1f);
        }
        if (ImGui.button("Spawn box") && !textureId.get().isBlank()) {
            Identifier tex = Identifier.parse(textureId.get().trim());
            Vec3 p = spawn.position();
            Decal d = Decals.box(tex, p, new Vector3f(0f, 1f, 0f), size[0], size[1], size[2]);
            if (relight.get()) {
                d.roughness(rough[0]).metallic(metal[0]);
                if (!normalId.get().isBlank()) d.normalMap(Identifier.parse(normalId.get().trim()));
            }
        }
        ImGui.separator();
        ImGui.text("Active decals: " + Decals.active().size());

        int idx = 0;
        for (Decal d : Decals.active()) {
            ImGui.pushID(idx++);
            if (EditorSelection.is(d)) ImGui.setNextItemOpen(true);
            if (ImGui.collapsingHeader(d.texture().toString() + "##" + idx)) {
                Vec3 c = d.center();
                float[] pos = {(float) c.x, (float) c.y, (float) c.z};
                if (ImGui.dragFloat3("Position", pos, 0.05f)) d.center(new Vec3(pos[0], pos[1], pos[2]));
                Vector3f n = d.normal();
                float[] nrm = {n.x, n.y, n.z};
                if (ImGui.dragFloat3("Normal", nrm, 0.02f, -1f, 1f)) d.normal(nrm[0], nrm[1], nrm[2]);
                float[] wh = {d.width(), d.height(), d.depth()};
                if (ImGui.dragFloat3("Width/Height/Depth", wh, 0.05f, 0f, 64f)) { d.width(wh[0]); d.height(wh[1]); d.depth(wh[2]); }

                ImGui.separator();
                float[] opacity = {d.opacity()};
                if (ImGui.sliderFloat("Opacity", opacity, 0f, 1f)) d.opacity(opacity[0]);
                float[] angleFade = {d.angleFade()};
                if (ImGui.sliderFloat("Angle fade", angleFade, 0f, 1f)) d.angleFade(angleFade[0]);
                Vector3f t = d.tint();
                float[] tint = {t.x, t.y, t.z};
                if (ImGui.colorEdit3("Tint", tint)) d.tint(tint[0], tint[1], tint[2]);

                ImGui.separator();
                ImBoolean rl = new ImBoolean(d.writesGBuffer());
                if (ImGui.checkbox("Relightable (write gbuffer)", rl)) {
                    if (rl.get()) { d.roughness(0.7f); } else { d.roughness(-1f); d.normalMap(null); }
                }
                if (d.writesGBuffer()) {
                    float[] r = {d.roughnessOr(0.7f)};
                    if (ImGui.sliderFloat("Roughness##" + idx, r, 0f, 1f)) d.roughness(r[0]);
                    float[] m = {d.metallic()};
                    if (ImGui.sliderFloat("Metallic##" + idx, m, 0f, 1f)) d.metallic(m[0]);
                    ImString nm = new ImString(d.normalMap() == null ? "" : d.normalMap().toString(), 256);
                    if (TexturePicker.draw("Normal map##" + idx, nm)) {
                        d.normalMap(nm.get().isBlank() ? null : Identifier.parse(nm.get().trim()));
                    }
                }

                ImGui.separator();
                if (ImGui.button("Duplicate")) {
                    Decal nd = Decals.box(d.texture(), d.center(), new Vector3f(d.normal()), d.width(), d.height(), d.depth());
                    nd.opacity(d.opacity()).angleFade(d.angleFade()).tint(d.tint().x, d.tint().y, d.tint().z);
                    if (d.writesGBuffer()) { nd.roughness(d.roughnessOr(0.7f)).metallic(d.metallic()); if (d.normalMap() != null) nd.normalMap(d.normalMap()); }
                }
                ImGui.sameLine();
                if (ImGui.button("Remove")) d.remove();
            }
            ImGui.popID();
        }
    }
}
