![Banner](.github/assets/Banner.webp)

> [!NOTE]
>
> This project is still in development and cannot be used for production use. It is publicly available as a reference implementation of FunCraft's v2 Rush games with lots of extra features which the game desperately needs and can be found under [this game design](https://github.com/walidkorchi/tigrou-rush/wiki). If you wish to try it our Rush games and play with a french-community on an active server or stay up to date with the latest developments, please join us in [TLand Discord private server](https://discord.gg/VxpJy6NCNf).

## ✨ Features

### ⚔️ Core Gameplay

- **Bed destruction** permanently eliminates a team; when *Extra Hearts* is on, each bed destroyed also grants the destroying team **+2 permanent hearts**
- **Forbidden zone** — a geometric corridor between opposing spawns that blocks all block placement and lifts automatically the moment overtime begins
- **Lodestone compass** updates every second to the nearest living enemy; always in slot 0 of every player's hotbar
- **Restricted building** — only sandstone, end stone, and TNT are placeable, and only within the island ring paths
- **Island merchants** — one speed baby-villager (shop hub) and four typed villagers per island (Weaponsmith, Armorsmith, Alchemist, Builder) placed at fixed cardinal-direction offsets
- **XP coefficient** scales every reward (kill, assist, bed, win) by a factor derived from team count × team size: **1.00× (2 teams, 1v1) up to 1.45× (4 teams, 4v4)**

### 🕹️ Host Room Configuration

Rooms are created and configured entirely in-game through a GUI before launch:

| Option | Values |
|---|---|
| **Format** | 1v1 · 2v2 · 3v3 · 4v4, across 2–4 teams |
| **Island type** | 4 islands (8 planned) |
| **Map** | cycle through loaded schematics |
| **Overtime duration** | 5 – 120 minutes, in 5-minute steps |
| **Overtime start** | begin the game already in overtime phase |
| **Extra Hearts** | toggle; adds neutral beds on unused islands |

A confirmation screen summarises the full configuration before the room is created.

### ⏱️ Overtime

- Forbidden zone deactivates instantly; all placement restrictions lift
- Dedicated two-part music: intro cue → 40-second loop until game end
- Per-room duration (default 30 min), set by the host at room creation

### 📼 Replay System

Every game is recorded automatically. Replays capture:

- Positions and rotations at **100 ms intervals** (every 2 ticks)
- Block placements and breaks with full before/after block state
- Kills, bed destructions, respawns, and overtime entry

Playback controls available to viewers:

- **Speed**: 0.25×, 0.5×, 1×, 2×, 3×, 4×
- **Seek**: ±5 seconds per click
- **Follow mode**: compass teleports the viewer to any recorded player
- Multiple concurrent viewers per replay session, each in their own isolated world

### 🏅 36-Rank Prestige System

- First rank at **10 000 XP**; each successive rank costs **1.25× more**
- Three prestiges — **Bronze** (1–12), **Argent** (13–24), **Or** (25–36) — each split into four gem tiers: Émeraude, Améthyste, Diamant, Rubis
- Rank-up triggers a title + toast; prestige advancement broadcasts to all online players
- Per-game stats persisted: kills, deaths, assists, beds destroyed, win streak, weighted score

### 🤖 AI Mannequins

- Hosts can spawn named mannequin bots and assign them to any team, filling out uneven lobbies
- Mannequins respawn with the same **6-second invulnerability window** as real players (invisible during cooldown)
- Count toward team size for ender-chest generator scaling (2–4 generators per island, depending on occupancy)


## 🏆 Roadmap

### ⚔️ Implentations

#### 🎨 Textures

- [ ] GUI/GameHostPanelUI : create a GUI for the game host to manage the game room.
- [ ] Allow game host players from their GameHostPanelUI to manage game participants (players/mannequins), hosts must be able to :
  - [ ] Kick players/mannequins from their game room.
  - [ ] Create mannequins : specify their name through an anvil container with a nametag they would typically rename, give them a skill level (BAD, AVERAGE, GOOD, GOD RUSH, HACKER), and assign them a team color from the TeamSelectionGUI.

#### 🛠️ Code

- [ ] `slot_highlight_front` must be taken to consideration to disable inventory slot highlights of empty_slot slots in custom GUIs.

### 🐛 Bugs

<!--none 🎉-->

- [ ] Potion trade is a splash bottle water, not a healing potion
- [ ] GamePlayer no longer take regular fall damage in a running game world
- [ ] flightmode is not removed within the restore lobby state

### 📜 Needs testing but optimistically fixed/implemented

- [ ] Replay worlds do not replicate the record game world state > enderchests with resources generators do not exist in islands with team beds of replay world in contrast to the recorded game world.
- [ ] In a running game room when scenario "Coeurs supplémentaires" is enabled, islands with no teams have no beds, which is contradictory because only destroying beds grants +2 permanent hearts as per game design, a color bed must be assigned respectively. Same thing must applies to replay worlds.
- [ ] Create a helper to color the BannerPatternKeys.PIGLIN banner pattern with visible contrast color either with white or black based on the team color instead of creating a condition for each team color, so the pattern is visible against any background color.
- [ ] In a running game, enderchests and resources spanwers appear only in existing teams islands with beds. As per game design, they appear on all islands by default wether the island has a bed or not.
- [ ] When player dies from void, no killing sound effect is emitted. Usually players take damage for that purpose. It's becausing we are rescusing the player before actually dying, but we need to revive him and make sure that the client player does not render the death screen.
- [ ] In a running game room, mannequins don't die from void which is 20 blocks below the islands the way normal players die and are telported back to their team bed island. > Fix attempt result : The message appears in chat where they did die by getting pushed by another player to the void, but are not teleported back to their team bed island. As per game design, they have a respawn cooldown of 6 seconds, where they are invisible to everyone else which is a state of invulnerability. Currently, the message is spammed infinitely as "%mannequinplayer% est mort"
