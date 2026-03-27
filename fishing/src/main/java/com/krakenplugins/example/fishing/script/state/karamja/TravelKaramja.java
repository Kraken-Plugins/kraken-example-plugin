package com.krakenplugins.example.fishing.script.state.karamja;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.core.script.PriorityTask;
import com.kraken.api.query.container.inventory.InventoryEntity;
import com.kraken.api.query.npc.NpcEntity;
import com.kraken.api.service.movement.MovementService;
import com.kraken.api.service.movement.VariableStrideConfig;
import com.kraken.api.service.pathfinding.LocalPathfinder;
import com.kraken.api.service.util.RandomService;
import com.kraken.api.service.util.SleepService;
import com.krakenplugins.example.fishing.FishingConfig;
import com.krakenplugins.example.fishing.FishingPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.util.List;

import static com.krakenplugins.example.fishing.script.state.karamja.BankDepositBox.PORT_SARIM_DEPOSIT_BOX;

@Slf4j
@Singleton
public class TravelKaramja extends PriorityTask {
    private static final WorldPoint PORT_SARIM_DOCKS = new WorldPoint(3026, 3218, 0);
    private static final List<Integer> seamen = List.of(14978, 14980, 14982); // lol
    private final VariableStrideConfig strideConfig = VariableStrideConfig
            .builder()
            .tileDeviation(true)
            .build();


    private final MovementService movementService;
    private final FishingConfig config;
    private final LocalPathfinder localPathfinder;
    private final FishingPlugin fishingPlugin;

    @Inject
    public TravelKaramja(FishingConfig config, MovementService movementService, LocalPathfinder localPathfinder, FishingPlugin fishingPlugin) {
        this.config = config;
        this.localPathfinder = localPathfinder;
        this.movementService = movementService;
        this.fishingPlugin = fishingPlugin;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean validate() {
        List<Integer> fishIds = config.fishingLocation().getFishIds();
        boolean hasSpace = !ctx.inventory().isFull();
        boolean hasNoFish = ctx.inventory().filter(item -> fishIds.contains(item.getId())).count() == 0;

        return hasSpace &&
                hasNoFish &&
                (ctx.players().local().isInArea(PORT_SARIM_DOCKS, 6) || ctx.players().local().isInArea(PORT_SARIM_DEPOSIT_BOX)) &&
                config.bankFishKaramja();
    }

    @Override
    public int execute() {
        boolean noSeamenFound = ctx.npcs().filter(n -> seamen.contains(n.getId())).count() == 0;
        if (noSeamenFound) {
            log.info("No seamen found, pathing to travel point...");
            List<WorldPoint> directPath = localPathfinder.findApproximatePath(ctx.players().local().location(), new WorldPoint(3026, 3218, 0));

            if (directPath != null && !directPath.isEmpty()) {
                log.info("Direct path to Karamja located...");
                List<WorldPoint> stridedPath = movementService.applyVariableStride(directPath, strideConfig);

                fishingPlugin.getCurrentPath().clear();
                fishingPlugin.getCurrentPath().addAll(stridedPath);

                movementService.traversePath(ctx.getClient(), stridedPath);
                SleepService.sleepWhile(() -> ctx.players().local().isMoving(), 15000);
            }
        }

        NpcEntity randomSeaman = ctx.npcs().filter(n -> seamen.contains(n.getId())).random();
        if (randomSeaman == null) {
            log.info("No Seaman found on dock.");
            return 600;
        }

        fishingPlugin.setNpc(randomSeaman);

        InventoryEntity coins = ctx.inventory().withId(995).first();
        if(coins == null) {
            log.error("No coins found in inventory, cannot travel on boat to Karamja.");
            return 3200;
        }

        if(coins.raw().getQuantity() < 30) {
            log.error("Not enough coins to travel.");
            return 3200;
        }

        if(config.useMouse()) {
            ctx.getMouse().move(randomSeaman.raw());
        }
        randomSeaman.interact("Travel");
        SleepService.sleepWhile(() -> ctx.players().local().isMoving(), 10000);
        fishingPlugin.setNpc(null);
        return RandomService.between(1800, 3200);
    }

    @Override
    public String status() {
        return "Traveling to Karamja...";
    }
}
