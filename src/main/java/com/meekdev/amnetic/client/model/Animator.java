package com.meekdev.amnetic.client.model;

public final class Animator {

    @SuppressWarnings("unused")
    private final Model model;

    private String current;
    private String crossfadeTo;
    private float crossfadeDuration;
    private float crossfadeElapsed;
    private boolean loop = true;
    private float speed = 1f;
    private float time;

    Animator(Model model) { this.model = model; }

    public Animator play(String clip) {
        this.current = clip;
        this.crossfadeTo = null;
        this.time = 0f;
        return this;
    }

    public Animator loop(boolean loop) { this.loop = loop; return this; }

    public Animator speed(float speed) { this.speed = speed; return this; }

    public Animator setTime(float seconds) { this.time = Math.max(0f, seconds); return this; }

    public Animator crossfade(String from, String clip, float seconds) {
        this.current = from;
        this.crossfadeTo = clip;
        this.crossfadeDuration = Math.max(1e-3f, seconds);
        this.crossfadeElapsed = 0f;
        return this;
    }

    public Animator update(float dt) {
        time += dt * speed;
        if (crossfadeTo != null) {
            crossfadeElapsed += dt;
            if (crossfadeElapsed >= crossfadeDuration) {
                current = crossfadeTo;
                crossfadeTo = null;
                time = 0f;
            }
        }
        return this;
    }

    public String current() { return current; }
    public boolean isLooping() { return loop; }
    public float speedValue() { return speed; }
    public float time() { return time; }

    public void dispose() {

    }
}
