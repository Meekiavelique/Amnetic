package com.meekdev.amnetic.client.ui.scene;

import com.meekdev.amnetic.client.decal.Decal;
import com.meekdev.amnetic.client.ui.widget.TexturePicker;
import com.meekdev.amnetic.client.ui.widget.Widgets;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class DecalSelectable implements Selectable, Transformable {

    private final Decal decal;
    private final String name;

    public DecalSelectable(Decal decal, String name) {
        this.decal = decal;
        this.name = name;
    }

    @Override public Object target() { return decal; }
    @Override public String category() { return "Decals"; }
    @Override public String displayName() { return name; }
    @Override public Transformable transform() { return this; }
    @Override public void remove() { decal.remove(); }

    @Override public double posX() { return decal.center().x; }
    @Override public double posY() { return decal.center().y; }
    @Override public double posZ() { return decal.center().z; }
    @Override public void setPosition(double x, double y, double z) { decal.center(new Vec3(x, y, z)); }

    @Override public boolean aimable() { return true; }
    @Override public float dirX() { return decal.normal().x; }
    @Override public float dirY() { return decal.normal().y; }
    @Override public float dirZ() { return decal.normal().z; }
    @Override public void setDirection(float x, float y, float z) { decal.normal(x, y, z); }

    @Override public boolean resizable() { return true; }
    @Override public float extent() { return decal.width(); }
    @Override public void setExtent(float e) { decal.width(e); decal.height(e); }

    @Override
    public void renderInspector() {
        Vec3 c = decal.center();
        float[] pos = {(float) c.x, (float) c.y, (float) c.z};
        if (Widgets.vec3("Position", pos, 0.05f)) decal.center(new Vec3(pos[0], pos[1], pos[2]));

        Vector3f n = decal.normal();
        float[] nrm = {n.x, n.y, n.z};
        if (Widgets.vec3("Normal", nrm, 0.02f)) decal.normal(nrm[0], nrm[1], nrm[2]);

        float[] whd = {decal.width(), decal.height(), decal.depth()};
        if (Widgets.vec3("W/H/Depth", whd, 0.05f)) { decal.width(whd[0]); decal.height(whd[1]); decal.depth(whd[2]); }

        float[] op = {decal.opacity()};
        if (Widgets.slider("Opacity", op, 0f, 1f)) decal.opacity(op[0]);
        float[] af = {decal.angleFade()};
        if (Widgets.slider("Angle fade", af, 0f, 1f)) decal.angleFade(af[0]);
        Vector3f t = decal.tint();
        float[] tint = {t.x, t.y, t.z};
        if (Widgets.color3("Tint", tint)) decal.tint(tint[0], tint[1], tint[2]);

        if (Widgets.section("Relighting")) {
            ImBoolean rl = new ImBoolean(decal.writesGBuffer());
            if (Widgets.checkbox("Relightable", rl)) {
                if (rl.get()) decal.roughness(0.7f); else { decal.roughness(-1f); decal.normalMap(null); }
            }
            if (decal.writesGBuffer()) {
                float[] r = {decal.roughnessOr(0.7f)};
                if (Widgets.slider("Roughness", r, 0f, 1f)) decal.roughness(r[0]);
                float[] m = {decal.metallic()};
                if (Widgets.slider("Metallic", m, 0f, 1f)) decal.metallic(m[0]);
                ImString nm = new ImString(decal.normalMap() == null ? "" : decal.normalMap().toString(), 256);
                if (TexturePicker.draw("Normal map", nm)) {
                    decal.normalMap(nm.get().isBlank() ? null : Identifier.parse(nm.get().trim()));
                }
            }
        }
    }
}
