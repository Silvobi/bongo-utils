package pl.bongo.bongoutils;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MailAttachmentsTest {
    @Test void acceptsHttpAndHttpsPreservesQueriesAndDeduplicatesBlankPaddedLines() {
        assertEquals(List.of(), MailAttachments.parse(" \n \r\n"));
        assertEquals(List.of("https://example.com/a.png?token=a%2Fb&expires=123#part", "http://example.org/file"),
                MailAttachments.parse(" https://example.com/a.png?token=a%2Fb&expires=123#part \n\nhttp://example.org/file\nhttp://example.org/file"));
    }
    @Test void rejectsInvalidProtocolsCredentialsMalformedUrlsAndExceededLimits() {
        for (String url : List.of("javascript:alert(1)", "file:///C:/test", "data:text/plain,test", "ftp://example.com/a",
                "//example.com/a", "www.example.com", "https:///missing", "https://user:pass@example.com", "https://example.com/a b",
                "https://example.com:99999/a", "https://example.com/\rforged"))
            assertThrows(IllegalArgumentException.class, () -> MailAttachments.validate(List.of(url)), url);
        assertThrows(IllegalArgumentException.class, () -> MailAttachments.parse(String.join("\n", java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> "https://example.com/" + i).toList())));
        assertThrows(IllegalArgumentException.class, () -> MailAttachments.parse("https://example.com/" + "x".repeat(2048)));
        String prefix = "https://example.com/";
        String longest = prefix + "x".repeat(MailAttachments.MAX_URL - prefix.length());
        assertEquals(List.of(longest), MailAttachments.parse(longest));
    }
}
