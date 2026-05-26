package io.github.rush.utils;

import com.google.gson.Gson;
import io.github.rush.Main;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationStore;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class i18n {

    private static Map<String, String> entries;

    private i18n() {}

    public static void register(Main plugin) {
        final Gson gson = new Gson();

        try (InputStream in = plugin.getClass().getClassLoader().getResourceAsStream("translations/fr_fr.json")) {
            if (in == null) {
                plugin.getLogger().warning("translations/fr_fr.json not found in resources");
                return;
            }

            @SuppressWarnings("unchecked")
            final Map<String, String> loaded = gson.fromJson(new InputStreamReader(in), Map.class);
            final TranslationStore<MessageFormat> store = TranslationStore.messageFormat(Key.key("rush", "translations"));

            store.defaultLocale(Locale.FRANCE);

            for (Map.Entry<String, String> entry : loaded.entrySet()) {
                store.register(entry.getKey(), Locale.FRANCE, new MessageFormat(entry.getValue(), Locale.FRANCE));
            }

            GlobalTranslator.translator().addSource(store);
            entries = new HashMap<>(loaded);

            plugin.getLogger().info("Loaded " + entries.size() + " translations (fr_FR)");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load translations: " + e.getMessage());
        }
    }

    /** Returns a {@link Component#text()} with the resolved translation, for item names/lores. */
    public static Component txt(String key, Object... args) {
        final String resolved = resolve(key, args);
        return resolved != null ? Component.text(resolved) : Component.translatable(key);
    }

    /** Returns the raw translation string, or {@code null} if the key is not found. */
    public static String raw(String key) {
        return entries != null ? entries.get(key) : null;
    }

    private static String resolve(String key, Object... args) {
        String pattern = entries != null ? entries.get(key) : null;
        if (pattern == null) return null;
        if (args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                pattern = pattern.replace("{" + i + "}", String.valueOf(args[i]));
            }
        }
        return pattern;
    }
}
