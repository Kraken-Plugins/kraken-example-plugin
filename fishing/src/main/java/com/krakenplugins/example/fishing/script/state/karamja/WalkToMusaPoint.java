package com.krakenplugins.example.fishing.script.state.karamja;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.core.script.PriorityTask;
import com.kraken.api.service.movement.MovementService;
import com.kraken.api.service.movement.VariableStrideConfig;
import com.kraken.api.service.pathfinding.LocalPathfinder;
import com.kraken.api.service.tile.AreaService;
import com.krakenplugins.example.fishing.FishingConfig;
import com.krakenplugins.example.fishing.FishingPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.util.List;

import static com.krakenplugins.example.fishing.script.state.karamja.WalkToDocks.KARAMJA_DOCKS;

@Slf4j
@Singleton
public class WalkToMusaPoint extends PriorityTask {

    private static final WorldPoint MUSA_POINT =  new WorldPoint(2924, 3179, 0);

    private final FishingConfig config;
    private final LocalPathfinder localPathfinder;
    private final MovementService movementService;
    private final FishingPlugin plugin;

    private boolean isTraversing = false;
    private final VariableStrideConfig strideConfig = VariableStrideConfig
            .builder()
            .tileDeviation(true)
            .build();

    @Inject
    public WalkToMusaPoint(FishingPlugin plugin, AreaService areaService, FishingConfig config, LocalPathfinder localPathfinder, MovementService movementService) {
        this.config = config;
        this.plugin = plugin;
        this.localPathfinder = localPathfinder;
        this.movementService = movementService;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean validate() {
        if (isTraversing) {
            return true;
        }

        boolean hasSpace = !ctx.inventory().isFull();
        List<Integer> fishIds = config.fishingLocation().getFishIds();
        boolean hasNoFish = ctx.inventory().filter(item -> fishIds.contains(item.getId())).count() == 0;
        boolean playerInKaramja = ctx.players().local().raw().getWorldLocation().distanceTo(KARAMJA_DOCKS) <= 7;

        return hasSpace &&
                hasNoFish &&
                playerInKaramja &&
                config.bankFishKaramja();
    }

    @Override
    public int execute() {
        WorldPoint playerLocation = ctx.getClient().getLocalPlayer().getWorldLocation();
        if (playerLocation.distanceTo(MUSA_POINT) <= 7) {
            isTraversing = false;
            return 1000;
        }

        isTraversing = true;

        try {
            // Try to find a DIRECT path to the real destination
            // We do not use backoff here. We want to know if the "Good" path is valid.
            List<WorldPoint> directPath = localPathfinder.findApproximatePath(playerLocation, MUSA_POINT);

            if (directPath != null && !directPath.isEmpty()) {
                log.info("Direct path found to: MUSA_POINT.");
                List<WorldPoint> stridedPath = movementService.applyVariableStride(directPath, strideConfig);

                plugin.getCurrentPath().clear();
                plugin.getCurrentPath().addAll(stridedPath);

                movementService.traversePath(ctx.getClient(), stridedPath);
                isTraversing = false;
                return 600;
            }

            // Direct path failed, use BACKOFF
            log.info("Direct path failed. Attempting backoff...");
            List<WorldPoint> backoffPath = localPathfinder.findApproximatePathWithBackoff(playerLocation, KARAMJA_DOCKS, 5);

            if (backoffPath != null && !backoffPath.isEmpty()) {
                List<WorldPoint> stridedPath = movementService.applyVariableStride(backoffPath, strideConfig);

                plugin.getCurrentPath().clear();
                plugin.getCurrentPath().addAll(stridedPath);

                movementService.traversePath(ctx.getClient(), stridedPath);
                return 0;
            }

            log.error("Failed to generate any path (Direct or Backoff)");
            isTraversing = false;
            return 1000;
        } catch (Exception e) {
            log.error("Error during walk to Musa Point", e);
            isTraversing = false;
            return 1000;
        }
    }

    @Override
    public String status() {
        return "Walking to Musa Point...";
    }
}
