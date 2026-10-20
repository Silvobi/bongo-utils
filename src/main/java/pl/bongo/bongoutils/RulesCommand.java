package pl.bongo.bongoutils;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RulesCommand {
    public static int show(CommandSourceStack source, BongoUtils.Rules rules) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (rules.mode.equals("link")) {
            player.sendSystemMessage(link(rules));
        } else {
            player.openDialog(dialog(rules));
        }
        return 1;
    }

    static Component link(BongoUtils.Rules rules) {
        return Component.literal(rules.title + ": ").append(Component.literal(rules.url)
                .withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(rules.url)))));
    }

    static Holder<Dialog> dialog(BongoUtils.Rules rules) {
        List<DialogBody> body = new ArrayList<>();
        for (int i = 0; i < rules.entries.size(); i++)
            body.add(new PlainMessage(Component.literal((i + 1) + ". " + rules.entries.get(i)), 320));
        if (body.isEmpty()) body.add(new PlainMessage(Component.literal("Regulamin nie został jeszcze ustawiony."), 320));
        return Holder.direct(new NoticeDialog(new CommonDialogData(Component.literal(rules.title), Optional.empty(),
                true, false, DialogAction.CLOSE, body, List.of()),
                new ActionButton(new CommonButtonData(Component.literal("Zamknij"), 150), Optional.empty())));
    }
}
