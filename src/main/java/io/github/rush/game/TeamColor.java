package io.github.rush.game;

import lombok.Getter;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;

import java.util.List;

public enum TeamColor {
    // Order matches Minecraft's DyeColor enum.
    // Color is derived from DyeColor.getFireworkColor(), which holds the exact wool block colour.
    WHITE     (NamedTextColor.WHITE,        DyeColor.WHITE,      1),
    ORANGE    (NamedTextColor.GOLD,         DyeColor.ORANGE,     2),
    MAGENTA   (NamedTextColor.LIGHT_PURPLE, DyeColor.MAGENTA,    3),
    LIGHT_BLUE(NamedTextColor.AQUA,         DyeColor.LIGHT_BLUE, 4),
    YELLOW    (NamedTextColor.YELLOW,       DyeColor.YELLOW,     5),
    LIME      (NamedTextColor.GREEN,        DyeColor.LIME,       6),
    PINK      (NamedTextColor.LIGHT_PURPLE, DyeColor.PINK,       7),
    GRAY      (NamedTextColor.GRAY,         DyeColor.GRAY,       8),
    LIGHT_GRAY(NamedTextColor.GRAY,         DyeColor.LIGHT_GRAY, 9),
    CYAN      (NamedTextColor.DARK_AQUA,    DyeColor.CYAN,       10),
    PURPLE    (NamedTextColor.DARK_PURPLE,  DyeColor.PURPLE,     11),
    BLUE      (NamedTextColor.DARK_BLUE,    DyeColor.BLUE,       12),
    BROWN     (NamedTextColor.DARK_RED,     DyeColor.BROWN,      13),
    GREEN     (NamedTextColor.DARK_GREEN,   DyeColor.GREEN,      14),
    RED       (NamedTextColor.RED,          DyeColor.RED,        15),
    BLACK     (NamedTextColor.BLACK,        DyeColor.BLACK,      16);

    @Getter private final NamedTextColor textColor;
    @Getter private final Color color;
    @Getter private final DyeColor dyeColor;
    @Getter private final int islandNumber;
    @Getter private final String sectionColor;

    TeamColor(NamedTextColor textColor, DyeColor dye, int islandNumber) {
        this.textColor = textColor;
        this.dyeColor = dye;
        this.color = dye.getFireworkColor();
        this.islandNumber = islandNumber;
        int index = List.copyOf(NamedTextColor.NAMES.values()).indexOf(textColor);
        this.sectionColor = "§" + (index >= 0 ? Integer.toHexString(index) : "f");
    }

    public static TeamColor[] firstN(int n) {
        TeamColor[] values = TeamColor.values();
        int length = Math.min(n, values.length);
        TeamColor[] result = new TeamColor[length];
        System.arraycopy(values, 0, result, 0, length);
        return result;
    }

    public String getChatColor() {
        return this.textColor.toString();
    }

    public DyeColor getContrastDyeColor() {
        final Color c = this.dyeColor.getColor();
        final double brightness = c.getRed() * 0.299 + c.getGreen() * 0.587 + c.getBlue() * 0.114;
        return brightness > 128 ? DyeColor.BLACK : DyeColor.WHITE;
    }
}
