# TigrouRush — Game Design Document

**Platform:** Minecraft Bukkit plugin (Paper 1.21.11), Java 21  
**Type:** Bed Wars / Rush hybrid — 4-team arena, each team on its own island. Destroy enemy beds to prevent their respawns. Last team standing wins.

---

## Table of Contents

1. [World & Map](#world--map)
2. [Game States](#game-states)
3. [Teams](#teams)
4. [Resource Economy](#resource-economy)
5. [Merchants](#merchants)
6. [Combat](#combat)
7. [Death & Respawn](#death--respawn)
8. [Forbidden Zone](#forbidden-zone)
9. [Restrictions](#restrictions)
10. [Progression](#progression)
11. [HUD & UI](#hud--ui)
12. [Balance Numbers](#balance-numbers)
13. [Build System & Dependencies](#build-system--dependencies)

---

## World & Map

Two map schemas exist, each with different island layout and buildable-zone geometry:

### 4-Island Schema

- **4 islands** loaded from `rush_island.schem` (WorldEdit), each rotated and placed at ±40 blocks from center (0, 0)
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
- **Multi-room support:** each `GameRoom` gets a dedicated void world named `rush_game_{n}_{host}` with auto-save disabled

---

## Game States

`WAITING → RUNNING → STOPPED`

| State | Description |
|---|---|
| WAITING | Lobby phase; accepts players, team selection open |
| RUNNING | Active game; combat enabled, resources spawning |
| STOPPED | Game ended; stats saved, world cleaned up |

- Lobby countdown: **60 seconds**
- Auto-start check: every **5 seconds** (100 ticks)
- **Overtime** triggered at **30 minutes** (1800 seconds): removes block placement restrictions

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

When a team destroys an enemy bed, **all surviving members of the destroying team immediately earn a permanent health bonus**:
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

**Spectator compass** (slot 0 when bed is destroyed): right-clicking returns the player to the main lobby and clears their inventory.

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
| Game List | All active rooms with status color (green/yellow/red); click to join |
| Game Creation | Choose island schema (4 or 8 islands) and team size (2v2 / 3v3 / 4v4) |
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
