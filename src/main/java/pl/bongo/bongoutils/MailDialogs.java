package pl.bongo.bongoutils;

import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.*;
import net.minecraft.server.dialog.action.CustomAll;

/** Vanilla dialogs; all routing and authorization stays in the server-side MailUi session. */
public final class MailDialogs {
    private MailDialogs() {}
    static Holder<Dialog> show(String title, List<PlainMessage> body, List<Input> fields,
                               List<ActionButton> buttons, ActionButton exit) {
        return show(title, body, fields, buttons, exit, DialogAction.CLOSE);
    }
    private static Holder<Dialog> show(String title, List<PlainMessage> body, List<Input> fields,
                                       List<ActionButton> buttons, ActionButton exit, DialogAction afterAction) {
        return Holder.direct(new MultiActionDialog(new CommonDialogData(Component.literal(title), Optional.empty(),
                true, false, afterAction, new ArrayList<>(body), fields), buttons, Optional.of(exit), 1));
    }
    static PlainMessage line(String text) { return new PlainMessage(Component.literal(text), 320); }
    public static Holder<Dialog> compose(String locale, String token, String recipients, boolean important, MailUi.Draft draft, String error) {
        List<PlainMessage> body = new ArrayList<>();
        body.add(line(Lang.text(locale, "mail_to", recipients)));
        if (!error.isEmpty()) body.add(line(error));
        List<Input> fields = new ArrayList<>();
        if (important) body.add(line(Lang.text(locale, "mail_admin_hint")));
        fields.add(title(locale, "title", important ? "mail_title_pl" : "mail_subject", draft.title()));
        fields.add(text(locale, "body", important ? "mail_body_pl" : "mail_body", draft.body()));
        if (important) {
            fields.add(title(locale, "title_en", "mail_title_en", draft.englishTitle()));
            fields.add(text(locale, "body_en", "mail_body_en", draft.englishBody()));
        }
        return show(Lang.text(locale, "mail_compose_title"), body, fields,
                List.of(Dialogs.button(Lang.text(locale, "mail_send"), "mail_send/" + token)),
                Dialogs.button(Lang.text(locale, "cancel_button"), "mail_cancel/" + token));
    }
    private static Input title(String locale, String key, String label, String initial) {
        return new Input(key, new TextInput(300, Component.literal(Lang.text(locale, label)), true,
                initial, MailStore.MAX_TITLE, Optional.empty()));
    }
    private static Input text(String locale, String key, String label, String initial) {
        return new Input(key, new TextInput(300, Component.literal(Lang.text(locale, label)), true,
                initial, MailStore.MAX_BODY, Optional.of(new TextInput.MultilineOptions(Optional.empty(), Optional.of(110)))));
    }
    public static Holder<Dialog> list(String locale, String token, boolean unreadOnly, int page, int pages, List<String> labels) {
        return list(locale, token, unreadOnly ? MailStore.Folder.NEW : MailStore.Folder.ALL, page, pages, labels);
    }
    public static Holder<Dialog> list(String locale, String token, MailStore.Folder folder, int page, int pages, List<String> labels) {
        List<ActionButton> buttons = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            buttons.add(new ActionButton(new CommonButtonData(Component.literal(label.length() > 52 ? label.substring(0, 52) + "…" : label)
                    .withStyle(label.startsWith("⚠ ") ? ChatFormatting.GOLD : ChatFormatting.AQUA),
                    Optional.of(Component.literal(label)), 300), Optional.of(new CustomAll(Dialogs.id("mail_open_" + i + "/" + token), Optional.empty()))));
        }
        if (page > 0) buttons.add(Dialogs.button(Lang.text(locale, "mail_previous"), "mail_previous/" + token));
        if (page + 1 < pages) buttons.add(Dialogs.button(Lang.text(locale, "mail_next"), "mail_next/" + token));
        List<PlainMessage> body = List.of(line(labels.isEmpty() ? Lang.text(locale, "mail_empty") : Lang.text(locale, "mail_page", page + 1, pages)));
        // MultiActionDialog requires at least one action, including for an empty mailbox.
        if (buttons.isEmpty()) buttons.add(Dialogs.button(Lang.text(locale, "close_button"), "mail_cancel/" + token));
        String title = switch (folder) {
            case NEW -> "mail_new_title"; case ALL -> "mail_all_title";
            case SENT -> "mail_sent_title"; case RECEIVED -> "mail_received_title";
        };
        return show(Lang.text(locale, title), body, List.of(), buttons,
                Dialogs.button(Lang.text(locale, "close_button"), "mail_cancel/" + token));
    }
    public static Holder<Dialog> message(String locale, String token, MailStore.Message message, String date) {
        List<PlainMessage> body = new ArrayList<>();
        body.add(line(Lang.text(locale, message.important() ? "mail_admin_from" : "mail_from", message.senderName(), date)));
        body.add(new PlainMessage(Component.literal(message.title(locale)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), 320));
        body.add(new PlainMessage(message.important() ? MailLinks.render(message.body(locale)) : Component.literal(message.body(locale)), 320));
        List<ActionButton> buttons = new ArrayList<>();
        // Preserve old URL attachments as inline links, without extra buttons.
        for (String url : message.attachments()) body.add(new PlainMessage(MailLinks.render(url), 320));
        buttons.add(Dialogs.button(Lang.text(locale, "back_button"), "mail_back/" + token));
        return show(Lang.text(locale, "mail_message_title"), body, List.of(), buttons,
                Dialogs.button(Lang.text(locale, "close_button"), "mail_cancel/" + token), DialogAction.NONE);
    }
}
