package com.krakenplugins.example.fishing.script.state.corsair;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.core.script.PriorityTask;
import com.kraken.api.service.movement.MovementService;
import com.kraken.api.service.movement.VariableStrideConfig;
import com.kraken.api.service.pathfinding.LocalPathfinder;
import com.kraken.api.service.tile.AreaService;
import com.kraken.api.service.tile.GameArea;
import com.krakenplugins.example.fishing.FishingConfig;
import com.krakenplugins.example.fishing.FishingPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.util.List;

@Slf4j
@Singleton
public class WalkToCorsairBank extends PriorityTask {

    public static final WorldPoint CORSAIR_COVE_BANK = new WorldPoint(2570, 2864, 0);

    private final FishingConfig config;
    private final GameArea resourceAreaFishingArea;
    private final LocalPathfinder localPathfinder;
    private final MovementService movementService;
    private final FishingPlugin plugin;

    private boolean isTraversing = false;
    private final VariableStrideConfig strideConfig = VariableStrideConfig
            .builder()
            .tileDeviation(true)
            .build();

    @Inject
    public WalkToCorsairBank(FishingPlugin plugin, AreaService areaService, FishingConfig config, LocalPathfinder localPathfinder, MovementService movementService) {
        this.config = config;
        this.plugin = plugin;
        this.localPathfinder = localPathfinder;
        this.movementService = movementService;
        resourceAreaFishingArea = areaService.createAreaFromRadius(config.fishingLocation().getLocation(), 8);
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean validate() {
        // If actively traversing, keep running regardless of idle state
        if (isTraversing) {
            return true;
        }

        boolean isFull = ctx.inventory().isFull();
        List<Integer> fishIds = config.fishingLocation().getFishIds();
        boolean hasFish = ctx.inventory().filter(item -> fishIds.contains(item.getId())).count() > 0;

        return isFull &&
                hasFish &&
                ctx.players().local().isIdle() &&  // only require idle on fresh trigger
                config.bankFishCorsair() &&
                ctx.players().local().isInArea(resourceAreaFishingArea);
    }

    @Override
    public int execute() {
        WorldPoint playerLocation = ctx.getClient().getLocalPlayer().getWorldLocation();
        if (playerLocation.distanceTo(CORSAIR_COVE_BANK) <= 7) {
            isTraversing = false;
            plugin.getCurrentPath().clear(); // arrived, safe to clear
            return 1000;
        }

        isTraversing = true;

        try {
            List<WorldPoint> directPath = localPathfinder.findApproximatePath(playerLocation, CORSAIR_COVE_BANK);

            if (directPath != null && !directPath.isEmpty()) {
                log.info("Direct path found to: CORSAIR_COVE_BANK.");
                List<WorldPoint> stridedPath = movementService.applyVariableStride(directPath, strideConfig);

                // Only clear AFTER we have a valid new path
                plugin.getCurrentPath().clear();
                plugin.getCurrentPath().addAll(stridedPath);

                movementService.traversePath(ctx.getClient(), stridedPath);

                // Don't reset isTraversing here — let validate() keep task alive
                // while player is still walking the stride
                return 600;
            }

            List<WorldPoint> backoffPath = localPathfinder.findApproximatePathWithBackoff(playerLocation, CORSAIR_COVE_BANK, 5);

            if (backoffPath != null && !backoffPath.isEmpty()) {
                List<WorldPoint> stridedPath = movementService.applyVariableStride(backoffPath, strideConfig);

                plugin.getCurrentPath().clear();
                plugin.getCurrentPath().addAll(stridedPath);

                movementService.traversePath(ctx.getClient(), stridedPath);
                return 600;
            }

            log.error("Failed to generate any path to Corsair cove bank (Direct or Backoff)");
            isTraversing = false;
            return 1000;

        } catch (Exception e) {
            log.error("Error during walk to Corsair Cove Bank", e);
            isTraversing = false;
            return 1000;
        }
    }

    @Override
    public String status() {
        return "Walking to Corsair Cove...";
    }
}
