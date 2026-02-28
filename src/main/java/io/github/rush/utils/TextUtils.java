package io.github.rush.utils;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class TextUtils {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    public static String convertHexToLegacy(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        return SERIALIZER.serialize(SERIALIZER.deserialize(text));
    }
}
