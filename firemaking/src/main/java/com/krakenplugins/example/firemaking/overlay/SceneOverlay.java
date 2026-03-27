package com.krakenplugins.example.firemaking.overlay;

import com.google.inject.Inject;
import com.kraken.api.Context;
import com.kraken.api.core.AbstractEntity;
import com.krakenplugins.example.firemaking.FiremakingConfig;
import com.krakenplugins.example.firemaking.FiremakingPlugin;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;
import java.util.stream.Collectors;

public class SceneOverlay extends Overlay {

    private final FiremakingPlugin plugin;
    private final FiremakingConfig config;
    private final Context ctx;
    private final ModelOutlineRenderer modelOutlineRenderer;
    private final Client client;

    @Inject
    public SceneOverlay(FiremakingPlugin plugin, Client client, FiremakingConfig config, Context ctx, ModelOutlineRenderer modelOutlineRenderer) {
        this.plugin = plugin;
        this.config = config;
        this.ctx = ctx;
        this.client = client;
        this.modelOutlineRenderer = modelOutlineRenderer;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    public Dimension render(Graphics2D graphics) {
        if (config.debug()) {
            renderDebug(graphics);
        }

        if (config.showBanker()) {
            renderTargetBanker();
        }

        if (config.showTargetFire()) {
            renderTargetFire();
        }

        return null;
    }

    private void renderTargetFire() {
        GameObject fire = plugin.getTargetFire();
        if (fire != null) {
            modelOutlineRenderer.drawOutline(fire, 2, Color.GREEN, 2);
        }
    }

    private void renderTargetBanker() {
        NPC banker = plugin.getTargetBanker();
        if (banker != null) {
            modelOutlineRenderer.drawOutline(banker, 2, Color.GREEN, 2);
        }
    }

    private void renderDebug(Graphics2D graphics) {
        boolean inArea = ctx.players().local().isInArea(plugin.getBankLocation());

        Color outline = inArea ? Color.GREEN : Color.RED;
        Color fill = inArea ? new Color(18, 227, 61, 20) : new Color(223, 41, 41, 20);

        plugin.getBankLocation().render(client, graphics, fill, false);
        plugin.getBankLocation().render(client, graphics, outline, true);


        LocalPoint localPoint = ctx.players().local().localLocation();

        // 49927 foresters
        // 26185 regular

        // Also render other nearby found fires which are not the target fire
        java.util.List<GameObject> fires = ctx.gameObjects()
                .withId(26185)
                .within(10).stream().map(AbstractEntity::raw).collect(Collectors.toList());

        for (GameObject fire : fires) {
            if (fire != plugin.getTargetFire()) {
                int distance = localPoint.distanceTo(fire.getLocalLocation()) / Perspective.LOCAL_TILE_SIZE;

                String overlayText = String.format("Dist: %d", distance);
                net.runelite.api.Point textLocation = fire.getCanvasTextLocation(graphics, overlayText, 0);

                if (textLocation != null) {
                    Color color = Color.CYAN;
                    OverlayUtil.renderTextLocation(graphics, textLocation, overlayText, color);

                    if (fire.getClickbox() != null) {
                        OverlayUtil.renderPolygon(graphics, fire.getClickbox(), color);
                    }
                }
            }
        }


        java.util.List<GameObject> foresterFires = ctx.gameObjects()
                .withId(49927)
                .within(10).stream().map(AbstractEntity::raw).collect(Collectors.toList());

        for (GameObject fire : foresterFires) {
            if (fire != plugin.getTargetFire()) {
                int distance = localPoint.distanceTo(fire.getLocalLocation()) / Perspective.LOCAL_TILE_SIZE;

                String overlayText = String.format("Dist: %d", distance);
                net.runelite.api.Point textLocation = fire.getCanvasTextLocation(graphics, overlayText, 0);

                if (textLocation != null) {
                    Color color = Color.MAGENTA;
                    OverlayUtil.renderTextLocation(graphics, textLocation, overlayText, color);

                    if (fire.getClickbox() != null) {
                        OverlayUtil.renderPolygon(graphics, fire.getClickbox(), color);
                    }
                }
            }
        }
    }
}