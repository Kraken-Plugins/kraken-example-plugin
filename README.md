<br />
<div align="center">
  <a href="https://kraken-plugins.com">
    <img src="src/main/resources/kraken.png" alt="Logo" width="128" height="128">
  </a>

<h3 align="center">Kraken Example Plugins</h3>

  <p align="center">
   An set of example automation plugins utilizing the Kraken API
    <br />
</div>

[![Contributors][contributors-shield]][contributors-url]
[![Discord](https://img.shields.io/badge/Discord-5865F2?style=flat&logo=discord&logoColor=white)](https://discord.gg/6UGZqXj22s)
[![Forks][forks-shield]][forks-url]
[![Stargazers][stars-shield]][stars-url]
[![Issues][issues-shield]][issues-url]

# Kraken Example Plugins

This repository contains examples for writing automation plugins using the [Kraken API](https://github.com/cbartram/kraken-api.git).
The Kraken API extends the RuneLite API with the ability to interact with various game entities, including:

- Widgets (Prayers, Spells, Interfaces, etc...)
- NPC's
- Players
- Ground Items
- Game Objects
- Equipment
- World Hopping
- Container Items (Inventory, Bank, etc...)
- and more!

The Kraken API also ships with several handy features for developing automation plugins (scripts) right on top of RuneLite
like `TaskChain`'s, pathfinding, advanced mouse movement, abstractions for writing scripts, and network packet classes. 
This repository contains several examples of fully functioning automation scripts showcasing the API's capabilities.

### Plugin & Script Requirements

You can read more about the individual plugins, their features, and their requirements
in their respective README's linked below.

- [Mining Plugin](docs/MINING.md)
- [Woodcutting Plugin](docs/WOODCUTTING.md)
- [Fishing Plugin](docs/FISHING.md)
- [Jewelry Plugin](docs/JEWELRY.md)
- [Firemaking Plugin](docs/FIREMAKING.md)
- [Runecrafting Plugin](docs/RUNECRAFTING.md)

> **Note:** These plugins are dependent on the [Kraken API](https://github.com/cbartram/kraken-api) and require the Kraken Client to be installed. We strongly
> recommend using the [Kraken Client](https://kraken-plugins.com/) to make testing and using these plugins simple.

# QuickStart

Because the [Kraken API](https://github.com/cbartram/kraken-api) is required on the Runtime classpath we recommend using the [Kraken Client](https://kraken-plugins.com/docs/client/download.html) to sideload the plugins
as it already loads the [Kraken API](https://github.com/cbartram/kraken-api) automatically. This ensures you don't have to write your own plugin loader! The
following steps assume you are using the Kraken client.

Instructions for setting up the Kraken Client can [be found here](https://kraken-plugins.com/docs/client/download.html).

If you'd prefer to run the plugins individually or without the Kraken Client, then each plugin has its own test file
in `{plugin}/src/test/Run{Plugin}PluginTest.java` class which will run the plugin in RuneLite. Remember to add `-ea` to 
your JVM assertions!

## Run with External Repository

The Kraken client supports loading plugins from external repositories, including this one! To load these 
plugins, simply add this repo as a source for the plugins.

![external-repo](docs/images/add-repo.png)

Paste the link to the repositories latest manifest here in the dialogue:

`https://github.com/cbartram/kraken-example-plugin/releases/latest/download/manifest.json`

Once you add this repository, the plugins will be loaded automatically:

![repo-loaded](docs/images/repo-loaded.png)

You will find your plugins in the Kraken plugins list:

![sideloaded-plugins-list](docs/images/sideloaded.png)

## Run Sideloaded

If you would like to load all the plugins at once, you can move the built plugin jar files to `~/.runelite/kraken/sideloaded-plugins` assuming you are using the 
[Kraken client](https://kraken-plugins.com/).

The plugins will be loaded in the list of Kraken plugins.

![sideloaded-plugins](docs/images/sideloaded.png)

## Building

To set up your development environment, we recommend following [this guide on RuneLite's Wiki](https://github.com/runelite/runelite/wiki/Building-with-IntelliJ-IDEA).
You must add `-ea` to your VM args to enable assertions and add `--developer-mode` as an argument when you run the JAR. 

Once you have the example plugin cloned and set up within Intellij, build and the plugins with:

```shell
export GITHUB_ACTOR=<github-username>
export GITHUB_TOKEN=<github-personal-access-token>

./gradlew clean buildAndCollectSimpleJars --stacktrace --parallel
```

Your plugin jars will be built in the `./build/plugins` directory. You will need to ensure the Kraken API jar 
is on RuneLite's runtime classpath in order for the plugins to load correctly.

## Gradle Kraken API

Please see [these docs](https://github.com/cbartram/kraken-api?tab=readme-ov-file#gradle-example-recommended) for including the Kraken API
as part of your RuneLite plugin's build process.

## 🛠 Built With

* [Java](https://www.java.org/) — Core language
* [Gradle](https://gradle.org/) — Build tool
* [RuneLite](https://runelite.net) — Used for as the backbone for the API
* [Kraken API](https://github.com/cbartram/kraken-api) – Interaction API

---

## 🤝 Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

---

## 🔖 Versioning

We use [Semantic Versioning](http://semver.org/).
See the [tags on this repository](https://github.com/cbartram/kraken-api/tags) for available releases.

---

## 📜 License

This project is licensed under the [GNU General Public License 3.0](LICENSE.md).

---

## 🙏 Acknowledgments

* **RuneLite** — The splash screen and much of the core codebase come from RuneLite.
* **Microbot** — For clever ideas on client and plugin interaction.
* **Packet Utils** - Plugin from Ethan Vann providing access to complex packet sending functionality which was used to develop the core.packet package of the API


[contributors-shield]: https://img.shields.io/github/contributors/cbartram/kraken-example-plugin.svg?style=for-the-badge
[contributors-url]: https://github.com/cbartram/kraken-example-plugin/graphs/contributors
[forks-shield]: https://img.shields.io/github/forks/cbartram/kraken-example-plugin.svg?style=for-the-badge
[forks-url]: https://github.com/cbartram/kraken-example-plugin/network/members
[stars-shield]: https://img.shields.io/github/stars/cbartram/kraken-example-plugin.svg?style=for-the-badge
[stars-url]: https://github.com/cbartram/kraken-example-plugin/stargazers
[issues-shield]: https://img.shields.io/github/issues/cbartram/kraken-example-plugin.svg?style=for-the-badge
[issues-url]: https://github.com/cbartram/kraken-example-plugin/issues