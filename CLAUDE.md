# TigrouRush — Game Design Document

**Platform:** Minecraft Bukkit plugin (Paper 1.21.11), Java 21  
**Type:** Bed Wars / Rush hybrid — 4-team arena, each team on its own island. Destroy enemy beds to prevent their respawns. Last team standing wins.

---

## Table of Contents

1. [World & Map](#world--map)
2. [Game States](#game-states)
3. [Teams](#teams)
4. [Host & Room Config](#host--room-config)
5. [Resource Economy](#resource-economy)
6. [Merchants](#merchants)
7. [Combat](#combat)
8. [Death & Respawn](#death--respawn)
9. [Forbidden Zone](#forbidden-zone)
10. [Restrictions](#restrictions)
11. [Progression](#progression)
12. [HUD & UI](#hud--ui)
13. [Balance Numbers](#balance-numbers)
14. [Build System & Dependencies](#build-system--dependencies)

---

## World & Map

### GameRoom

A self-contained game instance: one void world, one `Game` object, one host. Identified by a short UUID (8 chars). Destroyed (world unloaded and deleted) after the game ends. Each `GameRoom` is identified in the world filesystem as `rush_game_{n}_{host}` with auto-save disabled.

### WaitingRoom

A schematic (`waiting_room.schem`) pasted at `(0, 0, 0)` of each GameRoom's void world during the CREATING phase. Serves as the pre-game lobby where players congregate and select teams. The WorldEdit copy origin is the player spawn point — players are teleported to `(0, 0, 0)` of the game world on join or when room creation completes.

### 4-Island Schema

- **4 islands** loaded from a map-type schematic (WorldEdit), each rotated and placed at ±40 blocks from center (0, 0)
  - Island 0: (−offset, 0) rotation −90°
  - Island 1: (+offset, 0) rotation +90°
  - Island 2: (0, −offset) rotation 180°
  - Island 3: (0, +offset) rotation 0°
- The only buildable zone is a **ring path** connecting all 4 islands; the center and outer corners are always blocked
- Supports 2–4 teams

### 8-Island Schema

- **8 islands** at all 8 compass points (N, NE, E, SE, S, SW, W, NW) at ±40 blocks from center
- The only buildable zones are **4 corner quarter-circles** (one per diagonal), connecting adjacent diagonal island pairs; the central square and edge bands are always blocked
- Supports 2–8 teams
- Status: not yet fully implemented (`EIGHT_ISLANDS` enum exists, creation is blocked with a message)

### Common

- Islands are pasted at `worldMaxHeight − 12` (Y ≈ 308 in 1.21)
- **Void world generator** — no terrain between islands
- **Multi-room support:** each `GameRoom` gets a dedicated void world

### MapType

An enum of 8 visual themes for the island schematic: `NORMAL`, `OLD_SCHOOL`, `NETHER`, `END`, `AQUAMARINE`, `SUMMER`, `CHERRY`, `WINTER`. Purely cosmetic — only the island schematic filename changes (`rush_island-{type}.schem`). World environment, gameplay rules, and placeable blocks are identical across all map types. Chosen by the host at room creation.

---

## Game States

`CREATING → WAITING → RUNNING → STOPPED`

| State | Description |
|---|---|
| CREATING | World and schematics are being set up asynchronously; room is not joinable; host remains in main lobby until this phase completes |
| WAITING | Lobby phase; team selection open; players may join if the room is not full |
| RUNNING | Active game; combat enabled, resources spawning |
| STOPPED | Game ended; stats saved, world queued for cleanup |

**CREATING cancellation:** if the host disconnects before CREATING completes, the callback chain halts, the world is unloaded and deleted, and the `GameRoom` entry is never promoted to WAITING.

- Lobby countdown: **60 seconds**
- Auto-start check: every **5 seconds** (100 ticks)
- **Overtime** triggered at **30 minutes** (1800 seconds): all surviving beds are destroyed and all block placement restrictions lifted. The host option `overtimeStart` fires this trigger at tick 0 — the entire game is played in deathmatch mode with no respawns from the very start.

---

## Teams

- **2–4 players** per team, up to **4 teams** on 4-island schema (Red, Blue, Green, Yellow), up to **8 teams** on 8-island schema
- Each team has:
  - Spawn point on its island
  - Bed location (placed on game start)
  - 2–4 ender chests (scales with team size)
- **Starting equipment** (given on spawn and each respawn):
  - Leather helmet, chestplate, boots — colored by team, Protection I
  - Wooden pickaxe — Efficiency I
- Assignment order when auto-assigning: islands 0, 2, 1, 3

### TeamSize

Fixed enum: `VS2` (2 players/team), `VS3` (3 players/team), `VS4` (4 players/team). Chosen by the host at room creation. Cannot be changed after the room is created.

### IslandType

`FOUR_ISLANDS` (4 slots, max 4 teams) or `EIGHT_ISLANDS` (8 slots, max 8 teams). Determines the cap on `maxTeams`.

### MaxTeams

Number of team objects created for the room. Chosen by the host (min 2, capped by `IslandType`). Exactly this many teams exist — unused island slots receive no schematic paste and no merchants.

---

## Host & Room Config

### GameRoomConfig

Immutable record holding all host-configurable room settings, passed to `GameRoom` at construction:

| Field | Type | Description |
|---|---|---|
| `islandType` | `IslandType` | 4-island or 8-island layout |
| `maxTeams` | int | Number of teams (2–islandType.count) |
| `teamSize` | `TeamSize` | Players per team |
| `mapType` | `MapType` | Island visual theme |
| `extraHearts` | boolean | Whether bed destructions grant permanent health bonuses |
| `overtimeStart` | boolean | Whether the game starts in overtime (deathmatch) mode |

### Host

The player who created the `GameRoom`, identified by `hostUUID` (UUID) and `hostName` (String) on `GameRoom`. Teleported to the WaitingRoom when CREATING completes.

**Host powers during WAITING phase:**
- **Force-start** — skip the lobby countdown
- **Kick** — remove any player from the room

**Host transfer:** if the host disconnects during WAITING, host status transfers permanently to the longest-present player (join order tracked on `GameRoom`). The original host cannot reclaim status on reconnect — they rejoin as a regular player.

### Host Panel

A CraftEngine item (`tland:host_panel`) given exclusively to the host in inventory slot 8 upon entering the WaitingRoom. Right-clicking opens a `BasicGui` with a force-start button and a scrollable player list with per-player kick buttons. When host status transfers, the panel item is removed from the outgoing host and given to the incoming host.

---

## Resource Economy

Resources spawn passively at each team's ender chests during RUNNING state. Items are dropped at chest position with zero velocity and instant pickup.

| Resource | Spawn Interval |
|---|---|
| Copper Ingot | 1,000 ms (0.5 s) |
| Iron Ingot | 10,000 ms (10 s) |
| Gold Ingot | 20,000 ms (20 s) |
| Diamond | 300,000 ms (5 min) |

---

## Merchants

5 types of merchants on each island. All are invulnerable, silent, non-collidable villagers. An item frame 2 blocks in front shows their wares.

| Type | Profession | Sells | Currency |
|---|---|---|---|
| **Weaponsmith** | WEAPONSMITH | Iron Sword (Sharp I/II/III), Diamond Sword (Sharp II), TNT, Flint & Steel | Iron / Gold / Diamond |
| **Builder** | CARTOGRAPHER | Sandstone, End Stone, Iron Pickaxe (Efficiency I/II/III) | Copper / Iron / Gold |
| **Alchemist** | CLERIC | Golden Apple, Splash Healing Potion II | Iron (1) / Gold (1) |
| **Armorsmith** | ARMORER | Leather Chestplate (Protection I/II/III), Compass (nearest-enemy tracker) | Iron (2–5) / Gold (1–5) / Diamond (16) |
| **Speed** | LIBRARIAN (baby) | *(hub — opens full shop menu, no trades of its own)* | — |

### Merchant Interactions

- **Right-clicking a Speed merchant** opens the full 4-category shop hub (`ShopGUI.openMainMenu`) — Weapons, Armor, Potions, Blocks. The 4 categories are labelled by item type in the UI, though they conceptually map to the 4 real merchant types.
- **Right-clicking a regular merchant** opens that merchant's own trade list directly (`ShopGUI.openCategory`), skipping the hub.

### Positioning per island

- 2 Speed merchants at `speedOffset` (default 13) from island center — **1 block from ender chests** (`enderChestOffset = speedOffset − 1`)
- 4 regular merchants at `regularOffset` (default 12), spread ±1 block perpendicular

### Compass (Nearest-Enemy Tracker)

Purchased from the Armorsmith for **16 Diamonds**. When held, updates every **1 second (20 ticks)** to point toward the nearest non-teammate player in the same `GameRoom`, using Euclidean distance. Implemented via `CompassMeta` lodestone targeting.

> Distinct from the **Lobby Compass** (slot 0 on join → opens game list) and the **Spectator Compass** (slot 0 on bed destruction → returns player to lobby).

---

## Combat

### Damage

- **Fall damage** is divided by `fallDamage` multiplier (default 5.0)
- **TNT explosion damage** is divided by `TNTDamage` multiplier (default 5.0)
- **Melee damage** is unmodified (default Minecraft values)

### Kill & Assist Tracking

- Last player to hit within **10-second window** = **killer**
- Any player dealing **≥ 25% of the killer's total damage** to the victim = **assist**
- Tracking data cleared after kill resolution

### Scoring

| Event | Points |
|---|---|
| Kill | +10 |
| Assist | +5 |
| Death | 0 (no penalty to score) |

### Extra Hearts (Bed Bonus)

When a team destroys an enemy bed, **all surviving members of the destroying team immediately earn a permanent health bonus** (gated on `extraHearts` config flag):
- `+2 hearts × total beds destroyed by the team` (applied as `extra_hearts` attribute modifier)
- Old modifier is replaced by the new cumulative total on each subsequent destruction
- Applied regardless of whether enemy players are still alive

### TNT Mechanics

- Right-click a TNT block to ignite it
- Fuse: **80 ticks (4 seconds)**, configurable
- Explosion radius: **4.0 blocks**, configurable
- Destroys: sandstone, end stone, TNT, beds
- Chained TNT explosions credit the original detonator
- Bed destruction triggers `onBedDestroyed(team, destroyer)`

---

## Death & Respawn

| Condition | Outcome |
|---|---|
| Bed intact | Respawn at bed with protection |
| Bed destroyed | Become permanent spectator |
| Fall below `islandY − 60` | Teleport to bed/spawn, no damage |

**Respawn protection (default 6 seconds):**
- Slowness IX applied (prevents movement)
- Jump strength set to 0
- Player hidden from all enemies (bidirectional)
- Incoming damage fully cancelled
- Action bar shows countdown: `§aProtection: Xs`
- Disabled by setting `respawn-protection: 0`

### Spectator

A player in a `GameRoom` who is not part of any team. Two distinct origins, same treatment:

- **Eliminated** — bed was destroyed; became spectator automatically mid-game
- **Observer** — joined a RUNNING room from the room listing

Both are in SPECTATOR gamemode, hidden from team players, given a compass (slot 0) to return to the main lobby, and teleported back to the main lobby when the game ends.

**Spectator compass** (slot 0): right-clicking returns the player to the main lobby and clears their inventory.

---

## Forbidden Zone

Only active when exactly **2 teams** are in the game. Absent with 3 or more teams.

Before overtime, block placement is blocked along the short-path corridor between the two neighboring team islands. The wedge precisely covers the ring segment (4-island) or corner pocket (8-island) that forms the direct path between the two teams' islands, without bleeding into adjacent zones.

**Formula:**
```
midline_angle  = atan2(midZ, midX)          // midpoint between the two teams' spawns
block_angle    = atan2(block.Z, block.X)
diff           = block_angle − midline_angle
half_angle     = π / islandCount             // ±45° for 4-island, ±22.5° for 8-island
if |diff| < half_angle → placement BLOCKED
```

The blocked zone is **dynamic** — computed from whichever two teams are actually playing, not hardcoded to a fixed quadrant. Lifted when overtime begins at 30 minutes.

---

## Restrictions

| Action | Status |
|---|---|
| Crafting | Always blocked |
| Dropping armor | Always blocked |
| Sleeping in beds | Always blocked |
| Placing non-sandstone/end stone/TNT blocks | Blocked |
| Placing blocks within 3 blocks of a bed | Blocked |
| Placing blocks in the forbidden zone | Blocked (pre-overtime, 2-team games only) |
| Ender chest access | Allowed (persists through death) |
| Ender chest cleared | On game end |

---

## Progression

Stats are persisted to PostgreSQL across sessions.

### Per-Game Statistics (`player_statistics` table)

- Kills, Deaths, Assists
- Score
- Beds Destroyed
- Wins / Losses
- Win Streak (reset to 0 on loss)
- K/D Ratio

### Leveling (`player_levels` table)

- Max level: **150**
- XP required per level: `80 + (level × 20)`
  - Level 1 = 100 XP, Level 50 = 1,080 XP, Level 150 = 3,080 XP
- Cumulative XP: `80 × level + 10 × level × (level + 1)`

**XP gains per event:**

| Event | XP |
|---|---|
| Win | +100 |
| Loss | +20 |
| Kill | +15 |
| Assist | +5 |
| Death | −10 |

**Level tiers** have distinct icon and color formatting (☆ → ♕ across 0–150).

---

## HUD & UI

### Scoreboard (FastBoard, updates every tick)

**Lobby:**
```
[Animated separator]

✪ Niveau: {color}{level}{icon}  {xp}/{max_xp}
{XP progress bar}

☆ Statistiques:
{kills} 🗡  {assists} ⚔  {deaths} ☠  (K/D)

[Animated separator]
```

**In-Game:**
```
{timer or OVERTIME}

Lit: ✅ / ❌
Île: {island number}
{team list with bed status}

[Animated separator]
```

### Leaderboard Holograms

4 hologram types placed in-world by admins via `/leaderboard <kills|wins|level|winstreak>`. Each displays the top 10 players with gold/silver/bronze medal styling. Updated at the end of each game via `updateAllHolograms()`. Removed with `/leaderboard remove` (targets the nearest hologram within 5 blocks).

### Menus / GUIs

| Menu | Description |
|---|---|
| Team Selection | 2-row inventory, colored wool per team, shows roster size |
| Shop (ShopGUI) | Main hub: 4 item-type categories (Weapons, Armor, Potions, Blocks); category view: 3-row trade list |
| Room Listing | All GameRooms in WAITING or RUNNING state; CREATING rooms hidden. Yellow=waiting+available, Red=waiting+full, Green=running. Clicking a WAITING room joins as team player; clicking a RUNNING room joins as Spectator; clicking a full room shows error. |
| Host Config GUI | 3-row inventory for the host to configure IslandType, MaxTeams, TeamSize, MapType, extraHearts, overtimeStart before creating the room |
| Host Panel | In-WaitingRoom panel: force-start button + per-player kick list |
| Player Settings | Scoreboard toggle, music toggle |

### Chat Messages (French-language server)

- Join: `§a[+] §f{name}`
- Quit: `§c[-] §f{name}`
- Kill: `⚔ {victim} §7a été tué par {killer(s)}`
- Bed destroyed: `§c{destroyer} §7a détruit le lit de l'équipe §c{team}`
- Countdown: `§eLa partie commence dans §c{n} §esecondes!`
- Overtime: `§c§lOVERTIME! §7Les restrictions de placement sont levées!`
- Extra hearts: `+{n} Cœurs permanents!`

---

## Balance Numbers

| Setting | Default | Config Key |
|---|---|---|
| Island offset from center | 40 blocks | `islandOffset` |
| Bed protection radius | 3 blocks | `bedProtectionRadius` |
| Respawn protection duration | 6 seconds | `respawn-protection` |
| Fall damage divisor | 5.0 | `fallDamage` |
| TNT damage divisor | 5.0 | `TNTDamage` |
| TNT fuse | 80 ticks (4 s) | `explodeTicks` |
| TNT explosion radius | 4.0 blocks | `radius` |
| Overtime trigger | 1800 seconds (30 min) | hardcoded |
| Min players to start | 2 | hardcoded |
| Min teams to start | 2 | hardcoded |
| Max level | 150 | `maxLevel` |
| Kill tracker expiry | 10 seconds | hardcoded |
| Assist threshold | 25% of killer's damage to victim | hardcoded |
| Void threshold | islandY − 60 | hardcoded |
| Compass update interval | 20 ticks (1 s) | hardcoded |
| Compass cost | 16 Diamonds | hardcoded |

---

## Build System & Dependencies

**Build tool:** Gradle (build.gradle.kts), Java 21  
**Output:** `TigrouRush.jar` → copied to `../server/plugins/` on build

### Runtime Dependencies

| Library | Version | Purpose |
|---|---|---|
| Paper API | 1.21.11 | Minecraft server API (compileOnly) |
| WorldEdit Bukkit | 7.4.0 | Schematic loading & pasting (compileOnly) |
| PostgreSQL JDBC | 42.7.1 | Database driver |
| Hibernate ORM | 6.4.1 | ORM for stats & levels |
| FastBoard | 2.1.5 | Scoreboard rendering |
| HologramLib | 1.8.3.2 | Hologram display (leaderboards) |
| Lombok | 1.18.36 | Annotation processing |

### Required Server Plugins (load before TigrouRush)

- WorldEdit
- GMusic (background music)
- PacketEvents
- HologramLib
