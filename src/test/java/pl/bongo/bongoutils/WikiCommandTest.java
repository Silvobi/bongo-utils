package pl.bongo.bongoutils;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class WikiCommandTest {
    @BeforeAll static void boot() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void commandsAreAvailableToOrdinaryPlayersAndWikiUsesTheRequestedUrlInBothLanguages() {
        var dispatcher = new CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
        Rest.register(dispatcher); WikiCommand.register(dispatcher);
        for (String name : new String[]{"sit", "lay", "wiki"}) assertNotNull(dispatcher.getRoot().getChild(name));
        for (String locale : new String[]{"pl_pl", "en_us"}) {
            var dialog = (MultiActionDialog) WikiCommand.dialog(locale).value();
            assertEquals(Lang.text(locale, "wiki_title"), dialog.common().title().getString());
            assertEquals("https://tinyurl.com/bxw-smp", ((ClickEvent.OpenUrl) ((StaticAction) dialog.actions().getFirst().action().orElseThrow()).value()).uri().toString());
        }
    }
}
