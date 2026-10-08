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
    public static Holder<Dialog> auth(boolean registration, String token, String message) {
        List<Input> fields = registration ? List.of(text("password", "Hasło (8–128 znaków)", 128),
                text("repeat", "Powtórz hasło", 128)) : List.of(text("password", "Podaj hasło", 128));
        return Holder.direct(new MultiActionDialog(common(registration ? "BongoUtils • Rejestracja" : "BongoUtils • Logowanie",
                message, fields, false), List.of(button(registration ? "Zarejestruj" : "Zaloguj", "auth/" + token)),
                Optional.of(button("Rozłącz", "quit/" + token)), 1));
    }
    public static Holder<Dialog> skinMenu(String token) {
        return Holder.direct(new MultiActionDialog(common("BongoUtils • Zmiana skina", "Wybierz źródło skina.", List.of(), true),
                List.of(button("Nick (IGN)", "skin_ign/" + token), button("Adres URL PNG", "skin_url/" + token)),
                Optional.of(new ActionButton(new CommonButtonData(Component.literal("Zamknij"), 150), Optional.empty())), 2));
    }
    public static Holder<Dialog> skinForm(boolean url, String token, String error) {
        var fields = url ? List.of(text("source", "URL obrazu PNG (również link z Discorda)", 2048),
                new Input("slim", new net.minecraft.server.dialog.input.BooleanInput(Component.literal("Wąskie ramiona (Alex)"), false, "true", "false")))
                : List.of(text("source", "Nick gracza z kontem Minecraft", 16));
        return Holder.direct(new MultiActionDialog(common("BongoUtils • " + (url ? "Skin z URL" : "Skin z nicku"), error,
                fields, true), List.of(button("Ustaw skin", (url ? "skin_save_url/" : "skin_save_ign/") + token)),
                Optional.of(button("Wróć", "skin_back/" + token)), 1));
    }
    public static Holder<Dialog> changePassNick(String token, String message) {
        return Holder.direct(new MultiActionDialog(common("BongoUtils • Zmiana hasła", message,
                List.of(text("nick", "Nick gracza (IGN)", 16)), true),
                List.of(button("Sprawdź konto", "changepass_lookup/" + token)),
                Optional.of(button("Zamknij", "changepass_cancel/" + token)), 1));
    }
    public static Holder<Dialog> changePassEdit(String token, String target, String message) {
        return Holder.direct(new MultiActionDialog(common("BongoUtils • Hasło konta " + target, message,
                List.of(text("password", "Nowe hasło (8–128 znaków)", 128), text("repeat", "Powtórz nowe hasło", 128)), true),
                List.of(button("Zmień hasło", "changepass_save/" + token), button("Wyczyść hasło", "changepass_clear/" + token)),
                Optional.of(button("Wróć", "changepass_back/" + token)), 2));
    }
    public static Holder<Dialog> changePassClear(String token, String target) {
        return Holder.direct(new MultiActionDialog(common("BongoUtils • Wyczyść hasło",
                "Czy wyczyścić hasło konta " + target + "? Konto zostanie rozłączone. Przy następnym połączeniu osoba używająca tego IGN będzie mogła zarejestrować nowe hasło.", List.of(), true),
                List.of(button("Tak, wyczyść", "changepass_confirm/" + token)),
                Optional.of(button("Anuluj", "changepass_edit/" + token)), 1));
    }
}
