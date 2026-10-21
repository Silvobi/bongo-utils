package pl.bongo.bongoutils;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;

/** Builds literal text and HTTP(S) click spans; never fetches URLs or parses user-supplied markup. */
public final class MailLinks {
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"\\p{Cntrl}]+", Pattern.CASE_INSENSITIVE);
    private MailLinks() {}
    public static Component render(String text) {
        MutableComponent result = Component.empty();
        var matcher = URL.matcher(text); int end = 0;
        while (matcher.find()) {
            String url = matcher.group();
            while (!url.isEmpty() && trailing(url)) url = url.substring(0, url.length() - 1);
            try { MailAttachments.validate(List.of(url)); }
            catch (IllegalArgumentException e) { continue; }
            result.append(Component.literal(text.substring(end, matcher.start())));
            final String link = url;
            result.append(Component.literal(link).withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(link)))));
            end = matcher.start() + url.length();
        }
        result.append(Component.literal(text.substring(end)));
        return result;
    }
    private static boolean trailing(String url) {
        char c = url.charAt(url.length() - 1);
        if (".,;:!?".indexOf(c) >= 0) return true;
        char open = switch (c) { case ')' -> '('; case ']' -> '['; case '}' -> '{'; default -> 0; };
        return open != 0 && url.chars().filter(n -> n == c).count() > url.chars().filter(n -> n == open).count();
    }
}
