package pl.bongo.bongoutils;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class SocialCommandRegistrationTest {
    @BeforeAll static void boot() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void theDistributedFeatureSetRegistersIgnoreAndBothVisibilityCommands() {
        var dispatcher=new CommandDispatcher<CommandSourceStack>();
        Social.register(dispatcher);
        MigrationCommand.register(dispatcher);
        for(String command:new String[]{"ignore","vanish","semi-vanish"})
            assertNotNull(dispatcher.getRoot().getChild(command),command);
        assertNotNull(dispatcher.getRoot().getChild("vanish").getChild("fake"));
        assertNotNull(dispatcher.getRoot().getChild("bongoutils").getChild("migrate"));
        assertNotNull(dispatcher.getRoot().getChild("bongoutils").getChild("visibility"));
    }
}
