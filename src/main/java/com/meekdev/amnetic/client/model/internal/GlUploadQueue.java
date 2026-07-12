package com.meekdev.amnetic.client.model.internal;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * streams GPU asset work so a heavy scene doesn't hitch the frame it first appears.
 * CPU-side decode runs on worker threads, the GL uploads are queued and drained on the
 * render thread under a per-frame time budget so a big batch spreads over a few frames.
 * consumers that aren't uploaded yet just pop in when their task runs
 */
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

    // runs CPU-side decode off the render thread, the task should end by submitting the GL upload
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

    // render thread only, runs queued uploads until budgetNanos is exceeded, at least one per call
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
