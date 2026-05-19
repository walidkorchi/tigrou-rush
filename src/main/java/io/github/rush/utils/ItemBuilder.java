package io.github.rush.utils;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;
    private List<Component> loreLines = null;

    private ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    public ItemBuilder name(String name) {
        meta.displayName(Component.text(name));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        this.loreLines = Arrays.stream(lines).<Component>map(Component::text).toList();
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        this.loreLines = lines.stream().<Component>map(Component::text).toList();
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
