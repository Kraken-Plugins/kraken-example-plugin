package com.krakenplugins.example.fishing.script.state;

import com.google.inject.Inject;
import com.kraken.api.core.script.PriorityTask;
import com.kraken.api.query.container.inventory.InventoryEntity;
import com.kraken.api.service.ui.tab.InterfaceTab;
import com.kraken.api.service.ui.tab.TabService;
import com.kraken.api.service.util.RandomService;
import com.kraken.api.service.util.SleepService;
import com.krakenplugins.example.fishing.FishingConfig;
import com.krakenplugins.example.fishing.FishingPlugin;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class DropFish extends PriorityTask {

    @Inject
    private FishingConfig config;

    @Inject
    private FishingPlugin plugin;

    @Inject
    private TabService tabService;

    private boolean isDropping = false;

    // PROBABILITY STATE
    // 0 = 5% chance (Normal)
    // 1 = 3% chance (Caution)
    // 2 = 1% chance (Strict)
    private int missChanceState = 0;

    // How many items to process before relaxing the probability back to the previous state
    private int recoveryCounter = 0;

    @Override
    public boolean validate() {
        boolean isFull = ctx.inventory().isFull();

        List<Integer> fishIds = config.fishingLocation().getFishIds();
        boolean hasFish = ctx.inventory().filter(item -> fishIds.contains(item.getId())).count() > 0;

        // Engage if full. Disengage ONLY if no fish left.
        if (isFull) {
            isDropping = true;
        } else if (!hasFish) {
            isDropping = false;
            missChanceState = 0;
            recoveryCounter = 0;
        }

        return isDropping;
    }

    @Override
    public int execute() {
        if(!tabService.switchTo(InterfaceTab.INVENTORY)) {
            log.error("Failed to switch to inventory tab to drop fish.");
        }

        List<Integer> fishIds = config.fishingLocation().getFishIds();
        List<InventoryEntity> items = ctx.inventory()
                .orderBy(config.dropPattern())
                .filter(item -> fishIds.contains(item.getId()))
                .list();

        if (items.isEmpty()) {
            isDropping = false;
            missChanceState = 0;
            recoveryCounter = 0;
            plugin.setLastDropTimestamp(System.currentTimeMillis()); // <--- ADD THIS
            return 0;
        }

        // Determine drop count for this specific tick (burst vs single)
        int dropsThisTick = RandomService.between(4, 6);

        for (int i = 0; i < Math.min(items.size(), dropsThisTick); i++) {
            InventoryEntity item = items.get(i);

            // Check if we should simulate a miss based on current state
            if (shouldMiss()) {
                handleMiss(item);
                continue;
            }

            // Normal Execution
            if (config.useMouse()) {
                ctx.getMouse().move(item.raw());
            }

            item.drop();
            handleRecovery();

            SleepService.sleep(13, 41);
        }

        return RandomService.between(50, 105);
    }

    /**
     * Determines if we should miss based on the current probability state.
     */
    private boolean shouldMiss() {
        double chance;
        switch (missChanceState) {
            case 1:  chance = 0.03; break;
            case 2:  chance = 0.01; break;
            default: chance = 0.05; break;
        }
        return Math.random() < chance;
    }

    /**
     * Updates state when a miss occurs.
     * Increases strictness and sets the recovery counter.
     */
    private void handleMiss(InventoryEntity item) {
        log.info("Simulating misclick on item slot: " + item.raw().getSlot());

        // Simulate the time wasted by a misclick (move mouse but don't drop)
        if (config.useMouse()) {
            ctx.getMouse().move(item.raw());
        }
        SleepService.sleep(40, 100); // Slight pause simulating realization of miss

        if (missChanceState < 2) {
            missChanceState++;
        }
        recoveryCounter = RandomService.between(3, 5);
    }

    /**
     * Decrements the recovery counter.
     * If counter hits 0, relaxes the strictness back toward normal.
     */
    private void handleRecovery() {
        if (recoveryCounter > 0) {
            recoveryCounter--;

            // If we have processed the required "safe" items, relax the state
            if (recoveryCounter == 0 && missChanceState > 0) {
                missChanceState--;

                // If we relaxed to State 1 (3%), we still need to prove stability
                // before going back to State 0 (5%). Reset counter.
                if (missChanceState > 0) {
                    recoveryCounter = RandomService.between(3, 5);
                }
            }
        }
    }

    @Override
    public String status() {
        return "Dropping items";
    }

    @Override
    public int getPriority() {
        return -1;
    }
}
