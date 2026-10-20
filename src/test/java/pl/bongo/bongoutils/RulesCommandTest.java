package pl.bongo.bongoutils;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RulesCommandTest {
    @TempDir Path directory;

    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void dialogContainsNumberedRulesAndClosesWithoutCustomServerAction() {
        var rules = new BongoUtils.Rules();
        rules.title = "Zasady gry";
        rules.entries = List.of("Szanuj innych.", "Nie oszukuj.\nGraj uczciwie.");
        NoticeDialog dialog = (NoticeDialog) RulesCommand.dialog(rules).value();
        assertEquals("Zasady gry", dialog.common().title().getString());
        assertEquals(List.of("1. Szanuj innych.", "2. Nie oszukuj.\nGraj uczciwie."), dialog.common().body().stream()
                .map(body -> ((PlainMessage) body).contents().getString()).toList());
        assertTrue(dialog.common().inputs().isEmpty());
        assertTrue(dialog.common().canCloseWithEscape());
        assertEquals(DialogAction.CLOSE, dialog.common().afterAction());
        assertTrue(dialog.action().action().isEmpty());
    }

    @Test void emptyRulesHaveAnExplanationAndLinkHasNativeOpenUrlAction() {
        var rules = new BongoUtils.Rules();
        assertFalse(((NoticeDialog) RulesCommand.dialog(rules).value()).common().body().isEmpty());
        rules.url = "https://example.org/rules?lang=pl";
        var link = RulesCommand.link(rules);
        assertTrue(link.getString().contains(rules.url));
        ClickEvent.OpenUrl action = (ClickEvent.OpenUrl) link.getSiblings().getFirst().getStyle().getClickEvent();
        assertEquals(rules.url, action.uri().toString());
    }

    @Test void actualReloadCommandChecksPermissionsAndPreservesSnapshotOnFailure() throws Exception {
        Path file = directory.resolve("config.json");
        ConfigFile config = new ConfigFile(file);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        net.minecraft.server.commands.MsgCommand.register(dispatcher);
        BongoUtils.registerCommands(dispatcher, config);
        var ordinary = Commands.createCompilationContext(LevelBasedPermissionSet.ALL);
        var levelTwo = Commands.createCompilationContext(LevelBasedPermissionSet.GAMEMASTER);
        var admin = Commands.createCompilationContext(LevelBasedPermissionSet.ADMIN);
        var console = Commands.createCompilationContext(LevelBasedPermissionSet.OWNER);
        Files.writeString(file, "{\"rules\":{\"mode\":\"link\",\"url\":\"https://example.org/rules\"}}");
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("bongoutils reload", ordinary));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("bongoutils reload", levelTwo));
        assertEquals("dialog", config.current().rules.mode);
        assertEquals(1, dispatcher.execute("bongoutils reload", admin));
        assertEquals("link", config.current().rules.mode);
        var previous = config.current();
        Files.writeString(file, "{\"allowOffline\":false,\"rules\":{\"mode\":\"broken\"}}");
        assertEquals(0, dispatcher.execute("bongoutils reload", console));
        assertSame(previous, config.current());
        Files.writeString(file, "{\"rules\":{\"entries\":[\"Nowa zasada.\"]}}");
        assertEquals(1, dispatcher.execute("bongoutils reload", console));
        assertEquals(List.of("Nowa zasada."), config.current().rules.entries);
        assertTrue(dispatcher.getRoot().getChild("rules").canUse(ordinary));
        for(String command:List.of("rules","manageclaim","afk","status","m","r","skin","changepass"))
            assertNotNull(dispatcher.getRoot().getChild(command),command);
        assertSame(dispatcher.getRoot().getChild("msg"),dispatcher.getRoot().getChild("m").getRedirect());
        assertNotNull(dispatcher.getRoot().getChild("bongoutils").getChild("resetpassword"));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("rules", console));
    }
}
