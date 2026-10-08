package pl.bongo.bongoutils;

import java.net.SocketTimeoutException;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.common.*;
import static pl.bongo.bongoutils.IntegrationHarness.check;
import static pl.bongo.bongoutils.WhitelistIntegrationHarness.command;

/** Vanilla protocol and localhost RCON; no client mod or production authentication bypass. */
public final class ChangePassIntegrationHarness {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        IntegrationHarness.port = Integer.parseInt(args[0]);
        WhitelistIntegrationHarness.rconPort = Integer.parseInt(args[1]);
        WhitelistIntegrationHarness.password = args[2];
        String stamp = Long.toString(System.currentTimeMillis(), 36);
        String operator = "CpOp" + stamp, target = "CpT" + stamp;
        command("ign-whitelist off"); command("whitelist off");
        try (var admin = register(operator); var ordinary = register(target)) {
            ordinary.send(new ServerboundChatCommandPacket("changepass"));
            noDialog(ordinary);
            check(true, "ordinary player cannot open changepass");
            command("op " + operator);
            var nick = open(admin);
            check(nick.common().inputs().size() == 1 && nick.common().inputs().getFirst().key().equals("nick"), "operator gets IGN dialog");
            submitNick(admin, nick, "../bad"); nick = admin.gameDialog();
            check(nick.common().inputs().getFirst().key().equals("nick"), "invalid IGN stays on account selection");
            submitNick(admin, nick, "MissingCp" + stamp.substring(0, 4)); nick = admin.gameDialog();
            check(nick.common().inputs().getFirst().key().equals("nick"), "missing offline password cannot be edited");
            submitNick(admin, nick, "JeB_"); nick = admin.gameDialog();
            check(nick.common().inputs().getFirst().key().equals("nick"), "MSA fixture is not a registered offline account");
            submitNick(admin, nick, target.toUpperCase()); var edit = admin.gameDialog();
            check(edit.common().inputs().size() == 2 && edit.actions().size() == 2, "registered offline account gets new password, repeat and clear actions");
            admin.click(edit.actions().getFirst(), passwords("changed-test-password", "mismatch"));
            edit = admin.gameDialog();
            check(edit.common().inputs().size() == 2, "mismatched passwords do not save");
            // An action stolen from the operator's dialog must not authorize another connection.
            ordinary.click(edit.actions().getFirst(), passwords("forged-test-password", "forged-test-password"));
            command("deop " + operator);
            admin.click(edit.actions().getFirst(), passwords("revoked-test-password", "revoked-test-password"));
            chat(admin, "Nie masz już uprawnień");
            check(true, "removing OP invalidates an already open password dialog");
            command("op " + operator);
            edit = select(admin, target);
            // First enter and cancel clearing; this must leave credentials unchanged.
            admin.click(edit.actions().get(1), new CompoundTag()); var confirm = admin.gameDialog();
            check(confirm.common().inputs().isEmpty(), "clear action requires a separate confirmation and no new password");
            admin.click(confirm.exitAction().orElseThrow(), new CompoundTag()); edit = admin.gameDialog();
            ordinary.waitActive(200);
            admin.click(edit.actions().getFirst(), passwords("changed-test-password", "changed-test-password"));
            chat(admin, "Zmieniono hasło konta");
            expectKick(ordinary);
            check(true, "successful password replacement disconnects the online target");
            try (var login = new IntegrationHarness.Bot(target.toUpperCase(), UUID.randomUUID())) {
                var form = login.dialog();
                login.auth(form, "integration-cp-password", null); form = login.dialog();
                check(form.common().inputs().size() == 1, "previous password is rejected after replacement");
                Thread.sleep(1100);
                login.auth(form, "changed-test-password", null); login.finish(); login.prepareGame();
                edit = select(admin, target);
                admin.click(edit.actions().get(1), new CompoundTag()); confirm = admin.gameDialog();
                admin.click(confirm.actions().getFirst(), new CompoundTag());
                chat(admin, "Wyczyszczono hasło konta"); expectKick(login);
                check(true, "confirmed clear disconnects the target and removes the password");
            }
            try (var fresh = new IntegrationHarness.Bot(target, UUID.randomUUID())) {
                var registration = fresh.dialog();
                check(registration.common().inputs().size() == 2, "cleared account must register a new password on reconnect");
                fresh.auth(registration, "registered-again-password", "registered-again-password"); fresh.finish();
            }
            System.out.println("PASS: operator dialogs, account checks, password replacement, reset, confirmation, revoked permissions, per-connection authorization and reconnect authentication.");
        } finally { command("deop " + operator); }
    }
    private static IntegrationHarness.Bot register(String name) throws Exception {
        var bot = new IntegrationHarness.Bot(name, UUID.randomUUID());
        bot.auth(bot.dialog(), "integration-cp-password", "integration-cp-password");
        bot.finish(); bot.prepareGame(); return bot;
    }
    private static MultiActionDialog open(IntegrationHarness.Bot bot) throws Exception {
        bot.send(new ServerboundChatCommandPacket("changepass")); return bot.gameDialog();
    }
    private static MultiActionDialog select(IntegrationHarness.Bot bot, String name) throws Exception {
        submitNick(bot, open(bot), name); return bot.gameDialog();
    }
    private static void submitNick(IntegrationHarness.Bot bot, MultiActionDialog dialog, String name) throws Exception {
        CompoundTag fields = new CompoundTag(); fields.putString("nick", name); bot.click(dialog.actions().getFirst(), fields);
    }
    private static CompoundTag passwords(String password, String repeat) {
        CompoundTag fields = new CompoundTag(); fields.putString("password", password); fields.putString("repeat", repeat); return fields;
    }
    private static void chat(IntegrationHarness.Bot bot, String prefix) throws Exception {
        while (true) {
            var packet = bot.gamePacket();
            if (packet instanceof ClientboundSystemChatPacket message && message.content().getString().startsWith(prefix)) return;
            if (packet instanceof ClientboundShowDialogPacket) throw new AssertionError("Unexpected dialog while waiting for " + prefix);
        }
    }
    private static void noDialog(IntegrationHarness.Bot bot) throws Exception {
        bot.socket.setSoTimeout(600);
        try { while (true) { check(!(bot.gamePacket() instanceof ClientboundShowDialogPacket), "no unauthorized password dialog"); } }
        catch (SocketTimeoutException expected) {} finally { bot.socket.setSoTimeout(20000); }
    }
    private static void expectKick(IntegrationHarness.Bot bot) throws Exception {
        try { while (true) bot.gamePacket(); }
        catch (AssertionError expected) { check(expected.getMessage().contains("Administrator"), "target receives password administration disconnect explanation"); }
    }
}
