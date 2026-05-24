![Banner](.github/assets/Banner.webp)

> [!WARNING]
>
> This project is still in development and is not ready for production use.

## 🏆 Roadmap

### ⚔️ Implentations

- The player must be given for the "hub lobby state" his player skull which when right clicked must open a custom CraftEngine GUI.
- GUI/TeamColorSelection : create a GUI for the player to select their team color.
- GUI/GameHostPanel : create a GUI for the game host to manage the game room.

### 🐛 Bugs

<!--none 🎉-->

- Players no longer takes fall damage in running game world.
- In a running game, enderchests and resources spanwers appear only in existing teams islands with beds. As per game design, they appear on all islands by default wether the island has a bed or not.
- Clicking on "[YES]" or "[NO]" from message wether the host can archive a game or not, does actually nothing
- [01:53:47 WARN]: [Rush] Could not read gameendmusic.ogg duration, using 27s default

### Needs testing and optimistically fixed/implemented

- In the scoreboard of a running game, the island number changes based on the position of the player, which means if the player is within island's bounds, the island number must be updated accordingly.
- When player dies from void, no killing sound effect is emitted. Usually players take damage for that purpose. It's becausing we are rescusing the player before actually dying, but we need to revive him and make sure that the client player does not render the death screen.
- We cannot see when players are crouching, they are always standing in the recorded replay which does not reflect between the actual state of the players in the game room and the archived replay state.
- In a running game room, mannequins don't die from void which is 20 blocks below the islands the way normal players die and are telported back to their team bed island. > Fix attempt result : The message appears in chat where they did die by getting pushed by another player to the void, but are not teleported back to their team bed island. As per game design, they have a respawn cooldown of 6 seconds, where they are invisible to everyone else which is a state of invulnerability. Currently, the message is spammed infinitely as "%mannequinplayer% est mort"
