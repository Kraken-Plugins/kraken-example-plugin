package com.krakenplugins.example.jewelry.script;

import com.google.inject.Inject;
import com.kraken.api.core.script.Script;
import com.kraken.api.core.script.Task;
import com.krakenplugins.example.jewelry.script.state.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class JewelryScript extends Script {

    public static final int GOLD_BAR = 2357;
    public static final int FURNACE_GAME_OBJECT = 16469;
    public static final int BANK_BOOTH_ID = 10355;

    private final List<Task> tasks;

    @Getter
    private String status = "Initializing";

    @Inject
    public JewelryScript(BankTask bankTask, CraftTask craftTask, OpenBankTask openBankTask, OpenFurnaceTask openFurnaceTask,
                         PurchaseSuppliesTask purchaseSuppliesTask, WalkToEdgeville walkToEdgeville, WalkToGrandExchange walkToGrandExchange) {
        this.tasks = List.of(
                // Walking tasks should come before other tasks because they have latches (validate() is forced to return true).
                // meaning once we start walking, there's no guarantee we make it to our destination, which means
                // we latch the execute down to continue executing the movement until we arrive. We don't want
                // any other tasks returning true while we are latched, so evaluating the walking tasks first is key.
                walkToEdgeville,
                walkToGrandExchange,
                openBankTask,
                openFurnaceTask,
                bankTask,
                craftTask,
                purchaseSuppliesTask
        );
    }

    @Override
    public int loop() {
        for (Task task : tasks) {
            String taskName = task.getClass().getSimpleName();
            try {
                boolean isValid = task.validate();
                if (isValid) {
                    status = task.status();
                    return task.execute();
                }
            } catch (Throwable e) {
                log.error("Exception thrown while validating/executing task: {}", taskName, e);
            }
        }
        return 0;
    }
}
