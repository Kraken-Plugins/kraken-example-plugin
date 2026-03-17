package com.krakenplugins.example.fishing.overlay;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.overlay.log.LogOverlayComponent;
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
    private final FishingConfig config;
    private LogOverlayComponent logOverlayComponent;


    @Inject
    private ScriptOverlay(FishingPlugin plugin,  FishingConfig config, LogOverlayComponent logOverlayComponent) {
        this.plugin = plugin;
        this.config = config;
        this.logOverlayComponent = logOverlayComponent;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Auto Fisher")
                .color(HEADER_COLOR)
                .build());

        panelComponent.getChildren().add(TitleComponent.builder().text("").build());

        addLine("Status",      plugin.getStatus(),    Color.WHITE);
        addLine("Runtime",     plugin.getRuntime(),   Color.WHITE);
        addLine("Fish Caught", String.valueOf(plugin.getFishCaught()), Color.WHITE);

        if(config.showLog()) {
            logOverlayComponent.addTo(panelComponent);
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
}
