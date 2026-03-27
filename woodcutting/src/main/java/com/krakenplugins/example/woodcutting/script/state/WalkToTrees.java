package com.krakenplugins.example.woodcutting.script.state;

import com.google.inject.Inject;
import com.kraken.api.core.script.AbstractTask;
import com.kraken.api.service.bank.BankService;
import com.kraken.api.service.movement.MovementService;
import com.kraken.api.service.pathfinding.LocalPathfinder;
import com.kraken.api.service.util.SleepService;
import com.krakenplugins.example.woodcutting.WoodcuttingConfig;
import net.runelite.api.coords.WorldPoint;

import java.util.List;

import static com.krakenplugins.example.woodcutting.WoodcuttingPlugin.BANK_LOCATION;
import static com.krakenplugins.example.woodcutting.WoodcuttingPlugin.TREE_LOCATION;

public class WalkToTrees extends AbstractTask  {

    @Inject
    private BankService bankService;

    @Inject
    private LocalPathfinder pathfinder;

    @Inject
    private MovementService movementService;

    @Inject
    private WoodcuttingConfig config;

    @Override
    public boolean validate() {
        boolean playerInventoryEmpty = ctx.inventory().nameContains("logs").count() == 0;
        boolean playerInBank = ctx.players().local().isInArea(BANK_LOCATION);
        return playerInventoryEmpty && !bankService.isOpen() && playerInBank;
    }

    @Override
    public int execute() {
        List<WorldPoint> path = pathfinder.findApproximatePath(ctx.players().local().location(), TREE_LOCATION);

        if(path == null || path.isEmpty()) {
            return 1200;
        }

        // It's a short path so just move to the destination tile
        if(config.useMouse()) {
            movementService.moveTo(path.get(path.size() - 1));
        }
        SleepService.sleepUntil(() -> ctx.players().local().raw().getAnimation() == -1, 15000);
        return 2000;
    }

    @Override
    public String status() {
        return "Walking to Trees";
    }
}
