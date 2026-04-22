package com.krakenplugins.example.jewelry;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.kraken.api.Context;
import com.kraken.api.input.mouse.VirtualMouse;
import com.kraken.api.input.mouse.strategy.MouseMovementStrategy;
import com.kraken.api.input.mouse.strategy.linear.LinearStrategy;
import com.kraken.api.overlay.MouseOverlay;
import com.kraken.api.query.gameobject.GameObjectEntity;
import com.kraken.api.service.tile.AreaService;
import com.kraken.api.service.tile.GameArea;
import com.kraken.api.service.util.price.ItemPrice;
import com.kraken.api.service.util.price.ItemPriceService;
import com.krakenplugins.example.jewelry.overlay.SceneOverlay;
import com.krakenplugins.example.jewelry.overlay.ScriptOverlay;
import com.krakenplugins.example.jewelry.script.JewelryScript;
import com.krakenplugins.example.jewelry.script.ScriptMetrics;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
@PluginDescriptor(
        name = "Jewelry Example Plugin",
        enabledByDefault = false,
        description = "Demonstrates an example of building a Jewelry automation plugin using the Kraken API.",
        tags = {"example", "automation", "kraken", "jewelry"}
)
public class JewelryPlugin extends Plugin {

    @Inject
    private JewelryScript jewelryScript;

    @Inject
    private Context ctx;

    @Getter
    @Inject
    private ClientThread clientThread;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ScriptOverlay scriptOverlay;

    @Inject
    private MouseOverlay mouseTrackerOverlay;

    @Inject
    private SceneOverlay sceneOverlay;

    @Inject
    private JewelryConfig config;

    @Inject
    private AreaService areaService;

    @Inject
    private ItemPriceService itemPriceService;

    @Getter
    @Setter
    private GameObjectEntity targetBankBooth;

    @Getter
    private GameArea edgevilleFurnace;

    @Getter
    private GameArea edgevilleBank;

    @Getter
    private GameArea grandExchange;

    @Getter
    private final List<WorldPoint> currentPath = new ArrayList<>();

    private final long startTime = System.currentTimeMillis();

    @Getter
    private final ScriptMetrics metrics = new ScriptMetrics();

    @Provides
    JewelryConfig provideConfig(final ConfigManager configManager) {
        return configManager.getConfig(JewelryConfig.class);
    }

    @Override
    protected void startUp() {
        ctx.initializePackets();

        WorldPoint[] furnace = {
                new WorldPoint(3105, 3502, 0),
                new WorldPoint(3105, 3496, 0),
                new WorldPoint(3111, 3496, 0),
                new WorldPoint(3111, 3502, 0)
        };
        edgevilleFurnace = areaService.createPolygonArea(Arrays.asList(furnace));
        WorldPoint[] bank = {
                new WorldPoint(3091, 3500, 0),
                new WorldPoint(3089, 3496, 0),
                new WorldPoint(3091, 3492, 0),
                new WorldPoint(3091, 3487, 0),
                new WorldPoint(3099, 3487, 0),
                new WorldPoint(3099, 3500, 0)
        };
        edgevilleBank = areaService.createPolygonArea(Arrays.asList(bank));
        WorldPoint[] ge = {
                new WorldPoint(3161, 3495, 0),
                new WorldPoint(3157, 3490, 0),
                new WorldPoint(3161, 3483, 0),
                new WorldPoint(3171, 3485, 0),
                new WorldPoint(3172, 3492, 0),
                new WorldPoint(3168, 3496, 0)
        };
        grandExchange = areaService.createPolygonArea(Arrays.asList(ge));

        jewelryScript.start();
        overlayManager.add(scriptOverlay);
        overlayManager.add(mouseTrackerOverlay);
        overlayManager.add(sceneOverlay);
    }

    @Override
    protected void shutDown() {
        jewelryScript.stop();
        overlayManager.remove(scriptOverlay);
        overlayManager.remove(mouseTrackerOverlay);
        overlayManager.remove(sceneOverlay);
    }

    @Subscribe
    private void onMenuOptionClicked(MenuOptionClicked event) {
        log.info("Option={}, Target={}, Param0={}, Param1={}, MenuAction={}, ItemId={}, id={}, itemOp={}, str={}",
                event.getMenuOption(), event.getMenuTarget(), event.getParam0(), event.getParam1(), event.getMenuAction().name(), event.getItemId(),
                event.getId(), event.getItemOp(), event);
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged event) {
        if(event.getGroup().equals("autojewelry")) {
            String key = event.getKey();

            if(key.equals("mouseMovementStrategy")) {
                VirtualMouse.setMouseMovementStrategy(config.mouseMovementStrategy());
                if(config.mouseMovementStrategy() == MouseMovementStrategy.REPLAY) {
                    VirtualMouse.loadLibrary(config.replayLibrary());
                }

                if(config.mouseMovementStrategy() == MouseMovementStrategy.LINEAR) {
                    LinearStrategy linear = (LinearStrategy) MouseMovementStrategy.LINEAR.getStrategy();
                    linear.setSteps(config.linearSteps());
                }
            }

        }
    }

    @Subscribe
    private void onStatChanged(StatChanged e) {
        if(e.getSkill() == Skill.CRAFTING) {
            metrics.setNecklacesCrafted(metrics.getNecklacesCrafted() + 1);
            updateEstimatedProfitAsync();
        }
    }

    /**
     * Fetches prices for all 3 components in parallel, calculates profit,
     * and updates the metrics object safely.
     */
    private void updateEstimatedProfitAsync() {
        int goldId = JewelryScript.GOLD_BAR;
        int gemId = config.jewelry().getSecondaryGemId();
        int craftedId = config.jewelry().getCraftedItemId();
        String userAgent = "ItemServiceAPI/1.0";

        // Create a Future for each item
        CompletableFuture<ItemPrice> goldFuture = getPriceFuture(goldId, userAgent);
        CompletableFuture<ItemPrice> gemFuture = getPriceFuture(gemId, userAgent);
        CompletableFuture<ItemPrice> productFuture = getPriceFuture(craftedId, userAgent);

        // Wait for ALL of them to complete
        CompletableFuture.allOf(goldFuture, gemFuture, productFuture).thenAccept(v -> {
            try {
                ItemPrice goldPrice = goldFuture.join();
                ItemPrice gemPrice = gemFuture.join();
                ItemPrice necklacePrice = productFuture.join();

                if (goldPrice == null || gemPrice == null || necklacePrice == null) {
                    log.warn("Could not calculate profit: Missing price data.");
                    return;
                }

                int avgGold = getAvg(goldPrice);
                int avgGem = getAvg(gemPrice);
                int avgNecklace = getAvg(necklacePrice);

                int profitPerNecklace = avgNecklace - (avgGold + avgGem);
                int totalCrafted = metrics.getNecklacesCrafted();
                int totalProfit = totalCrafted * profitPerNecklace;

                clientThread.invokeLater(() -> metrics.setEstimatedProfit(totalProfit));

            } catch (Exception e) {
                log.error("Error calculating profit", e);
            }
        });
    }

    /**
     * Helper to bridge your callback-based Service to a CompletableFuture.
     */
    private CompletableFuture<ItemPrice> getPriceFuture(int itemId, String userAgent) {
        CompletableFuture<ItemPrice> future = new CompletableFuture<>();

        // This will be very fast after the first call and synchronous because the items prices will be cached
        itemPriceService.getItemPrice(itemId, userAgent, future::complete);
        return future;
    }

    private int getAvg(ItemPrice item) {
        if (item == null) return 0;
        return item.getLow() + ((item.getHigh() - item.getLow()) / 2);
    }

    @Subscribe
    private void onGameStateChanged(final GameStateChanged event) {
        final GameState gameState = event.getGameState();
        switch (gameState) {
            case LOGGED_IN:
                startUp();
                break;
            case HOPPING:
            case LOGIN_SCREEN:
                shutDown();
            default:
                break;
        }
    }

    public String getRuntime() {
        long millis = System.currentTimeMillis() - startTime;
        return String.format("%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) % 60,
                TimeUnit.MILLISECONDS.toSeconds(millis) % 60);
    }

    public String getStatus() {
        return jewelryScript.getStatus();
    }
}
