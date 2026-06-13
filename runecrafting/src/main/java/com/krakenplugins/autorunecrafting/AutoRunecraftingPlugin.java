package com.krakenplugins.autorunecrafting;

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
import com.krakenplugins.autorunecrafting.overlay.SceneOverlay;
import com.krakenplugins.autorunecrafting.overlay.ScriptOverlay;
import com.krakenplugins.autorunecrafting.script.RunecraftingScript;
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
        name = "Auto Runecrafting",
        enabledByDefault = false,
        description = "Demonstrates an example of building an runecrafting automation plugin using the Kraken API.",
        tags = {"example", "automation", "kraken", "runecrafting", "rune"}
)
public class AutoRunecraftingPlugin extends Plugin {

    @Inject
    private RunecraftingScript runecraftingScript;

    @Inject
    private Context ctx;

    @Getter
    @Inject
    private ClientThread clientThread;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private MouseOverlay mouseTrackerOverlay;

    @Inject
    private SceneOverlay sceneOverlay;

    @Inject
    private AutoRunecraftingConfig config;

    @Inject
    private AreaService areaService;

    @Inject
    private ScriptOverlay scriptOverlay;

    @Getter
    @Setter
    private GameObjectEntity targetBankBooth;

    @Getter
    private GameArea faladorBank;

    @Getter
    private GameArea airAltar;

    @Getter
    private final List<WorldPoint> currentPath = new ArrayList<>();

    private final long startTime = System.currentTimeMillis();

    @Provides
    AutoRunecraftingConfig provideConfig(final ConfigManager configManager) {
        return configManager.getConfig(AutoRunecraftingConfig.class);
    }

    @Override
    protected void startUp() {
        WorldPoint[] bank = {
                new WorldPoint(3009, 3358, 0),
                new WorldPoint(3009, 3353, 0),
                new WorldPoint(3021, 3353, 0),
                new WorldPoint(3022, 3357, 0),
                new WorldPoint(3019, 3357, 0),
                new WorldPoint(3019, 3359, 0),
                new WorldPoint(3009, 3359, 0)
        };
        faladorBank = areaService.createPolygonArea(bank);

        WorldPoint[] air = {
                new WorldPoint(2988, 3299, 0),
                new WorldPoint(2976, 3296, 0),
                new WorldPoint(2974, 3288, 0),
                new WorldPoint(2986, 3284, 0),
                new WorldPoint(2996, 3288, 0),
                new WorldPoint(2997, 3296, 0),
                new WorldPoint(2995, 3301, 0),
                new WorldPoint(2989, 3303, 0),
                new WorldPoint(2983, 3302, 0),
                new WorldPoint(2979, 3301, 0),
                new WorldPoint(2976, 3299, 0),
                new WorldPoint(2976, 3296, 0)
        };
        airAltar = areaService.createPolygonArea(air);

        runecraftingScript.start();
        overlayManager.add(scriptOverlay);
        overlayManager.add(mouseTrackerOverlay);
        overlayManager.add(sceneOverlay);
    }

    @Override
    protected void shutDown() {
        runecraftingScript.stop();
        overlayManager.remove(scriptOverlay);
        overlayManager.remove(mouseTrackerOverlay);
        overlayManager.remove(sceneOverlay);
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged event) {
        if(event.getGroup().equals("autorunecrafting")) {
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
        return runecraftingScript.getStatus();
    }
}
