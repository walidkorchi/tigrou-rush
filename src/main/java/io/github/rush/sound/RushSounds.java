package io.github.rush.sound;

import io.github.rush.Main;
import io.github.rush.storage.PlayerSettingsManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import org.bukkit.entity.Player;

public enum RushSounds {
    LOBBY_MUSIC("tland:music.global.lobby", Sound.Source.MUSIC, true),
    OVERTIME_INTRO("tland:music.global.overtime_intro_music", Sound.Source.MUSIC, true),
    OVERTIME_LOOP("tland:music.global.overtime_loop_music", Sound.Source.MUSIC, true),
    GAME_END_MUSIC("tland:music.global.gameendmusic", Sound.Source.MUSIC, true),
    WIN_CELEBRATE("tland:game.global.win_celebrate", Sound.Source.MUSIC, false),
    PLAYER_DIED("tland:game.global.player_died", Sound.Source.PLAYER, false),
    TEAM_ELIMINATED("tland:game.global.team_eliminated", Sound.Source.MASTER, false),
    TEAMMATE_ELIMINATED("tland:game.global.teammate_eliminated", Sound.Source.MASTER, false),
    CLICK_ITEM("tland:gui.global.click_item", Sound.Source.MASTER, false),
    COUNTDOWN("tland:music.global.countdown", Sound.Source.MUSIC, false),
    BORDER_SHRINKING("tland:game.global.border_shrinking", Sound.Source.MASTER, false);

    private final Key key;
    private final Sound.Source source;
    private final boolean isMusic;

    RushSounds(String key, Sound.Source source, boolean isMusic) {
        this.key = Key.key(key);
        this.source = source;
        this.isMusic = isMusic;
    }

    public Key key() {
        return key;
    }

    public Sound.Source source() {
        return source;
    }

    public boolean isMusic() {
        return isMusic;
    }

    public void play(Player player) {
        play(player, 1.0f, 1.0f);
    }

    public void play(Player player, float volume, float pitch) {
        if (isMusic) {
            PlayerSettingsManager settingsManager = Main.getInstance().getPlayerSettingsManager();
            if (settingsManager != null && !settingsManager.isMusicEnabled(player.getUniqueId())) {
                return;
            }
        }
        player.playSound(Sound.sound(key, source, volume, pitch));
    }

    public void stop(Player player) {
        player.stopSound(SoundStop.named(key));
    }
}
