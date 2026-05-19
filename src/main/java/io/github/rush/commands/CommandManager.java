package io.github.rush.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.RegistrarEvent;
import io.github.rush.Main;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

@NullMarked
public class CommandManager {

    private final List<CommandRegistration> commands = new ArrayList<>();
    private AuthorCommand authorCommand;

    @FunctionalInterface
    public interface CommandRegistration {
        LiteralArgumentBuilder<CommandSourceStack> create();
    }

    public void register(CommandRegistration registration) {
        commands.add(registration);
    }

    private LeaderboardCommand leaderboardCommand;

    public void registerAll(Main plugin) {
        register(new LevelDebugCommand(plugin)::createCommand);
        register(new DebugZonesCommand(plugin)::createCommand);
        register(new MannequinCommand()::createCommand);
        register(new ResetIslandsCommand(plugin)::createCommand);
        register(new ForceStartCommand(plugin)::createCommand);
        register(new ForceStopCommand(plugin)::createCommand);
        authorCommand = new AuthorCommand(plugin);
        register(authorCommand::createCommand);
        leaderboardCommand = new LeaderboardCommand();
        register(leaderboardCommand::createCommand);
        register(new SettingsCommand()::createCommand);
        register(new SetLobbySpawnCommand(plugin)::createCommand);
    }

    public LeaderboardCommand getLeaderboardCommand() {
        return leaderboardCommand;
    }

    public AuthorCommand getAuthorCommand() {
        return authorCommand;
    }

    public void onCommands(RegistrarEvent<Commands> event) {
        Commands registrar = event.registrar();
        for (CommandRegistration cmd : commands) {
            registrar.register(cmd.create().build());
        }
    }
}
