package com.krakenplugins.example.fishing.overlay;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.krakenplugins.example.fishing.FishingConfig;
import com.krakenplugins.example.fishing.FishingPlugin;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import java.awt.*;

@Singleton
public class ScriptOverlay extends OverlayPanel {

    private static final Color HEADER_COLOR  = Color.CYAN;
    private static final Color INFO_COLOR    = Color.LIGHT_GRAY;
    private static final Color WARN_COLOR    = Color.YELLOW;
    private static final Color ERROR_COLOR   = new Color(255, 80, 80);

    private final FishingPlugin plugin;
    private final ScriptLogger scriptLogger;
    private final FishingConfig config;

    @Inject
    private ScriptOverlay(FishingPlugin plugin, ScriptLogger scriptLogger, FishingConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.scriptLogger = scriptLogger;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        // ── Header ──────────────────────────────────────────────────────────
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Auto Fisher")
                .color(HEADER_COLOR)
                .build());

        panelComponent.getChildren().add(TitleComponent.builder().text("").build());

        // ── Stats ────────────────────────────────────────────────────────────
        addLine("Status",      plugin.getStatus(),    Color.WHITE);
        addLine("Runtime",     plugin.getRuntime(),   Color.WHITE);
        addLine("Fish Caught", String.valueOf(plugin.getFishCaught()), Color.WHITE);


        if(config.showLog()) {
            java.util.List<LogEntry> entries = scriptLogger.getEntries();
            if (!entries.isEmpty()) {
                panelComponent.getChildren().add(TitleComponent.builder()
                        .text("Log")
                        .color(HEADER_COLOR)
                        .build());
                panelComponent.getChildren().add(TitleComponent.builder().text("").build());

                for (LogEntry entry : entries) {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("[" + entry.getTimestamp() + "]")
                            .leftColor(Color.GRAY)
                            .right(entry.getMessage())
                            .rightColor(colorFor(entry.getLevel()))
                            .build());
                }
            }
        }

        return super.render(graphics);
    }

    private void addLine(String label, String value, Color valueColor) {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(label + ":")
                .leftColor(Color.GRAY)
                .right(value)
                .rightColor(valueColor)
                .build());
    }

    private static Color colorFor(LogLevel level) {
        switch (level) {
            case WARN:  return WARN_COLOR;
            case ERROR: return ERROR_COLOR;
            default:    return INFO_COLOR;
        }
    }
}
