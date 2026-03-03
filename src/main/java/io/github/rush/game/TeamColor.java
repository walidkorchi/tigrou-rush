package io.github.rush.game;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;

public enum TeamColor {
    GREEN(Color.fromRGB(0, 170, 0), NamedTextColor.DARK_GREEN, DyeColor.GREEN, 1),
    RED(Color.fromRGB(255, 85, 85), NamedTextColor.RED, DyeColor.RED, 2),
    BLUE(Color.fromRGB(0, 0, 170), NamedTextColor.DARK_BLUE, DyeColor.BLUE, 3),
    YELLOW(Color.fromRGB(255, 255, 85), NamedTextColor.YELLOW, DyeColor.YELLOW, 4),
    AQUA(Color.fromRGB(85, 255, 255), NamedTextColor.AQUA, DyeColor.CYAN, 5),
    BLACK(Color.BLACK, NamedTextColor.BLACK, DyeColor.BLACK, 6),
    GOLD(Color.fromRGB(255, 170, 0), NamedTextColor.GOLD, DyeColor.ORANGE, 7),
    DARK_BLUE(Color.fromRGB(0, 0, 170), NamedTextColor.DARK_BLUE, DyeColor.BLUE, 8),
    DARK_GREEN(Color.fromRGB(0, 170, 0), NamedTextColor.DARK_GREEN, DyeColor.GREEN, 9),
    DARK_RED(Color.fromRGB(170, 0, 0), NamedTextColor.DARK_RED, DyeColor.BROWN, 10),
    DARK_PURPLE(Color.fromRGB(170, 0, 170), NamedTextColor.DARK_PURPLE, DyeColor.MAGENTA, 11),
    GRAY(Color.fromRGB(170, 170, 170), NamedTextColor.GRAY, DyeColor.LIGHT_GRAY, 12),
    DARK_GRAY(Color.fromRGB(85, 85, 85), NamedTextColor.DARK_GRAY, DyeColor.GRAY, 13),
    LIGHT_PURPLE(Color.fromRGB(255, 85, 255), NamedTextColor.LIGHT_PURPLE, DyeColor.PINK, 14),
    WHITE(Color.WHITE, NamedTextColor.WHITE, DyeColor.WHITE, 15);

    private NamedTextColor textColor;
    private Color color;
    private DyeColor dyeColor;
    private int islandNumber;

    private TeamColor(Color color, NamedTextColor textColor, DyeColor dye, int islandNumber) {
        this.textColor = textColor;
        this.color = color;
        this.dyeColor = dye;
        this.islandNumber = islandNumber;
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

    public int getIslandNumber() {
        return this.islandNumber;
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

    public String getSectionColor() {
        return switch (this) {
            case GREEN, DARK_GREEN -> "§2";
            case RED -> "§c";
            case BLUE, DARK_BLUE -> "§1";
            case YELLOW -> "§e";
            case AQUA -> "§b";
            case BLACK -> "§0";
            case GOLD -> "§6";
            case DARK_RED -> "§4";
            case DARK_PURPLE -> "§5";
            case GRAY -> "§7";
            case DARK_GRAY -> "§8";
            case LIGHT_PURPLE -> "§d";
            case WHITE -> "§f";
        };
    }
}
