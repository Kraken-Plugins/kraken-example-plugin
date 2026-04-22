package com.krakenplugins.example.jewelry.script.state;

import com.google.inject.Inject;
import com.kraken.api.core.script.AbstractTask;
import com.kraken.api.query.container.bank.BankEntity;
import com.kraken.api.query.container.bank.BankInventoryEntity;
import com.kraken.api.service.bank.BankService;
import com.kraken.api.service.util.RandomService;
import com.kraken.api.service.util.SleepService;
import com.krakenplugins.example.jewelry.JewelryConfig;
import com.krakenplugins.example.jewelry.JewelryPlugin;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static com.krakenplugins.example.jewelry.script.JewelryScript.GOLD_BAR;

@Slf4j
public class BankTask extends AbstractTask {

    @Inject
    private BankService bankService;

    @Inject
    private JewelryConfig config;

    @Inject
    private JewelryPlugin plugin;

    @Inject
    private PurchaseSuppliesTask purchaseSuppliesTask;

    private final Random random = new Random();

    @Override
    public boolean validate() {
          return ctx.players().local().isIdle() && ctx.players().local().isInArea(plugin.getEdgevilleBank()) &&
                !ctx.inventory().hasItem(GOLD_BAR) && !ctx.inventory().hasItem(config.jewelry().getSecondaryGemId()) && bankService.isOpen();
    }

    @Override
    public int execute() {
        purchaseSuppliesTask.setPurchaseComplete(false);
        List<BankInventoryEntity> necklaces = ctx.bankInventory().withName(config.jewelry().getNecklaceName()).stream().limit(5).collect(Collectors.toList());
        BankInventoryEntity necklace = null;

        if (!necklaces.isEmpty()) {
            double spread = 1.5;
            int index = (int) Math.abs(random.nextGaussian() * spread);
            if (index >= necklaces.size()) {
                index = necklaces.size() - 1;
            }
            necklace = necklaces.get(index);
        }

        if (necklace != null) {
            if (config.useMouse()) {
                ctx.getMouse().move(necklace.raw());
            }
            necklace.depositAll();
            SleepService.sleepUntil(() -> ctx.inventory().withName(config.jewelry().getNecklaceName()).stream().findAny().isEmpty(), 3000);
        }

        Runnable withdrawGold = () -> {
            BankEntity goldBar = ctx.bank().withId(GOLD_BAR).first();
            if (goldBar != null) {
                log.info("Withdrawing Gold");
                goldBar.withdraw(13);
                plugin.getMetrics().setGoldBarsRemaining(Math.max(Math.abs(goldBar.count() - 13), 0));
            }
        };

        Runnable withdrawGem = () -> {
            BankEntity gem = ctx.bank().withId(config.jewelry().getSecondaryGemId()).first();
            if (gem != null) {
                log.info("Withdrawing id: {}", config.jewelry().getSecondaryGemId());
                gem.withdraw(13);

                plugin.getMetrics().setGemsRemaining(Math.max(Math.abs(gem.count() - 13), 0));
            }
        };

        BankInventoryEntity necklaceMould = ctx.bankInventory().withId(1597).first();

        if(necklaceMould == null) {
            BankEntity mould = ctx.bank().withId(1597).first();
            if(mould == null) {
                log.error("Player does not have mould in inventory and no necklace mould is present in the bank. Cannot craft.");
                return 600;
            }
            mould.withdrawOne();
        }

        // 50% chance to flip the order
        if (random.nextBoolean()) {
            withdrawGold.run();
            sleepGaussian(600, 1800);
            withdrawGem.run();
        } else {
            withdrawGem.run();
            sleepGaussian(600, 1800);
            withdrawGold.run();
        }

        SleepService.sleep(600, 1200);
        bankService.close();
        return RandomService.between(600, 1000);
    }

    private void sleepGaussian(int min, int max) {
        int mean = (min + max) / 2;
        int deviation = (max - min) / 6; // 99.7% of values fall within min-max
        int sleepTime = (int) (random.nextGaussian() * deviation + mean);

        // Clamp values just in case
        sleepTime = Math.max(min, Math.min(max, sleepTime));
        SleepService.sleep(sleepTime);
    }

    @Override
    public String status() {
        return "Walking to Bank";
    }
}
