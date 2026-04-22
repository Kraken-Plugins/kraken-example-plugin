package com.krakenplugins.example.mining.script.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.core.script.AbstractTask;
import com.kraken.api.service.movement.MovementService;
import com.kraken.api.service.pathfinding.GlobalPathfinder;
import com.kraken.api.service.pathfinding.GlobalPathfinderConfig;
import com.krakenplugins.example.mining.MiningPlugin;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.util.List;

import static com.krakenplugins.example.mining.MiningPlugin.BANK_LOCATION;

@Slf4j
@Singleton
public class WalkToBankTask extends AbstractTask {

    @Inject
    private GlobalPathfinder pathfinder;

    @Inject
    private MiningPlugin plugin;

    @Inject
    private MovementService movementService;

    @Setter
    @Getter
    private boolean arrivedAtIntermediatePoint = false;

    private boolean isTraversing = false;

    private static final WorldPoint VARROCK_EAST_BANK = new WorldPoint(3253, 3421, 0);

    @Override
    public boolean validate() {
        if (isTraversing) {
            return true;
        }

        boolean playerNotInBank = !ctx.players().local().isInArea(BANK_LOCATION, 3);
        return ctx.inventory().isFull()
                && playerNotInBank
                && !isTraversing
                && ctx.players().local().isIdle();
    }

    @Override
    public int execute() {
        plugin.setTargetRock(null);
        WorldPoint playerLocation = ctx.players().local().location();
        if (playerLocation.distanceTo(BANK_LOCATION) <= 2) {
            log.info("Arrived at bank");
            isTraversing = false;
            return 600;
        }

        isTraversing = true;

        try {
            List<WorldPoint> currentPath = pathfinder.findPath(playerLocation, VARROCK_EAST_BANK, GlobalPathfinderConfig.builder()
                    .useMinecarts(false)
                    .avoidWilderness(true)
                    .useSpiritTrees(false)
                    .useTeleportationLevers(false)
                    .useTeleportationPortalsPoh(false)
                    .useTeleportationSpells(false)
                    .useAgilityShortcuts(false)
                    .build());

            if (currentPath == null || currentPath.isEmpty()) {
                log.error("Failed to generate any path to Varrock East Bank");
                isTraversing = false;
                return 1000;
            }

            // Apply variable stride for more natural movement
            List<WorldPoint> stridedPath = movementService.applyVariableStride(currentPath);
            log.info("Path generated with {} waypoints", stridedPath.size());

            // Traverse the path
           movementService.traversePath(ctx.getClient(), stridedPath);
            return 1000;
        } catch (Exception e) {
            log.error("Error during walk to bank", e);
            isTraversing = false;
            return 1000;
        }
    }

    @Override
    public String status() {
        return "Pathing to bank";
    }
}
