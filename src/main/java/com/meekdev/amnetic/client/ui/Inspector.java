package com.meekdev.amnetic.client.ui;

import imgui.type.ImBoolean;

public abstract class Inspector {

    private final String group;
    private final String title;
    private final ImBoolean open;

    protected Inspector(String group, String title, boolean openByDefault) {
        this.group = group;
        this.title = title;
        this.open = new ImBoolean(openByDefault);
    }

    public final String group() { return group; }
    public final String title() { return title; }
    public final ImBoolean open() { return open; }

    public abstract void render();
}
