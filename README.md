![Banner](.github/assets/Banner.webp)

> [!WARNING]
>
> This project is still in development and is not ready for production use.

## 🏆 Roadmap

### ⚔️ Implentations

- The player must be given for the "hub lobby state" his player skull which when right clicked must open a custom CraftEngine GUI.
- At the end of the game, after everyone receives the "RÉSUMÉ DE LA PARTIE", only the game host player will receive a message either to archive the game or not. If the game is archived, the replay will be saved and can be viewed later. This is a replacement for auto-saving replays and auto-stating archived games.

### 🐛 Bugs

- The action bar with message format `[█████] %message%` of the loading game creation does not persist throughout the 5 creation phases, meaning that the phase message will disappear a second after it is displayed. The expected behavior is that the action bar visibility must persist until the phase is completed and moves to the next phase, and not be an ephemeral message that disappears after a short time. This bug is caused by server lag spike since while the world is creating or schematics are loaded, the server freezes to process both events asynchronously, which hangs the server and makes the action bar message disappear prematurely. The attempt to fix this is to offshore those two events outside of the main thread, which partially worked because players could still send messages in public chat while events are being processed asynchronously.

### Needs testing and optimistically fixed/implemented

- When player dies from void, no killing sound effect is emitted. Usually players take damage for that purpose. It's becausing we are rescusing the player before actually dying, but we need to revive him and make sure that the client player does not render the death screen.
- Replay does not replicate the same schematic parsing with the proper math island rotations and every aspect of how game host players create their game room from the host gui menu regarding the 4-island map on a 1vs1 for instance.
- The player mannequins from the recorded replay don't exist as well inside a replay world if they were in a game room with mannequins as team players.
- We cannot see when players are crouching, they are always standing in the recorded replay which does not reflect between the actual state of the players in the game room and the archived replay state.
