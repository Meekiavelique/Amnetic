package com.meekdev.amnetic.client.render;

import java.util.ArrayDeque;
import java.util.Deque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AmneticResources {

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Resources");
    private static final Deque<AutoCloseable> RESOURCES = new ArrayDeque<>();

    private AmneticResources() {}

    public static <T extends AutoCloseable> T register(T resource) {
        RESOURCES.push(resource);
        return resource;
    }

    public static void disposeAll() {
        while (!RESOURCES.isEmpty()) {
            AutoCloseable r = RESOURCES.pop();
            try {
                r.close();
            } catch (Throwable e) {
                LOG.error("Failed to dispose {}", r.getClass().getSimpleName(), e);
            }
        }
    }
}
