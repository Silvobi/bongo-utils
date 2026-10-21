package pl.bongo.bongoutils;

import java.util.*;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MailLinksTest {
    @Test void urlsKeepTextAndSignedQueriesWhilePunctuationStaysOutsideLinks() {
        String text="Zasady (https://example.com/a_(b)?token=a%2Fb&x=1). Dalej https://example.org/x!";
        Component rendered=MailLinks.render(text);
        assertEquals(text,rendered.getString());
        var links=rendered.getSiblings().stream().filter(c->c.getStyle().getClickEvent() instanceof ClickEvent.OpenUrl).toList();
        assertEquals(2,links.size());
        assertEquals("https://example.com/a_(b)?token=a%2Fb&x=1",((ClickEvent.OpenUrl)links.getFirst().getStyle().getClickEvent()).uri().toString());
        assertEquals("https://example.org/x",((ClickEvent.OpenUrl)links.getLast().getStyle().getClickEvent()).uri().toString());
        assertTrue(links.getFirst().getStyle().isUnderlined());
    }
    @Test void invalidSchemesCredentialsAndMarkupRemainLiteral() {
        String text="javascript:alert(1) file:///tmp/a https://user:pass@example.com/x https://bad_host/x {\"clickEvent\":{}}";
        var rendered=MailLinks.render(text);
        assertEquals(text,rendered.getString());
        assertTrue(rendered.getSiblings().stream().noneMatch(c->c.getStyle().getClickEvent()!=null));
    }
}
