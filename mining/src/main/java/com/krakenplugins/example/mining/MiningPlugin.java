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
import com.kraken.api.service.tile.AreaService;
import com.kraken.api.service.tile.GameArea;
import com.krakenplugins.example.mining.overlay.SceneOverlay;
import com.krakenplugins.example.mining.overlay.ScriptOverlay;
import com.krakenplugins.example.mining.script.MiningScript;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.FakeXpDrop;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import java.util.ArrayList;
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
    public static final List<Integer> IRON_ORE_GAME_OBJECTS = List.of(11365, 11364);
    public static final List<Integer> IRON_ORE_DEPLETED_GAME_OBJECTS= List.of(11391, 11390);
    public static final int BANK_BOOTH_GAME_OBJECT = 10583;

    @Inject
    private MiningScript miningScript;

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
    private GlobalPathfinderOverlay globalPathfinderOverlay;

    @Inject
    private SceneOverlay sceneOverlay;

    @Inject
    private MiningConfig config;

    @Inject
    private AreaService areaService;

    private final long startTime = System.currentTimeMillis();

    @Getter
    @Setter
    private GameObject targetRock;

    @Getter
    private int oreMined;

    @Getter
    private final List<WorldPoint> currentPath = new ArrayList<>();

    @Getter
    private GameArea varrockBank;

    @Getter
    private GameArea miningArea;

    @Provides
    MiningConfig provideConfig(final ConfigManager configManager) {
        return configManager.getConfig(MiningConfig.class);
    }

    @Override
    protected void startUp() {
        ctx.initializePackets();

        if(config.renderPath()) {
            overlayManager.add(globalPathfinderOverlay);
        }

        WorldPoint[] bankArea = {
                new WorldPoint(3258, 3424, 0),
                new WorldPoint(3258, 3418, 0),
                new WorldPoint(3249, 3418, 0),
                new WorldPoint(3249, 3424, 0)
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

        miningScript.start();

        overlayManager.add(scriptOverlay);
        overlayManager.add(mouseTrackerOverlay);
        overlayManager.add(sceneOverlay);
    }

    @Override
    protected void shutDown() {
        miningScript.stop();
        overlayManager.remove(scriptOverlay);
        overlayManager.remove(mouseTrackerOverlay);
        overlayManager.remove(sceneOverlay);
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged event) {
        if(event.getGroup().equals("autominer")) {
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

            if(key.equals("renderPath")) {
                if(config.renderPath()) {
                    overlayManager.remove(globalPathfinderOverlay);
                } else {
                    overlayManager.remove(globalPathfinderOverlay);
                }
            }

        }
    }

    @Subscribe
    private void onFakeXpDrop(FakeXpDrop event) {
        if(event.getSkill() == Skill.MINING) {
            oreMined += 1;
        }
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
        return miningScript.getStatus();
    }
}
