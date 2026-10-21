package pl.bongo.bongoutils;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.dialog.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DialogLifecycleTest {
    @TempDir Path root;
    @BeforeAll static void boot() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void authenticationKeepsItsFormWithoutUnlockingTheConfigurationGate() {
        for (String locale : List.of("pl_pl", "en_us", "de_de")) for (boolean registration : List.of(false, true)) {
            var dialog = (MultiActionDialog) Dialogs.auth(locale, registration, "token", "prompt").value();
            assertEquals(DialogAction.NONE, dialog.common().afterAction());
            assertFalse(dialog.common().canCloseWithEscape());
            assertTrue(dialog.actions().getFirst().action().isPresent());
            assertTrue(dialog.exitAction().orElseThrow().action().isPresent());
        }
    }

    @Test void gameFormsNeverRequireAResponseForClosingOrExpiredActions() {
        for (String locale : List.of("pl_pl", "en_us", "de_de")) {
            for (var holder : List.of(Dialogs.skinMenu(locale,"token"), Dialogs.skinForm(locale,false,"token",""),
                    Dialogs.skinForm(locale,true,"token",""), Dialogs.changePassNick(locale,"token",""),
                    Dialogs.changePassEdit(locale,"token","Player",""), Dialogs.changePassClear(locale,"token","Player"),
                    MailDialogs.compose(locale,"token","Player",false,new MailUi.Draft("","","",""),""),
                    MailDialogs.list(locale,"token",true,0,1,List.of()), WikiCommand.dialog(locale))) {
                assertEquals(DialogAction.CLOSE, holder.value().common().afterAction(), holder.value().common().title().getString());
            }
            // This button sends no packet: the client must close it without server assistance.
            assertTrue(((MultiActionDialog) Dialogs.skinMenu(locale,"token").value()).exitAction().orElseThrow().action().isEmpty());
            assertTrue(((MultiActionDialog) WikiCommand.dialog(locale).value()).exitAction().orElseThrow().action().isEmpty());
        }
    }

    @Test void theMergedCommandTreeRetainsMailRestWikiAndTheNewFeaturesTogether() throws Exception {
        var previous = BongoUtils.mailUi;
        try {
            BongoUtils.mailUi = new MailUi(new MailStore(root.resolve("mail.json")));
            var dispatcher = new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
            BongoUtils.registerCommands(dispatcher, new ConfigFile(root.resolve("config.json")));
            for (String command : List.of("mail","sit","lay","wiki","ignore","ingore","vanish","semi-vanish",
                    "skin","changepass","rules","afk","status","m","r","manageclaim"))
                assertNotNull(dispatcher.getRoot().getChild(command),command);
            for (String sub : List.of("reload","resetpassword","migrate","visibility"))
                assertNotNull(dispatcher.getRoot().getChild("bongoutils").getChild(sub),sub);
            for (String sub : List.of("send","send-multiple","admin-send","read"))
                assertNotNull(dispatcher.getRoot().getChild("mail").getChild(sub),sub);
            for (String sub : List.of("new","all","send","recive","receive"))
                assertNotNull(dispatcher.getRoot().getChild("mail").getChild("read").getChild(sub),sub);
            assertNull(dispatcher.getRoot().getChild("mail").getChild("send").getChild("*"));
            assertNotNull(dispatcher.getRoot().getChild("mail").getCommand());
        } finally { BongoUtils.mailUi = previous; }
    }
}
