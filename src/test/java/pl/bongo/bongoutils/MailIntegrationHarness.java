package pl.bongo.bongoutils;

import java.net.SocketTimeoutException;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.action.StaticAction;
import static pl.bongo.bongoutils.IntegrationHarness.*;
import static pl.bongo.bongoutils.WhitelistIntegrationHarness.command;

/** Real vanilla packets against a disposable localhost server, including offline delivery and OP URL attachments. */
public final class MailIntegrationHarness {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); port = Integer.parseInt(args[0]);
        WhitelistIntegrationHarness.rconPort = Integer.parseInt(args[1]); WhitelistIntegrationHarness.password = args[2];
        String stamp = Long.toString(System.nanoTime(), 36);
        String opName = "MOp" + stamp, enName = "MEn" + stamp, plName = "MPl" + stamp;
        command("ign-whitelist off"); command("whitelist off");
        try (Bot op = connect(opName, "pl_pl", true); Bot en = connect(enName, "en_us", true)) {
            try (Bot pl = connect(plName, "pl_pl", true)) { pl.waitActive(200); }
            Thread.sleep(1200);
            MultiActionDialog form = open(en, "mail send " + plName.toUpperCase(Locale.ROOT));
            check(form.common().inputs().size() == 1, "offline recipient resolves without case sensitivity; ordinary sender has no attachment input");
            CompoundTag fields = fields("Offline hello", false);
            fields.putString("important", "true"); fields.putString("recipient", opName);
            en.click(form.actions().getFirst(), fields); chat(en, "Mail sent. Recipients: 1.");
            en.click(form.actions().getFirst(), fields);
            try (Bot pl = connect(plName, "pl_pl", false)) {
                var notification = chat(pl, "Nowe wiadomości w skrzynce pocztowej: 1");
                check(notification.content().getStyle().getColor().getValue() == 0xFFAA00, "join notification text is orange");
                var count = notification.content().getSiblings().getFirst();
                check(count.getStyle().isUnderlined() && count.getStyle().getColor().getValue() == 0xFF5555
                        && ((ClickEvent.RunCommand) count.getStyle().getClickEvent()).command().equals("/mail read new"), "red underlined count opens /mail read new");
                var inbox = open(pl, "mail read new");
                check(inbox.actions().size() == 1 && inbox.common().title().getString().equals(Lang.text("pl_pl", "mail_new_title")), "new list contains exactly one mail despite replayed send");
                pl.click(inbox.actions().getFirst(), new CompoundTag()); var message = pl.gameDialog();
                check(body(message).contains("Offline hello") && !body(message).contains("ADMINISTRACJA"), "only opening mail reads it; forged importance is ignored");
                inbox = open(pl, "mail read new"); check(body(inbox).contains("Brak wiadomości"), "opened mail leaves the unread list");
                inbox = open(pl, "mail read all"); check(inbox.actions().size() == 1, "read mail stays in all mail");
                form = open(en, "mail send " + plName); en.click(form.exitAction().orElseThrow(), new CompoundTag());
                en.click(form.actions().getFirst(), fields("cancelled", false));
                check(open(pl, "mail read all").actions().size() == 1, "cancelled composer and replay cannot deliver mail");
                form = open(en, "mail send " + plName);
                en.click(form.actions().getFirst(), fields("forged URL", "https://example.com/forged")); chat(en, "This operation requires OP");
                check(body(open(pl, "mail read new")).contains("Brak wiadomości"), "ordinary sender cannot forge URL attachments through packet fields");
                en.send(new ServerboundChatCommandPacket("mail send *")); noDialog(en);
                en.send(new ServerboundChatCommandPacket("mail send-multiple " + opName + " " + plName)); noDialog(en);
                check(true, "ordinary players cannot broadcast or use send-multiple");
                pl.send(new ServerboundChatCommandPacket("ingore " + enName)); chat(pl, "Ignorowanie włączone");
                en.send(new ServerboundChatCommandPacket("mail send " + plName)); chat(en, "Cannot send mail");
                check(body(open(pl, "mail read all")).contains("Brak wiadomości"), "ignore alias blocks bilateral mail and hides history");
                pl.send(new ServerboundChatCommandPacket("ignore " + enName)); chat(pl, "Ignorowanie wyłączone");
                command("bongoutils visibility " + plName + " vanish");
                en.send(new ServerboundChatCommandPacket("mail send " + plName)); chat(en, "Cannot send mail");
                command("bongoutils visibility " + plName + " semi");
                form = open(en, "mail send " + plName);
                command("bongoutils visibility " + plName + " vanish");
                en.click(form.actions().getFirst(), fields("stale privacy", false)); chat(en, "Cannot send mail");
                command("bongoutils visibility " + plName + " semi");
                form = open(en, "mail send " + plName); en.click(form.actions().getFirst(), fields("semi allowed", false)); chat(en, "Mail sent.");
                check(true, "full vanish blocks ordinary delivery, semi allows it, and privacy is rechecked on submit");
                command("bongoutils visibility " + plName + " visible");
                command("op " + opName);
                pl.send(new ServerboundChatCommandPacket("ignore " + opName)); chat(pl, "Ignorowanie włączone");
                form = open(op, "mail send *");
                check(form.common().inputs().size() == 2 && form.common().inputs().get(1).key().equals("attachments"), "OP composer offers URL attachments even with empty hands");
                en.click(form.actions().getFirst(), fields("stolen session", true));
                op.click(form.actions().getFirst(), fields("Admin announcement", "javascript:alert(1)"));
                form = op.gameDialog();
                check(body(form).contains("Podaj do 5 pełnych adresów"), "invalid URL stays in composer without delivering mail");
                check(((net.minecraft.server.dialog.input.TextInput) form.common().inputs().getFirst().control()).initial().equals("Admin announcement"), "validation preserves message draft");
                CompoundTag attachmentFields = fields("Admin announcement", "https://example.com/rules.pdf?token=a%2Fb\nhttp://example.org/image.png");
                op.click(form.actions().getFirst(), attachmentFields); chat(op, "Wysłano maila. Liczba odbiorców:");
                chat(en, "New messages in mailbox:");
                inbox = open(pl, "mail read new");
                check(inbox.actions().getFirst().button().label().getString().startsWith("[WAŻNE]"), "important broadcast bypasses ignore and is listed newest first");
                pl.click(inbox.actions().getFirst(), new CompoundTag()); message = pl.gameDialog();
                check(body(message).contains("ADMINISTRACJA") && body(message).contains("Admin announcement"), "administration label and message are displayed");
                check(message.actions().size() == 3 && message.common().afterAction() == DialogAction.NONE, "two URL buttons and Back keep the dialog interactive");
                check(((ClickEvent.OpenUrl) ((StaticAction) message.actions().getFirst().action().orElseThrow()).value()).uri().toString()
                        .equals("https://example.com/rules.pdf?token=a%2Fb"), "first attachment uses native open_url and preserves signed query");
                check(((ClickEvent.OpenUrl) ((StaticAction) message.actions().get(1).action().orElseThrow()).value()).uri().toString()
                        .equals("http://example.org/image.png"), "second attachment has its own native open_url action");
                pl.click(message.actions().getLast(), new CompoundTag()); pl.gameDialog();
                check(true, "Back still works on a message with native URL buttons");
                form = open(op, "mail send-multiple " + plName + " " + enName + " " + plName);
                op.click(form.actions().getFirst(), fields("Batch distinct", false)); chat(op, "Wysłano maila. Liczba odbiorców: 2.");
                check(true, "send-multiple deduplicates and bypasses ignore for administration mail");
                form = open(op, "mail send *"); command("deop " + opName);
                op.click(form.actions().getFirst(), fields("revoked", true)); chat(op, "Ta operacja wymaga OP");
                check(true, "revoking OP invalidates an open administration composer");
                command("op " + opName);
                form = open(op, "mail send " + opName);
                command("deop " + opName);
                op.click(form.actions().getFirst(), fields("revoked attachment", true)); chat(op, "Ta operacja wymaga OP");
                check(true, "revoking OP also invalidates attachments on an ordinary single-recipient composer");
                command("op " + opName);
                // Fill more than one page; only opening a message changes its read state.
                for (int i = 0; i < 9; i++) {
                    form = open(op, "mail send-multiple " + plName + " " + enName);
                    op.click(form.actions().getFirst(), fields("Page mail " + i, false)); chat(op, "Wysłano maila.");
                }
                inbox = open(pl, "mail read new");
                check(inbox.actions().size() == 9 && inbox.actions().getFirst().button().tooltip().orElseThrow().getString().contains("Page mail 8"), "mailbox paginates eight newest mails with stable ordering");
                pl.click(inbox.actions().getLast(), new CompoundTag()); inbox = pl.gameDialog();
                check(body(inbox).contains("Strona 2 / 2"), "next page is reachable");
                pl.click(inbox.actions().getLast(), new CompoundTag()); inbox = pl.gameDialog();
                check(body(inbox).contains("Strona 1 / 2"), "previous page is reachable");
                System.out.println("PASS: mail offline delivery, localization, read state, cancel/replay protection, permissions, ignore, vanish, attachments and pagination.");
            }
        } finally { command("deop " + opName); }
    }
    private static Bot connect(String name, String locale, boolean register) throws Exception {
        Bot bot = new Bot(name, UUID.randomUUID(), locale);
        var dialog = bot.dialog(); bot.auth(dialog, "mail-test-password", register ? "mail-test-password" : null);
        bot.finish(); bot.prepareGame(); return bot;
    }
    private static MultiActionDialog open(Bot bot, String command) throws Exception {
        bot.send(new ServerboundChatCommandPacket(command));
        long until = System.currentTimeMillis() + 8000;
        bot.socket.setSoTimeout(1000);
        try {
            while (System.currentTimeMillis() < until) {
                try {
                    var packet = bot.gamePacket();
                    if (packet instanceof ClientboundShowDialogPacket dialog) return (MultiActionDialog) dialog.dialog().value();
                } catch (SocketTimeoutException expected) { }
            }
            throw new AssertionError("Missing dialog after /" + command);
        } finally { bot.socket.setSoTimeout(20000); }
    }
    private static CompoundTag fields(String body, boolean attach) {
        return fields(body, attach ? "https://example.com/attachment" : "");
    }
    private static CompoundTag fields(String body, String urls) {
        CompoundTag data = new CompoundTag(); data.putString("body", body); data.putString("attachments", urls); return data;
    }
    private static String body(MultiActionDialog dialog) {
        return dialog.common().body().stream().map(part -> ((PlainMessage) part).contents().getString()).reduce("", (a, b) -> a + "\n" + b);
    }
    private static ClientboundSystemChatPacket chat(Bot bot, String text) throws Exception {
        long until = System.currentTimeMillis() + 10000;
        bot.socket.setSoTimeout(10000);
        try {
            while (System.currentTimeMillis() < until) {
                var packet = bot.gamePacket();
                if (packet instanceof ClientboundSystemChatPacket chat && chat.content().getString().contains(text)) return chat;
            }
            throw new AssertionError("Missing mail chat: " + text);
        } finally { bot.socket.setSoTimeout(20000); }
    }
    private static void noDialog(Bot bot) throws Exception {
        bot.socket.setSoTimeout(500);
        long until = System.currentTimeMillis() + 800;
        try {
            while (System.currentTimeMillis() < until) {
                try { check(!(bot.gamePacket() instanceof ClientboundShowDialogPacket), "unauthorized command does not open mail dialog"); }
                catch (SocketTimeoutException expected) { }
            }
        } finally { bot.socket.setSoTimeout(20000); }
    }
}
