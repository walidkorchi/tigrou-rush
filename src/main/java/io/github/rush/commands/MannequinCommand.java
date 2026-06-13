package io.github.rush.commands;

import io.github.rush.Main;
import io.github.rush.game.Game;
import io.github.rush.game.GameManager;
import io.github.rush.entities.GameMannequin;
import io.github.rush.game.GameRoom;
import io.github.rush.abstracts.Team;
import io.github.rush.guis.TeamSelectionGUI;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@NullMarked
public class MannequinCommand {

    private static final String VRC_LOL_API = "https://vrc.lol/api.php?suggest=%s&limit=25";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final AtomicReference<@Nullable List<String>> NAME_CACHE = new AtomicReference<>(null);

    private final Random random = new Random();

    public static void invalidateCache() {
        NAME_CACHE.set(null);
    }

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

    private int runSpawn(CommandContext<CommandSourceStack> ctx) {
        return CommandManager.requirePlayer(ctx, player -> {
            spawnMannequin(player.getLocation(), ctx.getSource().getSender(), null);
            return Command.SINGLE_SUCCESS;
        });
    }

    private int runSpawnAtPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("target",
                PlayerSelectorArgumentResolver.class);
        final Location targetLoc = targetResolver.resolve(ctx.getSource()).getFirst().getLocation();

        spawnMannequin(targetLoc, ctx.getSource().getSender(), null);

        return Command.SINGLE_SUCCESS;
    }

    private int runSpawnAtPlayerWithTeam(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("target",
                PlayerSelectorArgumentResolver.class);
        final Player target = targetResolver.resolve(ctx.getSource()).getFirst();

        spawnMannequin(target.getLocation(), ctx.getSource().getSender(), ctx.getArgument("team", String.class));

        return Command.SINGLE_SUCCESS;
    }

    private void spawnMannequin(Location location, CommandSender sender, @Nullable String teamName) {
        resolveNames().thenAccept(names -> {
            if (names.isEmpty()) {
                sender.sendMessage(Component.translatable("rush.mannequin_names_used"));
                return;
            }
            Bukkit.getScheduler().runTask(Main.getInstance(),
                    () -> spawnOnMainThread(names, location, sender, teamName));
        });
    }

    private void spawnOnMainThread(List<String> names, Location location, CommandSender sender,
            @Nullable String teamName) {
        Set<String> inUse = new HashSet<>();
        for (Entity entity : location.getWorld().getEntities()) {
            if (entity instanceof Mannequin m) {
                Component cn = m.customName();
                if (cn != null) {
                    inUse.add(PlainTextComponentSerializer.plainText().serialize(cn));
                }
            }
        }

        List<String> available = names.stream().filter(n -> !inUse.contains(n)).toList();

        if (available.isEmpty()) {
            sender.sendMessage(Component.translatable("rush.mannequin_names_used"));
            return;
        }

        String name = available.get(random.nextInt(available.size()));

        Main main = Main.getInstance();
        GameManager gameManager = main.getGameManager();

        Game activeGame = null;
        if (gameManager != null && sender instanceof Player executorPlayer) {
            String worldName = executorPlayer.getWorld().getName();
            GameRoom room = gameManager.getGameRoomByWorld(worldName);
            if (room != null && room.isWaiting()) {
                activeGame = room.getGame();
            }
        }

        Team.Color teamColor = null;
        if (activeGame != null) {
            teamColor = teamName != null ? Team.Color.valueOf(teamName.toUpperCase()) : getSmallestTeam(activeGame);
        }

        final Mannequin mannequin = (Mannequin) location.getWorld().spawnEntity(location, EntityType.MANNEQUIN);

        mannequin.customName(Component.text(name));
        mannequin.setDescription(null);
        mannequin.setCustomNameVisible(true);

        Bukkit.createProfile(name).update().thenAccept(updatedProfile -> {
            if (updatedProfile != null && updatedProfile.getTextures() != null
                    && updatedProfile.getTextures().getSkin() != null) {
                ResolvableProfile resolvable = ResolvableProfile.resolvableProfile(updatedProfile);
                mannequin.setProfile(resolvable);
            }
        });

        if (teamColor != null && activeGame != null) {
            Team team = activeGame.getTeam(teamColor.name());
            GameMannequin gm = new GameMannequin(mannequin);
            boolean joined = activeGame.joinTeam(gm, teamColor);
            if (joined) {
                activeGame.setPlayerReady(gm, true);
                if (team != null && team.getSpawnLocation() != null) {
                    mannequin.teleport(team.getSpawnLocation());
                }
                mannequin.getEquipment().setItem(EquipmentSlot.HEAD, TeamSelectionGUI.createTeamBanner(teamColor));
                sender.sendMessage(Component.translatable("rush.mannequin_spawned",
                        Component.text(name), Component.text(teamColor.name())));
            } else {
                sender.sendMessage(Component.translatable("rush.mannequin_join_failed"));
            }
            return;
        }

        sender.sendMessage(Component.translatable("rush.mannequin_no_game", Component.text(name)));
    }

    private CompletableFuture<List<String>> resolveNames() {
        List<String> cached = NAME_CACHE.get();
        if (cached != null)
            return CompletableFuture.completedFuture(cached);

        String prefix = String.valueOf((char) ('a' + random.nextInt(26)))
                + (char) ('a' + random.nextInt(26));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(VRC_LOL_API, prefix)))
                .header("User-Agent", "TigrouRush (ma.walidkorchi@gmail.com)")
                .timeout(Duration.ofSeconds(10))
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200)
                        return List.<String>of();

                    List<String> names = new ArrayList<>();
                    JsonArray arr = JsonParser.parseString(response.body()).getAsJsonArray();
                    for (JsonElement el : arr) {
                        JsonObject obj = el.getAsJsonObject();
                        JsonArray types = obj.getAsJsonArray("types");
                        if (types == null)
                            continue;
                        for (JsonElement t : types) {
                            if ("Java".equals(t.getAsString())) {
                                names.add(obj.get("name").getAsString());
                                break;
                            }
                        }
                    }

                    NAME_CACHE.set(names);
                    return names;
                })
                .exceptionally(e -> List.of());
    }

    private Team.Color getSmallestTeam(Game game) {
        return game.getTeams().values().stream()
                .min((a, b) -> Integer.compare(a.getPlayers().size(), b.getPlayers().size()))
                .map(t -> t.getColor())
                .orElse(Team.Color.RED);
    }

    private int runClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
        Player target = targetResolver.resolve(ctx.getSource()).getFirst();
        CommandSender sender = ctx.getSource().getSender();

        Main main = Main.getInstance();
        GameManager gameManager = main.getGameManager();
        GameRoom room = gameManager != null ? gameManager.getGameRoomByWorld(target.getWorld().getName()) : null;
        Game game = room != null ? room.getGame() : null;

        int count = 0;

        for (Entity entity : target.getWorld().getEntities()) {
            if (entity instanceof Mannequin m) {
                if (game != null) {
                    game.leaveTeam(new GameMannequin(m));
                }
                entity.remove();
                count++;
            }
        }

        sender.sendMessage(Component.translatable("rush.mannequin_removed",
                Component.text(count), Component.text(target.getWorld().getName())));

        return Command.SINGLE_SUCCESS;
    }
}
