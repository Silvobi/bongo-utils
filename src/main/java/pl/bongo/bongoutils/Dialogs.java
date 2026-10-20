package pl.bongo.bongoutils;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import java.util.List;
import java.util.Optional;

public final class Dialogs {
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath("bongoutils", path); }
    public static Input text(String key, String label, int max) {
        return new Input(key, new TextInput(300, Component.literal(label), true, "", max, Optional.empty()));
    }
    public static ActionButton button(String label, String action) {
        return new ActionButton(new CommonButtonData(Component.literal(label), 150),
                Optional.of(new CustomAll(id(action), Optional.empty())));
    }
    private static CommonDialogData common(String title, String message, List<Input> inputs, boolean escape) {
        return new CommonDialogData(Component.literal(title), Optional.empty(), escape, false,
                DialogAction.WAIT_FOR_RESPONSE, List.of(new PlainMessage(Component.literal(message), 320)), inputs);
    }
    public static Holder<Dialog> auth(String language, boolean registration, String token, String message) {
        List<Input> fields = registration ? List.of(text("password", I18n.t(language,"auth.password.label"), 128),
                text("repeat", I18n.t(language,"auth.repeat.label"), 128)) : List.of(text("password", I18n.t(language,"auth.enter.label"), 128));
        return Holder.direct(new MultiActionDialog(common(registration ? I18n.t(language,"auth.register") : I18n.t(language,"auth.login"),
                message, fields, false), List.of(button(registration ? I18n.t(language,"auth.register.button") : I18n.t(language,"auth.login.button"), "auth/" + token)),
                Optional.of(button(I18n.t(language,"auth.disconnect"), "quit/" + token)), 1));
    }
    public static Holder<Dialog> skinMenu(String language, String token) {
        return Holder.direct(new MultiActionDialog(common(I18n.t(language,"skin.title"), I18n.t(language,"skin.choose"), List.of(), true),
                List.of(button(I18n.t(language,"skin.ign"), "skin_ign/" + token), button(I18n.t(language,"skin.url"), "skin_url/" + token)),
                Optional.of(new ActionButton(new CommonButtonData(Component.literal(I18n.t(language,"close")), 150), Optional.empty())), 2));
    }
    public static Holder<Dialog> skinForm(String language, boolean url, String token, String error) {
        var fields = url ? List.of(text("source", I18n.t(language,"skin.url.label"), 2048),
                new Input("slim", new net.minecraft.server.dialog.input.BooleanInput(Component.literal(I18n.t(language,"skin.slim")), false, "true", "false")))
                : List.of(text("source", I18n.t(language,"skin.ign.label"), 16));
        return Holder.direct(new MultiActionDialog(common("BongoUtils • " + (url ? I18n.t(language,"skin.url.title") : I18n.t(language,"skin.ign.title")), error,
                fields, true), List.of(button(I18n.t(language,"skin.apply"), (url ? "skin_save_url/" : "skin_save_ign/") + token)),
                Optional.of(button(I18n.t(language,"back"), "skin_back/" + token)), 1));
    }
    public static Holder<Dialog> changePassNick(String language, String token, String message) {
        return Holder.direct(new MultiActionDialog(common(I18n.t(language,"password.title"), message,
                List.of(text("nick", I18n.t(language,"password.nick"), 16)), true),
                List.of(button(I18n.t(language,"password.check"), "changepass_lookup/" + token)),
                Optional.of(button(I18n.t(language,"close"), "changepass_cancel/" + token)), 1));
    }
    public static Holder<Dialog> changePassEdit(String language, String token, String target, String message) {
        return Holder.direct(new MultiActionDialog(common(I18n.t(language,"password.account",target), message,
                List.of(text("password", I18n.t(language,"password.new.label"), 128), text("repeat", I18n.t(language,"password.repeat.label"), 128)), true),
                List.of(button(I18n.t(language,"password.change"), "changepass_save/" + token), button(I18n.t(language,"password.clear"), "changepass_clear/" + token)),
                Optional.of(button(I18n.t(language,"back"), "changepass_back/" + token)), 2));
    }
    public static Holder<Dialog> changePassClear(String language, String token, String target) {
        return Holder.direct(new MultiActionDialog(common(I18n.t(language,"password.clear.title"),
                I18n.t(language,"password.confirm",target), List.of(), true),
                List.of(button(I18n.t(language,"password.yes"), "changepass_confirm/" + token)),
                Optional.of(button(I18n.t(language,"cancel"), "changepass_edit/" + token)), 1));
    }
}
