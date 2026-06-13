# 🏆 Roadmap

## ⚔️ Implentations

### 🛠️ Code

- [ ] Migrate from FastBoard to [ScoreboardLibrary](MegavexNetwork/scoreboard-library)

### 🎨 Textures

- [ ] PlayerInventory : create a custom texture for player inventory overriding the default one which should remove 2x2 grid inventory.
- [ ] ReplayFollowGUI : add a generic GUI to choose a player to teleport to in a replay.
- [ ] PlayerSettingsGUI : create a GUI for the player to customize their settings.
- [ ] GamePanelGUI : create a GUI for the game host to manage the game room.
- [ ] `slot_highlight_front` must be taken to consideration to disable inventory slot highlights of empty_slot custom items in custom GUIs.

## 🐛 Bugs

- [ ] Fix scoreboards unresolved font bitmapped text display for "joueurs classés" only where rank component is displayed.

## ♻️ Refactos

- [ ] Instead of storing replays data on json files with serialization, we should use binary with formal data for storing with a codified binary headers to read `%name%.replay` files.
- [ ] No longer use `getWorld().getName()` but rather `getKey()` because based on paper API `This method is considered obsolete and is a candidate for future deprecation. Prefer using getKey() as the world identity.`.
- [ ] Instead of displaying the current level of the rank of the player in the xp bar, display the xp amount left before next rank up.
- [ ] Replace holograms implementation in Leaderboard command to using mannequins with the following format tag string : "%rank_image_component% %player_name%". The command will spawn a mannequin's at the command executor's position with the skin player of the corresponding leaderboard players (top1, top2, top3) only players as command args.

## 🧪 Needs testing but optimistically fixed/implemented

- [ ] GamePlayer no longer takes regular fall damage in a running game world.
- [ ] Try out game compass trackers as early non-tested implementation.
- [ ] Check if message is indeed world-scoped in the server.
- [ ] When a player lost a game by another team and got final-killed, he is teleported to the waiting lobby room, which should not happen, he must turn into a spectator and get teleported in the same position as when the player is teleported in replay world.
- [ ] In a 1vs1 game, when someone already chose a team color, other player can join the same team color.
- [ ] The tablist bar is not updated after everyones gets teleported to the hub after game end, keeping the team color displayed in the hub. In the same context, both players from a 1vs1 ended game after teleported to the hub can no longer see each other. one that does not see the other player is even hidden from their tablist.
