package pl.bongo.bongoutils;

import java.net.URI;
import java.util.*;

/** Links are stored and opened by the client; the server never fetches their contents. */
public final class MailAttachments {
    public static final int MAX_COUNT = 5, MAX_URL = 2048, MAX_INPUT = MAX_COUNT * MAX_URL + MAX_COUNT - 1;
    private MailAttachments() {}
    public static List<String> parse(String input) {
        if (input.length() > MAX_INPUT) throw new IllegalArgumentException("Too many attachment characters");
        return validate(input.lines().map(String::strip).filter(s -> !s.isEmpty()).distinct().toList());
    }
    public static List<String> validate(List<String> links) {
        if (links == null || links.size() > MAX_COUNT) throw new IllegalArgumentException("Too many attachments");
        for (String link : links) {
            if (link == null || link.isEmpty() || link.length() > MAX_URL) throw new IllegalArgumentException("Invalid attachment length");
            URI uri = URI.create(link);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getPort() > 65535)
                throw new IllegalArgumentException("Expected an absolute HTTP(S) URL");
        }
        return List.copyOf(links);
    }
}
