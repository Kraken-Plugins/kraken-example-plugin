package com.krakenplugins.example.mining.script.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.core.script.AbstractTask;
import com.kraken.api.service.bank.BankService;
import com.kraken.api.service.movement.MovementService;
import com.kraken.api.service.movement.VariableStrideConfig;
import com.kraken.api.service.pathfinding.LocalPathfinder;
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
    private LocalPathfinder pathfinder;

    @Inject
    private MovementService movementService;

    @Inject
    private MiningPlugin plugin;

    private boolean isTraversing = false;
    private final VariableStrideConfig strideConfig = VariableStrideConfig.builder().tileDeviation(true).build();

    private static final List<WorldPoint> path = List.of(
            new WorldPoint(3253, 3428, 0),
            new WorldPoint(3275, 3428, 0),
            new WorldPoint(3288, 3410, 0),
            new WorldPoint(3292, 3386, 0),
            new WorldPoint(3290, 3374, 0),
            new WorldPoint(3286, 3367, 0)
    );

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
        WorldPoint playerLocation = ctx.getClient().getLocalPlayer().getWorldLocation();
        if (playerLocation.distanceTo(MINE_LOCATION) <= 4) {
            log.info("Arrived at mine");
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
