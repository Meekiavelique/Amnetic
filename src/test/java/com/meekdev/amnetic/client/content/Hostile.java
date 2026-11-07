package com.meekdev.amnetic.client.content;

public final class Hostile {
    private static final long FAST_MILLIS = 2000;

    public interface Sample {
        void run() throws Exception;
    }

    private Hostile() {}

    public static void refused(String label, String reason, Sample sample) {
        long start = System.nanoTime();
        Throwable thrown = null;
        try {
            sample.run();
        } catch (Exception e) {
            thrown = e;
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        require(thrown != null, label + ": accepted");
        require(millis < FAST_MILLIS, label + ": took " + millis + " ms to refuse");
        String message = messages(thrown);
        require(message.contains(reason), label + ": refused for another reason: " + message);
        System.out.println("PASS: " + label + " refused in " + millis + " ms (" + reason + ")");
    }

    public static void require(boolean holds, String label) {
        if (!holds) throw new AssertionError(label);
    }

    private static String messages(Throwable thrown) {
        StringBuilder all = new StringBuilder();
        for (Throwable at = thrown; at != null; at = at.getCause()) all.append(at.getMessage()).append(" / ");
        return all.toString();
    }
}
