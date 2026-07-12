package com.meekdev.amnetic.client.grade;

import com.meekdev.amnetic.client.grade.internal.ColorGradePass;

public final class ColorGrade {

    private static final ColorGradeSettings SETTINGS = new ColorGradeSettings();

    private ColorGrade() {}

    public static ColorGradeSettings settings() { return SETTINGS; }
    public static void enable() { SETTINGS.enabled(true); }
    public static void disable() { SETTINGS.enabled(false); }

    public static void render() { ColorGradePass.INSTANCE.render(SETTINGS); }
    public static void dispose() { ColorGradePass.INSTANCE.dispose(); }
}
