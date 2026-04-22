package com.krakenplugins.example.mining.script.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kraken.api.core.script.AbstractTask;
import com.kraken.api.query.gameobject.GameObjectEntity;
import com.kraken.api.service.pathfinding.GlobalPathfinder;
import com.kraken.api.service.util.SleepService;
import com.krakenplugins.example.mining.MiningPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;

import static com.krakenplugins.example.mining.MiningPlugin.*;

@Slf4j
@Singleton
public class MiningTask extends AbstractTask {

    @Inject
    private MiningPlugin plugin;

    @Inject
    private GlobalPathfinder pathfinder;

    @Override
    public boolean validate() {
        boolean playerInMiningArea = ctx.players().local().isInArea(plugin.getMiningArea());
        boolean inventoryHasSpace = !ctx.inventory().isFull();
        return playerInMiningArea && inventoryHasSpace;
    }

    @Override
    public int execute() {
        // If target rock is not null and target rock is now depleted pick a new rock
        if(plugin.getTargetRock() != null) {
            GameObjectEntity targetRock = ctx.gameObjects().filter(g -> g.raw().getWorldLocation().getX() == plugin.getTargetRock().getWorldLocation().getX() &&
                    g.raw().getWorldLocation().getY() == plugin.getTargetRock().getWorldLocation().getY()).first();

            if(targetRock == null) {
                log.error("Target rock (match) is null");
                return 0;
            }

            pathfinder.clearLastResult();
            if(IRON_ORE_DEPLETED_GAME_OBJECTS.contains(targetRock.getId())) {
                GameObjectEntity ironRock = findRock();
                if(ironRock == null) {
                    return 650;
                }
                plugin.setTargetRock(ironRock.raw());
                ctx.getMouse().move(ironRock.raw());
                ironRock.interact("Mine");
                SleepService.sleepUntilIdle();
                return 250;
            }

            // If player is mining and target rock is not depleted
            if (isPlayerMining(ctx.players().local().raw()) || ctx.players().local().isMoving()) {
                SleepService.sleepUntilIdle();
            }
        }

        GameObjectEntity ironRock = findRock();
        if(ironRock == null) {
            return 650;
        }

        plugin.setTargetRock(ironRock.raw());
        ironRock.interact("Mine");

        // Wait for the player to start mining (or moving to the rock).
        // If the player is still idle after a timeout, something went wrong, and we should try again.
        SleepService.sleepUntilIdle();
        return 250;
    }

    private GameObjectEntity findRock() {
        GameObjectEntity ironRock = ctx.gameObjects()
                .within(8)
                .filter(g -> IRON_ORE_GAME_OBJECTS.contains(g.getId()) && !IRON_ORE_DEPLETED_GAME_OBJECTS.contains(g.getId()))
                .random();

        if (ironRock == null || ironRock.isNull()) {
            log.info("No iron rock could be located, sleeping...");
            return null;
        }
        return ironRock;
    }

    private boolean isPlayerMining(Player player) {
        return player.getAnimation() == net.runelite.api.AnimationID.MINING_BRONZE_PICKAXE ||
                player.getAnimation() == net.runelite.api.AnimationID.MINING_IRON_PICKAXE ||
                player.getAnimation() == net.runelite.api.AnimationID.MINING_STEEL_PICKAXE ||
                player.getAnimation() == net.runelite.api.AnimationID.MINING_BLACK_PICKAXE ||
                player.getAnimation() == net.runelite.api.AnimationID.MINING_MITHRIL_PICKAXE ||
                player.getAnimation() == net.runelite.api.AnimationID.MINING_ADAMANT_PICKAXE ||
                player.getAnimation() == net.runelite.api.AnimationID.MINING_RUNE_PICKAXE ||
                player.getAnimation() == net.runelite.api.AnimationID.MINING_DRAGON_PICKAXE ||
                player.getAnimation() == net.runelite.api.AnimationID.MINING_CRYSTAL_PICKAXE;
    }

    @Override
    public String status() {
        return "Mining Ore";
    }
}
