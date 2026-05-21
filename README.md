![Banner](.github/assets/Banner.webp)

> [!WARNING]
>
> This project is still in development and is not ready for production use.

## ⚔️ Roadmap

### Todos

- The player must be given for the "hub lobby state" his player skull which when right clicked must open a custom CraftEngine GUI.

### Bugs

- The action bar with message format `[█████] %message%` of the loading game creation does not persist throughout the 5 creation phases, meaning that the phase message will disappear a second after it is displayed. The expected behavior is that the action bar visibility must persist until the phase is completed and moves to the next phase, and not be an ephemeral message that disappears after a short time. This bug is caused by server lag spike since while the world is creating or schematics are loaded, the server freezes to process both events asynchronously, which hangs the server and makes the action bar message disappear prematurely. The attempt to fix this is to offshore those two events outside of the main thread, which partially worked because players could still send messages in public chat while events are being processed asynchronously.

### Needs testing and optimistically fixed

none
