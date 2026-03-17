package com.krakenplugins.example.fishing.script.state.corsair;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.core.script.PriorityTask;
import com.kraken.api.query.npc.NpcEntity;
import com.kraken.api.service.util.RandomService;
import com.kraken.api.service.util.SleepService;
import com.krakenplugins.example.fishing.FishingConfig;
import com.krakenplugins.example.fishing.FishingPlugin;
import com.krakenplugins.example.fishing.script.FishingLocation;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class FishCorsair extends PriorityTask {

    @Inject
    private FishingPlugin plugin;

    @Inject
    private FishingConfig config;

    @Override
    public boolean validate() {
        if (!ctx.inventory().hasItem(311) && !ctx.inventory().hasItem(301)) {
            log.error("Player does not have a harpoon or lobster pot. Nothing to fish with.");
            return false;
        }
        return ctx.players().local().isInArea(FishingLocation.CORSAIR_COVE.getLocation(), 12) &&
                ctx.players().local().isIdle() &&
                !ctx.inventory().isFull();
    }

    @Override
    public int execute() {
        NpcEntity spot = ctx.npcs().withId(FishingLocation.CORSAIR_COVE.getSpotId()).nearest();
        if (spot != null) {
            plugin.getCurrentPath().clear();
            plugin.setTargetSpot(spot);

            if (config.useMouse()) {
                ctx.getMouse().move(spot.raw());
            }
            log.info("Finding new fishing spot...");
            if (spot.interact(config.fishingMethod().getInteractionName())) {
                SleepService.sleepUntil(
                        () -> ctx.players().local().isMoving()
                                || ctx.players().local().raw().getAnimation() != -1,
                        5000
                );
            }
        } else {
            plugin.setTargetSpot(null);
        }
        return RandomService.between(1200, 1800);
    }

    @Override
    public String status() {
        return "Fishing (Corsair Cove)";
    }

    @Override
    public int getPriority() {
        return 0;
    }
}
