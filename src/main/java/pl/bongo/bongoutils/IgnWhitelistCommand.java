package pl.bongo.bongoutils;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.util.*;

public final class IgnWhitelistCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ign-whitelist").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .executes(c -> list(c.getSource()))
                .then(Commands.literal("on").executes(c -> enabled(c.getSource(), true)))
                .then(Commands.literal("off").executes(c -> enabled(c.getSource(), false)))
                .then(Commands.literal("list").executes(c -> list(c.getSource())))
                .then(Commands.literal("reload").executes(c -> reload(c.getSource())))
                .then(Commands.literal("add").then(Commands.argument("names", StringArgumentType.greedyString())
                        .suggests((c, builder) -> SharedSuggestionProvider.suggest(
                                c.getSource().getServer().getPlayerList().getPlayers().stream()
                                        .map(p -> p.getGameProfile().name()).filter(n -> !BongoUtils.ignWhitelist.contains(n)), builder))
                        .executes(c -> edit(c.getSource(), StringArgumentType.getString(c, "names"), true))))
                .then(Commands.literal("remove").then(Commands.argument("names", StringArgumentType.greedyString())
                        .suggests((c, builder) -> SharedSuggestionProvider.suggest(BongoUtils.ignWhitelist.names(), builder))
                        .executes(c -> edit(c.getSource(), StringArgumentType.getString(c, "names"), false)))));
    }
    private static int enabled(CommandSourceStack source, boolean enabled) throws CommandSyntaxException {
        try {
            if (!BongoUtils.ignWhitelist.setEnabled(enabled)) throw error("Lista IGN jest już " + (enabled ? "włączona." : "wyłączona."));
            source.sendSuccess(() -> Component.literal(enabled
                    ? "Włączono whitelistę IGN. Wejście zależy wyłącznie od wpisanego nicku; hasło i bany nadal obowiązują."
                    : "Wyłączono whitelistę IGN. Ponownie obowiązują ustawienia zwykłej whitelisty."), true);
            enforce(source); return 1;
        } catch (IOException e) { throw ioError(e); }
    }
    private static int list(CommandSourceStack source) {
        var whitelist = BongoUtils.ignWhitelist; var names = whitelist.names();
        source.sendSuccess(() -> Component.literal("Whitelista IGN: " + (whitelist.enabled() ? "włączona" : "wyłączona")
                + ". Nicków: " + names.size() + (names.isEmpty() ? "." : ". " + String.join(", ", names))), false);
        return names.size();
    }
    private static int edit(CommandSourceStack source, String raw, boolean add) throws CommandSyntaxException {
        List<String> names = Arrays.asList(raw.strip().split("\\s+"));
        try {
            int count = add ? BongoUtils.ignWhitelist.add(names) : BongoUtils.ignWhitelist.remove(names);
            if (count == 0) throw error(add ? "Podane nicki są już na whiteliście IGN." : "Podanych nicków nie ma na whiteliście IGN.");
            source.sendSuccess(() -> Component.literal((add ? "Dodano" : "Usunięto") + " " + count + " nicków "
                    + (add ? "do" : "z") + " whitelisty IGN: " + String.join(", ", names)), true);
            if (!add && BongoUtils.ignWhitelist.enabled()) BongoUtils.ignWhitelist.kickUnlisted(source.getServer());
            return count;
        } catch (IllegalArgumentException e) { throw error("Nick musi mieć 1–16 znaków: litery A–Z, cyfry lub _. Podaj nicki oddzielone spacją."); }
        catch (IOException e) { throw ioError(e); }
    }
    private static int reload(CommandSourceStack source) throws CommandSyntaxException {
        try {
            BongoUtils.ignWhitelist.reload();
            source.sendSuccess(() -> Component.literal("Przeładowano ign-whitelist.json."), true);
            enforce(source); return 1;
        } catch (IOException e) { throw ioError(e); }
    }
    private static void enforce(CommandSourceStack source) {
        if (BongoUtils.ignWhitelist.enabled()) BongoUtils.ignWhitelist.kickUnlisted(source.getServer());
        else source.getServer().kickUnlistedPlayers();
    }
    private static CommandSyntaxException ioError(IOException e) {
        BongoUtils.LOG.error("Cannot update IGN whitelist", e);
        return error("Nie można zapisać lub odczytać whitelisty IGN. Dotychczasowa lista pozostaje aktywna; szczegóły w logu serwera.");
    }
    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }
}
