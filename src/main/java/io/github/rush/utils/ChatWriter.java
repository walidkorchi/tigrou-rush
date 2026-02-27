package io.github.rush.utils;

import io.github.rush.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class ChatWriter {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    public static String pluginMessage(String str) {
        final String prefix = Main.getInstance().getConfig().getString("chat-prefix",
                "[TigrouRush]");
        final Component prefixComponent = SERIALIZER.deserialize(prefix);
        final Component message = prefixComponent
                .append(Component.text(" ").append(Component.text(str, NamedTextColor.WHITE)));

        return SERIALIZER.serialize(message);
    }

}
