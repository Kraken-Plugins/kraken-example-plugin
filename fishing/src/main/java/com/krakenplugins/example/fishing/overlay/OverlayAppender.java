package com.krakenplugins.example.fishing.overlay;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.google.inject.Inject;

public class OverlayAppender extends AppenderBase<ILoggingEvent> {

    private final ScriptLogger scriptLogger;

    @Inject
    public OverlayAppender(ScriptLogger scriptLogger) {
        this.scriptLogger = scriptLogger;
        setName("OverlayAppender");
    }

    @Override
    protected void append(ILoggingEvent event) {
        // getFormattedMessage() resolves any {} placeholders, e.g.
        // log.info("Caught fish: {}", fishName) → "Caught fish: Lobster"
        String message = event.getFormattedMessage();
        Level level = event.getLevel();

        if (level.isGreaterOrEqual(Level.ERROR)) {
            scriptLogger.error(message);
        } else if (level.isGreaterOrEqual(Level.WARN)) {
            scriptLogger.warn(message);
        } else {
            scriptLogger.log(message);
        }
    }
}