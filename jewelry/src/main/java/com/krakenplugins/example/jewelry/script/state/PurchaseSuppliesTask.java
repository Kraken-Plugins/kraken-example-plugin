package com.krakenplugins.example.jewelry.script.state;

import com.kraken.api.Context;
import com.kraken.api.core.script.AbstractTask;
import com.kraken.api.query.container.bank.BankEntity;
import com.kraken.api.query.container.inventory.InventoryEntity;
import com.kraken.api.query.npc.NpcEntity;
import com.kraken.api.service.bank.BankService;
import com.kraken.api.service.grandexchange.GrandExchangeService;
import com.kraken.api.service.grandexchange.GrandExchangeSlot;
import com.kraken.api.service.util.SleepService;
import com.kraken.api.service.util.price.ItemPriceService;
import com.krakenplugins.example.jewelry.JewelryConfig;
import com.krakenplugins.example.jewelry.JewelryPlugin;
import com.krakenplugins.example.jewelry.script.JewelryScript;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Singleton
public class PurchaseSuppliesTask extends AbstractTask {

    @Inject
    private GrandExchangeService geService;

    @Inject
    private BankService bankService;

    @Inject
    private JewelryPlugin plugin;

    @Inject
    private Context ctx;

    @Inject
    private JewelryConfig config;

    @Inject
    private ItemPriceService itemPriceService;

    private long lastPurchaseAttemptTime = -1;
    private int bankGoldBars = 0;
    private int bankGems = 0;

    @Getter
    @Setter
    private boolean purchaseComplete = false;

    @Override
    public boolean validate() {
        if (lastPurchaseAttemptTime != -1 && System.currentTimeMillis() - lastPurchaseAttemptTime > 50000) {
            log.info("Resetting GE Purchase Attempt Time");
            lastPurchaseAttemptTime = -1;
            return false;
        }

        return ctx.players().local().isInArea(plugin.getGrandExchange()) &&
                lastPurchaseAttemptTime == -1 &&
                !purchaseComplete &&
                config.enableResupply();
    }

    @Override
    public int execute() {
        log.info("Attempting to purchase");
        try {
            // 1. Prepare Bank: Check supplies, withdraw crafted items to sell, withdraw coins
            if (!prepareBank()) {
                log.error("Failed to prepare bank");
                return 600;
            }

            NpcEntity clerk = ctx.npcs().withAction("Exchange").nearest();
            if (clerk == null) {
                log.error("Could not find Grand Exchange Clerk");
                return 600;
            }

            // 2. Open GE
            if (!clerk.interact("Exchange")) {
                log.error("Failed to interact with Grand Exchange Clerk");
                return 600;
            }

            SleepService.sleepUntil(geService::isOpen, 5000);

            // Wait several ticks before attempting to sell
            SleepService.sleepFor(3);
            sellCraftedItems();
            buySupplies();
            depositAll();

            lastPurchaseAttemptTime = System.currentTimeMillis();
            purchaseComplete = true;
            return 0;
        } catch (Exception e) {
            log.error("Failed to resupply: ", e);
        } finally {
            lastPurchaseAttemptTime = System.currentTimeMillis();
        }
        return 0;
    }

    private boolean prepareBank() {
        NpcEntity banker = ctx.npcs().withAction("Bank").nearest();
        if (banker == null) {
            log.error("Cannot find banker");
            return false;
        }

        if (!banker.interact("Bank")) {
            log.error("Failed to interact with banker");
            return false;
        }

        SleepService.sleepUntil(bankService::isOpen, 5000);

        if (!ctx.inventory().isEmpty()) {
            bankService.depositAll();
            SleepService.sleepFor(1);
        }

        // Check supplies in bank
        BankEntity goldBars = ctx.bank().withId(JewelryScript.GOLD_BAR).first();
        bankGoldBars = goldBars != null ? goldBars.count() : 0;

        BankEntity gems = ctx.bank().withId(config.jewelry().getSecondaryGemId()).first();
        bankGems = gems != null ? gems.count() : 0;

        // If we have items in the bank set purchase complete to true and close the bank
        // this will move to the Walk to edgeville task.
        if(bankGoldBars > 0 && bankGems > 0) {
            purchaseComplete = true;
            bankService.close();
            SleepService.sleepUntil(() -> !bankService.isOpen(), 3000);
            return false; // Return false so that it "fails" this and doesn't go and try to purchase stuff
        }

        // Withdraw crafted jewelry (Noted)
        int craftedId = config.jewelry().getCraftedItemId();
        BankEntity crafted = ctx.bank().withId(craftedId).first();
        if (crafted != null && crafted.count() > 0) {
            crafted.withdrawAllNoted();
            SleepService.sleepFor(1);
        }

        bankService.close();
        SleepService.sleepUntil(() -> !bankService.isOpen(), 3000);
        return true;
    }


    private void sellCraftedItems() {
        int craftedId = config.jewelry().getCraftedItemId();
        int notedCraftedId = craftedId + 1;
        InventoryEntity craftedItem = ctx.inventory().withId(notedCraftedId).first();

        if (craftedItem != null) {
            int price = getMinSellPrice(craftedId).join();

            if (price <= 0) {
                log.error("Invalid sell price returned (0), aborting sell.");
                return;
            }

            GrandExchangeSlot slot = geService.queueSellOrder(notedCraftedId, price);
            if (slot != null) {
                log.info("Selling {} {}@{}", craftedItem.raw().getQuantity(), config.jewelry().name(), price);
                while(!slot.isFulfilled()) {
                    SleepService.tick();
                }

                SleepService.sleepFor(1);
                geService.collect(slot, false);
                SleepService.sleepFor(1);
            } else {
                log.info("GE Slot is null, ensure there is a free slot available");
            }
        } else {
            log.info("No crafted items in inventory to sell.");
        }
    }

    private void buySupplies() {
        int spendingLimit = config.maxCoins();

        if (spendingLimit <= 0) {
            log.info("Spending limit is 0, skipping purchases");
            return;
        }

        CompletableFuture<Integer> goldFuture = getMaxBuyPrice(JewelryScript.GOLD_BAR);
        CompletableFuture<Integer> gemFuture = getMaxBuyPrice(config.jewelry().getSecondaryGemId());

        CompletableFuture.allOf(goldFuture, gemFuture).join();

        int goldBarPrice = goldFuture.join();
        int gemPrice = gemFuture.join();

        // Safety check
        if (goldBarPrice <= 0 || gemPrice <= 0) {
            log.error("Invalid prices returned (Gold: {}, Gem: {}). Aborting buy.", goldBarPrice, gemPrice);
            return;
        }

        int toBuyGold = 0;
        int toBuyGems = 0;
        int coinsRemaining = spendingLimit;

        // First, balance existing supplies
        if (bankGoldBars < bankGems) {
            int diff = bankGems - bankGoldBars;
            int maxCanBuy = coinsRemaining / goldBarPrice;
            int toBalance = Math.min(diff, maxCanBuy);

            if (toBalance > 0) {
                toBuyGold += toBalance;
                coinsRemaining -= toBalance * goldBarPrice;
                log.info("Balancing: buying {} gold bars to match: {} gems, cost: {}", toBalance, bankGems, toBalance * goldBarPrice);
            }
        } else if (bankGems < bankGoldBars) {
            int diff = bankGoldBars - bankGems;
            int maxCanBuy = coinsRemaining / gemPrice;
            int toBalance = Math.min(diff, maxCanBuy);

            if (toBalance > 0) {
                toBuyGems += toBalance;
                coinsRemaining -= toBalance * gemPrice;
                log.info("Balancing: buying {} gems to match {} gold bars, cost: {}", toBalance, bankGoldBars, toBalance * gemPrice);
            }
        }

        // Buy pairs with remaining budget
        if (coinsRemaining > 0) {
            int pairCost = goldBarPrice + gemPrice;
            int pairs = coinsRemaining / pairCost;

            if (pairs > 0) {
                toBuyGold += pairs;
                toBuyGems += pairs;
                coinsRemaining -= pairs * pairCost;
                log.info("Buying {} pairs (gems + gold) with remaining budget", pairs);
            }
        }

        int totalCost = (toBuyGold * goldBarPrice) + (toBuyGems * gemPrice);
        log.info("Buying {} Gold Bars@{} and {} Gems@{} | Total cost: {} / {} limit, leftover coins: {}",
                toBuyGold, goldBarPrice, toBuyGems, gemPrice, totalCost, spendingLimit, coinsRemaining);

        // Execute purchases
        GrandExchangeSlot goldSlot = null;
        GrandExchangeSlot gemSlot = null;

        if (toBuyGold > 0) {
            goldSlot = geService.queueBuyOrder(JewelryScript.GOLD_BAR, toBuyGold, goldBarPrice);
            SleepService.sleepFor(3);
        }

        if (toBuyGems > 0) {
            gemSlot = geService.queueBuyOrder(config.jewelry().getSecondaryGemId(), toBuyGems, gemPrice);
            SleepService.sleepFor(3);
        }

        // Wait for fulfillment
        long start = System.currentTimeMillis();
        long timeout = 120000; // 2 minutes

        while (System.currentTimeMillis() - start < timeout) {
            boolean goldComplete = (goldSlot == null || goldSlot.isFulfilled());
            boolean gemComplete = (gemSlot == null || gemSlot.isFulfilled());

            if (goldComplete && gemComplete) {
                log.info("Both orders fulfilled successfully");
                break;
            }

            SleepService.sleep(500);
        }

        // Check final status
        boolean goldFulfilled = (goldSlot == null || goldSlot.isFulfilled());
        boolean gemFulfilled = (gemSlot == null || gemSlot.isFulfilled());

        if (!goldFulfilled || !gemFulfilled) {
            log.warn("Orders timed out - Gold: {}, Gems: {}", goldFulfilled, gemFulfilled);
        }

        // Collect or cancel orders
        if (goldSlot != null) {
            if (!goldSlot.isFulfilled()) {
                log.info("Cancelling unfulfilled gold bar order");
                geService.cancelOffer(goldSlot);
                SleepService.sleepFor(2);
            }
            geService.collect(goldSlot, true);
            SleepService.sleepFor(1);
        }

        if (gemSlot != null) {
            if (!gemSlot.isFulfilled()) {
                log.info("Cancelling unfulfilled gem order");
                geService.cancelOffer(gemSlot);
                SleepService.sleepFor(2);
            }
            geService.collect(gemSlot, true);
            SleepService.sleepFor(1);
        }
    }

    private void depositAll() {
        NpcEntity banker = ctx.npcs().withAction("Bank").nearest();
        if (banker != null && banker.interact("Bank")) {
            SleepService.sleepUntil(bankService::isOpen, 5000);
            if (bankService.isOpen()) {
                bankService.depositAll();
                bankService.close();
            }
        }
    }
    /**
     * Async calculation of Max Buy Price.
     * Returns a Future that will eventually contain the calculated price.
     */
    private CompletableFuture<Integer> getMaxBuyPrice(int itemId) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        double percentBuffer = (double) config.purchaseBufferPercent() / 100;

        // Call the async service
        itemPriceService.getItemPrice(itemId, "ItemPriceAPI/1.0", (price) -> {
            if (price == null) {
                log.error("Failed to lookup buy price for item: {}", itemId);
                future.complete(1); // Default safety value
                return;
            }

            try {
                int averagePrice = price.getLow() + ((price.getHigh() - price.getLow()) / 2);
                int bufferAmount = (int) (averagePrice * percentBuffer);
                int finalPrice = averagePrice + bufferAmount;

                log.info("Buy price for item {}: {} (avg: {}, buffer: +{})", itemId, finalPrice, averagePrice, bufferAmount);
                future.complete(finalPrice);
            } catch (Exception e) {
                log.error("Error calculating buy price for item {}", itemId, e);
                future.complete(1); // Default safety value
            }
        });

        return future;
    }

    /**
     * Async calculation of Min Sell Price.
     * Returns a Future that will eventually contain the calculated price.
     */
    private CompletableFuture<Integer> getMinSellPrice(int itemId) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        double percentBuffer = (double) config.sellBufferPercent() / 100;

        itemPriceService.getItemPrice(itemId, "ItemPriceAPI/1.0", (price) -> {
            if (price == null) {
                log.error("Failed to lookup sell price for item: {}", itemId);
                future.complete(0);
                return;
            }

            try {
                int averagePrice = price.getLow() + ((price.getHigh() - price.getLow()) / 2);
                int bufferAmount = (int) (averagePrice * percentBuffer);
                int finalPrice = averagePrice - bufferAmount;

                log.info("Sell price for item {}: {} (avg: {}, buffer: -{})", itemId, finalPrice, averagePrice, bufferAmount);
                future.complete(finalPrice);
            } catch (Exception e) {
                log.error("Error calculating sell price for item {}", itemId, e);
                future.complete(0);
            }
        });

        return future;
    }

    @Override
    public String status() {
        return "Purchasing Supplies";
    }
}
