package com.krakenplugins.example.fishing.script.state.karamja;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.core.script.PriorityTask;
import com.kraken.api.query.npc.NpcEntity;
import com.kraken.api.service.util.RandomService;
import com.krakenplugins.example.fishing.FishingConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.krakenplugins.example.fishing.script.state.karamja.WalkToDocks.KARAMJA_DOCKS;

@Slf4j
@Singleton
public class TravelBoat extends PriorityTask {

    private static final int CUSTOMS_OFFICER_NPC_ID = 14984; // Travel

    @Inject
    private FishingConfig config;

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean validate() {
        boolean isFull = ctx.inventory().isFull();
        List<Integer> fishIds = config.fishingLocation().getFishIds();
        boolean hasFish = ctx.inventory().filter(item -> fishIds.contains(item.getId())).count() > 0;
        int distanceToDocks = ctx.players().local().raw().getWorldLocation().distanceTo(KARAMJA_DOCKS);
        log.info("Distance to docks: {}", distanceToDocks);
        return isFull &&
                hasFish &&
                distanceToDocks <= 7 &&
                config.bankFishKaramja();
    }

    @Override
    public int execute() {
        NpcEntity customsOfficer = ctx.npcs().withId(CUSTOMS_OFFICER_NPC_ID).first();
        if (customsOfficer == null) {
            log.info("No customs officer found on dock.");
            return 600;
        }

        // TODO Check that we have 30gp in the inventory
        log.info("Interacting with travel customs officer.");
        customsOfficer.interact("Travel");
        return RandomService.between(1800, 3200);
    }

    @Override
    public String status() {
        return "Traveling on Boat...";
    }
}
