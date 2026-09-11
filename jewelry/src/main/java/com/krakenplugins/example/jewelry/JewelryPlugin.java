package com.krakenplugins.example.jewelry;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.kraken.api.Context;
import com.kraken.api.input.mouse.VirtualMouse;
import com.kraken.api.input.mouse.strategy.MouseMovementStrategy;
import com.kraken.api.input.mouse.strategy.linear.LinearStrategy;
import com.kraken.api.overlay.GlobalPathfinderOverlay;
import com.kraken.api.overlay.MouseOverlay;
import com.kraken.api.query.container.ContainerItem;
import com.kraken.api.query.gameobject.GameObjectEntity;
import com.kraken.api.service.pathfinding.GlobalPathfinderConfig;
import com.kraken.api.service.tile.AreaService;
import com.kraken.api.service.tile.GameArea;
import com.kraken.api.service.util.price.ItemPrice;
import com.kraken.api.service.util.price.ItemPriceService;
import com.kraken.api.service.walker.WalkResult;
import com.kraken.api.service.walker.WalkerConfig;
import com.krakenplugins.example.jewelry.overlay.SceneOverlay;
import com.krakenplugins.example.jewelry.overlay.ScriptOverlay;
import com.krakenplugins.example.jewelry.script.JewelryScript;
import com.krakenplugins.example.jewelry.script.ScriptMetrics;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import java.util.Arrays;
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

    // Edgeville and the Grand Exchange are a short walk apart, so every form of transport is turned
    // off: a teleport or a spirit tree would be slower than walking and far more conspicuous. The
    // route hugs the wilderness wall, so the planner is told to stay out of it.
    public static final WalkerConfig WALK_CONFIG = WalkerConfig.builder()
            .pathfinderConfig(GlobalPathfinderConfig.builder()
                    .avoidWilderness(true)
                    .useAgilityShortcuts(false)
                    .useCanoes(false)
                    .useMinecarts(false)
                    .useSpiritTrees(false)
                    .useTeleportationLevers(false)
                    .useTeleportationMinigames(false)
                    .useTeleportationPortalsPoh(false)
                    .useTeleportationSpells(false)
                    .build())
            .build();

    private static final String PRICE_USER_AGENT = "ItemServiceAPI/1.0";

    @Inject
    private JewelryScript jewelryScript;

    @Inject
    private Context ctx;

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

    @Inject
    private GlobalPathfinderOverlay globalPathfinderOverlay;

    /**
     * -- SETTER --
     *  Records the bank booth the script is walking to, for the scene overlay to outline.
     *
     * @param booth The booth being clicked, or {@code null} once the script has moved on.
     */
    @Setter
    @Getter
    private volatile GameObjectEntity targetBankBooth;

    @Getter
    private volatile GameArea edgevilleFurnace;

    @Getter
    private volatile GameArea edgevilleBank;

    @Getter
    private volatile GameArea grandExchange;

    private volatile long startTime;

    private volatile String haltReason;

    // The crafting experience last seen, so the login stat snapshot is not counted as a necklace.
    private volatile int craftingExperience;

    @Getter
    private final ScriptMetrics metrics = new ScriptMetrics();

    @Provides
    JewelryConfig provideConfig(final ConfigManager configManager) {
        return configManager.getConfig(JewelryConfig.class);
    }

    @Override
    protected void startUp() {
        applyMouseConfig();

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

        startTime = System.currentTimeMillis();
        craftingExperience = 0;
        haltReason = null;
        metrics.reset();

        overlayManager.add(scriptOverlay);
        overlayManager.add(mouseTrackerOverlay);
        overlayManager.add(sceneOverlay);
        if (config.showCurrentPath()) {
            overlayManager.add(globalPathfinderOverlay);
        }

        if (ctx.getClient().getGameState() == GameState.LOGGED_IN) {
            jewelryScript.start();
        }
    }

    @Override
    protected void shutDown() {
        jewelryScript.stop();
        overlayManager.remove(scriptOverlay);
        overlayManager.remove(mouseTrackerOverlay);
        overlayManager.remove(sceneOverlay);
        overlayManager.remove(globalPathfinderOverlay);
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals("autojewelry")) {
            return;
        }

        switch (event.getKey()) {
            case "mouseMovementStrategy":
            case "replayLibrary":
            case "linearSteps":
                applyMouseConfig();
                break;
            case "showCurrentPath":
                if (config.showCurrentPath()) {
                    overlayManager.add(globalPathfinderOverlay);
                } else {
                    overlayManager.remove(globalPathfinderOverlay);
                }
                break;
            default:
                break;
        }
    }

    @Subscribe
    private void onStatChanged(StatChanged e) {
        if (e.getSkill() != Skill.CRAFTING) {
            return;
        }

        // The client posts a StatChanged for every skill once stats load, so the first reading is a
        // baseline rather than a necklace.
        int experience = e.getXp();
        if (craftingExperience > 0 && experience > craftingExperience) {
            metrics.setNecklacesCrafted(metrics.getNecklacesCrafted() + 1);
            updateEstimatedProfitAsync();
        }
        craftingExperience = experience;
    }

    @Subscribe
    private void onGameStateChanged(final GameStateChanged event) {
        // Covers enabling the plugin while logged out; start() is a no-op when already running
        if (event.getGameState() == GameState.LOGGED_IN) {
            jewelryScript.start();
        }
    }

    /**
     * Applies the configured mouse movement strategy. Called from {@code startUp()} as well as on a
     * config change, otherwise the strategy stays on the API default until the setting is toggled.
     */
    private void applyMouseConfig() {
        VirtualMouse.setMouseMovementStrategy(config.mouseMovementStrategy());

        if (config.mouseMovementStrategy() == MouseMovementStrategy.REPLAY) {
            VirtualMouse.loadLibrary(config.replayLibrary());
        }

        if (config.mouseMovementStrategy() == MouseMovementStrategy.LINEAR) {
            LinearStrategy linear = (LinearStrategy) MouseMovementStrategy.LINEAR.getStrategy();
            linear.setSteps(config.linearSteps());
        }
    }

    /**
     * Moves the cursor onto a booth, a furnace or anything else in the scene before it is clicked,
     * when the config asks for a visible cursor. Actions are dispatched at their own clickbox either
     * way, so this only decides whether the cursor travels there first.
     *
     * @param target What is about to be interacted with.
     */
    public void moveMouseTo(TileObject target) {
        if (config.useMouse()) {
            ctx.getMouse().move(target);
        }
    }

    /**
     * Moves the cursor onto an inventory item before it is clicked, when the config asks for a
     * visible cursor.
     *
     * @param target What is about to be interacted with.
     */
    public void moveMouseTo(ContainerItem target) {
        if (config.useMouse()) {
            ctx.getMouse().move(target);
        }
    }

    /**
     * Moves the cursor onto an interface component before it is clicked, when the config asks for a
     * visible cursor.
     *
     * @param target What is about to be interacted with.
     */
    public void moveMouseTo(Widget target) {
        if (config.useMouse()) {
            ctx.getMouse().move(target);
        }
    }

    /**
     * Stops the script and reports why on the overlay. This is for states the script cannot work its
     * way out of by retrying, such as a bank with no gold bars left in it. Re-enable the plugin to
     * start again.
     *
     * @param reason Shown in place of the task status on the script overlay.
     */
    public void halt(String reason) {
        log.warn("Halting the jewelry script: {}", reason);
        haltReason = reason;
        jewelryScript.pause();
    }

    /**
     * Reports a walk that did not arrive and returns how long the calling task should wait. A stall
     * or a timeout is worth another attempt, since the walker re-plans from scratch every call. A
     * destination it cannot route to reads the same on every attempt, so the script halts instead of
     * retrying forever.
     *
     * @param result What the walker returned.
     * @param destination Where the walk was headed, named for the log and the overlay.
     * @return The number of milliseconds the calling task should sleep for.
     */
    public int reportWalkFailure(WalkResult result, String destination) {
        switch (result.getOutcome()) {
            case NO_ROUTE:
            case IN_INSTANCE:
            case CALLED_ON_CLIENT_THREAD:
            case TRANSPORT_REQUIREMENTS_UNMET:
            case TRANSPORT_UNSUPPORTED:
                halt("Cannot walk to " + destination + ": " + result.getReason());
                return 0;
            default:
                log.warn("Walk to {} ended: {}", destination, result);
                return 1000;
        }
    }

    /**
     * Fetches prices for all 3 components in parallel, calculates profit,
     * and updates the metrics object safely.
     */
    private void updateEstimatedProfitAsync() {
        CompletableFuture<ItemPrice> goldFuture = getPriceFuture(JewelryScript.GOLD_BAR);
        CompletableFuture<ItemPrice> gemFuture = getPriceFuture(config.jewelry().getSecondaryGemId());
        CompletableFuture<ItemPrice> productFuture = getPriceFuture(config.jewelry().getCraftedItemId());

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

                int profitPerNecklace = getAvg(necklacePrice) - (getAvg(goldPrice) + getAvg(gemPrice));
                metrics.setEstimatedProfit(metrics.getNecklacesCrafted() * profitPerNecklace);
            } catch (Exception e) {
                log.error("Error calculating profit", e);
            }
        });
    }

    /**
     * Helper to bridge the callback-based price service to a CompletableFuture.
     */
    private CompletableFuture<ItemPrice> getPriceFuture(int itemId) {
        CompletableFuture<ItemPrice> future = new CompletableFuture<>();

        // This will be very fast after the first call and synchronous because the items prices will be cached
        itemPriceService.getItemPrice(itemId, PRICE_USER_AGENT, future::complete);
        return future;
    }

    private int getAvg(ItemPrice item) {
        return item.getLow() + ((item.getHigh() - item.getLow()) / 2);
    }

    public String getRuntime() {
        long millis = System.currentTimeMillis() - startTime;
        return String.format("%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) % 60,
                TimeUnit.MILLISECONDS.toSeconds(millis) % 60);
    }

    public String getStatus() {
        return haltReason != null ? haltReason : jewelryScript.getStatus();
    }
}
