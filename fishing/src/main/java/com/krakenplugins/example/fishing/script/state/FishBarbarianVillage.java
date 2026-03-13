package com.krakenplugins.example.fishing.script.state;

import com.google.inject.Inject;
import com.kraken.api.core.script.PriorityTask;
import com.kraken.api.query.npc.NpcEntity;
import com.kraken.api.service.util.RandomService;
import com.kraken.api.service.util.SleepService;
import com.krakenplugins.example.fishing.FishingConfig;
import com.krakenplugins.example.fishing.FishingPlugin;
import com.krakenplugins.example.fishing.script.FishingLocation;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FishBarbarianVillage extends PriorityTask {

    private static final long IDLE_RESET_MS = 2000;
    private static final long DROP_IDLE_BYPASS_MS = 5000;
    private static final double BASE_REACTION_CHANCE = 0.01;

    private static final int LOW_TICK_THRESHOLD = 1000;
    private static final int MEDIUM_TICK_THRESHOLD = 6000; // 1 hour
    private static final int HIGH_TICK_THRESHOLD = 12000; // 2 hours
    private static final int VERY_HIGH_TICK_THRESHOLD = 18000; // 3 hours

    private static final double INCREMENT_LOW = 0.07; // 7% increase every tick
    private static final double INCREMENT_MEDIUM = 0.025; // 2.5% increase every tick
    private static final double INCREMENT_HIGH = 0.015; // 1.5% increase every tick
    private static final double INCREMENT_VERY_HIGH = 0.01; // 1% increase every tick
    private static final double INCREMENT_EXTREME = 0.005; // 0.5% increase every tick

    @Inject
    private FishingPlugin plugin;

    @Inject
    private FishingConfig config;

    private int idleTicks = 0;
    private long lastLoopTime = 0;

    @Override
    public boolean validate() {
        // Rod and feathers
        if (!ctx.inventory().hasItem(309) || !ctx.inventory().hasItem(314)) return false;

        return ctx.players().local().isInArea(FishingLocation.BARBARIAN_VILLAGE.getLocation(), 15) &&
                ctx.players().local().isIdle() &&
                !ctx.inventory().isFull();
    }

    @Override
    public int execute() {
        // If it's been more than 2000ms since the last execute call, we assume
        // this is a fresh "idle" session (we just finished fishing), so we reset the counter.
        long now = System.currentTimeMillis();
        if (now - lastLoopTime > IDLE_RESET_MS) {
            idleTicks = 0;
        }
        lastLoopTime = now;

        // Check if we recently finished dropping (e.g., within 4 seconds)
        // If so, we are "Active" and should click immediately, bypassing the idle simulation.
        long lastDropTimestamp = plugin.getLastDropTimestamp();
        long timeSinceDrop = now - lastDropTimestamp;
        boolean skipIdleAfterDrop = lastDropTimestamp > 0 && timeSinceDrop < DROP_IDLE_BYPASS_MS;

        if (!skipIdleAfterDrop) {
            // Calculate a chance to click this tick based on how long we've been waiting.
            // The increment scales by client tick count to simulate fatigue over time.
            // TODO Add some randomization just because I've been playing for 3 hours doesn't mean Im super slow EVERY single time I go
            // to a new fishing spot
            double reactionIncrement = getReactionIncrement();
            double reactionChance = BASE_REACTION_CHANCE + (idleTicks * reactionIncrement);
            reactionChance = Math.min(1.0, reactionChance);

            if (Math.random() > reactionChance) {
                log.info("Missed reaction window, increasing reaction chance by {} next tick. Current chance = {}", reactionIncrement, reactionChance);
                idleTicks++;
                return RandomService.between(400, 600);
            }
        } else {
            idleTicks = 0;
        }

        NpcEntity spot = ctx.npcs().withId(FishingLocation.BARBARIAN_VILLAGE.getSpotId()).nearest();
        if (spot != null) {
            plugin.setTargetSpot(spot);

            if (config.useMouse()) {
                ctx.getMouse().move(spot.raw());
            }

            if (spot.interact("Lure")) {
                // Successful click, reset idle ticks for the next time we become idle
                idleTicks = 0;
                if (skipIdleAfterDrop) {
                    plugin.setLastDropTimestamp(0);
                }
                SleepService.sleepUntil(() -> ctx.players().local().isMoving() || ctx.players().local().raw().getAnimation() != -1, 5000);
            }
        } else {
            log.info("No spot found.");
            idleTicks = 0;
        }

        return 0;
    }

    @Override
    public String status() {
        if (idleTicks > 0) {
            return "Idling (Reaction Delay)";
        }
        return "Fishing (Barbarian Village)";
    }

    @Override
    public int getPriority() {
        return 0;
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
        if (tickCount < VERY_HIGH_TICK_THRESHOLD) {
            return INCREMENT_VERY_HIGH;
        }
        return INCREMENT_EXTREME;
    }
}
