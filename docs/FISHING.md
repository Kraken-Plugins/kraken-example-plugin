# Fishing Plugin

The Fishing Plugin is an example automation script built using the Kraken API. 
It demonstrates how to create a script that can be used across different areas and uses the `Widget` and `PriorityTask` API's 
to structure itself.

## Features
- Automatic fishing at several locations including
  - Karamja Musa Point
  - Draynor Village Shrimps
  - Barbarian Village Salmon and Trout
- It can automatically cook food at the Barbarian village location
- Multiple options for "smart" dropping of caught fish

## Requirements & Usage
To use this plugin effectively, please ensure the following:

1. **Rod/Feathers/Small Net/Cage/Harpoon**: You must have the appropriate fishing gear in your inventory for your location
2. **Starting Location**: Your character must be located at or near one of the starting locations.
3. **Inventory**: Start with an **empty inventory** (except for your fishing equipment if it is not equipped).

## Limitations

- **Location** - The script only works in the 3 aforementioned locations.
- **Cooking** - Cooking is only supported at the barbarian village location. It uses the permanent fire next to the fishing spots, and needs Cooking level 15 for trout and 25 for salmon. Fish above your Cooking level are not cooked. Enable **Drop Fish** too, so the cooked (and any uncookable) fish are dropped and the script goes back to fishing.

## Running the Plugin
To run the mining plugin, use the following commands in your terminal:

```shell
./gradlew build

java -jar ./build/libs/kraken-example-plugin-1.0.0.jar --developer-mode com.krakenplugins.example.fishing.FishingPlugin
```

## Configuration
The plugin offers several configuration options to customize its behavior.

### Mouse Settings
* **Movement Strategy**: Determines how the mouse moves. Options include `BEZIER` (default), `LINEAR`, etc.
* **Replay Library**: If `REPLAY` strategy is chosen, this defines which library to load.
* **Linear Steps**: If `LINEAR` strategy is chosen, this sets the number of steps (points) along the path (5-3000).

### Overlays
* **Highlight Fishing Spot**: Visualizes which spot the script is currently targeting.
* **Render Path**: Draws the path the player is currently walking on the screen.
