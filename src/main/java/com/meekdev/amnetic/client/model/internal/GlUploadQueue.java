package com.meekdev.amnetic.client.model.internal;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GlUploadQueue {
    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Upload");

    private static final ConcurrentLinkedQueue<Runnable> QUEUE = new ConcurrentLinkedQueue<>();

    private static final ExecutorService DECODE = Executors.newFixedThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2), r -> {
                Thread t = new Thread(r, "Amnetic-Decode");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });

    private GlUploadQueue() {}

    public static void decode(Runnable task) {
        DECODE.submit(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                LOG.error("Amnetic: asset decode failed", t);
            }
        });
    }

    public static void submit(Runnable glTask) {
        QUEUE.add(glTask);
    }

    public static void drain(long budgetNanos) {
        long start = System.nanoTime();
        Runnable task;
        while ((task = QUEUE.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                LOG.error("Amnetic: GL upload task failed", t);
            }
            if (System.nanoTime() - start >= budgetNanos) {
                break;
            }
        }
    }

    public static boolean idle() {
        return QUEUE.isEmpty();
    }
}
