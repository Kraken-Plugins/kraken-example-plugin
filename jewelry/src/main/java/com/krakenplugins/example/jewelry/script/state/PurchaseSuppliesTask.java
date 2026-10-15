package com.krakenplugins.example.jewelry.script.state;

import com.kraken.api.core.script.AbstractTask;
import com.kraken.api.query.container.bank.BankEntity;
import com.kraken.api.query.container.inventory.InventoryEntity;
import com.kraken.api.query.npc.NpcEntity;
import com.kraken.api.service.bank.BankService;
import com.kraken.api.service.grandexchange.GrandExchangeService;
import com.kraken.api.service.grandexchange.GrandExchangeSlot;
import com.kraken.api.service.util.SleepService;
import com.kraken.api.service.util.price.ItemPrice;
import com.kraken.api.service.util.price.ItemPriceService;
import com.krakenplugins.example.jewelry.JewelryConfig;
import com.krakenplugins.example.jewelry.JewelryPlugin;
import com.krakenplugins.example.jewelry.script.JewelryScript;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class PurchaseSuppliesTask extends AbstractTask {

    // How long an offer gets to fill before it is cancelled and collected back.
    private static final long OFFER_TIMEOUT_MS = 120000;

    // How long an interface has to open or close before the trip gives up on it.
    private static final long INTERFACE_TIMEOUT_MS = 5000;

    @Inject
    private GrandExchangeService geService;

    @Inject
    private BankService bankService;

    @Inject
    private JewelryPlugin plugin;

    @Inject
    private JewelryConfig config;

    @Inject
    private ItemPriceService itemPriceService;

    private int bankGoldBars = 0;
    private int bankGems = 0;

    @Getter
    @Setter
    private boolean purchaseComplete = false;

    @Override
    public boolean validate() {
        return config.enableResupply()
                && !purchaseComplete
                && ctx.players().local().isInArea(plugin.getGrandExchange());
    }

    @Override
    public int execute() {
        log.info("Attempting to purchase");
        try {
            // 1. Prepare Bank: Check supplies, withdraw crafted items to sell, withdraw coins
            if (!prepareBank()) {
                return 600;
            }

            NpcEntity clerk = ctx.npcs().withAction("Exchange").nearest().orElse(null);
            if (clerk == null || !clerk.interact("Exchange")) {
                plugin.halt("No Grand Exchange clerk to trade with");
                return 0;
            }

            if (!SleepService.sleepUntil(geService::isOpen, INTERFACE_TIMEOUT_MS)) {
                log.info("The Grand Exchange did not open, retrying");
                return 600;
            }

            // Wait several ticks before attempting to sell
            SleepService.sleepFor(3);
            sellCraftedItems();
            buySupplies();

            if (!depositAndVerify()) {
                plugin.halt("The Grand Exchange trip finished without stocking the bank");
                return 0;
            }

            purchaseComplete = true;
            return 600;
        } catch (Exception e) {
            log.error("Failed to resupply: ", e);
            return 600;
        }
    }

    /**
     * Empties the inventory into the bank, counts what is in stock and takes out the crafted jewelry
     * to sell.
     *
     * @return true when the trip should go on to buy, false when there is nothing left to do here.
     *         Anything the trip cannot recover from halts the script instead.
     */
    private boolean prepareBank() {
        NpcEntity banker = ctx.npcs().withAction("Bank").nearest().orElse(null);
        if (banker == null || !banker.interact("Bank")) {
            plugin.halt("No Grand Exchange banker to bank with");
            return false;
        }

        if (!SleepService.sleepUntil(bankService::isOpen, INTERFACE_TIMEOUT_MS)) {
            log.info("The bank did not open, retrying");
            return false;
        }

        if (!ctx.inventory().isEmpty()) {
            bankService.depositAll();
            SleepService.sleepFor(1);
        }

        // Check supplies in bank
        BankEntity goldBars = ctx.bank().withId(JewelryScript.GOLD_BAR).first().orElse(null);
        bankGoldBars = goldBars != null ? goldBars.getQuantity() : 0;

        BankEntity gems = ctx.bank().withId(config.jewelry().getSecondaryGemId()).first().orElse(null);
        bankGems = gems != null ? gems.getQuantity() : 0;

        // Another trip already stocked the bank, so head home rather than buying more.
        if (bankGoldBars > 0 && bankGems > 0) {
            closeBank();
            purchaseComplete = true;
            return false;
        }

        // Withdraw crafted jewelry to sell. Noted, so a full trip's worth fits in one slot.
        BankEntity crafted = ctx.bank().withId(config.jewelry().getCraftedItemId()).first().orElse(null);
        if (crafted != null && crafted.getQuantity() > 0) {
            crafted.withdrawAllNoted();
            SleepService.sleepFor(1);
        }

        closeBank();
        return true;
    }

    private void sellCraftedItems() {
        // Noted and unnoted share a name but not an id, so the item's own id is what the offer needs.
        InventoryEntity crafted = ctx.inventory().withName(config.jewelry().getNecklaceName()).noted().first().orElse(null);
        if (crafted == null) {
            log.info("No crafted items in inventory to sell.");
            return;
        }

        int price = getMinSellPrice(config.jewelry().getCraftedItemId());
        if (price <= 0) {
            log.error("Invalid sell price returned (0), aborting sell.");
            return;
        }

        GrandExchangeSlot slot = geService.queueSellOrder(crafted.getId(), price);
        if (slot == null) {
            log.info("GE Slot is null, ensure there is a free slot available");
            return;
        }

        log.info("Selling {} {}@{}", crafted.raw().getQuantity(), config.jewelry().name(), price);
        collectOrCancel(waitForOffer(slot), false);
    }

    private void buySupplies() {
        int spendingLimit = config.maxCoins();

        if (spendingLimit <= 0) {
            log.info("Spending limit is 0, skipping purchases");
            return;
        }

        int goldBarPrice = getMaxBuyPrice(JewelryScript.GOLD_BAR);
        int gemPrice = getMaxBuyPrice(config.jewelry().getSecondaryGemId());

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

        collectOrCancel(waitForOffer(goldSlot), true);
        collectOrCancel(waitForOffer(gemSlot), true);
    }

    /**
     * Waits for an offer to fill, giving up after {@link #OFFER_TIMEOUT_MS} so a price the market
     * will not take cannot park the script.
     *
     * @param slot The slot the offer was placed in, or {@code null} when no offer was placed.
     * @return The same slot, so this can be chained into {@link #collectOrCancel}.
     */
    private GrandExchangeSlot waitForOffer(GrandExchangeSlot slot) {
        if (slot != null && !SleepService.sleepUntil(slot::isFulfilled, OFFER_TIMEOUT_MS)) {
            log.warn("The offer in slot {} did not fill within {}ms", slot.getSlot(), OFFER_TIMEOUT_MS);
        }
        return slot;
    }

    /**
     * Takes an offer back out of the Grand Exchange, aborting it first if it never filled.
     *
     * @param slot The slot to collect, or {@code null} when no offer was placed.
     * @param noted True to collect the items as notes.
     */
    private void collectOrCancel(GrandExchangeSlot slot, boolean noted) {
        if (slot == null) {
            return;
        }

        if (!slot.isFulfilled()) {
            log.info("Cancelling the unfilled offer in slot {}", slot.getSlot());
            geService.cancelOffer(slot);
            SleepService.sleepFor(2);
        }

        geService.collect(slot, noted);
        SleepService.sleepFor(1);
    }

    /**
     * Banks everything the trip came back with.
     *
     * @return true when the bank now holds both materials, which is the only outcome that lets the
     *         script go home and craft.
     */
    private boolean depositAndVerify() {
        NpcEntity banker = ctx.npcs().withAction("Bank").nearest().orElse(null);
        if (banker == null || !banker.interact("Bank")) {
            log.error("Could not reach a banker to deposit the purchase into");
            return false;
        }

        if (!SleepService.sleepUntil(bankService::isOpen, INTERFACE_TIMEOUT_MS)) {
            log.error("The bank did not open to deposit the purchase into");
            return false;
        }

        bankService.depositAll();
        SleepService.sleepFor(1);

        boolean stocked = ctx.bank().withId(JewelryScript.GOLD_BAR).first().isPresent()
                && ctx.bank().withId(config.jewelry().getSecondaryGemId()).first().isPresent();

        closeBank();
        return stocked;
    }

    private void closeBank() {
        bankService.close();
        SleepService.sleepUntil(bankService::isClosed, INTERFACE_TIMEOUT_MS);
    }

    /**
     * Calculates the most the script will pay for an item: its wiki price plus the configured purchase buffer.
     * @param itemId The item to price
     * @return The buffered buy price, or 0 when the item has no price, which aborts the buy
     */
    private int getMaxBuyPrice(int itemId) {
        ItemPrice price = itemPriceService.getItemPrice(itemId);
        if (price == null) {
            log.error("Failed to lookup buy price for item: {}", itemId);
            return 0;
        }

        long bufferAmount = (long) (price.getWikiPrice() * (config.purchaseBufferPercent() / 100.0));
        int finalPrice = (int) (price.getWikiPrice() + bufferAmount);
        log.info("Buy price for item {}: {} (wiki: {}, buffer: +{})", itemId, finalPrice, price.getWikiPrice(), bufferAmount);
        return finalPrice;
    }

    /**
     * Calculates the least the script will accept for an item: its wiki price minus the configured sell buffer.
     * @param itemId The item to price
     * @return The buffered sell price, or 0 when the item has no price, which aborts the sale
     */
    private int getMinSellPrice(int itemId) {
        ItemPrice price = itemPriceService.getItemPrice(itemId);
        if (price == null) {
            log.error("Failed to lookup sell price for item: {}", itemId);
            return 0;
        }

        long bufferAmount = (long) (price.getWikiPrice() * (config.sellBufferPercent() / 100.0));
        int finalPrice = (int) (price.getWikiPrice() - bufferAmount);
        log.info("Sell price for item {}: {} (wiki: {}, buffer: -{})", itemId, finalPrice, price.getWikiPrice(), bufferAmount);
        return finalPrice;
    }

    @Override
    public String status() {
        return "Purchasing Supplies";
    }
}
