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
    public static Holder<Dialog> auth(String locale, boolean registration, String token, String message) {
        List<Input> fields = registration ? List.of(text("password", Lang.text(locale,"password_label"), 128),
                text("repeat", Lang.text(locale,"repeat_label"), 128)) : List.of(text("password", Lang.text(locale,"login_label"), 128));
        return Holder.direct(new MultiActionDialog(common(registration ? Lang.text(locale,"register_title") : Lang.text(locale,"login_title"),
                message, fields, false), List.of(button(registration ? Lang.text(locale,"register_button") : Lang.text(locale,"login_button"), "auth/" + token)),
                Optional.of(button(Lang.text(locale,"disconnect_button"), "quit/" + token)), 1));
    }
    public static Holder<Dialog> skinMenu(String locale, String token) {
        return Holder.direct(new MultiActionDialog(common(Lang.text(locale,"skin_title"), Lang.text(locale,"skin_choose"), List.of(), true),
                List.of(button(Lang.text(locale,"ign_button"), "skin_ign/" + token), button(Lang.text(locale,"url_button"), "skin_url/" + token)),
                Optional.of(new ActionButton(new CommonButtonData(Component.literal(Lang.text(locale,"close_button")), 150), Optional.empty())), 2));
    }
    public static Holder<Dialog> skinForm(String locale, boolean url, String token, String error) {
        var fields = url ? List.of(text("source", Lang.text(locale,"skin_url_label"), 2048),
                new Input("slim", new net.minecraft.server.dialog.input.BooleanInput(Component.literal(Lang.text(locale,"slim_label")), false, "true", "false")))
                : List.of(text("source", Lang.text(locale,"skin_ign_label"), 16));
        return Holder.direct(new MultiActionDialog(common("BongoUtils • " + (url ? Lang.text(locale,"skin_url_title") : Lang.text(locale,"skin_ign_title")), error,
                fields, true), List.of(button(Lang.text(locale,"skin_set_button"), (url ? "skin_save_url/" : "skin_save_ign/") + token)),
                Optional.of(button(Lang.text(locale,"back_button"), "skin_back/" + token)), 1));
    }
    public static Holder<Dialog> changePassNick(String locale, String token, String message) {
        return Holder.direct(new MultiActionDialog(common(Lang.text(locale,"change_title"), message,
                List.of(text("nick", Lang.text(locale,"nick_label"), 16)), true),
                List.of(button(Lang.text(locale,"check_button"), "changepass_lookup/" + token)),
                Optional.of(button(Lang.text(locale,"close_button"), "changepass_cancel/" + token)), 1));
    }
    public static Holder<Dialog> changePassEdit(String locale, String token, String target, String message) {
        return Holder.direct(new MultiActionDialog(common(Lang.text(locale,"change_account_title", target), message,
                List.of(text("password", Lang.text(locale,"new_password_label"), 128), text("repeat", Lang.text(locale,"new_repeat_label"), 128)), true),
                List.of(button(Lang.text(locale,"change_button"), "changepass_save/" + token), button(Lang.text(locale,"clear_button"), "changepass_clear/" + token)),
                Optional.of(button(Lang.text(locale,"back_button"), "changepass_back/" + token)), 2));
    }
    public static Holder<Dialog> changePassClear(String locale, String token, String target) {
        return Holder.direct(new MultiActionDialog(common(Lang.text(locale,"clear_title"),
                Lang.text(locale,"clear_body", target), List.of(), true),
                List.of(button(Lang.text(locale,"clear_confirm"), "changepass_confirm/" + token)),
                Optional.of(button(Lang.text(locale,"cancel_button"), "changepass_edit/" + token)), 1));
    }
}
