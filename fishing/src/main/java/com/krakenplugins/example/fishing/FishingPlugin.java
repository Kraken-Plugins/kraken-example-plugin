package com.krakenplugins.example.fishing;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.kraken.api.Context;
import com.kraken.api.input.mouse.VirtualMouse;
import com.kraken.api.input.mouse.strategy.MouseMovementStrategy;
import com.kraken.api.input.mouse.strategy.linear.LinearStrategy;
import com.kraken.api.overlay.MouseOverlay;
import com.kraken.api.query.gameobject.GameObjectEntity;
import com.kraken.api.query.npc.NpcEntity;
import com.krakenplugins.example.fishing.overlay.SceneOverlay;
import com.krakenplugins.example.fishing.overlay.ScriptOverlay;
import com.krakenplugins.example.fishing.script.FishingScript;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
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
        name = "Fishing Example Plugin",
        enabledByDefault = false,
        description = "Demonstrates an example of building a fishing automation plugin using the Kraken API.",
        tags = {"example", "automation", "kraken", "fishing"}
)
public class FishingPlugin extends Plugin {

    @Inject
    private FishingScript fishingScript;

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
    private FishingConfig config;

    private final long startTime = System.currentTimeMillis();

    @Getter
    @Setter
    private NpcEntity targetSpot;

    @Getter
    @Setter
    private GameObjectEntity depositBox;

    @Getter
    @Setter
    private NpcEntity npc;

    @Getter
    private int fishCaught;

    @Getter
    @Setter
    private long lastDropTimestamp = 0;

    @Getter
    private final List<WorldPoint> currentPath = new ArrayList<>();

    @Provides
    FishingConfig provideConfig(final ConfigManager configManager) {
        return configManager.getConfig(FishingConfig.class);
    }

    @Override
    protected void startUp() {
        ctx.initializePackets();
        fishingScript.setTasksForLocation(config.fishingLocation());
        fishingScript.start();
        overlayManager.add(scriptOverlay);
        overlayManager.add(mouseTrackerOverlay);
        overlayManager.add(sceneOverlay);
    }

    @Override
    protected void shutDown() {
        fishingScript.stop();
        overlayManager.remove(scriptOverlay);
        overlayManager.remove(mouseTrackerOverlay);
        overlayManager.remove(sceneOverlay);
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged event) {
        if(event.getGroup().equals("autofisher")) {
            String key = event.getKey();

            if(key.equals("fishingLocation") || key.equals("dropFish")) {
                log.info("Updating fishing location to: {}", config.fishingLocation().name());
                fishingScript.setTasksForLocation(config.fishingLocation());
            }

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
        return fishingScript.getStatus();
    }
}

