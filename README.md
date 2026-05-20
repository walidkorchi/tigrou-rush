![Banner](.github/assets/Banner.webp)

> [!WARNING]
>
> This project is still in development and is not ready for production use.

## ⚔️ Roadmap

### Bugs & Todos

none

### Needs testing and optimistically fixed

- [ ] Create a `/hub` with aliases: `/spawn | /lobby` Command which will teleport the player to the hub location based on the server's configuration `config.yml` and the player will receive the "hub lobby" state.
- [ ] Players die from the void because the hub is an island, if player is below y = 0 of the hub world, they must be teleported to the hub lobby coordinates based on the server's configuration `config.yml`.
- [ ] Speed merchants which are baby villagers grows overtime because this is how Minecraft works, we must disable their growth. *(fixed with setAgeLock method)*
- [ ] When a player falls into the void in a game room where phase is `RUNNING` with his team bed still not destroyed, the player is teleported back to the spawn team bed island, which is expected, but he does not die and looses all items in his inventory and there's no respawn cooldown with invulnerability and invisibility. The must loose all items in his inventory with the appropriate state of player dying state and must be given the initial non-removable and and non-droppable items such as enchanted wooden pickaxe and protection I armor as per game design.
- [ ] The action bar with message format `[█████] %message%` of the loading game creation does not persist throughout the 5 creation phases, meaning that the phase message will disappear a second after it is displayed. The expected behavior is that the action bar visibility must persist until the phase is completed and moves to the next phase, and not be an ephemeral message that disappears after a short time. This bug is caused by server lag spike since while the world is creating or schematics are loaded, the server freezes to process both events asynchronously, which hangs the server and makes the action bar message disappear prematurely. The attempt to fix this is to offshore those two events outside of the main thread, which partially worked because players could still send messages in public chat while events are being processed asynchronously.
