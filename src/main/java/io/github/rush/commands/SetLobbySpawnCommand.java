package io.github.rush.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.rush.Main;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import static net.kyori.adventure.text.Component.text;

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
        CommandSender sender = ctx.getSource().getSender();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(text("§cCette commande doit être exécutée par un joueur."));
            return Command.SINGLE_SUCCESS;
        }

        Location loc = player.getLocation();

        plugin.getConfig().set("lobby-spawn.world", loc.getWorld().getName());
        plugin.getConfig().set("lobby-spawn.x", loc.getX());
        plugin.getConfig().set("lobby-spawn.y", loc.getY());
        plugin.getConfig().set("lobby-spawn.z", loc.getZ());
        plugin.getConfig().set("lobby-spawn.yaw", (double) loc.getYaw());
        plugin.getConfig().set("lobby-spawn.pitch", (double) loc.getPitch());
        plugin.saveConfig();

        sender.sendMessage(text(String.format(
                "§aSpawn du lobby défini à §f%.2f, %.2f, %.2f §7(yaw=%.1f, pitch=%.1f)",
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch())));

        return Command.SINGLE_SUCCESS;
    }
}
