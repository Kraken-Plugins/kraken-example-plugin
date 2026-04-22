package com.krakenplugins.example.mining.script.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.core.script.AbstractTask;
import com.kraken.api.service.bank.BankService;
import com.kraken.api.service.movement.MovementService;
import com.kraken.api.service.movement.VariableStrideConfig;
import com.kraken.api.service.pathfinding.GlobalPathfinder;
import com.kraken.api.service.pathfinding.GlobalPathfinderConfig;
import com.krakenplugins.example.mining.MiningPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.util.List;

import static com.krakenplugins.example.mining.MiningPlugin.MINE_LOCATION;

@Slf4j
@Singleton
public class WalkToMineTask extends AbstractTask {

    @Inject
    private BankService bankService;

    @Inject
    private GlobalPathfinder pathfinder;

    @Inject
    private MovementService movementService;

    @Inject
    private MiningPlugin plugin;

    private boolean isTraversing = false;
    private final VariableStrideConfig strideConfig = VariableStrideConfig.builder().tileDeviation(true).build();


    @Override
    public boolean validate() {
        if (isTraversing) {
            return true;
        }

        boolean playerNotInMine = !ctx.players().local().isInArea(MINE_LOCATION, 3);
        return !ctx.inventory().isFull()
                && playerNotInMine
                && ctx.players().local().isIdle()
                && !bankService.isOpen()
                && !isTraversing;
    }

    @Override
    public int execute() {
        WorldPoint playerLocation = ctx.players().local().location();
        if (playerLocation.distanceTo(MINE_LOCATION) <= 4) {
            log.info("Arrived at mine");
            isTraversing = false;
            return 600;
        }

        isTraversing = true;
        try {
            List<WorldPoint> currentPath = pathfinder.findPath(playerLocation, plugin.getMiningArea().getRandomTile(), GlobalPathfinderConfig.builder()
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
            List<WorldPoint> stridedPath = movementService.applyVariableStride(currentPath, strideConfig);
            log.info("Path generated with {} waypoints", stridedPath.size());

            // Traverse the path
            movementService.traversePath(ctx.getClient(), stridedPath);
            return 1000;
        } catch (Exception e) {
            log.error("Error during walk to mine", e);
            isTraversing = false;
            return 1000;
        }
    }


    @Override
    public String status() {
        return "Pathing to Mine";
    }
}
