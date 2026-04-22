package com.krakenplugins.example.mining.script.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.core.script.AbstractTask;
import com.kraken.api.query.gameobject.GameObjectEntity;
import com.kraken.api.service.bank.BankService;
import com.kraken.api.service.pathfinding.GlobalPathfinder;
import com.kraken.api.service.util.SleepService;
import com.krakenplugins.example.mining.MiningPlugin;

import static com.krakenplugins.example.mining.MiningPlugin.BANK_BOOTH_GAME_OBJECT;

@Singleton
public class OpenBankTask extends AbstractTask {

    @Inject
    private BankService bankService;

    @Inject
    private MiningPlugin plugin;

    @Inject
    private GlobalPathfinder globalPathfinder;

    @Override
    public boolean validate() {
        return ctx.players().local().isInArea(plugin.getVarrockBank()) && ctx.inventory().isFull();
    }

    @Override
    public int execute() {
        globalPathfinder.clearLastResult();
        GameObjectEntity booth = ctx.gameObjects().withId(BANK_BOOTH_GAME_OBJECT).nearest();
        ctx.getMouse().move(booth.raw());
        booth.interact("Bank");
        SleepService.sleepUntil(() -> bankService.isOpen(), 10000);
        return 500;
    }

    @Override
    public String status() {
        return "Opening Bank";
    }
}
