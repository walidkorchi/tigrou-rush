package io.github.rush.utils;

import io.github.rush.Main;
import net.momirealms.craftengine.bukkit.util.SoundUtils;
import net.momirealms.craftengine.core.util.Key;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public final class CustomSoundRegistrar {

    private static final List<String> SOUNDS = List.of(
            "tland:music.global.overtime_intro_music",
            "tland:music.global.overtime_loop_music",
            "tland:music.global.gameendmusic",
            "tland:game.global.win_celebrate",
            "tland:music.global.lobby");

    private CustomSoundRegistrar() {
    }

    public static void register(Main plugin) {
        try {
            Class<?> builtInRegistries = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Field soundEventField = builtInRegistries.getDeclaredField("SOUND_EVENT");
            Object soundEventRegistry = soundEventField.get(null);

            Class<?> mappedRegistryClass = Class.forName("net.minecraft.core.MappedRegistry");
            Field frozenField = mappedRegistryClass.getDeclaredField("frozen");
            frozenField.setAccessible(true);
            frozenField.setBoolean(soundEventRegistry, false);

            Class<?> registryClass = Class.forName("net.minecraft.core.Registry");
            Method registerMethod = registryClass.getMethod("register",
                    registryClass, String.class, Object.class);

            for (String soundKey : SOUNDS) {
                Key ceKey = Key.of(soundKey);
                Object soundEvent = SoundUtils.createSoundEvent(ceKey);
                registerMethod.invoke(null, soundEventRegistry, soundKey, soundEvent);
                plugin.getLogger().info(i18n.log("internal.sound.registered", soundKey));
            }

            frozenField.setBoolean(soundEventRegistry, true);
            plugin.getLogger().info(i18n.log("internal.sound.all_registered", SOUNDS.size()));
        } catch (InvocationTargetException e) {
            plugin.getLogger().warning(i18n.log("internal.sound.register_failed",
                    e.getCause().getClass().getName(), e.getCause().getMessage()));
            for (StackTraceElement ste : e.getCause().getStackTrace()) {
                plugin.getLogger().warning("  at " + ste.toString());
            }
        } catch (Exception e) {
            plugin.getLogger().warning(i18n.log("internal.sound.register_failed",
                    e.getClass().getName(), e.getMessage()));
            for (StackTraceElement ste : e.getStackTrace()) {
                plugin.getLogger().warning("  at " + ste.toString());
            }
        }
    }
}
