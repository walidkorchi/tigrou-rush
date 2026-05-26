# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

TigrouRush is a PaperMC plugin (Minecraft 26.1) implementing a Rush minigame — a BedWars-style game where teams defend their bed while racing to destroy others'. Full game design: https://github.com/walidkorchi/tigrou-rush/wiki

## Wiki — Update via gh CLI

The game design doc lives on the GitHub wiki. Update it directly:

```bash
# Clone wiki repo (separate from code repo)
gh repo clone walidkorchi/tigrou-rush -- --single-branch --branch wiki 2>/dev/null ||
  git clone https://github.com/walidkorchi/tigrou-rush.wiki.git /tmp/wiki
cd /tmp/wiki && git pull
# Edit pages, then
git add -A && git commit -m "refactor(docs): update <topic>" && git push
```

## Build & Deploy

```bash
# Build and deploy to local server (../server/plugins/)
./gradlew build

# Build shadow JAR only (no deploy)
./gradlew shadowJar

# Lint (results are informational — both tools have ignoreFailures=true)
./gradlew checkstyle spotbugs
```

`tasks.build` depends on `copyToPlugins`, which copies `TigrouRush.jar` into `../server/plugins/`. The project targets Java 25; the Eclipse block in `build.gradle.kts` is IDE-only and set to 21 for compatibility.

## Database

```bash
# Start PostgreSQL (credentials: rush/rush/rush)
docker-compose up -d
```

Hibernate runs `hbm2ddl.auto=update` on boot — the schema migrates automatically. The 36-rank prestige system (`player_levels` table) is persisted; see `PlayerLevel.java` for the XP formula.

## AI Bot (separate project)

```bash
cd ai
bun run dev    # watch mode
bun run start  # production
```

Mineflayer bot that joins the server, accepts the resource pack, clicks through GUIs, and selects a team. It targets `localhost:25565` with username `TigrouAI`.

## Architecture

### Plugin entry point

`Main.java` is the `JavaPlugin` subclass. It constructs and holds singleton managers accessed via `Main.getInstance()`:

| Manager | Responsibility |
|---|---|
| `GameManager` | Create/destroy rooms, player↔room mapping, world lifecycle |
| `ScoreboardManager` | FastBoard sidebars, updated every tick |
| `TablistManager` | FastTablist with rank badge images (via CraftEngine bitmap fonts) |
| `PlayerStatisticManager` / `PlayerLevelManager` | Hibernate JPA → PostgreSQL |
| `PlayerSettingsManager` | Per-player preferences |
| `ReplayStorage` / `ReplayManager` | Replay file I/O and live playback sessions |
| `CommandManager` | Registers all Brigadier commands |
| `ConfigManager` | Wraps `config.yml` |

### Game lifecycle

```
GameManager.createGameRoom()
  → void world created (main thread)
  → schematics pasted via FAWE (async thread — DiskOptimizedClipboard requires same thread for load+paste)
  → merchants/ender chests placed (main thread)
  → GameRoom.state: CREATING → WAITING → RUNNING → STOPPED
```

Each room owns a dedicated Bukkit `World` (named `rush_game_N_<host>`). On removal the world is unloaded and its folder deleted asynchronously.

### Core types

- **`GameRoom`** — container: world, `GameRoomConfig`, island list, `Game` instance. State checked via `isWaiting()`, `isRunning()`.
- **`Game`** — runtime logic: teams, kill tracking, resource spawners, replay recorder, overtime, win condition.
- **`GameCycle`** — 1-second BukkitTask that increments `game.gameTime`.
- **`GameParticipant`** — sealed interface implemented by `GamePlayer` (real player wrapper) and `GameMannequin` (AI bot entity). All game logic uses this interface for polymorphism.
- **`Team`** — holds `GameParticipant` list, bed state, spawn/bed `Location`, ender chest locations.
- **`IslandLayout`** — static island positions, directions, yaw values for up to 8 islands.

### Replay system

Recording happens inside `Game` via `ReplayRecorder`. Files are stored as `.rush` in `plugins/Rush/replays/`. `ReplayManager` creates isolated replay worlds (named `rush_replay_<sessionId>`) and manages multiple concurrent viewers per session. The world population mirrors a live game world: beds, ender chests, merchants placed from the recorded header metadata.

### GUI system

`GUI.java` wraps a Bukkit inventory with per-slot left/right-click handlers. All menus extend or delegate to it: `GameSelectionGUI`, `TeamSelectionGUI`, `HostConfigGUI`, `HostPanelGUI`, `ShopGUI`, `PlayerSettingsGUI`, replay menus.

### i18n

All user-facing strings go through Adventure's `Component.translatable("rush.<key>")`. The single supported locale is `fr_FR`, backed by `src/main/resources/translations/fr_fr.json` (loaded as a `ResourceBundle` in `Main.onEnable`). Add new keys to that file and reference them with `Component.translatable()` — never use raw string literals for player-visible text.

### External plugin dependencies

| Plugin | Usage |
|---|---|
| FastAsyncWorldEdit (FAWE) | Schematic paste for game and replay worlds |
| HologramLib | Leaderboard and author holograms |
| CraftEngine | Custom items, sounds, resource pack, bitmap font rank images |
| packetevents | Packet-level features (tablist etc.) |

Schematics are read from `../server/plugins/FastAsyncWorldEdit/schematics/`. The game island schematic name is configured via `schematicFilename` in `config.yml`; the waiting room uses `waiting_room.schem`.
