package io.github.rush.game;

public enum MapType {
    NORMAL,
    OLD_SCHOOL,
    NETHER,
    END,
    AQUAMARINE,
    SUMMER,
    CHERRY,
    WINTER;

    public String schematicName() {
        return "rush_island-" + name().toLowerCase() + ".schem";
    }

    public String displayName() {
        return name().charAt(0) + name().substring(1).toLowerCase().replace('_', ' ');
    }
}
