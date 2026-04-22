package com.krakenplugins.example.jewelry.script;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Jewelry {
    SAPPHIRE_NECKLACE(29229080, "sapphire necklace", "<col=ff9040>Sapphire necklace</col>", 1607, 1656),
    RUBY_NECKLACE(29229082, "ruby necklace", "<col=ff9040>Ruby necklace</col>", 1603, 1660);

    private final int widgetId;
    private final String necklaceName;
    private final String formattedName;
    private final int secondaryGemId;
    private final int craftedItemId; // The id of the final product
}
