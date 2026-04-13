package com.krakenplugins.example.firemaking.script.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.core.script.AbstractTask;
import com.kraken.api.query.container.inventory.InventoryEntity;
import com.kraken.api.query.gameobject.GameObjectEntity;
import com.kraken.api.service.bank.BankService;
import com.kraken.api.service.dialogue.DialogueService;
import com.kraken.api.service.movement.MovementService;
import com.kraken.api.service.ui.processing.ProcessingService;
import com.kraken.api.service.util.RandomService;
import com.kraken.api.service.util.SleepService;
import com.krakenplugins.example.firemaking.FiremakingConfig;
import com.krakenplugins.example.firemaking.FiremakingPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Singleton
public class BurnLogsTask extends AbstractTask {

    @Inject
    private FiremakingConfig config;

    @Inject
    private BankService bankService;

    @Inject
    private FiremakingPlugin plugin;

    @Inject
    private ProcessingService processingService;

    @Inject
    private DialogueService dialogueService;

    private static final int BURNING_ANIM = 10572;
    private static final int TICK_THRESHOLD = 16;

    @Override
    public boolean validate() {
        return ctx.inventory().hasItem(config.logName())
                && ctx.inventory().hasItem(590) // 590 is Tinderbox ID
                && !bankService.isOpen()
                && (ctx.players().local().isIdle() || ctx.players().local().raw().getAnimation() == BURNING_ANIM);
    }

    // Priority for this method goes:
    // If forest fire exists, use that
    // If normal fire exists, turn it into a foresters fire and use that
    // Light a new fire and turn it into a foresters fire.
    @Override
    public int execute() {
        InventoryEntity tinderbox = ctx.inventory().withName("Tinderbox").first();
        InventoryEntity randomLog = ctx.inventory().withName(config.logName()).random();

        // Get the nearest fire rather than a random one to prevent long runs
        GameObjectEntity fire = ctx.gameObjects().withId(26185).nearest();
        GameObjectEntity foresterFire = ctx.gameObjects().withId(49927).nearest();

        if (ctx.players().local().raw().getAnimation() == BURNING_ANIM) {
            if(dialogueService.isDialoguePresent()) {
                dialogueService.continueDialogue();
            }

            if(ctx.getClient().getTickCount() - plugin.getLastFiremakingXpDropTick() > TICK_THRESHOLD && plugin.getLastFiremakingXpDropTick() != -1) {
                log.info("Threshold since last burn reached, restarting log burn bonfire");
                if(foresterFire != null && randomLog != null) {
                    startBonfire(foresterFire, randomLog);
                } else if(fire != null && randomLog != null) {
                    startBonfire(fire, randomLog);
                }
            }

            return 600;
        }


        // Always prioritize forester fire over starting a new forester fire
        if(foresterFire != null) {
            startBonfire(foresterFire, randomLog);
            SleepService.sleepUntil(() -> ctx.players().local().raw().getAnimation() == BURNING_ANIM, RandomService.between(2000, 4000));
            return 600;
        }

        // If existing fire is present (and close), add logs to it, turning it into a foresters fire
        if (fire != null && randomLog != null && fire.raw().getWorldLocation().distanceTo(ctx.players().local().location()) < 5) {
            startBonfire(fire, randomLog);
            return 600;
        }

        // 3. Select a point not in the bank area but within 4 tiles of a random bank tile
        Set<WorldPoint> bankTiles = plugin.getBankLocation().getTiles();
        WorldPoint myLoc = ctx.players().local().location();

        // If we are currently standing IN the bank, we must move out
        if (bankTiles.contains(myLoc)) {
            if (ctx.players().local().isMoving()) {
                return 600;
            }

            WorldPoint targetSpot = findSafeSpot(bankTiles);
            if (targetSpot != null) {
                ctx.getService(MovementService.class).moveTo(targetSpot);
                SleepService.sleepUntil(() -> ctx.players().local().isMoving(), 1200);
                return 1200;
            } else {
                log.warn("Could not find a valid firemaking spot nearby.");
                return 1000;
            }
        }

        // We are now outside the bank, and no valid fire exists nearby.
        if (tinderbox != null && randomLog != null) {
            tinderbox.useOn(randomLog.raw());

            // Wait for the animation to start so we don't spam click
            SleepService.sleepUntil(() -> ctx.players().local().raw().getAnimation() != -1, RandomService.between(1200, 1800));
        }

        return 600;
    }

    private void startBonfire(GameObjectEntity fire, InventoryEntity log) {
        plugin.setTargetFire(fire.raw());
        if (config.useMouse()) {
            ctx.getMouse().move(log.raw());
       }

        log.useOn(fire.raw());

        SleepService.sleepUntil(() -> processingService.isOpen(), RandomService.between(4000, 6000));
        processingService.process("Burn", config.logName());
        plugin.setLastFiremakingXpDropTick(ctx.getClient().getTickCount() + 5); // Buffer so this doesn't continually execute
    }

    /**
     * Helper to find a tile that is NOT in the bank, but close to it.
     */
    private WorldPoint findSafeSpot(Set<WorldPoint> bankTiles) {
        if (bankTiles.isEmpty()) return null;

        // Convert set to list to pick a random anchor point
        List<WorldPoint> tilesList = new ArrayList<>(bankTiles);

        // Try up to 10 times to find a valid spot to avoid infinite loops
        for (int i = 0; i < 10; i++) {
            WorldPoint randomBankTile = tilesList.get(RandomService.between(0, tilesList.size()));

            int dx = RandomService.between(-4, 4);
            int dy = RandomService.between(-4, 4);

            WorldPoint candidate = randomBankTile.dx(dx).dy(dy);

            if (!bankTiles.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public String status() {
        return "Burning Logs";
    }
}