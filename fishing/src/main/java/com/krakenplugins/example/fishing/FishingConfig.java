package com.krakenplugins.example.fishing;


import com.kraken.api.input.mouse.strategy.MouseMovementStrategy;
import com.kraken.api.query.container.inventory.InventoryOrder;
import com.krakenplugins.example.fishing.script.FishingLocation;
import com.krakenplugins.example.fishing.script.FishingMethod;
import net.runelite.client.config.*;

@ConfigGroup("autofisher")
public interface FishingConfig extends Config {

	@ConfigSection(
			name = "Mouse",
			description = "Options for configuring mouse movements",
			position = 1
	)
	String mouse = "mouse";

	@ConfigItem(
			keyName = "useMouse",
			name = "Use Mouse Movement",
			description = "When true the mouse will be moved on the canvas.",
			position = 1,
			section = mouse
	)
	default boolean useMouse() {
		return false;
	}

	@ConfigItem(
			keyName = "mouseMovementStrategy",
			name = "Movement Strategy",
			description = "Determines which strategy is used to move the client's mouse.",
			position = 2,
			section = mouse
	)
	default MouseMovementStrategy mouseMovementStrategy() {
		return MouseMovementStrategy.BEZIER;
	}

	@ConfigItem(
			keyName = "replayLibrary",
			name = "Replay Library",
			description = "Determines which library to load when the mouse strategy is set to: REPLAY.",
			position = 3,
			section = mouse
	)
	default String replayLibrary() {
		return "";
	}

	@Range(min = 5, max = 3000)
	@ConfigItem(
			keyName = "linearSteps",
			name = "Linear Steps",
			description = "Determines how many steps (points along the linear path) should be generated when the mouse strategy is set to: LINEAR.",
			position = 4,
			section = mouse
	)
	default int linearSteps() {
		return 150;
	}

	@ConfigSection(
			name = "Fishing Settings",
			description = "General options for configuring fishing agnostic of location.",
			position = 2
	)
	String fishing = "fishing";

	@ConfigItem(
			keyName = "fishingLocation",
			name = "Location",
			description = "Determines where the script will catch fish and what fish to expect.",
			position = 1,
			section = fishing
	)
	default FishingLocation fishingLocation() {
		return FishingLocation.DRAYNOR_VILLAGE;
	}

	@ConfigItem(
			keyName = "dropFish",
			name = "Drop Fish",
			description = "Automatically drops the fish using the provided pattern.",
			position = 2,
			section = fishing
	)
	default boolean dropFish() {
		return false;
	}

	@ConfigItem(
			keyName = "dropPattern",
			name = "Drop Pattern",
			description = "Determines which pattern to use to drop the fish.",
			position = 3,
			section = fishing
	)
	default InventoryOrder dropPattern() {
		return InventoryOrder.TOP_DOWN_LEFT_RIGHT;
	}

	@ConfigSection(
			name = "Barbarian Fishing Settings",
			description = "General options for configuring fishing when in the barbarian village.",
			position = 3
	)
	String barbarianFishing = "barbarianFishing";

	@ConfigItem(
			keyName = "barbVillageCook",
			name = "Cook Fish",
			description = "Automatically uses the fire to cook fish in the Barbarian Village.",
			position = 1,
			section = barbarianFishing
	)
	default boolean barbVillageCook() {
		return false;
	}


	@ConfigSection(
			name = "Karamja Fishing Settings",
			description = "General options for configuring fishing when at Musa point (Karamja).",
			position = 4
	)
	String karamjaFishing = "karamjaFishing";

	@ConfigItem(
			keyName = "fishingMethod",
			name = "Fishing Method",
			description = "Choose to fish lobsters via caging or Tuna/Swordfish via Harpooning.",
			position = 1,
			section = karamjaFishing
	)
	default FishingMethod fishingMethod() {
		return FishingMethod.CAGE;
	}

	@ConfigItem(
			keyName = "bankFishKaramja",
			name = "Bank Fish",
			description = "Automatically uses the Karamja charter ship to bank fish in Port Sarim. This will be overriden if Drop fish is checked.",
			position = 2,
			section = karamjaFishing
	)
	default boolean bankFishKaramja() {
		return false;
	}

	@ConfigSection(
		name = "Overlays",
		description = "Script overlay options",
		position = 999
	)
	String overlay = "overlay";

	@ConfigItem(
		keyName = "highlightTargetSpot",
		name = "Highlight Target Spot",
		description = "Highlights the selected fishing spot to lure.",
		position = 1,
		section = overlay
	)
	default boolean highlightTargetSpot() {
		return false;
	}

	@ConfigItem(
			keyName = "highlightBankDepositBox",
			name = "Highlight Deposit Box",
			description = "Highlights the selected bank deposit box.",
			position = 2,
			section = overlay
	)
	default boolean highlightDepositBox() {
		return false;
	}

	@ConfigItem(
			keyName = "highlightNpcs",
			name = "Highlight Npcs",
			description = "Highlights any NPC being chosen to interact with (i.e. Customs officer & Seamen for Karamja).",
			position = 2,
			section = overlay
	)
	default boolean highlightNpcs() {
		return false;
	}

	@ConfigItem(
			keyName = "highlightCurrentPath",
			name = "Highlight Path",
			description = "Highlights the current path the player will traverse.",
			position = 3,
			section = overlay
	)
	default boolean highlightCurrentPath() {
		return false;
	}

	@ConfigItem(
			keyName = "showLog",
			name = "Show Log",
			description = "Show log information from the script in the overlay.",
			position = 4,
			section = overlay
	)
	default boolean showLog() {
		return false;
	}

	@ConfigItem(
			keyName = "debug",
			name = "Debug",
			description = "Show debug information and overlays.",
			position = 5,
			section = overlay
	)
	default boolean debug() {
		return false;
	}

}
