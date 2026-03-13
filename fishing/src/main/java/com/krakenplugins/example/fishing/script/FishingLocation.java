package com.krakenplugins.example.fishing.script;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

import java.util.List;

@Getter
@AllArgsConstructor
public enum FishingLocation {
    DRAYNOR_VILLAGE(new WorldPoint(3088, 3228, 0), 1525, List.of(317, 321, 7954, 323)),
    KARAMJA(new WorldPoint(2924, 3179, 0), 1522, List.of(359, 371, 377)),
    BARBARIAN_VILLAGE(new WorldPoint(3104, 3430, 0), 1526, List.of(331, 329, 333, 335, 343));

    private final WorldPoint location;
    private final int spotId;
    private final List<Integer> fishIds;
}
