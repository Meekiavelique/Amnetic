package com.meekdev.amnetic.client.camera;

/**
 * A consumer-defined per-frame camera modifier — the modular extension point of the camera system.
 * Register one with {@link CameraEffects#addModifier} and it is invoked once per rendered frame with
 * a fresh {@link CameraFrame}, into which it contributes additive position / rotation / FOV offsets.
 *
 * <p>The modifier owns all of its own state (phase, amplitude, noise seeds, ramp). Amnetic only
 * guarantees the per-frame call and a correct {@link CameraFrame#dt() delta time} and
 * {@link CameraFrame#time() clock}, so frame-rate-independent effects (turbulence, sway, bob, drunk,
 * vehicle rumble) can be built entirely in the consuming mod without touching Amnetic.
 *
 * <p>Contributions compose with the built-in effects ({@link CameraEffects#impulse impulse},
 * {@link CameraEffects#shake shake}, {@link CameraEffects#springTo spring}) and with other modifiers;
 * a {@link CameraDirector} override still takes precedence over everything.
 *
 * <pre>{@code
 * // A falling/tumbling effect implemented entirely in the consumer mod:
 * class FallTurbulence implements CameraModifier {
 *     float amp; // 0..1, ramped toward a target each frame
 *     float target;
 *
 *     void setTarget(float t) { this.target = t; }
 *
 *     public void modify(CameraFrame f) {
 *         amp += Math.signum(target - amp) * Math.min(Math.abs(target - amp), 2.5f * f.dt());
 *         if (amp < 1e-3f) return;
 *         float t = f.time();
 *         f.addRotation(noise(t, 1) * 5f * amp,   // pitch
 *                       noise(t, 2) * 5f * amp,   // yaw
 *                       noise(t, 3) * 9f * amp);  // roll leads the tumble
 *         f.addPositionLocal(noise(t, 4) * 0.2f * amp, noise(t, 5) * 0.2f * amp, 0);
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface CameraModifier {

    /** Contribute this frame's offsets into {@code frame}. Called once per rendered frame. */
    void modify(CameraFrame frame);
}
