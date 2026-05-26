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

- [ ] TeamSelectionGUI : add colored tooltips for each team color.
- [ ] PlayerSettingsGUI : create a GUI for the player to customize their settings.
- [ ] GameHostPanelUI : create a GUI for the game host to manage the game room.
- [ ] Allow game host players from their GameHostPanelUI to manage game participants (players/mannequins), hosts must be able to :
  - [ ] Kick players/mannequins from their game room.
  - [ ] Create mannequins : specify their name through an anvil container with a nametag they would typically rename, give them a skill level (BAD, AVERAGE, GOOD, GOD RUSH, HACKER), and assign them a team color from the TeamSelectionGUI.

#### 🛠️ Code

- [ ] `slot_highlight_front` must be taken to consideration to disable inventory slot highlights of empty_slot slots in custom GUIs.

### 🐛 Bugs

- [ ] GamePlayer no longer takes regular fall damage in a running game world

### 📜 Needs testing but optimistically fixed/implemented

- [ ] Potion trade is a splash bottle water, not a healing potion
- [ ] Replay worlds do not replicate the record game world state > enderchests with resources generators do not exist in islands with team beds of replay world in contrast to the recorded game world.
- [ ] In a running game room when scenario "Coeurs supplémentaires" is enabled, islands with no teams have no beds, which is contradictory because only destroying beds grants +2 permanent hearts as per game design, a color bed must be assigned respectively. Same thing must applies to replay worlds.
- [ ] Create a helper to color the BannerPatternKeys.PIGLIN banner pattern with visible contrast color either with white or black based on the team color instead of creating a condition for each team color, so the pattern is visible against any background color.
- [ ] In a running game, enderchests and resources spanwers appear only in existing teams islands with beds. As per game design, they appear on all islands by default whether the island has a bed or not.
- [ ] When player dies from void, no killing sound effect is emitted. Usually players take damage for that purpose. It's becausing we are rescusing the player before actually dying, but we need to revive him and make sure that the client player does not render the death screen.
- [ ] In a running game room, mannequins don't die from void which is 20 blocks below the islands the way normal players die and are telported back to their team bed island. > Fix attempt result : The message appears in chat where they did die by getting pushed by another player to the void, but are not teleported back to their team bed island. As per game design, they have a respawn cooldown of 6 seconds, where they are invisible to everyone else which is a state of invulnerability. Currently, the message is spammed infinitely as "%mannequinplayer% est mort"
