package com.krakenplugins.example.fishing.script.state.barbarian;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.core.script.PriorityTask;
import com.kraken.api.query.gameobject.GameObjectEntity;
import com.kraken.api.service.ui.processing.ProcessingService;
import com.kraken.api.service.util.RandomService;
import com.kraken.api.service.util.SleepService;
import com.krakenplugins.example.fishing.FishingConfig;
import com.krakenplugins.example.fishing.FishingPlugin;
import com.krakenplugins.example.fishing.script.FishingLocation;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Cooks a full inventory of trout and salmon on the permanent fire by the Barbarian Village fishing spots,
 * one kind of fish per trip through the cooking interface.
 */
@Slf4j
@Singleton
public class CookFish extends PriorityTask {

    /**
     * How far from the fishing spots the player and the fire may be. Other fires in the village share the
     * fire's id, so the search is anchored on the fishing spots rather than on the player.
     */
    private static final int FIRE_RADIUS = 15;

    /** Quantity handed to the "How many?" interface, so one click cooks every fish of the chosen kind. */
    private static final int COOK_ALL = 28;

    /**
     * A full inventory cooks in roughly 26 fish x 4 ticks. The wait resolves as soon as the raw fish
     * run out, so this only caps a cook that never ends.
     */
    private static final long COOK_TIMEOUT_MS = 75_000;

    /**
     * How long the player may stand idle with raw fish left before the cook counts as interrupted, for
     * example by a level up. Cooking leaves a one tick idle gap between fish, so this spans several ticks.
     */
    private static final long INTERRUPTED_IDLE_MS = 3000;

    private static final long IDLE_RESET_MS = 2000;
    private static final double BASE_REACTION_CHANCE = 0.02;

    private static final int LOW_TICK_THRESHOLD = 1000;
    private static final int MEDIUM_TICK_THRESHOLD = 3000;
    private static final int HIGH_TICK_THRESHOLD = 6000;

    private static final double INCREMENT_LOW = 0.04;
    private static final double INCREMENT_MEDIUM = 0.025;
    private static final double INCREMENT_HIGH = 0.015;
    private static final double INCREMENT_VERY_HIGH = 0.01;

    @Inject
    private FishingConfig config;

    @Inject
    private FishingPlugin plugin;

    @Inject
    private ProcessingService processingService;

    private int idleTicks = 0;
    private long lastLoopTime = 0;

    @Override
    public boolean validate() {
        // Holds the full inventory whether or not the player is idle. The fishing animation can still be
        // playing when the inventory fills, and the cooking animation can outlast the last fish of a kind.
        // DropFish validates on a full inventory alone, so handing it either moment drops the raw fish.
        return config.barbVillageCook() &&
                ctx.inventory().isFull() &&
                nextFish() != null &&
                ctx.players().local().isInArea(FishingLocation.BARBARIAN_VILLAGE.getLocation(), FIRE_RADIUS);
    }

    @Override
    public int execute() {
        RawFish fish = nextFish();
        if (fish == null) {
            return 0;
        }

        if (processingService.isOpen()) {
            idleTicks = 0;
            return cook(fish);
        }

        // Still finishing the last catch, the last fish of a cook, or the walk to the fire.
        if (!ctx.players().local().isIdle()) {
            return RandomService.between(300, 600);
        }

        long now = System.currentTimeMillis();
        if (now - lastLoopTime > IDLE_RESET_MS) {
            idleTicks = 0;
        }
        lastLoopTime = now;

        double reactionIncrement = getReactionIncrement();
        double reactionChance = BASE_REACTION_CHANCE + (idleTicks * reactionIncrement);
        reactionChance = Math.min(1.0, reactionChance);
        if (Math.random() > reactionChance) {
            log.info("Missed reaction window before cooking, increasing reaction chance by {} next tick. Current chance = {}", reactionIncrement, reactionChance);
            idleTicks++;
            return RandomService.between(400, 600);
        }

        // Only the permanent fire has a left click "Cook" option. A fire lit by a player has no options at
        // all, so it is skipped even when it is closer.
        GameObjectEntity fire = ctx.gameObjects()
                .withId(ObjectID.FIRE_COOK)
                .within(FishingLocation.BARBARIAN_VILLAGE.getLocation(), FIRE_RADIUS)
                .nearest()
                .orElse(null);

        if (fire == null) {
            // This task holds every full inventory of raw fish, so nothing else would run in its place.
            plugin.pauseScript("Cannot find the fire by the Barbarian Village fishing spots");
            return 0;
        }

        if (fire.interact("Cook")) {
            idleTicks = 0;
            if (!SleepService.sleepUntilTrue(processingService::isOpen, 400, 5000)) {
                log.warn("Clicked the fire but the cooking interface never opened.");
            }
        }

        return 0;
    }

    /**
     * Cooks every fish of one kind. {@code process} matches on the id of the product, which is the only
     * thing the interface lists. Any other kind left in the inventory is cooked on a later loop, which
     * clicks the fire again once the player is idle.
     */
    private int cook(RawFish fish) {
        processingService.setAmount(COOK_ALL);

        if (!processingService.process("Cook", fish.cookedId)) {
            // Usually the interface does not list this fish. Retrying will not change that, so stop instead
            // of spinning on it.
            plugin.pauseScript("Could not pick item id " + fish.cookedId + " in the cooking interface");
            return 0;
        }

        if (!waitForCook(fish.rawId)) {
            log.info("Stopped cooking with raw fish id {} left, clicking the fire again.", fish.rawId);
        }
        return RandomService.between(600, 900);
    }

    /**
     * Waits until no raw fish of the given id are left. Returns early when the player has stood idle long
     * enough that cooking must have stopped, so a level up does not hold the script for the full timeout.
     *
     * @param rawId the raw fish being cooked
     * @return true once the raw fish are gone, false if cooking stopped first or timed out
     */
    private boolean waitForCook(int rawId) {
        AtomicLong idleSince = new AtomicLong();
        SleepService.sleepUntilTrue(() -> {
            if (!ctx.inventory().hasItem(rawId)) {
                return true;
            }

            if (!ctx.players().local().isIdle()) {
                idleSince.set(0);
                return false;
            }

            long now = System.currentTimeMillis();
            idleSince.compareAndSet(0, now);
            return now - idleSince.get() >= INTERRUPTED_IDLE_MS;
        }, 600, COOK_TIMEOUT_MS);

        return !ctx.inventory().hasItem(rawId);
    }

    /**
     * The first raw fish in the inventory that the player can cook, or null when none are left. Fish above
     * the player's Cooking level are left for DropFish, since the game will not cook them and clicking the
     * fire for them would never finish.
     */
    private RawFish nextFish() {
        int cookingLevel = ctx.players().local().getBoostedLevel(Skill.COOKING);
        for (RawFish fish : RawFish.values()) {
            if (cookingLevel >= fish.level && ctx.inventory().hasItem(fish.rawId)) {
                return fish;
            }
        }
        return null;
    }

    @Override
    public String status() {
        return "Cooking Fish";
    }

    @Override
    public int getPriority() {
        // Ahead of DropFish, which validates on a full inventory alone and would otherwise throw the
        // raw fish away before cooking is ever considered.
        return -2;
    }

    private double getReactionIncrement() {
        int tickCount = ctx.getClient().getTickCount();
        if (tickCount < LOW_TICK_THRESHOLD) {
            return INCREMENT_LOW;
        }
        if (tickCount < MEDIUM_TICK_THRESHOLD) {
            return INCREMENT_MEDIUM;
        }
        if (tickCount < HIGH_TICK_THRESHOLD) {
            return INCREMENT_HIGH;
        }
        return INCREMENT_VERY_HIGH;
    }

    /**
     * The fish caught at Barbarian Village, with the Cooking level each needs.
     */
    @AllArgsConstructor
    private enum RawFish {
        TROUT(ItemID.RAW_TROUT, ItemID.TROUT, 15),
        SALMON(ItemID.RAW_SALMON, ItemID.SALMON, 25);

        private final int rawId;
        private final int cookedId;
        private final int level;
    }
}
