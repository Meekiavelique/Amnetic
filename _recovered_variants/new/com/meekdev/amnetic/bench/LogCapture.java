package com.meekdev.amnetic.bench;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

final class LogCapture extends AbstractAppender {

    static final LogCapture INSTANCE = new LogCapture();

    private int warnings;
    private int errors;
    private final List<String> samples = new ArrayList<>();
    private boolean installed;

    private LogCapture() {
        super("AmneticBenchCapture", null, null, true, Property.EMPTY_ARRAY);
    }

    void install() {
        if (installed) return;
        installed = true;
        start();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().getRootLogger().addAppender(this, Level.WARN, null);
        ctx.updateLoggers();
    }

    synchronized void reset() {
        warnings = 0;
        errors = 0;
        samples.clear();
    }

    synchronized int warnings() { return warnings; }

    synchronized int errors() { return errors; }

    synchronized List<String> samples() { return List.copyOf(samples); }

    @Override
    public synchronized void append(LogEvent event) {
        String logger = event.getLoggerName();
        if (!fromAmnetic(logger, event.getThrown())) return;
        if (event.getLevel().isMoreSpecificThan(Level.ERROR)) errors++;
        else if (event.getLevel().isMoreSpecificThan(Level.WARN)) warnings++;
        else return;
        if (samples.size() < 6) {
            samples.add(event.getLevel() + " " + logger + ": " + event.getMessage().getFormattedMessage()
                    + (event.getThrown() != null ? " (" + event.getThrown() + ")" : ""));
        }
    }

    private static boolean fromAmnetic(String logger, Throwable thrown) {
        if (logger != null && (logger.toLowerCase().contains("amnetic") || logger.startsWith("com.meekdev"))) return true;
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            for (StackTraceElement e : t.getStackTrace()) {
                if (e.getClassName().startsWith("com.meekdev.amnetic")) return true;
            }
        }
        return false;
    }
}
