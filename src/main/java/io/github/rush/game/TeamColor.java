package io.github.rush.game;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;

public enum TeamColor {
    GREEN(Color.fromRGB(85, 255, 85), NamedTextColor.GREEN, DyeColor.LIME),
    RED(Color.fromRGB(255, 85, 85), NamedTextColor.RED, DyeColor.RED),
    BLUE(Color.fromRGB(85, 85, 255), NamedTextColor.BLUE, DyeColor.LIGHT_BLUE),
    YELLOW(Color.fromRGB(255, 255, 85), NamedTextColor.YELLOW, DyeColor.YELLOW),
    AQUA(Color.fromRGB(85, 255, 255), NamedTextColor.AQUA, DyeColor.CYAN),
    BLACK(Color.BLACK, NamedTextColor.BLACK, DyeColor.BLACK),
    GOLD(Color.fromRGB(255, 170, 0), NamedTextColor.GOLD, DyeColor.ORANGE),
    DARK_BLUE(Color.fromRGB(0, 0, 170), NamedTextColor.DARK_BLUE, DyeColor.BLUE),
    DARK_GREEN(Color.fromRGB(0, 170, 0), NamedTextColor.DARK_GREEN, DyeColor.GREEN),
    DARK_RED(Color.fromRGB(170, 0, 0), NamedTextColor.DARK_RED, DyeColor.BROWN),
    DARK_PURPLE(Color.fromRGB(170, 0, 170), NamedTextColor.DARK_PURPLE, DyeColor.MAGENTA),
    GRAY(Color.fromRGB(170, 170, 170), NamedTextColor.GRAY, DyeColor.LIGHT_GRAY),
    DARK_GRAY(Color.fromRGB(85, 85, 85), NamedTextColor.DARK_GRAY, DyeColor.GRAY),
    LIGHT_PURPLE(Color.fromRGB(255, 85, 255), NamedTextColor.LIGHT_PURPLE, DyeColor.PINK),
    WHITE(Color.WHITE, NamedTextColor.WHITE, DyeColor.WHITE);

    private NamedTextColor textColor;
    private Color color;
    private DyeColor dyeColor;

    private TeamColor(Color color, NamedTextColor textColor, DyeColor dye) {
        this.textColor = textColor;
        this.color = color;
        this.dyeColor = dye;
    }

    public NamedTextColor getTextColor() {
        return this.textColor;
    }

    public Color getColor() {
        return this.color;
    }

    public DyeColor getDyeColor() {
        return this.dyeColor;
    }
}
