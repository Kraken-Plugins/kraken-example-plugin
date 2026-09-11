package com.krakenplugins.example.mining;

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
import com.kraken.api.service.pathfinding.GlobalPathfinderConfig;
import com.kraken.api.service.tile.AreaService;
import com.kraken.api.service.tile.GameArea;
import com.kraken.api.service.walker.WalkResult;
import com.kraken.api.service.walker.WalkerConfig;
import com.krakenplugins.example.mining.overlay.SceneOverlay;
import com.krakenplugins.example.mining.overlay.ScriptOverlay;
import com.krakenplugins.example.mining.script.MiningScript;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
@PluginDescriptor(
        name = "Mining Example Plugin",
        enabledByDefault = false,
        description = "Demonstrates an example of building a Mining automation plugin using the Kraken API.",
        tags = {"example", "automation", "kraken", "mining"}
)
public class MiningPlugin extends Plugin {

    public static final WorldPoint BANK_LOCATION = new WorldPoint(3253, 3421, 0);
    public static final WorldPoint MINE_LOCATION = new WorldPoint(3287, 3367, 0);

    public static final List<Integer> IRON_ROCKS = List.of(ObjectID.IRONROCK1, ObjectID.IRONROCK2);

    // The spent rock left behind after mining. Clay and coal rocks deplete into the same two objects,
    // so these only mean "mined out iron" alongside a check that we are in the iron mine.
    public static final List<Integer> DEPLETED_ROCKS = List.of(ObjectID.ROCKS1, ObjectID.ROCKS2);

    public static final int BANK_BOOTH = ObjectID.FAI_VARROCK_BANKBOOTH;

    // The mine and the bank are a short walk apart, so every form of transport is turned off: taking a
    // spirit tree or casting a teleport to cross Varrock would be slower and far more conspicuous.
    // Canoes and minigame teleports are off because the walker cannot operate them, and a route
    // through one would only fail once the player was standing at it.
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

    // The message printed for every ore that reaches the inventory. The game sends it as filterable
    // spam, and as a plain game message once the chat filter is off.
    private static final String MINED_ORE_MESSAGE = "You manage to mine";

    @Inject
    private MiningScript miningScript;

    @Inject
    private Context ctx;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ScriptOverlay scriptOverlay;

    @Inject
    private MouseOverlay mouseTrackerOverlay;

    @Inject
    private GlobalPathfinderOverlay globalPathfinderOverlay;

    @Inject
    private SceneOverlay sceneOverlay;

    @Inject
    private MiningConfig config;

    @Inject
    private AreaService areaService;

    private volatile long startTime;

    private volatile String haltReason;

    @Getter
    @Setter
    private volatile GameObject targetRock;

    @Getter
    private volatile int oreMined;

    @Getter
    private volatile GameArea varrockBank;

    @Getter
    private volatile GameArea miningArea;

    @Provides
    MiningConfig provideConfig(final ConfigManager configManager) {
        return configManager.getConfig(MiningConfig.class);
    }

    @Override
    protected void startUp() {
        applyMouseConfig();

        // createPolygonArea rasterizes with java.awt.Polygon, whose insideness rule excludes the
        // maximum x and y edges, so the north and east vertices sit one tile outside the bank floor
        // they enclose.
        WorldPoint[] bankArea = {
                new WorldPoint(3259, 3425, 0),
                new WorldPoint(3259, 3418, 0),
                new WorldPoint(3249, 3418, 0),
                new WorldPoint(3249, 3425, 0)
        };
        varrockBank = areaService.createPolygonArea(bankArea);

        WorldPoint[] minePath = {
                new WorldPoint(3280, 3371, 0),
                new WorldPoint(3281, 3369, 0),
                new WorldPoint(3281, 3365, 0),
                new WorldPoint(3277, 3362, 0),
                new WorldPoint(3278, 3359, 0),
                new WorldPoint(3283, 3359, 0),
                new WorldPoint(3284, 3360, 0),
                new WorldPoint(3287, 3360, 0),
                new WorldPoint(3291, 3356, 0),
                new WorldPoint(3295, 3357, 0),
                new WorldPoint(3295, 3360, 0),
                new WorldPoint(3293, 3363, 0),
                new WorldPoint(3292, 3365, 0),
                new WorldPoint(3292, 3372, 0)
        };
        miningArea = areaService.createPolygonArea(minePath);

        startTime = System.currentTimeMillis();
        oreMined = 0;
        haltReason = null;

        overlayManager.add(scriptOverlay);
        overlayManager.add(mouseTrackerOverlay);
        overlayManager.add(sceneOverlay);
        if (config.renderPath()) {
            overlayManager.add(globalPathfinderOverlay);
        }

        if (ctx.getClient().getGameState() == GameState.LOGGED_IN) {
            miningScript.start();
        }
    }

    @Override
    protected void shutDown() {
        miningScript.stop();
        overlayManager.remove(scriptOverlay);
        overlayManager.remove(mouseTrackerOverlay);
        overlayManager.remove(sceneOverlay);
        overlayManager.remove(globalPathfinderOverlay);
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals("autominer")) {
            return;
        }

        switch (event.getKey()) {
            case "mouseMovementStrategy":
            case "replayLibrary":
            case "linearSteps":
                applyMouseConfig();
                break;
            case "renderPath":
                if (config.renderPath()) {
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
    private void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.SPAM && event.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }

        if (event.getMessage().startsWith(MINED_ORE_MESSAGE)) {
            oreMined += 1;
        }
    }

    @Subscribe
    private void onGameStateChanged(final GameStateChanged event) {
        // Covers enabling the plugin while logged out; start() is a no-op when already running
        if (event.getGameState() == GameState.LOGGED_IN) {
            miningScript.start();
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
     * Moves the cursor onto a rock, a booth or anything else in the scene before it is clicked, when
     * the config asks for a visible cursor. Actions are dispatched at their own clickbox either way,
     * so this only decides whether the cursor travels there first.
     *
     * @param target What is about to be interacted with.
     */
    public void moveMouseTo(TileObject target) {
        if (config.useMouseMovement()) {
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
        if (config.useMouseMovement()) {
            ctx.getMouse().move(target);
        }
    }

    /**
     * Stops the script and reports why on the overlay. This is for states the script cannot work its
     * way out of by retrying, such as a full inventory holding nothing the bank will take. Re-enable
     * the plugin to start again.
     *
     * @param reason Shown in place of the task status on the script overlay.
     */
    public void halt(String reason) {
        log.warn("Halting the mining script: {}", reason);
        haltReason = reason;
        miningScript.pause();
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

    public String getRuntime() {
        long millis = System.currentTimeMillis() - startTime;
        return String.format("%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) % 60,
                TimeUnit.MILLISECONDS.toSeconds(millis) % 60);
    }

    public String getStatus() {
        return haltReason != null ? haltReason : miningScript.getStatus();
    }
}
