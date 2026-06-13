package io.github.rush.guis;

import io.github.rush.utils.i18n;
import io.github.rush.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.AnvilView;
import net.kyori.adventure.text.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Utility that opens an anvil inventory to capture a single text input from a
 * player. The result is delivered asynchronously via a {@link Consumer} once
 * the player clicks the output slot.
 *
 * <p>Usage:
 * <pre>
 *   AnvilInputGUI.open(player, "Bot", name -> { ... });
 * </pre>
 *
 * <p>Wire {@link #tryConsume(Player, InventoryClickEvent)} into
 * {@code onInventoryClick} and {@link #cancel(Player)} into
 * {@code onInventoryClose} in your event listener.
 */
public final class AnvilInputGUI {

    /** Default name stored per-player so fallback is accurate if text is blank. */
    private static final Map<UUID, String> defaults = new HashMap<>();
    private static final Map<UUID, Consumer<String>> pending = new HashMap<>();

    private AnvilInputGUI() {}

    /**
     * Opens an anvil for {@code player} pre-filled with a nametag named
     * {@code defaultName}. When the player clicks the output slot, {@code callback}
     * receives the entered text (falling back to {@code defaultName} if blank).
     *
     * @param player      the player to open the anvil for
     * @param defaultName initial nametag label shown in the rename field
     * @param callback    consumer receiving the final name on the main thread
     */
    public static void open(Player player, String defaultName, Consumer<String> callback) {
        pending.put(player.getUniqueId(), callback);
        defaults.put(player.getUniqueId(), defaultName);

        player.openInventory(MenuType.ANVIL.builder()
                .title(Component.text(i18n.log("rush.mannequin_anvil_title")))
                .build(player));

        player.getOpenInventory().getTopInventory().setItem(0,
                ItemBuilder.of(Material.NAME_TAG)
                        .name(Component.text(defaultName))
                        .build());
    }

    /**
     * Called from {@code onInventoryClick}. If the event is a click on the
     * output slot (raw slot 2) of an open anvil for which a callback is pending,
     * cancels the event, closes the inventory, and fires the callback.
     *
     * @return {@code true} if the event was consumed and no further handling is needed
     */
    public static boolean tryConsume(Player player, InventoryClickEvent event) {
        if (event.getRawSlot() != 2)
            return false;
        if (!(event.getView() instanceof AnvilView anvilView))
            return false;

        final Consumer<String> callback = pending.remove(player.getUniqueId());
        if (callback == null)
            return false;

        final String fallback = defaults.remove(player.getUniqueId());

        event.setCancelled(true);

        final String text = anvilView.getRenameText();
        final String result = (text == null || text.isBlank()) ? fallback : text;

        player.closeInventory();
        callback.accept(result);
        return true;
    }

    public static boolean hasPending(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    /**
     * Called from {@code onInventoryClose} when an AnvilView is confirmed to be
     * closing. Drops any pending callback so stale entries do not accumulate.
     */
    public static void cancel(Player player) {
        pending.remove(player.getUniqueId());
        defaults.remove(player.getUniqueId());
    }
}
