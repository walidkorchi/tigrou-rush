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

The test server, or any other local server, requires FastAsyncWorldEdit to be installed along with island schematic files. The plugin fetches these files and performs quite heavy math — for not only to position islands based on the map type and compass directions, but also to calculate the ring path for [forbidden zone](https://github.com/walidkorchi/tigrou-rush/wiki/forbidden-zone) block placement restrictions. Entities and blocks on each island are positioned relative to the schematic's pivot point, which is determined by the player's position at the time the copy/save command is executed.

You will likely encounter TypeScript code as well. It relies on the `dz.jtsgen.annotations.TSModule` dependency to generate type definitions from the Java plugin source at compile time, using Java annotations to mark what gets exported. This allows unifying two seperate codebases and keeping the type definitions and game logic in sync between two languages. The AI feature is built on [mineflayer](https://github.com/PrismarineJS/mineflayer/), a high-level Minecraft bot API with a rich ecosystem of plugins for controlling bot behavior.

## 🏆 Roadmap

### ⚔️ Implentations

#### 🎨 Textures

- [ ] ReplayFollowGUI : add a generic GUI to choose a player to teleport to in a replay.
- [ ] TeamSelectionGUI : add colored tooltips for each team color.
- [ ] PlayerSettingsGUI : create a GUI for the player to customize their settings.
- [ ] GameHostPanelUI : create a GUI for the game host to manage the game room.
- [ ] Allow game host players from their GameHostPanelUI to manage game participants (players/mannequins), hosts must be able to :
  - [ ] Kick players/mannequins from their game room.
  - [ ] Create mannequins : specify their name through an anvil container with a nametag they would typically rename, give them a skill level (BAD, AVERAGE, GOOD, GOD RUSH, HACKER), and assign them a team color from the TeamSelectionGUI.

#### 🛠️ Code

- [ ] `slot_highlight_front` must be taken to consideration to disable inventory slot highlights of empty_slot slots in custom GUIs.

### 🐛 Bugs

- [ ] When a placed TNT block explodes after ignite, all surrounding TNTs ignite all at once spawning TNTPrimed which creates a canon tnt fly which is expected as per game design, but in replay worlds of recorded games, TNTPrimed are not spawned and all surrounding TNTs are thrown into the void.
- [ ] GamePlayer no longer takes regular fall damage in a running game world

### 📜 Needs testing but optimistically fixed/implemented

- [ ] TNT explosions ignited by game combattant do not seem to break surrouding breakable blocks (like: sandstone, endstone).
- [ ] When replay is paused using controls for a recorded game, the ignited TNT action must be stopped as well, what happens is that the TNT explodes even if the replay is paused.
- [ ] In a running game room, mannequins don't die from void which is 20 blocks below the islands the way normal players die and are telported back to their team bed island. > Fix attempt result : The message appears in chat where they did die by getting pushed by another player to the void, but are not teleported back to their team bed island. As per game design, they have a respawn cooldown of 6 seconds, where they are invisible to everyone else which is a state of invulnerability. Currently, the message is spammed infinitely as "%mannequinplayer% est mort"
