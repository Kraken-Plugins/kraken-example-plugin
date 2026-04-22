package com.krakenplugins.example.fishing.script.state.corsair;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.core.script.PriorityTask;
import com.kraken.api.service.movement.MovementService;
import com.kraken.api.service.movement.VariableStrideConfig;
import com.kraken.api.service.pathfinding.GlobalPathfinder;
import com.kraken.api.service.pathfinding.GlobalPathfinderConfig;
import com.krakenplugins.example.fishing.FishingConfig;
import com.krakenplugins.example.fishing.FishingPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.util.List;

import static com.krakenplugins.example.fishing.script.state.corsair.WalkToCorsairBank.CORSAIR_COVE_BANK;

@Slf4j
@Singleton
public class WalkToResourceArea extends PriorityTask {

    private static final WorldPoint RESOURCE_AREA = new WorldPoint(2456, 2892, 0);

    private final FishingConfig config;
    private final GlobalPathfinder pathfinder;
    private final MovementService movementService;
    private final FishingPlugin plugin;

    private boolean isTraversing = false;
    private final VariableStrideConfig strideConfig = VariableStrideConfig
            .builder()
            .tileDeviation(true)
            .build();

    @Inject
    public WalkToResourceArea(FishingPlugin plugin, FishingConfig config, GlobalPathfinder pathfinder, MovementService movementService) {
        this.config = config;
        this.plugin = plugin;
        this.pathfinder = pathfinder;
        this.movementService = movementService;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean validate() {
        // Keep task alive while actively traversing
        if (isTraversing) {
            return true;
        }

        boolean hasSpace = !ctx.inventory().isFull();
        List<Integer> fishIds = config.fishingLocation().getFishIds();
        boolean hasNoFish = ctx.inventory().filter(item -> fishIds.contains(item.getId())).count() == 0;
        boolean playerInBank = ctx.players().local().location().distanceTo(CORSAIR_COVE_BANK) <= 7;

        return hasSpace &&
                hasNoFish &&
                playerInBank &&
                config.bankFishCorsair();
    }

    @Override
    public int execute() {
        WorldPoint playerLocation = ctx.players().local().location();
        if (playerLocation.distanceTo(RESOURCE_AREA) <= 7) {
            isTraversing = false;
            return 1000;
        }

        isTraversing = true;

        try {
            List<WorldPoint> directPath = pathfinder.findPath(playerLocation, RESOURCE_AREA, GlobalPathfinderConfig.builder()
                    .useCharterShips(false)
                    .useBoats(false)
                    .useSpiritTrees(false)
                    .build());

            if (directPath != null && !directPath.isEmpty()) {
                log.info("Direct path found to: RESOURCE_AREA.");
                List<WorldPoint> stridedPath = movementService.applyVariableStride(directPath, strideConfig);
                movementService.traversePath(ctx.getClient(), stridedPath);
                return 600;
            }

            log.error("Failed to generate any path to Resource Area");
            isTraversing = false;
            return 1000;

        } catch (Exception e) {
            log.error("Error during walk to Resource Area", e);
            isTraversing = false;
            return 1000;
        }
    }

    @Override
    public String status() {
        return "Walking to Musa Point...";
    }
}