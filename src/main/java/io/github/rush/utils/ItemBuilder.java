package io.github.rush.utils;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public final class ItemBuilder<M extends ItemMeta> {

    private final ItemStack item;
    private final M meta;
    private List<Component> loreLines = null;

    @SuppressWarnings("unchecked")
    private ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = (M) item.getItemMeta();
    }

    @SuppressWarnings("unchecked")
    private ItemBuilder(ItemStack item) {
        this.item = item;
        this.meta = (M) item.getItemMeta();
    }

    public static <M extends ItemMeta> ItemBuilder<M> of(Material material) {
        return new ItemBuilder<>(material);
    }

    public static <M extends ItemMeta> ItemBuilder<M> of(ItemStack item) {
        return new ItemBuilder<>(item);
    }

    public static <M extends ItemMeta> ItemBuilder<M> of(Material material, int amount) {
        return ItemBuilder.<M>of(material).amount(amount);
    }

    public ItemBuilder<M> amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder<M> meta(Consumer<M> action) {
        action.accept(meta);
        return this;
    }

    public ItemBuilder<M> name(String name) {
        meta.displayName(Component.text(name));
        return this;
    }

    public ItemBuilder<M> name(Component name) {
        meta.displayName(name);
        return this;
    }

    public ItemBuilder<M> lore(String... lines) {
        this.loreLines = Arrays.stream(lines).<Component>map(Component::text).toList();
        return this;
    }

    public ItemBuilder<M> lore(List<String> lines) {
        this.loreLines = lines.stream().<Component>map(Component::text).toList();
        return this;
    }

    public ItemBuilder<M> lore(Component... lines) {
        this.loreLines = List.of(lines);
        return this;
    }

    public ItemBuilder<M> addEnchant(Enchantment enchantment, int level, boolean ignoreLevelRestriction) {
        meta.addEnchant(enchantment, level, ignoreLevelRestriction);
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        if (loreLines != null) {
            item.setData(DataComponentTypes.LORE, ItemLore.lore(loreLines));
        }
        return item;
    }
}
