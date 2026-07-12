package com.meekdev.amnetic.client.ui.inspector;

import com.meekdev.amnetic.client.decal.Decals;
import com.meekdev.amnetic.client.gbuffer.GBuffer;
import com.meekdev.amnetic.client.light.internal.LightRegistry;
import com.meekdev.amnetic.client.particle.Particles;
import com.meekdev.amnetic.client.ui.Inspector;
import imgui.ImGui;
import imgui.type.ImBoolean;

public final class StatsInspector extends Inspector {

    private final float[] frametimes = new float[120];
    private int frameCursor;
    private long lastNano;

    public StatsInspector() {
        super("Info", "Stats", false);
    }

    @Override
    public void render() {
        long now = System.nanoTime();
        if (lastNano != 0) {
            float ms = (now - lastNano) / 1_000_000f;
            frametimes[frameCursor] = ms;
            frameCursor = (frameCursor + 1) % frametimes.length;
        }
        lastNano = now;

        ImGui.text("Lights:    " + LightRegistry.INSTANCE.all().size());
        ImGui.text("Decals:    " + Decals.active().size());
        ImGui.text("Particles: " + Particles.liveCount());
        ImGui.separator();

        float last = frametimes[(frameCursor + frametimes.length - 1) % frametimes.length];
        ImGui.text(String.format("Frame: %.2f ms (%.0f fps)", last, last > 0 ? 1000f / last : 0f));
        ImGui.plotLines("##frametime", frametimes, frametimes.length);
        ImGui.separator();

        ImBoolean gbuffer = new ImBoolean(GBuffer.isEnabled());
        if (ImGui.checkbox("GBuffer enabled", gbuffer)) GBuffer.setEnabled(gbuffer.get());
    }
}
