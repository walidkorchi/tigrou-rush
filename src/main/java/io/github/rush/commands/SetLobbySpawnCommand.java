package io.github.rush.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.rush.Main;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Location;
import org.jspecify.annotations.NullMarked;

import net.kyori.adventure.text.Component;

@NullMarked
public class SetLobbySpawnCommand {

    private final Main plugin;

    public SetLobbySpawnCommand(Main plugin) {
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("setlobbyspawn")
                .requires(ctx -> ctx.getSender().isOp())
                .executes(this::run);
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        return CommandManager.requirePlayer(ctx, player -> {
            final Location loc = player.getLocation();

            plugin.getConfig().set("lobby-spawn.world", loc.getWorld().getName());
            plugin.getConfig().set("lobby-spawn.x", loc.getX());
            plugin.getConfig().set("lobby-spawn.y", loc.getY());
            plugin.getConfig().set("lobby-spawn.z", loc.getZ());
            plugin.getConfig().set("lobby-spawn.yaw", (double) loc.getYaw());
            plugin.getConfig().set("lobby-spawn.pitch", (double) loc.getPitch());
            plugin.saveConfig();

            ctx.getSource().getSender().sendMessage(Component.translatable("rush.setlobby_spawn_set",
                    Component.text(String.format("%.2f", loc.getX())),
                    Component.text(String.format("%.2f", loc.getY())),
                    Component.text(String.format("%.2f", loc.getZ())),
                    Component.text(String.format("%.1f", loc.getYaw())),
                    Component.text(String.format("%.1f", loc.getPitch()))));

            return Command.SINGLE_SUCCESS;
        });
    }
}
