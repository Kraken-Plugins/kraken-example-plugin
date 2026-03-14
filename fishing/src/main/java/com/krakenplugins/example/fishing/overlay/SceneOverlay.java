package com.krakenplugins.example.fishing.overlay;


import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.Context;
import com.kraken.api.query.npc.NpcEntity;
import com.kraken.api.service.pathfinding.LocalPathfinder;
import com.krakenplugins.example.fishing.FishingConfig;
import com.krakenplugins.example.fishing.FishingPlugin;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;
import java.util.List;

@Singleton
public class SceneOverlay extends Overlay {
    private final Client client;
    private final FishingPlugin plugin;
    private final Context ctx;
    private final ModelOutlineRenderer modelOutlineRenderer;
    private final FishingConfig config;
    private final LocalPathfinder localPathfinder;

    @Inject
    public SceneOverlay(Client client, Context ctx, FishingPlugin plugin, ModelOutlineRenderer modelOutlineRenderer, FishingConfig config, LocalPathfinder localPathfinder) {
        this.client = client;
        this.plugin = plugin;
        this.ctx = ctx;
        this.localPathfinder = localPathfinder;
        this.modelOutlineRenderer = modelOutlineRenderer;
        this.config = config;

        this.setPosition(OverlayPosition.DYNAMIC);
        this.setLayer(OverlayLayer.ABOVE_WIDGETS);
        this.setPriority(OverlayPriority.HIGH);
    }

    public Dimension render(Graphics2D graphics) {
        if (this.client.getCanvas() == null) {
            return null;
        }

        if(config.highlightTargetSpot()) {
            renderTargetSpot();
        }

        if(config.debug()) {
            renderNearbySpots(graphics);
        }

        if(config.highlightCurrentPath()) {
            localPathfinder.renderPath(plugin.getCurrentPath(), graphics, new Color(24, 191, 243));
        }

        return null;
    }

    private void renderTargetSpot() {
        if(plugin.getTargetSpot() != null && plugin.getTargetSpot().raw() != null) {
            modelOutlineRenderer.drawOutline(plugin.getTargetSpot().raw(), 2, Color.GREEN, 2);
        }
    }

    private void renderNearbySpots(Graphics2D graphics) {
        if (client.getLocalPlayer() == null) {
            return;
        }
        LocalPoint localPoint = client.getLocalPlayer().getLocalLocation();
        if (localPoint != null) {
            List<NpcEntity> spots = ctx.npcs()
                    .within(10)
                    .reachable()
                    .nameContains("fishing spot")
                    .list();

            for(NpcEntity spot : spots) {
                int distance = localPoint.distanceTo(spot.raw().getLocalLocation()) / Perspective.LOCAL_TILE_SIZE;
                String overlayText = String.format("Dist: %d", distance);
                net.runelite.api.Point textLocation = spot.raw().getCanvasTextLocation(graphics, overlayText, 0);

                if (textLocation != null) {
                    Color color;

                    if(plugin.getTargetSpot() != null) {
                        if (spot.raw().getWorldLocation().getX() == plugin.getTargetSpot().raw().getWorldLocation().getX() &&
                                spot.raw().getWorldLocation().getY() == plugin.getTargetSpot().raw().getWorldLocation().getY()) {
                            color = Color.GREEN;
                        } else {
                            color = Color.CYAN;
                        }
                    } else {
                        color = Color.CYAN;
                    }

                    OverlayUtil.renderTextLocation(graphics, textLocation, overlayText, color);

                    if (spot.raw() != null) {
                        Shape clickbox = spot.raw().getConvexHull();
                        if (clickbox != null) {
                            graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 100));
                            graphics.fill(clickbox);
                            graphics.setColor(color);
                            graphics.draw(clickbox);
                        }
                    }
                }
            }
        }
    }
}