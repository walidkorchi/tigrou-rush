package io.github.rush.menus;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/**
 * Class representing a menu in a chest with clickable items.
 */
public class GUI implements InventoryHolder {

    /**
     * Returns the GUI currently shown to a player if it has the required title.
     *
     * @param player The player who clicked
     * @param title  The title of the requested GUI
     *
     * @return The current GUI if titles match, null otherwise.
     */
    public static GUI getIfOpen(Player player, String title) {
        return player.getOpenInventory().getTopInventory().getHolder() instanceof GUI menu && menu.title.equals(title)
                ? menu
                : null;
    }

    /**
     * The Bukkit inventory representing the chest.
     */
    private final Inventory inventory;

    /**
     * The title of the GUI.
     */
    private final String title;

    /**
     * The actions to apply when a player clicks on a specific slot with any button.
     */
    private final Map<Integer, Consumer<Player>> clickActions;

    /**
     * The actions to apply when a player clicks on a specific slot with the right
     * button.
     */
    private final Map<Integer, Consumer<Player>> rightClickActions;

    /**
     * The predicate testing whether to allow a click depending on the slot.
     */
    private final IntPredicate cancelClicks;

    /**
     * Creates a new GUI with no clicks allowed.
     *
     * @param title The title of the GUI
     * @param rows  The number of rows
     */
    public GUI(String title, int rows) {
        this(title, rows, null);
    }

    /**
     * Creates a new GUI.
     *
     * @param title        The title of the GUI
     * @param rows         The number of rows
     * @param cancelClicks The click policy with respect to the clicked slot
     */
    GUI(String title, int rows, IntPredicate cancelClicks) {
        this.inventory = Bukkit.createInventory(this, rows * 9, Component.text(title));
        this.title = title;
        this.clickActions = new HashMap<>();
        this.rightClickActions = new HashMap<>();
        this.cancelClicks = cancelClicks;
    }

    /**
     * Adds an item to the GUI with no action.
     *
     * @param slot The slot to put the item at
     * @param item The item to add
     *
     * @return The current GUI
     */
    public GUI addItem(int slot, ItemStack item) {
        return addItem(slot, item, null, null);
    }

    /**
     * Adds an item to the GUI with a specific action.
     *
     * @param slot   The slot to put the item at
     * @param item   The item to add
     * @param action The action to execute on click
     *
     * @return The current GUI
     */
    public GUI addItem(int slot, ItemStack item, Consumer<Player> action) {
        return addItem(slot, item, action, null);
    }

    /**
     * Adds an item to the GUI with a specific action.
     *
     * @param slot             The slot to put the item at
     * @param item             The item to add
     * @param action           The action to execute on click with any button
     * @param rightClickAction The action to execute on right click
     *
     * @return The current GUI
     */
    public GUI addItem(int slot, ItemStack item, Consumer<Player> action, Consumer<Player> rightClickAction) {
        inventory.setItem(slot, item);
        if (action != null)
            clickActions.put(slot, action);
        if (rightClickAction != null)
            rightClickActions.put(slot, rightClickAction);
        return this;
    }

    /**
     * Returns the {@link Inventory} representing the chest.
     *
     * @return The inventory
     */
    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /**
     * Returns the title of the GUI.
     *
     * @return The title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Executes the action corresponding to the clicked slot.
     *
     * @param player The player who clicked the item
     * @param slot   The clicked slot
     * @param type   The type of click (left or right)
     *
     * @return Whether to allow the click interaction
     */
    boolean onClick(Player player, int slot, ClickType type) {
        if (type == ClickType.RIGHT) {
            // Prioritize right click action over generic when it exists
            Consumer<Player> action = rightClickActions.get(slot);
            if (action != null) {
                action.accept(player);
                return true;
            }
        }

        // No right click action found, execute generic
        Consumer<Player> action = clickActions.get(slot);
        if (action != null) {
            action.accept(player);
            return true;
        }

        return cancelClicks == null || cancelClicks.test(slot);
    }

    /**
     * Displays the current GUI to a given player.
     *
     * @param player The player to show the GUI to
     */
    public void openGUI(Player player) {
        player.openInventory(inventory);
    }

    /**
     * Removes the actions bound to an item.
     *
     * @param slot The slot where the item is
     */
    public void removeActions(int slot) {
        clickActions.remove(slot);
        rightClickActions.remove(slot);
    }

    /**
     * Changes an item within the GUI, without modifying any action
     * (unless the item is null, in that case actions are removed).
     *
     * @param slot The slot to put the item at
     * @param item The item to insert
     */
    public void setItem(int slot, ItemStack item) {
        inventory.setItem(slot, item);
        if (item == null)
            removeActions(slot);
    }

    /**
     * Changes the lore of an item within the GUI.
     *
     * @param slot The slot where the item is
     * @param lore The list of lines to apply
     */
    public void setItemLore(int slot, List<String> lore) {
        inventory.getItem(slot).setData(DataComponentTypes.LORE,
                ItemLore.lore(lore.stream().map(Component::text).toList()));
    }

    /**
     * Changes a line of the lore of an item within the GUI.
     *
     * @param slot The slot where the item is
     * @param line The line to change
     * @param text The text to apply
     */
    public void setItemLore(int slot, int line, String text) {
        ItemStack item = inventory.getItem(slot);
        List<Component> lines = new ArrayList<>(item.getData(DataComponentTypes.LORE).lines());
        lines.set(line, Component.text(text));
        item.setData(DataComponentTypes.LORE, ItemLore.lore(lines));
    }
}
