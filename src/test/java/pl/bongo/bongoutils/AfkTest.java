package pl.bongo.bongoutils;

import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.scores.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class AfkTest {
    @BeforeAll static void boot() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void suffixIsGrayAndItalicAndDoesNotMutateExistingName() {
        var name = Component.literal("Nick").withStyle(ChatFormatting.GOLD);
        var marked = Afk.append(name);
        assertEquals("Nick", name.getString()); assertEquals("Nick AFK", marked.getString());
        assertEquals(0xAAAAAA, marked.getSiblings().getLast().getStyle().getColor().getValue());
        assertTrue(marked.getSiblings().getLast().getStyle().isItalic());
    }
    @Test void overlayPreservesDecorationsAndServerMembershipWithoutModifyingOriginalTeam() {
        var scoreboard = new Scoreboard(); var team = scoreboard.addPlayerTeam("clan");
        scoreboard.addPlayerToTeam("Alice", team); scoreboard.addPlayerToTeam("Bob", team);
        team.setPlayerPrefix(Component.literal("[Clan] ")); team.setPlayerSuffix(Component.literal(" +"));
        team.setColor(Optional.of(TeamColor.GOLD)); team.setAllowFriendlyFire(false);
        team.setNameTagVisibility(Team.Visibility.HIDE_FOR_OTHER_TEAMS);
        team.setCollisionRule(Team.CollisionRule.PUSH_OTHER_TEAMS);
        var copy = Afk.overlay("afk", "Alice", team, team);
        assertEquals("[Clan] ", copy.getPlayerPrefix().getString()); assertEquals(" + AFK", copy.getPlayerSuffix().getString());
        assertEquals(team.getColor(), copy.getColor()); assertFalse(copy.isAllowFriendlyFire());
        assertEquals(Team.Visibility.ALWAYS, copy.getNameTagVisibility()); assertEquals(Team.CollisionRule.ALWAYS, copy.getCollisionRule());
        assertSame(team, scoreboard.getPlayersTeam("Alice")); assertEquals(2, team.getPlayers().size());
        assertEquals(" +", team.getPlayerSuffix().getString());
        var other = Afk.overlay("other", "Alice", team, null);
        assertEquals(Team.Visibility.NEVER, other.getNameTagVisibility()); assertEquals(Team.CollisionRule.NEVER, other.getCollisionRule());
    }
    @Test void manualCommandIsRegisteredAndFeedbackIsLocalized() {
        var dispatcher = new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
        Afk.register(dispatcher); assertNotNull(dispatcher.getRoot().getChild("afk"));
        assertEquals("AFK włączone.", Lang.text("pl_pl", "afk_on")); assertEquals("AFK disabled.", Lang.text("en_us", "afk_off"));
    }
}
