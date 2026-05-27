package io.github.rush.utils;

import org.bukkit.Bukkit;

public final class RushLogger {

    private static final String RESET  = "[0m";
    private static final String ORANGE = "[38;5;214m";
    private static final String CYAN   = "[96m";
    private static final String GRAY   = "[90m";
    private static final String YELLOW = "[33m";
    private static final String RED    = "[91m";

    private static final String PREFIX = ORANGE + "[Rush]" + RESET;

    private RushLogger() {}

    public static void info(String message) {
        Bukkit.getLogger().info(PREFIX + " " + CYAN + "[INFO]" + RESET + " " + message);
    }

    public static void debug(String message) {
        Bukkit.getLogger().info(PREFIX + " " + GRAY + "[DEBUG]" + RESET + " " + message);
    }

    public static void warn(String message) {
        Bukkit.getLogger().warning(PREFIX + " " + YELLOW + "[WARN]" + RESET + " " + message);
    }

    public static void error(String message) {
        Bukkit.getLogger().severe(PREFIX + " " + RED + "[ERROR]" + RESET + " " + message);
    }
}
