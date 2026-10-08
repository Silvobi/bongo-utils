package pl.bongo.bongoutils;

import java.nio.file.*;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import static pl.bongo.bongoutils.IntegrationHarness.check;
import static pl.bongo.bongoutils.MsaLogin.Route.*;

/** Tests rejection on a real localhost server. The jeb_ reservation is an explicit storage fixture,
 * not a claim that this harness has authenticated a real Microsoft session. */
public final class MsaIntegrationHarness {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        IntegrationHarness.port = Integer.parseInt(args[0]);
        Path premium = Path.of(args[1]);
        var before = Store.JSON.fromJson(Files.readString(premium), com.google.gson.JsonObject.class);
        check(before.has("jeb_") && !before.has("notch"), "test fixture has local jeb_ MSA reservation, no Notch reservation");
        try (var bot = new IntegrationHarness.Bot("NoTcH", UUID.randomUUID())) {
            check(bot.disconnected, "globally occupied MSA IGN rejected on first offline connection");
            check(!bot.challengeAuthenticated, "occupied offline IGN rejected before encryption or password form");
            check(MsaLogin.rejection(OCCUPIED_MSA, "NoTcH").equals(bot.disconnectReason), "global MSA rejection preserves attempted IGN and exact two-line message");
        }
        try (var bot = new IntegrationHarness.Bot("JeB_", UUID.randomUUID())) {
            check(bot.disconnected, "stored MSA reservation rejects offline IGN regardless of casing");
            check(MsaLogin.rejection(RESERVED_MSA, "JeB_").equals(bot.disconnectReason), "stored MSA reservation sends exact three-line message");
        }
        try (var bot = new IntegrationHarness.Bot("Notch", UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"))) {
            check(bot.challengeAuthenticated && bot.disconnected, "forged official UUID still requires a valid Mojang session");
            check(MsaLogin.rejection(OCCUPIED_MSA, "Notch").equals(bot.disconnectReason), "failed official-UUID session also receives MSA occupation explanation");
        }
        try (var bot = new IntegrationHarness.Bot("jeb_", UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6"))) {
            check(bot.challengeAuthenticated && bot.disconnected, "reserved official UUID cannot bypass session verification");
            check(MsaLogin.rejection(RESERVED_MSA, "jeb_").equals(bot.disconnectReason), "failed session for stored MSA account receives local registration explanation");
        }
        var after = Store.JSON.fromJson(Files.readString(premium), com.google.gson.JsonObject.class);
        check(!after.has("notch"), "lookup and forged UUID did not persist an unverified MSA account");
        System.out.println("PASS: MSA priority, stored reservations, exact disconnect messages and no forged MSA registration.");
    }
}
