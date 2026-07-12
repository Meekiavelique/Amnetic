package com.meekdev.amnetic.client.ui.scene;

public final class EditorSelection {

    private static Object target;

    private EditorSelection() {}

    public static void set(Selectable s) { target = s == null ? null : s.target(); }
    public static void setTarget(Object t) { target = t; }
    public static void clear() { target = null; }
    public static boolean is(Object t) { return t != null && t == target; }
    public static boolean has() { return target != null; }
    public static boolean is(Selectable s) { return s != null && s.target() == target; }
    public static Object target() { return target; }
}
