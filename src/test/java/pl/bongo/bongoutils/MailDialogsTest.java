package pl.bongo.bongoutils;

import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.input.TextInput;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class MailDialogsTest {
    @BeforeAll static void boot() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void notificationsHaveExactLocalizedTextAndOnlyTheRedUnderlinedCountIsClickable() {
        for (String locale : List.of("pl_pl", "en_us", "de_de")) {
            var component = MailUi.notification(locale, 3);
            assertEquals(Lang.text(locale, "mail_notification") + "3", component.getString());
            assertEquals(0xFFAA00, component.getStyle().getColor().getValue());
            assertNull(component.getStyle().getClickEvent());
            var count = component.getSiblings().getFirst();
            assertEquals(0xFF5555, count.getStyle().getColor().getValue());
            assertTrue(count.getStyle().isUnderlined());
            assertEquals("/mail read new", ((ClickEvent.RunCommand) count.getStyle().getClickEvent()).command());
        }
    }
    @Test void composeUsesMultilineTextAndOnlyOperatorsGetAttachmentInput() {
        for (String locale : List.of("pl_pl", "en_us")) {
            var ordinary = (MultiActionDialog) MailDialogs.compose(locale, "token", "Player", null, "draft", "").value();
            assertEquals(1, ordinary.common().inputs().size());
            var text = (TextInput) ordinary.common().inputs().getFirst().control();
            assertTrue(text.multiline().isPresent()); assertEquals("draft", text.initial());
            assertEquals(MailStore.MAX_BODY, text.maxLength());
            assertEquals(Lang.text(locale, "mail_send"), ordinary.actions().getFirst().button().label().getString());
            assertEquals(Lang.text(locale, "cancel_button"), ordinary.exitAction().orElseThrow().button().label().getString());
            var admin = (MultiActionDialog) MailDialogs.compose(locale, "token", "Player", "3 × Diamond", "", "").value();
            assertEquals(2, admin.common().inputs().size());
            var empty = (MultiActionDialog) MailDialogs.list(locale, "token", true, 0, 1, List.of()).value();
            assertFalse(empty.actions().isEmpty());
        }
    }
}
