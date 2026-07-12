package com.meekdev.amnetic.client;

import com.meekdev.amnetic.client.bloom.Bloom;
import com.meekdev.amnetic.client.model.ModelLighting;
import com.meekdev.amnetic.client.ssao.Ssao;
import com.meekdev.amnetic.client.ssgi.Ssgi;
import com.meekdev.amnetic.client.ssr.Ssr;

/** one-call quality presets for the whole screen-space effect stack. each preset tunes SSAO, SSGI, SSR
 *  and Bloom together to trade performance for fidelity. systems stay fully configurable afterwards via
 *  their own settings() (e.g. Ssr.settings().maxSteps(48)); a preset is just a starting point */
public final class Quality {

    private Quality() {}

    // everything off, cheapest, vanilla-ish look
    public static void off() {
        Ssao.disable();
        Ssgi.disable();
        Ssr.disable();
        Bloom.disable();
        // reset lighting too, otherwise ultra's exposure bump sticks around
        ModelLighting.INSTANCE.tonemap(true).exposure(1.0f);
    }

    // cheap AO + emissive bloom only, good for low-end / high-FPS
    public static void low() {
        Ssao.enable();
        Ssao.settings().scale(0.5f).intensity(0.9f).radius(0.6f).temporal(true);
        Ssgi.disable();
        Ssr.disable();
        Bloom.enable();
        Bloom.settings().all(false).levels(4).intensity(0.9f).threshold(0.75f);
        ModelLighting.INSTANCE.tonemap(true).exposure(1.0f);
    }

    // balanced default: AO + GI + half-res temporal SSR + bloom
    public static void balanced() {
        Ssao.enable();
        Ssao.settings().scale(0.5f).intensity(1.0f).radius(0.7f).temporal(true);
        Ssgi.enable();
        Ssgi.settings().scale(0.5f).intensity(0.6f).radius(1.5f);
        Ssr.enable();
        Ssr.settings().intensity(1.0f).reflectivity(0.3f).maxSteps(48).stride(0.4f)
                .maxDistance(48f).thickness(0.6f).edgeFade(0.12f).resolution(0.75f)
                .temporal(true).feedback(0.85f);
        Bloom.enable();
        Bloom.settings().all(false).levels(6).intensity(0.9f).threshold(0.75f).knee(0.5f);
        ModelLighting.INSTANCE.tonemap(true).exposure(1.0f);
    }

    // full-res temporal SSR, high-quality AO/GI, big bloom
    public static void ultra() {
        Ssao.enable();
        Ssao.settings().scale(1.0f).intensity(1.0f).radius(0.8f).temporal(true);
        Ssgi.enable();
        Ssgi.settings().scale(1.0f).intensity(0.7f).radius(1.8f);
        Ssr.enable();
        Ssr.settings().intensity(1.0f).reflectivity(0.3f).maxSteps(96).stride(0.3f)
                .maxDistance(64f).thickness(0.5f).edgeFade(0.1f).resolution(1.0f)
                .temporal(true).feedback(0.88f);
        Bloom.enable();
        Bloom.settings().all(false).levels(7).intensity(1.0f).threshold(0.7f).knee(0.5f);
        ModelLighting.INSTANCE.tonemap(true).exposure(1.05f);
    }
}
