package com.krakenplugins.example.mining.script.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.core.script.AbstractTask;
import com.kraken.api.service.movement.MovementService;
import com.kraken.api.service.util.RandomService;
import com.krakenplugins.example.mining.MiningConfig;
import com.krakenplugins.example.mining.MiningPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.util.List;

@Slf4j
@Singleton
public class FollowPathTask extends AbstractTask {

    @Inject
    private MiningPlugin plugin;

    @Inject
    private MiningConfig config;

    @Inject
    private MovementService movementService;

    private WorldPoint lastPosition;
    private int stuckCounter = 0;

    @Override
    public boolean validate() {
        return !plugin.getCurrentPath().isEmpty();
    }

    @Override
    public int execute() {
        int randomRun = RandomService.between(config.runEnergyThresholdMin(), config.runEnergyThresholdMax());
        if(ctx.players().local().currentRunEnergy() >= randomRun && !ctx.players().local().isRunEnabled()) {
            log.info("Toggling run on, met threshold: {} between min={} max={}", randomRun, config.runEnergyThresholdMin(), config.runEnergyThresholdMax());
            ctx.players().local().toggleRun();
        }

        WorldPoint playerLocation = ctx.players().local().location();

        // Stuck detection logic
        if (lastPosition != null && lastPosition.distanceTo(playerLocation) == 0) {
            stuckCounter++;
        } else {
            stuckCounter = 0;
        }

        if (stuckCounter > 5) {
            plugin.getCurrentPath().clear();
            stuckCounter = 0;
            lastPosition = null;
            return 0;
        }

        // Remove waypoints that we are very close to
        if (!plugin.getCurrentPath().isEmpty() && playerLocation.distanceTo(plugin.getCurrentPath().get(0)) < 4) {
            plugin.getCurrentPath().remove(0);
            stuckCounter = 0;
            lastPosition = null;
            return 0; // Re-evaluate on next tick
        }

        if (plugin.getCurrentPath().isEmpty()) {
            return 0; // Path is complete
        }

        // Find the furthest visible point on the path to walk to. This allows for more natural movement.
        List<WorldPoint> path = plugin.getCurrentPath();
        WorldPoint target = path.get(0); // Default to the next point in the path

        // Find the furthest point on the path that is still within a clickable distance
        for (int i = path.size() - 1; i >= 0; i--) {
            WorldPoint currentPoint = path.get(i);
            // A distance of 14 is a good heuristic for a tile that is reachable on screen.
            if (playerLocation.distanceTo(currentPoint) < 14) {
                target = currentPoint;
                break;
            }
        }

        ctx.getMouse().move(target);
        movementService.moveTo(target);
        lastPosition = playerLocation;

        return 300;
    }

    @Override
    public String status() {
        return "Following path";
    }
}
