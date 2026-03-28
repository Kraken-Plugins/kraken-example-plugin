package com.krakenplugins.autorunecrafting.script.task;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.core.script.AbstractTask;
import com.kraken.api.service.bank.BankService;
import com.kraken.api.service.movement.MovementService;
import com.kraken.api.service.movement.VariableStrideConfig;
import com.kraken.api.service.pathfinding.LocalPathfinder;
import com.krakenplugins.autorunecrafting.AutoRunecraftingPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.util.List;

import static com.krakenplugins.autorunecrafting.script.RunecraftingScript.*;

@Slf4j
@Singleton
public class WalkToAltarTask extends AbstractTask {

    private static final WorldPoint AIR_ALTAR = new WorldPoint(2985, 3295, 0);

    @Inject
    private AutoRunecraftingPlugin plugin;

    @Inject
    private BankService bankService;

    @Inject
    private LocalPathfinder pathfinder;

    @Inject
    private MovementService movementService;

    private boolean isTraversing = false;
    private final VariableStrideConfig strideConfig = VariableStrideConfig.builder().tileDeviation(true).build();

    @Override
    public boolean validate() {
        if (isTraversing) {
            return true;
        }

        boolean hasNoRunes = ctx.bankInventory().nameContains("rune").first() == null;
        boolean hasEssence = ctx.bankInventory().stream().anyMatch((i) -> i.raw().getId() == PURE_ESSENCE || i.raw().getId() == RUNE_ESSENCE);
        boolean isWearingTiara = ctx.equipment().inInterface().isWearing(AIR_TIARA);

        return ctx.players().local().isInArea(plugin.getFaladorBank())
                && hasNoRunes
                && hasEssence
                && isWearingTiara
                && !isTraversing;
    }

    @Override
    public int execute() {
        plugin.setTargetBankBooth(null);
        bankService.close();

        WorldPoint playerLocation = ctx.players().local().location();
        if (playerLocation.distanceTo(AIR_ALTAR) <= 7) {
            isTraversing = false;
            return 1000;
        }

        isTraversing = true;

        try {
            // Try to find a DIRECT path to the real destination
            // We do not use backoff here. We want to know if the "Good" path is valid.
            List<WorldPoint> directPath = pathfinder.findApproximatePath(playerLocation, AIR_ALTAR);

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
            List<WorldPoint> backoffPath = pathfinder.findApproximatePathWithBackoff(playerLocation, AIR_ALTAR, 5);

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
        return "Walking to Altar";
    }
}
