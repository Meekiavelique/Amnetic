package com.meekdev.amnetic.client.outline;

import com.meekdev.amnetic.client.outline.internal.OutlinePass;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;

// crisp silhouette outlines drawn over the world after post, so taa and bloom never smear them
// submit geometry every frame it should be outlined, it is dropped once drawn
// positions are camera relative, the same space an entity pose stack is in
public final class Outlines {

    private Outlines() {}

    // starts a shape outlined in this colour (argb, alpha is the outline opacity)
    public static Shape shape(int argb) {
        return new Shape(argb);
    }

    // outline thickness in pixels at 1080p, scales with the window height
    public static void width(float pixels) {
        OutlinePass.INSTANCE.width(pixels);
    }

    public static final class Shape {
        private final float r;
        private final float g;
        private final float b;
        private final float a;
        private Matrix4fc pose;
        private Identifier texture;
        private final float[] quad = new float[20];
        private int corners;

        private Shape(int argb) {
            this.a = ((argb >>> 24) & 0xFF) / 255f;
            this.r = ((argb >>> 16) & 0xFF) / 255f;
            this.g = ((argb >>> 8) & 0xFF) / 255f;
            this.b = (argb & 0xFF) / 255f;
        }

        // transform applied to the vertices that follow
        public Shape pose(Matrix4fc pose) {
            this.pose = pose;
            return this;
        }

        // texture whose alpha cuts the silhouette (cutout sprites), null for solid geometry
        public Shape texture(Identifier texture) {
            this.texture = texture;
            return this;
        }

        // one quad corner, every 4 make a quad
        public Shape vertex(float x, float y, float z, float u, float v) {
            float px = x, py = y, pz = z;
            if (pose != null) {
                px = pose.m00() * x + pose.m10() * y + pose.m20() * z + pose.m30();
                py = pose.m01() * x + pose.m11() * y + pose.m21() * z + pose.m31();
                pz = pose.m02() * x + pose.m12() * y + pose.m22() * z + pose.m32();
            }
            int o = corners * 5;
            quad[o] = px;
            quad[o + 1] = py;
            quad[o + 2] = pz;
            quad[o + 3] = u;
            quad[o + 4] = v;
            if (++corners == 4) {
                corners = 0;
                OutlinePass.INSTANCE.quad(texture, quad, r, g, b, a);
            }
            return this;
        }

        // solid box, handy when there's no real geometry to trace
        public Shape box(float x0, float y0, float z0, float x1, float y1, float z1) {
            Identifier keep = texture;
            texture = null;
            corners = 0;
            vertex(x0, y0, z0, 0, 0).vertex(x1, y0, z0, 0, 0).vertex(x1, y0, z1, 0, 0).vertex(x0, y0, z1, 0, 0);
            vertex(x0, y1, z0, 0, 0).vertex(x0, y1, z1, 0, 0).vertex(x1, y1, z1, 0, 0).vertex(x1, y1, z0, 0, 0);
            vertex(x0, y0, z0, 0, 0).vertex(x0, y1, z0, 0, 0).vertex(x1, y1, z0, 0, 0).vertex(x1, y0, z0, 0, 0);
            vertex(x0, y0, z1, 0, 0).vertex(x1, y0, z1, 0, 0).vertex(x1, y1, z1, 0, 0).vertex(x0, y1, z1, 0, 0);
            vertex(x0, y0, z0, 0, 0).vertex(x0, y0, z1, 0, 0).vertex(x0, y1, z1, 0, 0).vertex(x0, y1, z0, 0, 0);
            vertex(x1, y0, z0, 0, 0).vertex(x1, y1, z0, 0, 0).vertex(x1, y1, z1, 0, 0).vertex(x1, y0, z1, 0, 0);
            texture = keep;
            return this;
        }
    }
}
