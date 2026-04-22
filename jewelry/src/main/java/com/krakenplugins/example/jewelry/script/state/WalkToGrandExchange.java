package com.krakenplugins.example.jewelry.script.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.core.script.AbstractTask;
import com.kraken.api.service.bank.BankService;
import com.kraken.api.service.movement.MovementService;
import com.kraken.api.service.pathfinding.GlobalPathfinder;
import com.kraken.api.service.pathfinding.GlobalPathfinderConfig;
import com.kraken.api.service.util.RandomService;
import com.krakenplugins.example.jewelry.JewelryConfig;
import com.krakenplugins.example.jewelry.JewelryPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.util.List;

import static com.krakenplugins.example.jewelry.script.JewelryScript.GOLD_BAR;

@Singleton
@Slf4j
public class WalkToGrandExchange extends AbstractTask {

    private static final WorldPoint GRAND_EXCHANGE = new WorldPoint(3164, 3486, 0);

    @Inject
    private JewelryPlugin plugin;

    @Inject
    private BankService bankService;

    @Inject
    private JewelryConfig config;

    @Inject
    private GlobalPathfinder pathfinder;

    @Inject
    private MovementService movementService;

    private boolean isTraversing = false;

    @Override
    public boolean validate() {
        if (isTraversing) {
            return true;
        }

        if(!bankService.isOpen()) {
            return false;
        }

        boolean hasNoGold = ctx.bank().withId(GOLD_BAR).first() == null;
        boolean hasNoGems = ctx.bank().withId(config.jewelry().getSecondaryGemId()).first() == null;

        return ctx.players().local().isInArea(plugin.getEdgevilleBank()) && (hasNoGold || hasNoGems) && !isTraversing && config.enableResupply();
    }

    @Override
    public int execute() {
        bankService.close();

        int randomRun = RandomService.between(config.runEnergyThresholdMin(), config.runEnergyThresholdMax());
        if(ctx.players().local().currentRunEnergy() >= randomRun && !ctx.players().local().isRunEnabled()) {
            log.info("Toggling run on, met threshold: {} between min={} max={}", randomRun, config.runEnergyThresholdMin(), config.runEnergyThresholdMax());
            ctx.players().local().toggleRun();
        }

        WorldPoint playerLocation = ctx.players().local().location();
        if (playerLocation.distanceTo(GRAND_EXCHANGE) <= 5) {
            log.info("Arrived at Grand Exchange.");
            isTraversing = false;
            return 1000;
        }

        isTraversing = true;

        try {
            // Try to find a DIRECT path to the real destination
            // We do not use backoff here. We want to know if the "Good" path is valid.
            List<WorldPoint> directPath = pathfinder.findPath(playerLocation, GRAND_EXCHANGE, GlobalPathfinderConfig.builder()
                            .useMinecarts(false)
                    .useTeleportationLevers(false)
                    .useTeleportationSpells(false)
                    .useAgilityShortcuts(false)
                    .build());

            if (directPath != null && !directPath.isEmpty()) {
                // We have a valid path to the GE!
                // We can fully commit to this path.
                List<WorldPoint> stridedPath = movementService.applyVariableStride(directPath);
                movementService.traversePath(ctx.getClient(), stridedPath);
                // This handles the entire path to GE destination in one execution so
                // its safe to set is traversing to false and release the latch
                isTraversing = false;
                return 600;
            }

            log.error("Failed to generate any path (Direct or Backoff)");
            isTraversing = false;
            return 1000;
        } catch (Exception e) {
            log.error("Error during walk to GE", e);
            isTraversing = false;
            return 1000;
        }
    }

    @Override
    public String status() {
        return "Walking to G.E.";
    }
}
