package pl.bongo.bongoutils;

import java.net.URI;
import java.util.*;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.*;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.*;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;

public final class WikiCommand {
    public static final URI URL = URI.create("https://tinyurl.com/bxw-smp");
    private WikiCommand() {}
    public static Holder<Dialog> dialog(String locale) {
        return Holder.direct(new MultiActionDialog(new CommonDialogData(Component.literal(Lang.text(locale, "wiki_title")),
                Optional.empty(), true, false, DialogAction.CLOSE,
                List.of(new PlainMessage(Component.literal(URL.toString()), 320)), List.of()),
                List.of(new ActionButton(new CommonButtonData(Component.literal(Lang.text(locale, "wiki_open")), 300),
                        Optional.of(new StaticAction(new ClickEvent.OpenUrl(URL))))),
                Optional.of(new ActionButton(new CommonButtonData(Component.literal(Lang.text(locale, "close_button")), 150), Optional.empty())), 1));
    }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wiki").executes(c -> {
            var player = c.getSource().getPlayerOrException(); player.openDialog(dialog(Lang.locale(player))); return 1;
        }));
    }
}
