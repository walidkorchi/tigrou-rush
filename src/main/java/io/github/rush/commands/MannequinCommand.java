package io.github.rush.commands;

import io.github.rush.Main;
import io.github.rush.game.Game;
import io.github.rush.game.GameManager;
import io.github.rush.game.GameRoom;
import io.github.rush.game.Team;
import io.github.rush.game.TeamColor;
import io.github.rush.menus.TeamSelectionGUI;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import com.destroystokyo.paper.profile.PlayerProfile;
import org.jspecify.annotations.NullMarked;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static net.kyori.adventure.text.Component.text;

@NullMarked
public class MannequinCommand {

    private static final String[] NAMES = {
            "KaffWorld", "BYSLIDE", "HikawiiLeKiwi"
    };

    private final Random random = new Random();

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("mannequin")
                .requires(ctx -> ctx.getSender().isOp())
                .executes(ctx -> runSpawn(ctx))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .executes(ctx -> runSpawnAtPlayer(ctx))))
                .then(Commands.literal("spawnteam")
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .executes(ctx -> runSpawnAtPlayerWithTeam(ctx)))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .executes(ctx -> runClear(ctx))));
    }

    private int runSpawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final CommandSender sender = ctx.getSource().getSender();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(text("This command must be run by a player", NamedTextColor.RED));
            return 0;
        }

        spawnMannequinAsync(player.getLocation(), sender, null);

        return Command.SINGLE_SUCCESS;
    }

    private int runSpawnAtPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("target",
                PlayerSelectorArgumentResolver.class);
        final Location targetLoc = targetResolver.resolve(ctx.getSource()).getFirst().getLocation();

        spawnMannequinAsync(targetLoc, ctx.getSource().getSender(), null);

        return Command.SINGLE_SUCCESS;
    }

    private int runSpawnAtPlayerWithTeam(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
        Player target = targetResolver.resolve(ctx.getSource()).getFirst();
        String teamName = ctx.getArgument("team", String.class);
        CommandSender sender = ctx.getSource().getSender();

        spawnMannequinAsync(target.getLocation(), sender, teamName);

        return Command.SINGLE_SUCCESS;
    }

    private void spawnMannequinAsync(Location location, CommandSender sender, String teamName) {
        Set<String> existingNames = new HashSet<>();
        for (var entity : location.getWorld().getEntities()) {
            if (entity instanceof Mannequin m && m.customName().toString() != null) {
                existingNames.add(m.customName().toString());
            }
        }

        List<String> availableNames = Arrays.stream(NAMES)
                .filter(n -> !existingNames.contains(n))
                .toList();

        if (availableNames.isEmpty()) {
            sender.sendMessage(text("All mannequin names are already in use!", NamedTextColor.RED));
            return;
        }

        String name = availableNames.get(random.nextInt(availableNames.size()));

        Main main = Main.getInstance();
        GameManager gameManager = main.getGameManager();

        // Resolve the active game from the executor's world so hub admins get no team
        Game activeGame = null;
        if (gameManager != null && sender instanceof Player executorPlayer) {
            String worldName = executorPlayer.getWorld().getName();
            GameRoom room = gameManager.getGameRoomByWorld(worldName);
            if (room != null && room.isRunning()) {
                activeGame = room.getGame();
            } else if (worldName.equals(main.getGameWorld())) {
                Game legacy = gameManager.getCurrentGame();
                if (legacy != null && legacy.getState() == io.github.rush.game.GameState.RUNNING) {
                    activeGame = legacy;
                }
            }
        }

        TeamColor teamColor = null;
        if (activeGame != null) {
            teamColor = teamName != null ? TeamColor.valueOf(teamName.toUpperCase()) : getSmallestTeam(activeGame);
        }

        PlayerProfile profile = Bukkit.createProfile(name);

        Mannequin mannequin = (Mannequin) location.getWorld().spawnEntity(location, EntityType.MANNEQUIN);
        mannequin.customName(Component.text(name));
        mannequin.setCustomNameVisible(true);

        profile.update().thenAccept(updatedProfile -> {
            if (updatedProfile != null && updatedProfile.getTextures() != null
                    && updatedProfile.getTextures().getSkin() != null) {
                ResolvableProfile resolvable = ResolvableProfile.resolvableProfile(updatedProfile);
                mannequin.setProfile(resolvable);
            }
        });

        if (teamColor != null && activeGame != null) {
            Team team = activeGame.getTeam(teamColor.name());
            boolean joined = activeGame.joinTeam(mannequin, teamColor);
            if (joined) {
                activeGame.setPlayerReady(mannequin, true);
                if (team != null && team.getSpawnLocation() != null) {
                    mannequin.teleport(team.getSpawnLocation());
                }
                mannequin.getEquipment().setItem(EquipmentSlot.HEAD, TeamSelectionGUI.createTeamBanner(teamColor));
                sender.sendMessage(text("Spawned mannequin \"" + name + "\" on team " + teamColor.name(),
                        NamedTextColor.GREEN));
            } else {
                sender.sendMessage(text("Failed to join mannequin to team", NamedTextColor.RED));
            }
            return;
        }

        sender.sendMessage(text("Spawned mannequin \"" + name + "\" (no active game in this world)", NamedTextColor.YELLOW));
    }

    private TeamColor getSmallestTeam(Game game) {
        return game.getTeams().values().stream()
                .min((a, b) -> Integer.compare(a.getPlayers().size(), b.getPlayers().size()))
                .map(t -> t.getColor())
                .orElse(TeamColor.RED);
    }

    private int runClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
        Player target = targetResolver.resolve(ctx.getSource()).getFirst();
        CommandSender sender = ctx.getSource().getSender();

        Main main = Main.getInstance();
        GameManager gameManager = main.getGameManager();
        Game game = gameManager != null ? gameManager.getCurrentGame() : null;

        int count = 0;

        for (Entity entity : target.getWorld().getEntities()) {
            if (entity instanceof Mannequin) {
                if (game != null) {
                    game.leaveTeam(entity);
                }
                entity.remove();
                count++;
            }
        }

        sender.sendMessage(
                text("Removed " + count + " mannequins from " + target.getWorld().getName(), NamedTextColor.GREEN));

        return Command.SINGLE_SUCCESS;
    }
}
