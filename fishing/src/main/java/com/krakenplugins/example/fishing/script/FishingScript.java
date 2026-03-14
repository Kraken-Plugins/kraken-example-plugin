package com.krakenplugins.example.fishing.script;


import com.google.inject.Inject;
import com.kraken.api.core.script.PriorityTask;
import com.kraken.api.core.script.Script;
import com.kraken.api.core.script.Task;
import com.krakenplugins.example.fishing.FishingConfig;
import com.krakenplugins.example.fishing.script.state.*;
import com.krakenplugins.example.fishing.script.state.barbarian.CookFish;
import com.krakenplugins.example.fishing.script.state.barbarian.FishBarbarianVillage;
import com.krakenplugins.example.fishing.script.state.karamja.FishKaramja;
import com.krakenplugins.example.fishing.script.state.karamja.TravelBoat;
import com.krakenplugins.example.fishing.script.state.karamja.WalkToDocks;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
public class FishingScript extends Script {

    private List<PriorityTask> tasks = new ArrayList<>();

    private final FishingConfig config;
    private final DropFish dropFish;
    private final FishKaramja fishKaramja;
    private final FishBarbarianVillage fishBarbarianVillage;
    private final FishDraynor fishDraynor;
    private final CookFish cookFish;
    private final WalkToDocks walkToDocks;
    private final TravelBoat travelBoat;

    @Getter
    private String status = "Initializing";

    @Inject
    public FishingScript(final FishingConfig config, final DropFish dropFish, final FishKaramja fishKaramja, final FishBarbarianVillage fishBarbarianVillage,
                         final FishDraynor fishDraynor, final CookFish cookFish, final WalkToDocks walkToDocks, final TravelBoat travelBoat) {
        this.config = config;
        this.dropFish = dropFish;
        this.fishKaramja = fishKaramja;
        this.fishBarbarianVillage = fishBarbarianVillage;
        this.fishDraynor = fishDraynor;
        this.cookFish = cookFish;
        this.walkToDocks = walkToDocks;
        this.travelBoat = travelBoat;
    }

    public void setTasksForLocation(FishingLocation location) {
        List<PriorityTask> tasks = new ArrayList<>();
        if(config.dropFish()) {
            tasks.add(dropFish);
        }

        switch (location) {
            case DRAYNOR_VILLAGE:
                tasks.add(fishDraynor);
                break;
            case KARAMJA:
                tasks.add(fishKaramja);
                // Safe to add this same as the reason for cook fish task in barb village
                tasks.add(walkToDocks);
                tasks.add(travelBoat);
                break;
            case BARBARIAN_VILLAGE:
                // Safe to always add cook fish task regardless of user config since the activate() method checks
                // config before executing cook fish task.
                tasks.add(fishBarbarianVillage);
                tasks.add(cookFish);
                break;
            default:
                break;
        }

        this.tasks = tasks;
        this.tasks.sort(Comparator.comparingInt(PriorityTask::getPriority));
    }

    @Override
    public int loop() {
        for (Task task : tasks) {
            if (task.validate()) {
                status = task.status();
                return task.execute();
            }
        }
        return 0;
    }
}
