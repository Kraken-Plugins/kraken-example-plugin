package com.krakenplugins.example.fishing.script.state.karamja;

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
public class BankDepositBox extends PriorityTask {

    private static final WorldPoint PORT_SARIM = new WorldPoint(3026, 3218, 0);
    public static final WorldPoint PORT_SARIM_DEPOSIT_BOX =  new WorldPoint(3045, 3235, 0);
    private static final int DEPOSIT_BOX_GAME_OBJECT = 26254; // Deposit

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
                config.bankFishKaramja() &&
                    (ctx.players().local().isInArea(PORT_SARIM, 10) || ctx.players().local().isInArea(PORT_SARIM_DEPOSIT_BOX));
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
