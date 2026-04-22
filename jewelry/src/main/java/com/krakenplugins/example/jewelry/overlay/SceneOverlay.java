package com.krakenplugins.example.jewelry.overlay;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.Context;
import com.kraken.api.query.gameobject.GameObjectEntity;
import com.krakenplugins.example.jewelry.JewelryConfig;
import com.krakenplugins.example.jewelry.JewelryPlugin;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;

@Singleton
public class SceneOverlay extends Overlay {
    private final Client client;
    private final JewelryPlugin plugin;
    private final Context ctx;
    private final JewelryConfig config;
    private final ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    public SceneOverlay(Client client, Context ctx, JewelryPlugin plugin, JewelryConfig config, ModelOutlineRenderer modelOutlineRenderer) {
        this.client = client;
        this.plugin = plugin;
        this.ctx = ctx;
        this.config = config;
        this.modelOutlineRenderer = modelOutlineRenderer;

        this.setPosition(OverlayPosition.DYNAMIC);
        this.setLayer(OverlayLayer.ABOVE_WIDGETS);
        this.setPriority(OverlayPriority.HIGH);
    }

    public Dimension render(Graphics2D graphics) {
        if (this.client.getCanvas() == null) {
            return null;
        }

        if(config.debug()) {
            renderDebug(graphics);
        }

        if(config.targetBankBooth()) {
            renderTargetBankBooth();
        }

        return null;
    }

    private void renderTargetBankBooth() {
        GameObjectEntity booth = plugin.getTargetBankBooth();
        if(booth != null && booth.raw() != null) {
            modelOutlineRenderer.drawOutline(booth.raw(), 2, Color.GREEN, 2);
        }
    }

    private void renderDebug(Graphics2D graphics) {
        boolean inArea = ctx.players().local().isInArea(plugin.getEdgevilleBank());
        boolean inFurnace = ctx.players().local().isInArea(plugin.getEdgevilleFurnace());
        boolean inGe = ctx.players().local().isInArea(plugin.getGrandExchange());

        Color outline = inArea ? Color.GREEN : Color.RED;
        Color fill = inArea ? new Color(18, 227, 61, 20) : new Color(223, 41, 41, 20);

        plugin.getEdgevilleBank().render(client, graphics, fill, false);
        plugin.getEdgevilleBank().render(client, graphics, outline, true);

        Color furnaceOutline = inFurnace ? Color.GREEN : Color.RED;
        Color furnaceFill = inFurnace ? new Color(18, 227, 61, 20) : new Color(223, 41, 41, 20);

        plugin.getEdgevilleFurnace().render(client, graphics, furnaceFill, false);
        plugin.getEdgevilleFurnace().render(client, graphics, furnaceOutline, true);

        Color geOutline = inGe ? Color.GREEN : Color.RED;
        Color geFill = inGe ? new Color(18, 227, 61, 20) : new Color(223, 41, 41, 20);

        plugin.getGrandExchange().render(client, graphics, geOutline, false);
        // plugin.getGrandExchange().render(client, graphics, geFill, true);
    }
}
