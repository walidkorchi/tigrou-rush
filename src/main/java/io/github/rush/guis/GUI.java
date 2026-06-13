package io.github.rush.guis;

import io.github.rush.Main;
import io.github.rush.utils.RushLogger;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.ItemManager;
import net.momirealms.craftengine.core.plugin.context.PlayerOptionalContext;
import net.momirealms.craftengine.core.plugin.gui.BasicGui;
import net.momirealms.craftengine.core.plugin.gui.GuiElement;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * CraftEngine-backed slot-indexed GUI wrapper.
 *
 * <p>
 * Exposes the same {@code addItem} / {@code openGUI} API that callers
 * expect from a plain inventory GUI, but builds a CE {@link PagedGui}
 * internally so the inventory carries CE's custom texture and font system.
 *
 * <p>
 * Slot ↔ layout-char mapping is handled automatically: each slot index
 * (0–53) is assigned a unique character from {@link #SLOT_CHARS}. Empty slots
 * become {@code '_'} (filler). The layout is rebuilt fresh on every
 * {@link #openGUI} call, so the same instance can be opened for multiple
 * players independently.
 *
 * <p>
 * CE click handlers fire off the main thread; all {@link Consumer} actions
 * are dispatched via {@link org.bukkit.scheduler.BukkitScheduler#runTask} so
 * callers can safely touch Bukkit state. Right-click prefers a dedicated
 * right-click action when one is registered, otherwise falls through to the
 * generic action — identical behaviour to the old Bukkit-backed GUI.
 *
 * <p>
 * Usage:
 *
 * <pre>
 *   final GUI gui = new GUI(Component.translatable("rush.my_title"), 3);
 *   gui.addItem(4, myItem, p -> { ... });
 *   gui.addItem(2, item, null, p -> { ... rightClick ... });
 *   gui.openGUI(player);
 * </pre>
 */
public final class GUI {

    /** One unique layout character per slot index (0–53). Must not contain {@link #FILLER}. */
    private static final char[] SLOT_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQR".toCharArray();

    /** Layout character used for every slot that has no registered item. Maps to the CE {@code tland:empty_slot} filler. */
    private static final char FILLER = '_';

    private final String miniMessageTitle;
    private final int rows;

    private final Map<Integer, ItemStack> items = new HashMap<>();
    private final Map<Integer, Consumer<Player>> clickActions = new HashMap<>();
    private final Map<Integer, Consumer<Player>> rightClickActions = new HashMap<>();

    /**
     * @param miniMessageTitle Title in MiniMessage format (e.g. from CE config).
     *                         Resolved with per-player tag resolvers on open.
     */
    public GUI(String miniMessageTitle, int rows) {
        this.miniMessageTitle = miniMessageTitle;
        this.rows = rows;
    }

    /**
     * Accepts a Kyori {@link Component} title (e.g. from {@code i18n.txt()} or
     * {@code Component.translatable()}).
     *
     * <p>
     * Bridge to CE's adventure library:
     * <ol>
     * <li>{@link GlobalTranslator#render} resolves translatable components to
     * their server-side French text.</li>
     * <li>{@link LegacyComponentSerializer} round-trips through any § codes
     * embedded as raw text by {@code i18n.txt()}, converting them to
     * proper Kyori styling.</li>
     * <li>{@link MiniMessage} serialises the result to a string CE can
     * deserialise with full colour fidelity.</li>
     * </ol>
     */
    public GUI(Component title, int rows) {
        final Component resolved = GlobalTranslator.render(title, Locale.FRANCE);
        final String legacy = LegacyComponentSerializer.legacySection().serialize(resolved);
        this.miniMessageTitle = MiniMessage.miniMessage().serialize(
                LegacyComponentSerializer.legacySection().deserialize(legacy));
        this.rows = rows;
    }

    /**
     * Registers a display-only item at {@code slot} with no click behaviour.
     *
     * @param slot 0-based slot index (0–53)
     * @param item the item to display
     * @return {@code this} for chaining
     */
    public GUI addItem(int slot, ItemStack item) {
        return addItem(slot, item, null, null);
    }

    /**
     * Registers an item at {@code slot} with a generic click action fired on any click type.
     *
     * @param slot   0-based slot index (0–53)
     * @param item   the item to display
     * @param action called on the main thread when the slot is clicked; may be {@code null}
     * @return {@code this} for chaining
     */
    public GUI addItem(int slot, ItemStack item, Consumer<Player> action) {
        return addItem(slot, item, action, null);
    }

    /**
     * Registers an item at {@code slot} with separate generic and right-click actions.
     *
     * <p>On a right-click, {@code rightClickAction} takes priority over {@code action}.
     * If {@code rightClickAction} is {@code null}, {@code action} fires for all click types.
     * Both consumers are always dispatched on the main thread.
     *
     * @param slot             0-based slot index (0–53)
     * @param item             the item to display
     * @param action           fired on any click that has no dedicated right-click handler; may be {@code null}
     * @param rightClickAction fired exclusively on right-click; may be {@code null}
     * @return {@code this} for chaining
     */
    public GUI addItem(int slot, ItemStack item,
            Consumer<Player> action,
            Consumer<Player> rightClickAction) {
        items.put(slot, item);
        if (action != null)
            clickActions.put(slot, action);
        if (rightClickAction != null)
            rightClickActions.put(slot, rightClickAction);
        return this;
    }

    /**
     * Removes the item and any associated actions from {@code slot}.
     * The slot reverts to a filler element on the next {@link #openGUI} call.
     *
     * @param slot 0-based slot index (0–53)
     */
    public void removeItem(int slot) {
        items.remove(slot);
        clickActions.remove(slot);
        rightClickActions.remove(slot);
    }

    /**
     * Builds the CE {@link PagedGui} from the current slot registrations and opens it for {@code player}.
     * The layout is constructed fresh on each call, so the same {@code GUI} instance is safe to
     * open for multiple players concurrently.
     *
     * @param player the player to show the inventory to
     */
    public void openGUI(Player player) {
        final net.momirealms.craftengine.core.entity.player.Player craftPlayer = Main.getInstance()
                .adaptCraftPlayer(player);
        final TagResolver[] resolvers = PlayerOptionalContext.of(craftPlayer).tagResolvers();

        BasicGui.builder()
                .layout(buildLayout())
                .inventoryClickConsumer(c -> {
                    final String type = c.type();
                    if ("SHIFT_LEFT".equals(type) || "SHIFT_RIGHT".equals(type)
                            || "DOUBLE_CLICK".equals(type)) {
                        c.cancel();
                    }
                })
                .build()
                .title(AdventureHelper.miniMessage().deserialize(miniMessageTitle, resolvers))
                .refresh()
                .open(craftPlayer);
    }

    /** Converts the slot→item map into a {@link GuiLayout} by assigning each occupied slot its unique {@link #SLOT_CHARS} character and every empty slot the {@link #FILLER} character. */
    private GuiLayout buildLayout() {
        final int totalSlots = rows * 9;

        final String[] rowStrings = new String[rows];
        for (int r = 0; r < rows; r++) {
            final StringBuilder sb = new StringBuilder(9);
            for (int col = 0; col < 9; col++) {
                final int slot = r * 9 + col;
                sb.append(items.containsKey(slot) ? SLOT_CHARS[slot] : FILLER);
            }
            rowStrings[r] = sb.toString();
        }

        GuiLayout layout = new GuiLayout(rowStrings);
        layout = layout.addIngredient(FILLER, fillerElement());

        for (int slot = 0; slot < totalSlots; slot++) {
            if (items.containsKey(slot)) {
                layout = layout.addIngredient(SLOT_CHARS[slot], slotElement(slot));
            }
        }

        return layout;
    }

    /**
     * Reads a CE GUI title string from {@code config.yml} by {@code key}.
     * Returns the raw value (may be {@code null} if the key is absent).
     * Any I/O or runtime error is logged via {@link RushLogger} and {@code null} is returned.
     *
     * @param key YAML path, e.g. {@code "gui.browser.speed_merchant.title"}
     */
    static String loadCETitle(String key) {
        try {
            return YamlConfiguration.loadConfiguration(
                    new File(Main.getInstance().getCraftEngineDataFolder(), "config.yml"))
                    .getString(key);
        } catch (Exception e) {
            RushLogger.error("Failed to load CE GUI title for key '" + key + "': " + e.getMessage());
            return null;
        }
    }

    /** Returns a non-interactive CE {@code tland:empty_slot} element with its tooltip hidden. */
    static GuiElement fillerElement() {
        final Item item = itemManager().createCustomWrappedItem(Key.of("tland:empty_slot"), null);
        if (item.platformItem() instanceof ItemStack is) {
            is.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                    TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        }
        return GuiElement.constant(item, (element, click) -> click.cancel());
    }

    /**
     * Wraps the item and actions registered at {@code slot} into a CE {@link GuiElement}.
     * Right-click dispatches {@code rightClickAction} when present; all other clicks dispatch
     * {@code clickAction}. The resolved consumer is scheduled on the main thread via
     * {@link org.bukkit.scheduler.BukkitScheduler#runTask} before being invoked.
     */
    private GuiElement slotElement(int slot) {
        final Item ceItem = itemManager().wrap(items.get(slot).clone());
        final Consumer<Player> clickAction = clickActions.get(slot);
        final Consumer<Player> rightClickAction = rightClickActions.get(slot);

        return GuiElement.constant(ceItem, (element, click) -> {
            click.cancel();

            final Consumer<Player> action;
            if ("RIGHT".equals(click.type()) && rightClickAction != null) {
                action = rightClickAction;
            } else {
                action = clickAction;
            }

            if (action == null)
                return;

            final Player bukkit = (Player) click.clicker().platformPlayer();
            Bukkit.getScheduler().runTask(Main.getInstance(), () -> action.accept(bukkit));
        });
    }

    static ItemManager itemManager() {
        return Main.getInstance().getCraftEngineItemManager();
    }
}
