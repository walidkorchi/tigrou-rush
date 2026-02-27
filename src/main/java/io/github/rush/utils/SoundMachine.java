package io.github.rush.utils;

import io.github.rush.Main;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

public class SoundMachine {

    public static Sound get(String v18, String v19) {
        Sound finalSound = null;

        try {
            finalSound = Registry.SOUND_EVENT.get(NamespacedKey.minecraft(v18.toLowerCase()));
        } catch (Exception ex) {
            Main.getInstance().getLogger().severe(ex.getMessage());
        }

        return finalSound;
    }

}
