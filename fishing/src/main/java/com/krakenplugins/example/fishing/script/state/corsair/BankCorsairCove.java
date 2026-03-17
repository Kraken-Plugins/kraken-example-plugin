package com.krakenplugins.example.fishing.script.state.corsair;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.core.script.PriorityTask;
import com.kraken.api.query.container.bank.DepositBoxEntity;
import com.kraken.api.query.gameobject.GameObjectEntity;
import com.kraken.api.service.bank.DepositBoxService;
import com.kraken.api.service.util.RandomService;
import com.kraken.api.service.util.SleepService;
import com.krakenplugins.example.fishing.FishingConfig;
import com.krakenplugins.example.fishing.FishingPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.util.List;

@Slf4j
@Singleton
public class BankCorsairCove extends PriorityTask {

    public static final WorldPoint CORSAIR_COVE_DEPOSIT_BOX =  new WorldPoint(2569, 2861, 0);
    private static final int DEPOSIT_BOX_GAME_OBJECT = 31726;

    @Inject
    private FishingConfig config;

    @Inject
    private FishingPlugin plugin;

    @Inject
    private DepositBoxService depositBoxService;

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean validate() {
        List<Integer> fishIds = config.fishingLocation().getFishIds();

        boolean isFull = ctx.inventory().isFull();
        boolean hasFish = ctx.inventory().filter(item -> fishIds.contains(item.getId())).count() > 0;
        return isFull &&
                hasFish &&
                ctx.players().local().isIdle() &&
                config.bankFishCorsair() &&
                ctx.players().local().isInArea(CORSAIR_COVE_DEPOSIT_BOX, 7);
    }

    @Override
    public int execute() {
        if(depositBoxService.isOpen()) {
            depositFish();
            SleepService.sleepFor(RandomService.between(1, 4));
            depositBoxService.close();
            return 0;
        }

        GameObjectEntity depositBox = ctx.gameObjects().withId(DEPOSIT_BOX_GAME_OBJECT).first();
        if(depositBox == null) {
            log.error("No deposit box found...");
            return 600;
        }

        if(config.useMouse()) {
            ctx.getMouse().move(depositBox.raw());
        }

        log.info("Interacting with bank deposit box");
        plugin.setDepositBox(depositBox);
        depositBox.interact("Deposit");
        SleepService.sleepWhile(() -> depositBoxService.isClosed(), 10000);

        if(depositBoxService.isOpen()) {
            depositFish();
            SleepService.sleepFor(RandomService.between(1, 4));
            depositBoxService.close();
        }
        return 600;
    }

    private void depositFish() {
        log.info("Depositing fish from inventory...");
        plugin.setDepositBox(null);
        List<Integer> fishIds = config.fishingLocation().getFishIds();
        List<DepositBoxEntity> fish = ctx.depositBox()
                .inInventory()
                .filter((item) -> fishIds.contains(item.getId()))
                .distinct(DepositBoxEntity::getId)
                .list();

        for(DepositBoxEntity f : fish) {
            if(config.useMouse()) {
                ctx.getMouse().move(f.raw());
            }
            f.depositAll();
        }
    }

    @Override
    public String status() {
        return "Banking Fish...";
    }
}
