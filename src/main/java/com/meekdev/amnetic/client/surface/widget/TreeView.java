package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Motion;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import com.meekdev.amnetic.client.surface.text.Fonts;
import com.meekdev.amnetic.client.surface.text.SdfFont;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

// collapsible tree: visible nodes flatten into a column of rows each layout like
// ListView does, rows glide via animateLayout and the chevron rotates on a spring
public class TreeView extends Column {

    // a node holds either a plain label or an arbitrary widget as its row content
    public static class TreeNode {

        final String label;
        final Widget content;
        final List<TreeNode> children = new ArrayList<>();
        public final Signal<Boolean> expanded = new Signal<>(false);

        public TreeNode(String label) {
            this.label = label;
            this.content = null;
        }

        public TreeNode(Widget content) {
            this.label = null;
            this.content = content;
        }

        public TreeNode add(TreeNode child) {
            children.add(child);
            return this;
        }

        public TreeNode expand(boolean e) {
            expanded.set(e);
            return this;
        }

        public List<TreeNode> childNodes() {
            return children;
        }
    }

    final List<TreeNode> roots;
    float indent = 14;
    float px = 13;
    float rowHeight = 18;
    int textColor = 0xFFFFFFFF;

    private final Map<TreeNode, NodeRow> rows = new IdentityHashMap<>();

    public TreeView(List<TreeNode> roots) {
        this.roots = roots;
        gap = 2;
    }

    public TreeView indent(float i) { indent = i; return this; }
    public TreeView px(float p) { px = p; return this; }
    public TreeView rowHeight(float h) { rowHeight = h; return this; }
    public TreeView textColor(int argb) { textColor = argb; return this; }

    // remount the visible slice, cached rows keep their springs and expansion feel
    private void rebuild() {
        children.clear();
        for (TreeNode root : roots) mount(root, 0);
    }

    private void mount(TreeNode node, int depth) {
        NodeRow row = rows.get(node);
        if (row == null) {
            row = new NodeRow(node);
            row.animateLayout(true);
            rows.put(node, row);
        }
        row.depth = depth;
        row.parent = this;
        children.add(row);
        if (node.expanded.peek()) {
            for (TreeNode child : node.children) mount(child, depth + 1);
        }
    }

    @Override
    protected float contentWidth() {
        rebuild();
        return super.contentWidth();
    }

    @Override
    protected float contentHeight(float forWidth) {
        rebuild();
        return super.contentHeight(forWidth);
    }

    @Override
    protected void placeChildren() {
        rebuild();
        super.placeChildren();
    }

    // one visible node: indent, chevron when it has children, label or mounted content
    private final class NodeRow extends Widget {

        private final TreeNode node;
        int depth;

        private final Signal<Float> openTarget = new Signal<>(0f);
        private final Motion<Float> openT = Motion.spring(0f, 60f, 9f);
        private final Signal<Float> hoverTarget = new Signal<>(0f);
        private final Motion<Float> hoverT = Motion.spring(0f, 60f, 9f);

        NodeRow(TreeNode node) {
            this.node = node;
            openTarget.set(node.expanded.peek() ? 1f : 0f);
            openT.follow(openTarget);
            hoverT.follow(hoverTarget);
            if (node.content != null) add(node.content);
        }

        private float inset() {
            return depth * indent + 12;
        }

        @Override
        protected boolean interactive() { return !node.children.isEmpty(); }

        @Override
        protected float contentWidth() {
            if (node.content != null) return inset() + node.content.measureWidth();
            Identifier id = Surfaces.defaultFont();
            SdfFont f = id == null ? null : Fonts.get(id);
            return inset() + (f == null || node.label == null ? 60 : f.width(node.label, px)) + 4;
        }

        @Override
        protected float contentHeight(float forWidth) {
            if (node.content != null) return Math.max(rowHeight, node.content.measureHeight(forWidth - inset()));
            return rowHeight;
        }

        @Override
        protected void placeChildren() {
            if (node.content != null) node.content.layout(x + inset(), y, w - inset(), h);
        }

        @Override
        protected void drawSelf(UiDraw d, float alpha) {
            boolean toggleable = !node.children.isEmpty();
            hoverTarget.set(hovered && toggleable ? 1f : 0f);
            float ht = hoverT.value().peek();
            if (ht > 0.01f) d.roundedRect(x, y, w, h, 4, fade(0x18FFFFFF, alpha * ht));

            if (toggleable) {
                openTarget.set(node.expanded.peek() ? 1f : 0f);
                // points right when collapsed, springs a quarter turn down when open
                float rot = -0.5f * (float) Math.PI + openT.value().peek() * 0.5f * (float) Math.PI;
                Dropdown.drawChevron(d, x + depth * indent + 5, y + h * 0.5f, rot, fade(0xB0FFFFFF, alpha));
            }
            if (node.label != null) {
                Identifier fid = Surfaces.defaultFont();
                if (fid == null) return;
                Identifier prev = d.currentFont();
                d.font(fid);
                d.textLeftCentered(node.label, x + inset(), y + h * 0.5f, px, fade(textColor, alpha));
                if (prev != null) d.font(prev);
            }
        }

        @Override
        public boolean onMouseDown(float mx, float my, int button) {
            return true;
        }

        @Override
        public void onMouseUp(float mx, float my, int button) {
            if (mx < x || my < y || mx > x + w || my > y + h) return;
            if (!node.children.isEmpty()) node.expanded.update(b -> !b);
        }

        @Override
        public void remove() {
            openT.dispose();
            hoverT.dispose();
            super.remove();
        }
    }

    // fluent overrides so chains keep the subtype
    @Override public TreeView size(float w, float h) { super.size(w, h); return this; }
    @Override public TreeView width(float w) { super.width(w); return this; }
    @Override public TreeView height(float h) { super.height(h); return this; }
    @Override public TreeView grow(float g) { super.grow(g); return this; }
    @Override public TreeView anchor(Anchor a) { super.anchor(a); return this; }
    @Override public TreeView offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public TreeView padding(float p) { super.padding(p); return this; }
    @Override public TreeView visible(boolean v) { super.visible(v); return this; }
    @Override public TreeView opacity(float o) { super.opacity(o); return this; }
}
