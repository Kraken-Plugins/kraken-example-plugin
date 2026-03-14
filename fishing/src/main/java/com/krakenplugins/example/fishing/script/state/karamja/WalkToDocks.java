package com.krakenplugins.example.fishing.script.state.karamja;

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
public class WalkToDocks extends PriorityTask {

    public static final WorldPoint KARAMJA_DOCKS = new WorldPoint(2957, 3147, 0);

    private final FishingConfig config;
    private final GameArea karamjaFishingArea;
    private final LocalPathfinder localPathfinder;
    private final MovementService movementService;
    private final FishingPlugin plugin;

    private boolean isTraversing = false;
    private final VariableStrideConfig strideConfig = VariableStrideConfig
            .builder()
            .tileDeviation(true)
            .build();

    @Inject
    public WalkToDocks(FishingPlugin plugin, AreaService areaService, FishingConfig config, LocalPathfinder localPathfinder, MovementService movementService) {
        this.config = config;
        this.plugin = plugin;
        this.localPathfinder = localPathfinder;
        this.movementService = movementService;
        karamjaFishingArea = areaService.createAreaFromRadius(config.fishingLocation().getLocation(), 8);
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

        boolean isFull = ctx.inventory().isFull();

        List<Integer> fishIds = config.fishingLocation().getFishIds();
        boolean hasFish = ctx.inventory().filter(item -> fishIds.contains(item.getId())).count() > 0;
        log.info("Walk to docks: is full ={}, has fish = {}, idle = {}, is not traversing = {}, config set to bank = {}, is in karamja fish area = {}", isFull, hasFish, ctx.players().local().isIdle(), !isTraversing, config.bankFishKaramja(), ctx.players().local().isInArea(karamjaFishingArea));
        return isFull &&
                hasFish &&
                ctx.players().local().isIdle() &&
                !isTraversing &&
                config.bankFishKaramja() &&
                ctx.players().local().isInArea(karamjaFishingArea);
    }

    @Override
    public int execute() {
        WorldPoint playerLocation = ctx.getClient().getLocalPlayer().getWorldLocation();
        if (playerLocation.distanceTo(KARAMJA_DOCKS) <= 7) {
            isTraversing = false;
            return 1000;
        }

        isTraversing = true;

        try {
            // Try to find a DIRECT path to the real destination
            // We do not use backoff here. We want to know if the "Good" path is valid.
            List<WorldPoint> directPath = localPathfinder.findApproximatePath(playerLocation, KARAMJA_DOCKS);

            if (directPath != null && !directPath.isEmpty()) {
                log.info("Direct path found.");
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
            log.error("Error during walk to Air Altar", e);
            isTraversing = false;
            return 1000;
        }
    }

    @Override
    public String status() {
        return "Walking to Docks...";
    }
}
