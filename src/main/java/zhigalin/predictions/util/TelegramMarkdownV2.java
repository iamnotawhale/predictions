package zhigalin.predictions.util;

import java.util.List;

public final class TelegramMarkdownV2 {

    private static final List<String> ESCAPED = List.of(
            "_", "*", "[", "]", "(", ")", "~", ">", "#", "+", "-", "=", "|", "{", "}", ".", "!"
    );

    private TelegramMarkdownV2() {}

    public static String escape(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        for (String s : ESCAPED) {
            if (result.contains(s)) {
                result = result.replace(s, "\\" + s);
            }
        }
        return result;
    }
}
