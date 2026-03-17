package com.krakenplugins.example.fishing.script;


import com.google.inject.Inject;
import com.kraken.api.core.script.PriorityTask;
import com.kraken.api.core.script.Script;
import com.kraken.api.core.script.Task;
import com.krakenplugins.example.fishing.FishingConfig;
import com.krakenplugins.example.fishing.script.state.*;
import com.krakenplugins.example.fishing.script.state.barbarian.CookFish;
import com.krakenplugins.example.fishing.script.state.barbarian.FishBarbarianVillage;
import com.krakenplugins.example.fishing.script.state.corsair.BankCorsairCove;
import com.krakenplugins.example.fishing.script.state.corsair.FishCorsair;
import com.krakenplugins.example.fishing.script.state.corsair.WalkToCorsairBank;
import com.krakenplugins.example.fishing.script.state.corsair.WalkToResourceArea;
import com.krakenplugins.example.fishing.script.state.karamja.*;
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
    private final TravelPortSarim travelPortSarim;
    private final BankDepositBox bankDepositBox;
    private final TravelKaramja travelKaramja;
    private final WalkToMusaPoint walkToMusaPoint;
    private final BankCorsairCove bankCorsairCove;
    private final FishCorsair fishCorsair;
    private final WalkToCorsairBank walkToCorsairBank;
    private final WalkToResourceArea walkToResourceArea;


    @Getter
    private String status = "Initializing";

    @Inject
    public FishingScript(final FishingConfig config, final DropFish dropFish, final FishKaramja fishKaramja, final FishBarbarianVillage fishBarbarianVillage,
                         final FishDraynor fishDraynor, final CookFish cookFish, final WalkToDocks walkToDocks, final TravelPortSarim travelPortSarim,
                         final BankDepositBox bankDepositBox, final TravelKaramja travelKaramja, final WalkToMusaPoint walkToMusaPoint,
                         final BankCorsairCove bankCorsairCove, final FishCorsair fishCorsair, final WalkToCorsairBank walkToCorsairBank, final WalkToResourceArea walkToResourceArea) {
        this.config = config;
        this.dropFish = dropFish;
        this.fishKaramja = fishKaramja;
        this.fishBarbarianVillage = fishBarbarianVillage;
        this.fishDraynor = fishDraynor;
        this.cookFish = cookFish;
        this.walkToDocks = walkToDocks;
        this.travelPortSarim = travelPortSarim;
        this.bankDepositBox = bankDepositBox;
        this.travelKaramja = travelKaramja;
        this.walkToMusaPoint = walkToMusaPoint;
        this.bankCorsairCove = bankCorsairCove;
        this.fishCorsair = fishCorsair;
        this.walkToCorsairBank = walkToCorsairBank;
        this.walkToResourceArea = walkToResourceArea;
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
                // Safe to add this same as the reason for cook fish task in barb village. We don't do the check for if
                // the user has selected config for banking fish here because any time the config is updated we would need to
                // receive that event and re-compute the tasks to add based on the new config. It's just simpler to keep the config
                // in the validate() method since its called so often it will instantly pickup config changes.
                tasks.add(walkToDocks);
                tasks.add(travelPortSarim);
                tasks.add(bankDepositBox);
                tasks.add(travelKaramja);
                tasks.add(walkToMusaPoint);
                break;
            case CORSAIR_COVE:
                tasks.add(fishCorsair);
                tasks.add(walkToCorsairBank);
                tasks.add(walkToResourceArea);
                tasks.add(bankCorsairCove);
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
