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
import java.util.*;

public final class RulesCommand {
    record Text(String title,String url,List<String> entries) {}
    static Text text(BongoUtils.Rules rules,String language) {
        var english=I18n.polish(language)?null:rules.en;
        String title=english!=null && english.title!=null?english.title:rules.title;
        if((english==null || english.title==null) && title.equals("Regulamin serwera")) title=I18n.t(language,"rules.title");
        return new Text(title,english!=null && english.url!=null?english.url:rules.url,
            english!=null && english.entries!=null?english.entries:rules.entries);
    }
    public static int show(CommandSourceStack source,BongoUtils.Rules rules) throws CommandSyntaxException {
        var player=source.getPlayerOrException();String language=I18n.language(player);
        if(rules.mode.equals("link")) player.sendSystemMessage(link(rules,language));
        else player.openDialog(dialog(rules,language));
        return 1;
    }
    static Component link(BongoUtils.Rules rules) { return link(rules,null); }
    static Component link(BongoUtils.Rules rules,String language) {
        var text=text(rules,language);
        return Component.literal(text.title+": ").append(Component.literal(text.url)
            .withStyle(style->style.withColor(ChatFormatting.AQUA).withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(URI.create(text.url)))));
    }
    static Holder<Dialog> dialog(BongoUtils.Rules rules) { return dialog(rules,null); }
    static Holder<Dialog> dialog(BongoUtils.Rules rules,String language) {
        var text=text(rules,language);List<DialogBody> body=new ArrayList<>();
        for(int i=0;i<text.entries.size();i++) body.add(new PlainMessage(Component.literal((i+1)+". "+text.entries.get(i)),320));
        if(body.isEmpty()) body.add(new PlainMessage(Component.literal(I18n.t(language,"rules.empty")),320));
        return Holder.direct(new NoticeDialog(new CommonDialogData(Component.literal(text.title),Optional.empty(),true,false,DialogAction.CLOSE,body,List.of()),
            new ActionButton(new CommonButtonData(Component.literal(I18n.t(language,"close")),150),Optional.empty())));
    }
}
