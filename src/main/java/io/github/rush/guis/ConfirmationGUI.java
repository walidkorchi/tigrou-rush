package io.github.rush.guis;

import io.github.rush.Main;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.context.PlayerOptionalContext;
import net.momirealms.craftengine.core.plugin.gui.BasicGui;
import net.momirealms.craftengine.core.plugin.gui.GuiElement;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.util.AdventureHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * CE-driven confirmation modal, analogous to {@link GUI} but backed by
 * CraftEngine's PagedGui so it carries the custom texture defined at
 * {@code gui.browser.confirm_modal_title} in CE's config.
 *
 * <p>
 * Usage:
 *
 * <pre>
 *   ConfirmationGUI.of(previewItem)
 *       .confirm(confirmItem, p -> { ... })
 *       .cancel(cancelItem,  p -> { ... })
 *       .open(player);
 * </pre>
 *
 * <p>
 * The wrapper auto-closes the inventory and dispatches both actions on the
 * main thread before handing control to the caller's {@link Consumer}.
 */
public final class ConfirmationGUI {

    private static String title;

    private final ItemStack previewItem;
    private ItemStack confirmItem;
    private Consumer<Player> confirmAction;
    private ItemStack cancelItem;
    private Consumer<Player> cancelAction;

    private ConfirmationGUI(ItemStack previewItem) {
        this.previewItem = previewItem;
    }

    public static ConfirmationGUI of(ItemStack previewItem) {
        return new ConfirmationGUI(previewItem);
    }

    public ConfirmationGUI confirm(ItemStack item, Consumer<Player> action) {
        this.confirmItem = item;
        this.confirmAction = action;
        return this;
    }

    public ConfirmationGUI cancel(ItemStack item, Consumer<Player> action) {
        this.cancelItem = item;
        this.cancelAction = action;
        return this;
    }

    public void open(Player player) {
        if (title == null)
            title = GUI.loadCETitle("gui.browser.confirmation_modal_title.title");

        final net.momirealms.craftengine.core.entity.player.Player craftPlayer = Main.getInstance()
                .adaptCraftPlayer(player);

        final GuiLayout layout = new GuiLayout("CCC_P_XXX")
                .addIngredient('_', GUI.fillerElement())
                .addIngredient('C', actionElement(confirmItem, confirmAction))
                .addIngredient('P', displayElement(previewItem))
                .addIngredient('X', actionElement(cancelItem, cancelAction));

        BasicGui.builder()
                .layout(layout)
                .inventoryClickConsumer(c -> {
                    final String type = c.type();
                    if ("SHIFT_LEFT".equals(type) || "SHIFT_RIGHT".equals(type)
                            || "DOUBLE_CLICK".equals(type)) {
                        c.cancel();
                    }
                })
                .build()
                .title(AdventureHelper.miniMessage().deserialize(title,
                        PlayerOptionalContext.of(craftPlayer).tagResolvers()))
                .refresh()
                .open(craftPlayer);
    }

    private GuiElement displayElement(ItemStack stack) {
        return GuiElement.constant(
                GUI.itemManager().wrap(stack.clone()),
                (element, click) -> click.cancel());
    }

    /**
     * Wraps a Bukkit {@link ItemStack} + {@link Consumer} into a CE
     * {@link GuiElement}.
     * Cancels the click, schedules {@code action} on the main thread, and
     * closes the inventory before handing off.
     */
    private GuiElement actionElement(ItemStack stack, Consumer<Player> action) {
        return GuiElement.constant(GUI.itemManager().wrap(stack.clone()), (element, click) -> {
            click.cancel();

            if (action == null)
                return;

            final Player bukkit = (Player) click.clicker().platformPlayer();

            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                bukkit.closeInventory();
                action.accept(bukkit);
            });
        });
    }

}
