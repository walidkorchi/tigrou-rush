# TigrouRush — Domain Glossary

## GameState
The lifecycle phase of a `Game` instance. Ordered: `CREATING → WAITING → RUNNING → STOPPED`.

- **CREATING** — world and schematics are being set up asynchronously; the room is not joinable; the host remains in the main lobby until this phase completes.
- **WAITING** — lobby phase; team selection open; players may join if the room is not full.
- **RUNNING** — active game; combat and resource spawning enabled.
- **STOPPED** — game ended; stats saved; world queued for cleanup.

## GameRoom
A self-contained game instance: one void world, one `Game`, one host. Identified by a short UUID. Holds the room's island configuration and team configuration. Destroyed (world unloaded and deleted) after the game ends.

## Overtime
A deathmatch trigger: all surviving beds are destroyed and all block placement restrictions are lifted. Normally fires at 30 minutes. The host option "start from overtime" fires this trigger at tick 0 — the entire game is played in deathmatch mode with no respawns.

## GameRoomConfig
An immutable record holding all host-configurable settings for a GameRoom: `IslandType`, `maxTeams` (int), `TeamSize`, `MapType`, `extraHearts` (boolean), `overtimeStart` (boolean). Created from the host's GUI selections and passed to `GameRoom` at construction. Immutable after room creation.

## Host Panel
A CraftEngine item (`tland:host_panel`) given exclusively to the host in inventory slot 8 upon entering the WaitingRoom. Right-clicking opens a `BasicGui` with a force-start button and a scrollable player list with per-player kick buttons. When host status transfers, the panel item is removed from the outgoing host and given to the incoming host.

## Room Listing
Displays all GameRooms in WAITING or RUNNING state. CREATING rooms are hidden. No filters. Clicking a WAITING room (not full) joins it as a team player; clicking a RUNNING room joins as a Spectator; clicking a full WAITING room shows an error message.

## CREATING cancellation
If the host disconnects before the CREATING phase completes (world + schematics not yet ready), the GameRoom is cancelled: the callback chain halts, the world is unloaded and deleted, and the GameRoom entry is never registered.

## Spectator
A player in a GameRoom who is not part of any team. Includes both eliminated players (bed destroyed) and observers (joined a RUNNING room from the lobby). Both are in SPECTATOR gamemode, hidden from team players, given a compass to return to the main lobby, and returned to the main lobby when the game ends.

## WaitingRoom
A schematic (`waiting_room.schem`) pasted at `(0, 0, 0)` of each GameRoom's void world during the CREATING phase. Serves as the pre-game lobby where players congregate and select teams. The WorldEdit copy origin of the schematic is the player spawn point — players are teleported to `(0, 0, 0)` of the game world when joining or completing room creation.

## MapType
An enum of 8 visual themes for the island schematic: `NORMAL`, `OLD_SCHOOL`, `NETHER`, `END`, `AQUAMARINE`, `SUMMER`, `CHERRY`, `WINTER`. Purely cosmetic — only the island schematic filename changes (`rush_island-${type}.schem`). World environment, gameplay rules, and placeable blocks are identical across all map types.

## MaxTeams
The number of team objects created for a GameRoom. Chosen by the host (default 2, capped by IslandType: max 4 for FOUR_ISLANDS, max 8 for EIGHT_ISLANDS). Exactly this many teams exist — unused island slots are never assigned.

## IslandType
An enum with two values: `FOUR_ISLANDS` (4 islands at N, E, S, W) and `EIGHT_ISLANDS` (8 islands at N, NE, E, SE, S, SW, W, NW). Both are fully supported. Chosen by the host at room creation. Determines the maximum number of teams allowed (`FOUR_ISLANDS` → max 4, `EIGHT_ISLANDS` → max 8).

## TeamSize
An enum with three fixed values: `VS2` (2 players/team), `VS3` (3 players/team), `VS4` (4 players/team). Chosen by the host at room creation. Cannot be changed after the room is created.

## Host
The player who created a `GameRoom`. Identified by `hostUUID` (UUID) on `GameRoom`; `hostName` kept separately for display. After being teleported to the WaitingRoom, the host retains two exclusive powers during the WAITING phase: **force-start** (skip the lobby countdown) and **kick** (remove any player from the room). If the host disconnects during WAITING, host status transfers permanently to the longest-present player (join order tracked on `GameRoom`). The original host cannot reclaim status on reconnect — they rejoin as a regular player.
