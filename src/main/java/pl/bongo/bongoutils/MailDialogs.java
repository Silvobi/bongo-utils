package pl.bongo.bongoutils;

import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import java.net.URI;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.action.StaticAction;

/** Vanilla dialogs; all routing and authorization stays in the server-side MailUi session. */
public final class MailDialogs {
    private MailDialogs() {}
    static Holder<Dialog> show(String title, List<PlainMessage> body, List<Input> fields,
                               List<ActionButton> buttons, ActionButton exit) {
        return show(title, body, fields, buttons, exit, DialogAction.WAIT_FOR_RESPONSE);
    }
    private static Holder<Dialog> show(String title, List<PlainMessage> body, List<Input> fields,
                                       List<ActionButton> buttons, ActionButton exit, DialogAction afterAction) {
        return Holder.direct(new MultiActionDialog(new CommonDialogData(Component.literal(title), Optional.empty(),
                true, false, afterAction, new ArrayList<>(body), fields), buttons, Optional.of(exit), 1));
    }
    static PlainMessage line(String text) { return new PlainMessage(Component.literal(text), 320); }
    public static Holder<Dialog> compose(String locale, String token, String recipients, boolean operator, String initial, String urls, String error) {
        List<PlainMessage> body = new ArrayList<>();
        body.add(line(Lang.text(locale, "mail_to", recipients)));
        if (!error.isEmpty()) body.add(line(error));
        List<Input> fields = new ArrayList<>();
        fields.add(new Input("body", new TextInput(300, Component.literal(Lang.text(locale, "mail_body")), true,
                initial, MailStore.MAX_BODY, Optional.of(new TextInput.MultilineOptions(Optional.empty(), Optional.of(140))))));
        if (operator) fields.add(new Input("attachments", new TextInput(300,
                Component.literal(Lang.text(locale, "mail_urls")), true, urls, MailAttachments.MAX_INPUT,
                Optional.of(new TextInput.MultilineOptions(Optional.empty(), Optional.of(70))))));
        return show(Lang.text(locale, "mail_compose_title"), body, fields,
                List.of(Dialogs.button(Lang.text(locale, "mail_send"), "mail_send/" + token)),
                Dialogs.button(Lang.text(locale, "cancel_button"), "mail_cancel/" + token));
    }
    public static Holder<Dialog> list(String locale, String token, boolean unreadOnly, int page, int pages, List<String> labels) {
        List<ActionButton> buttons = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            buttons.add(new ActionButton(new CommonButtonData(Component.literal(label.length() > 52 ? label.substring(0, 52) + "…" : label),
                    Optional.of(Component.literal(label)), 300), Optional.of(new CustomAll(Dialogs.id("mail_open_" + i + "/" + token), Optional.empty()))));
        }
        if (page > 0) buttons.add(Dialogs.button(Lang.text(locale, "mail_previous"), "mail_previous/" + token));
        if (page + 1 < pages) buttons.add(Dialogs.button(Lang.text(locale, "mail_next"), "mail_next/" + token));
        List<PlainMessage> body = List.of(line(labels.isEmpty() ? Lang.text(locale, "mail_empty") : Lang.text(locale, "mail_page", page + 1, pages)));
        // MultiActionDialog requires at least one action, including for an empty mailbox.
        if (buttons.isEmpty()) buttons.add(Dialogs.button(Lang.text(locale, "close_button"), "mail_cancel/" + token));
        return show(Lang.text(locale, unreadOnly ? "mail_new_title" : "mail_all_title"), body, List.of(), buttons,
                Dialogs.button(Lang.text(locale, "close_button"), "mail_cancel/" + token));
    }
    public static Holder<Dialog> message(String locale, String token, MailStore.Message message, String date) {
        List<PlainMessage> body = new ArrayList<>();
        body.add(line(Lang.text(locale, message.important() ? "mail_admin_from" : "mail_from", message.senderName(), date)));
        body.add(line(message.body()));
        List<ActionButton> buttons = new ArrayList<>();
        for (int i = 0; i < message.attachments().size(); i++) {
            String url = message.attachments().get(i);
            buttons.add(new ActionButton(new CommonButtonData(Component.literal(Lang.text(locale, "mail_open_url", i + 1)),
                    Optional.of(Component.literal(url)), 300), Optional.of(new StaticAction(new ClickEvent.OpenUrl(URI.create(url))))));
        }
        buttons.add(Dialogs.button(Lang.text(locale, "back_button"), "mail_back/" + token));
        return show(Lang.text(locale, "mail_message_title"), body, List.of(), buttons,
                Dialogs.button(Lang.text(locale, "close_button"), "mail_cancel/" + token), DialogAction.NONE);
    }
}
