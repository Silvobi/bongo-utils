package pl.bongo.bongoutils;

import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.Bootstrap;
import static pl.bongo.bongoutils.IntegrationHarness.*;
import static pl.bongo.bongoutils.WhitelistIntegrationHarness.command;

/** Two vanilla clients verify both TAB components and actual client team membership. */
public final class AfkIntegrationHarness {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); port = Integer.parseInt(args[0]);
        WhitelistIntegrationHarness.rconPort = Integer.parseInt(args[1]); WhitelistIntegrationHarness.password = args[2];
        GameProtocols.CLIENTBOUND_TEMPLATE.details().listPackets((type, id) -> RestIntegrationHarness.TYPES.put(id, type));
        String stamp = Long.toString(System.nanoTime(), 36), name = "AP" + stamp, observerName = "AE" + stamp, team = "at" + stamp;
        command("ign-whitelist off"); command("whitelist off");
        UUID id;
        try (var player = new RestIntegrationHarness.Client(name, "pl_pl", true);
             var viewer = new RestIntegrationHarness.Client(observerName, "en_us", true)) {
            player.pump(200); viewer.pump(200);
            id = viewer.tabs.values().stream().filter(e -> e.profile() != null && e.profile().name().equals(name)).findFirst().orElseThrow().profileId();
            player.send("afk"); player.chat("AFK włączone"); viewer.pump(300); player.pump(100);
            check(tabAfk(viewer, id) && tabAfk(player, id), "ordinary /afk adds gray italic AFK in both own and observer TAB");
            check(overhead(viewer, name), "AFK suffix is also sent as an overhead team suffix");
            player.send("afk"); player.chat("AFK wyłączone"); viewer.pump(200);
            check(!tabAfk(viewer, id) && !overhead(viewer, name), "second /afk clears TAB and overhead suffix");
            command("team add " + team); command("team modify " + team + " prefix {\"text\":\"[Clan] \"}");
            command("team modify " + team + " suffix {\"text\":\" +\"}"); command("team modify " + team + " color gold");
            command("team join " + team + " " + name); command("team join " + team + " " + observerName);
            player.send("afk"); player.chat("AFK włączone"); viewer.pump(300);
            var parameters = viewer.teams.get(viewer.membership.get(name));
            check(parameters.playerPrefix().getString().equals("[Clan] ") && parameters.playerSuffix().getString().equals(" + AFK"), "existing team prefix and suffix are preserved");
            check(command("team list " + team).contains(name) && command("team list " + team).contains(observerName), "AFK does not change real server team membership");
            command("team modify " + team + " suffix {\"text\":\" !\"}"); viewer.pump(300);
            check(viewer.teams.get(viewer.membership.get(name)).playerSuffix().getString().equals(" ! AFK"), "team edits while AFK update the overlay");
            command("team leave " + name); viewer.pump(300);
            check(overhead(viewer, name) && viewer.teams.get(viewer.membership.get(name)).playerPrefix().getString().isEmpty(), "leaving a real team keeps AFK without stale decorations");
            command("team join " + team + " " + name); viewer.pump(300);
            check(overhead(viewer, name), "real team join packets cannot displace the AFK overlay");
            try (var late = new RestIntegrationHarness.Client("AL" + stamp, "en_us", true)) {
                late.pump(300); check(tabAfk(late, id) && overhead(late, name), "late joiner receives AFK in TAB and above the player");
            }
            viewer.send("afk"); viewer.chat("AFK enabled"); viewer.send("afk"); viewer.chat("AFK disabled");
            player.send("ignore " + observerName); player.chat("Ignorowanie włączone"); viewer.pump(300);
            check(!tabAfk(viewer, id) && !overhead(viewer, name), "ignore masking does not reveal AFK on the hidden name");
            player.send("ignore " + observerName); player.chat("Ignorowanie wyłączone"); viewer.pump(300);
            check(tabAfk(viewer, id) && overhead(viewer, name), "unignore restores both AFK displays");
            command("bongoutils visibility " + name + " vanish"); viewer.pump(300);
            check(!viewer.tabs.containsKey(id) && !overhead(viewer, name), "full vanish removes AFK display and team overlay for other viewers");
            command("bongoutils visibility " + name + " visible"); viewer.pump(300);
            check(tabAfk(viewer, id) && overhead(viewer, name), "leaving vanish restores AFK");
            player.send("afk"); player.chat("AFK wyłączone"); viewer.pump(300);
            check(team.equals(viewer.membership.get(name)) && !tabAfk(viewer, id), "AFK off restores the latest real team");
            player.send("afk"); player.chat("AFK włączone");
        } finally { command("team remove " + team); }
        Thread.sleep(700);
        try (var player = new RestIntegrationHarness.Client(name, "pl_pl", false)) {
            player.pump(300); check(!tabAfk(player, id) && !overhead(player, name), "disconnect resets session-only AFK");
        }
        System.out.println("PASS: manual AFK, PL/EN feedback, gray italic TAB/overhead suffix, teams, late joins, ignore, vanish and reconnect.");
    }
    static boolean tabAfk(RestIntegrationHarness.Client client, UUID id) {
        var e = client.tabs.get(id);
        if (e == null || e.displayName() == null || !e.displayName().getString().endsWith(" AFK")) return false;
        var suffix = e.displayName().getSiblings().getLast();
        return suffix.getStyle().isItalic() && suffix.getStyle().getColor().getValue() == 0xAAAAAA;
    }
    static boolean overhead(RestIntegrationHarness.Client client, String name) {
        var team = client.teams.get(client.membership.get(name));
        return team != null && team.playerSuffix().getString().endsWith(" AFK");
    }
}
