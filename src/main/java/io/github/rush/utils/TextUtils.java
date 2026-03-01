package io.github.rush.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern AMPERSAND_PATTERN = Pattern.compile("(?i)&([0-9a-fk-orx])");

    public static String convertHexToLegacy(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        text = transformHexToLegacy(text);
        text = AMPERSAND_PATTERN.matcher(text).replaceAll("§$1");

        return text;
    }

    private static String transformHexToLegacy(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String hex = matcher.group(1);
            String legacy = "§x§" + hex.charAt(0) + "§" + hex.charAt(1) +
                    "§" + hex.charAt(2) + "§" + hex.charAt(3) +
                    "§" + hex.charAt(4) + "§" + hex.charAt(5);
            matcher.appendReplacement(result, legacy);
        }

        matcher.appendTail(result);

        return result.toString();
    }
}
