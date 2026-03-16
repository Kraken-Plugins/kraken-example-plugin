package com.krakenplugins.example.fishing.script.state.karamja;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.core.script.PriorityTask;
import com.kraken.api.query.container.inventory.InventoryEntity;
import com.kraken.api.query.npc.NpcEntity;
import com.kraken.api.service.util.RandomService;
import com.kraken.api.service.util.SleepService;
import com.krakenplugins.example.fishing.FishingConfig;
import com.krakenplugins.example.fishing.FishingPlugin;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.krakenplugins.example.fishing.script.state.karamja.WalkToDocks.KARAMJA_DOCKS;

@Slf4j
@Singleton
public class TravelPortSarim extends PriorityTask {

    private static final int CUSTOMS_OFFICER_NPC_ID = 14984;

    @Inject
    private FishingConfig config;

    @Inject
    private FishingPlugin plugin;

    // TODO Fix NPE in path
    // Add mouse movement to actions
    // Highlight key objects NPC's

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean validate() {
        boolean isFull = ctx.inventory().isFull();
        List<Integer> fishIds = config.fishingLocation().getFishIds();
        boolean hasFish = ctx.inventory().filter(item -> fishIds.contains(item.getId())).count() > 0;
        boolean atKaramjaDocks = ctx.players().local().raw().getWorldLocation().distanceTo(KARAMJA_DOCKS) <= 7;
        return isFull &&
                hasFish &&
                atKaramjaDocks &&
                config.bankFishKaramja();
    }

    @Override
    public int execute() {
        NpcEntity customsOfficer = ctx.npcs().withId(CUSTOMS_OFFICER_NPC_ID).first();
        if (customsOfficer == null) {
            log.info("No customs officer found on dock.");
            return 600;
        }

        plugin.setNpc(customsOfficer);

        plugin.getCurrentPath().clear();
        InventoryEntity coins = ctx.inventory().withId(995).first();
        if(coins == null) {
            log.error("No coins found in inventory, cannot travel on boat to Port Sarim.");
            return 3200;
        }

        if(coins.raw().getQuantity() < 30) {
            log.error("Not enough coins to travel.");
            return 3200;
        }

        if(config.useMouse()) {
            ctx.getMouse().move(customsOfficer.raw());
        }
        customsOfficer.interact("Travel");
        SleepService.sleepWhile(() -> ctx.players().local().isMoving(), 10000);
        plugin.setNpc(null);
        return RandomService.between(1800, 3200);
    }

    @Override
    public String status() {
        return "Traveling to Port Sarim...";
    }
}
