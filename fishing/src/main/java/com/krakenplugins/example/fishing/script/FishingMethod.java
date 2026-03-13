package com.krakenplugins.example.fishing.script;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FishingMethod {
    CAGE("Cage"),
    HARPOON("Harpoon");

    private final String interactionName;
}
