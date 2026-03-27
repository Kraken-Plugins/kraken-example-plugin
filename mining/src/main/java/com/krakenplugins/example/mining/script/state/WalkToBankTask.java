package com.krakenplugins.example.mining.script.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.core.script.AbstractTask;
import com.kraken.api.service.movement.MovementService;
import com.kraken.api.service.pathfinding.LocalPathfinder;
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
    private LocalPathfinder pathfinder;

    @Inject
    private MiningPlugin plugin;

    @Inject
    private MovementService movementService;

    @Setter
    @Getter
    private boolean arrivedAtIntermediatePoint = false;

    private boolean isTraversing = false;
    private static final List<WorldPoint> path = List.of(
            new WorldPoint(3287, 3370, 0),
            new WorldPoint(3293, 3377, 0),
            new WorldPoint(3291, 3388, 0),
            new WorldPoint(3290, 3398, 0),
            new WorldPoint(3289, 3410, 0),
            new WorldPoint(3283, 3418, 0),
            new WorldPoint(3278, 3426, 0),
            new WorldPoint(3267, 3427, 0),
            new WorldPoint(3257, 3428, 0),
            new WorldPoint(3253, 3420, 0)
    );

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
        WorldPoint playerLocation = ctx.getClient().getLocalPlayer().getWorldLocation();
        if (playerLocation.distanceTo(BANK_LOCATION) <= 2) {
            log.info("Arrived at bank");
            isTraversing = false;
            return 600;
        }

        isTraversing = true;

        try {
            List<WorldPoint> stridedPath = pathfinder.randomizeSparsePath(ctx.players().local().location(), path, 2, 5, false);
            plugin.getCurrentPath().clear();
            plugin.getCurrentPath().addAll(stridedPath);
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
