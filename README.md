![Banner](.github/assets/Banner.webp)

> [!NOTE]
>
> This project is still in development and cannot be used for production use. It is publicly available as a reference implementation of FunCraft's v2 Rush games with lots of extra features which the game desperately needs and can be found under [this game design](https://github.com/walidkorchi/tigrou-rush/wiki). If you wish to play rush games with a french-community on an active server or stay up to date with the latest developments, please join us in [TLand Discord private server](https://discord.gg/VxpJy6NCNf) or consider forking the project and submit a PR if you are interested in contributing 🌱.

## ✨ Features

- 🎮 **Funcraft Legacy BedWars** : Team-based game on floating islands where the goal is to defend your team's bed while attempting to destroy the beds of all your opponents by purchasing gear purchased from merchants using resource generators.
- 🕹️ **Host Rooms** : Any player can host and customize their game rooms allowing them to choose the island map (1v1→4v4), map, overtime duration, and extra hearts configured in-game before launch
- ⚔️ **Kill/Assists Tracking** : Determines to whom a kill or assist belongs with balanced game scenarios
- ⏱️ **Overtime Gameplay** : forbidden zone lifts, dedicated music loop plays, duration configurable from 5 to 120 minutes per room
- 📼 **Game Replays** : Archived games are saved and played back with replay controls (playback at 0.25×–4× speed with ±5s seek)
- 🏅 **Ranks & Prestiges Levels** : Progressive ranking system across Bronze, Argent, and Or — each split into four gem tiers with rank-up
- 🤖 **AI Bots** : Mannequins mimicking player's game mechanics

## 💭 Philosophy

The plugin stands out mostly for two things : Schematic islands parsing for 4/8-island maps and the AI player-bots mimicry.

The test server, or any other local server, requires FastAsyncWorldEdit to be installed along with island schematic files. The plugin fetches these files and performs quite heavy math — for not only to position islands based on the map type and compass directions (clockwise), but also to calculate the ring path for [forbidden zone](https://github.com/walidkorchi/tigrou-rush/wiki/forbidden-zone) block placement restrictions. Entities and blocks on each island are positioned relative to the schematic's pivot point, which is determined by the player's position at the time the copy/save command is executed.

You will likely encounter TypeScript code as well. The gradle project uses under the hood `dz.jtsgen.annotations.TSModule` dependency to generate TS type definitions from the Java plugin source at compile time, using Java annotations to mark what gets exported. This allows unifying two seperate codebases and keeping the type definitions and game logic in sync between two languages. The AI feature is built on [mineflayer](https://github.com/PrismarineJS/mineflayer/), a high-level Minecraft bot API with a rich ecosystem of plugins for controlling bot behavior.

## 🎯 Roadmap

TigrouRush project's roadmap can be under [here](./ROADMAP.md)
