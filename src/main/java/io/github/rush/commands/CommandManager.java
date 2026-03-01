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

    @FunctionalInterface
    public interface CommandRegistration {
        LiteralArgumentBuilder<CommandSourceStack> create();
    }

    public void register(CommandRegistration registration) {
        commands.add(registration);
    }

    public void registerAll(Main plugin) {
        register(new LevelDebugCommand(plugin)::createCommand);
    }

    public void onCommands(RegistrarEvent<Commands> event) {
        Commands registrar = event.registrar();
        for (CommandRegistration cmd : commands) {
            registrar.register(cmd.create().build());
        }
    }
}
