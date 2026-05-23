package io.github.rush.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.RegistrarEvent;
import io.github.rush.Main;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.kyori.adventure.text.Component;
import lombok.Getter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@NullMarked
public class CommandManager {

    private final List<CommandRegistration> commands = new ArrayList<>();
    @Getter
    private AuthorCommand authorCommand;

    @FunctionalInterface
    public interface CommandRegistration {
        LiteralArgumentBuilder<CommandSourceStack> create();
    }

    public static int requirePlayer(CommandContext<CommandSourceStack> ctx, Function<Player, Integer> callback) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return callback.apply(player);
        }
        sender.sendMessage(Component.translatable("rush.command_player_only"));
        return Command.SINGLE_SUCCESS;
    }

    public void register(CommandRegistration registration) {
        commands.add(registration);
    }

    @Getter
    private LeaderboardCommand leaderboardCommand;
    private HubCommand hubCommand;

    public void registerAll(Main plugin) {
        register(new DebugCommand(plugin)::createCommand);
        register(new MannequinCommand()::createCommand);
        register(new ForceStartCommand(plugin)::createCommand);
        register(new ForceStopCommand(plugin)::createCommand);
        authorCommand = new AuthorCommand(plugin);
        register(authorCommand::createCommand);
        leaderboardCommand = new LeaderboardCommand();
        register(leaderboardCommand::createCommand);
        register(new SettingsCommand()::createCommand);
        register(new SetLobbySpawnCommand(plugin)::createCommand);
        hubCommand = new HubCommand(plugin);
    }

    public void onCommands(RegistrarEvent<Commands> event) {
        Commands registrar = event.registrar();
        for (CommandRegistration cmd : commands) {
            registrar.register(cmd.create().build());
        }
        if (hubCommand != null) {
            registrar.register(hubCommand.createCommand().build(), List.of("spawn", "lobby"));
        }
    }
}
