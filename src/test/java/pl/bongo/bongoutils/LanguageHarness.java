package pl.bongo.bongoutils;

import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.network.protocol.game.*;
import static pl.bongo.bongoutils.IntegrationHarness.*;

/** Checks the actual configuration-phase locale and game dialogs over vanilla packets. */
public final class LanguageHarness {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();port=Integer.parseInt(args[0]);
        for(String language:new String[]{"pl_pl","en_us","de_de"}) {
            String name="BL"+Long.toString(System.nanoTime(),36);
            try(Bot bot=new Bot(name,UUID.randomUUID(),language)) {
                var dialog=bot.dialog();
                check(dialog.common().title().getString().equals(Lang.text(language,"register_title")),language+" registration title before world entry");
                check(dialog.actions().getFirst().button().label().getString().equals(Lang.text(language,"register_button")),language+" registration button");
                bot.auth(dialog,"short","short");dialog=bot.dialog();
                check(((PlainMessage)dialog.common().body().getFirst()).contents().getString().equals(Lang.text(language,"password_length")),language+" registration validation");
                Thread.sleep(1100);bot.auth(dialog,"language-test-password","language-test-password");bot.finish();bot.waitActive(300);
                bot.send(new net.minecraft.network.protocol.game.ServerboundChatCommandPacket("skin"));var skin=bot.gameDialog();
                check(skin.common().title().getString().equals(Lang.text(language,"skin_title")),language+" game-phase skin menu");
                check(skin.actions().get(1).button().label().getString().equals(Lang.text(language,"url_button")),language+" skin source button");
            }
        }
        if(args.length>1) {
            WhitelistIntegrationHarness.rconPort=Integer.parseInt(args[1]);
            WhitelistIntegrationHarness.password=args[2];
            String suffix=Long.toString(System.nanoTime(),36);
            String plName="BP"+suffix,enName="BE"+suffix;
            try(Bot pl=registered(plName,"pl_pl");Bot en=registered(enName,"en_us")) {
                WhitelistIntegrationHarness.command("gamerule send_command_feedback true");
                WhitelistIntegrationHarness.command("op "+plName);
                WhitelistIntegrationHarness.command("op "+enName);
                pl.send(new ServerboundChatCommandPacket("ign-whitelist reload"));
                expectChat(pl,"Przeładowano ign-whitelist.json.");
                expectChat(en,"Reloaded ign-whitelist.json.");
                check(true,"admin broadcast uses the observer's language rather than the author's");
                WhitelistIntegrationHarness.command("gamerule send_command_feedback false");
                pl.send(new ServerboundChatCommandPacket("ign-whitelist reload"));
                en.socket.setSoTimeout(500);
                try {
                    long deadline=System.currentTimeMillis()+1000;
                    while(System.currentTimeMillis()<deadline) {
                        try {
                            var packet=en.gamePacket();
                            check(!(packet instanceof ClientboundSystemChatPacket chat && chat.content().getString().contains("Reloaded ign-whitelist")),"disabled admin feedback stays suppressed");
                        } catch(java.net.SocketTimeoutException expected) {}
                    }
                } finally { en.socket.setSoTimeout(20000); }
                WhitelistIntegrationHarness.command("gamerule send_command_feedback true");
            }
        }
    }
    private static Bot registered(String name,String locale) throws Exception {
        Bot bot=new Bot(name,UUID.randomUUID(),locale);var dialog=bot.dialog();
        bot.auth(dialog,"language-test-password","language-test-password");bot.finish();bot.waitActive(300);
        return bot;
    }
    private static void expectChat(Bot bot,String text) throws Exception {
        long deadline=System.currentTimeMillis()+5000;
        while(System.currentTimeMillis()<deadline) {
            var packet=bot.gamePacket();
            if(packet instanceof ClientboundSystemChatPacket chat && chat.content().getString().contains(text))return;
        }
        throw new AssertionError("Missing localized message: "+text);
    }
}
