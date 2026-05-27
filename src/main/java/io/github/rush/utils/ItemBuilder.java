package io.github.rush.utils;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

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

    public ItemBuilder name(Component name) {
        meta.displayName(name);
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

    public ItemBuilder lore(Component... lines) {
        this.loreLines = List.of(lines);
        return this;
    }

    public ItemBuilder color(Color color) {
        if (meta instanceof LeatherArmorMeta lam) {
            lam.setColor(color);
        }
        return this;
    }

    public ItemBuilder addEnchant(Enchantment enchantment, int level, boolean ignoreLevelRestriction) {
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
