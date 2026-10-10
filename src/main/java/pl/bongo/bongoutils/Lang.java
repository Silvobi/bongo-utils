package pl.bongo.bongoutils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.*;
import pl.bongo.bongoutils.mixin.ConfigurationInfoAccessor;
import pl.bongo.bongoutils.mixin.CommandSourceAccessor;

/** Server-side translations; locale is read from the recipient at delivery time. */
public final class Lang {
    private Lang() {}
    private static final Map<String, Properties> LANGUAGES = Map.of("pl", load("pl"), "en", load("en"));
    private static Properties load(String name) {
        Properties result = new Properties();
        try (var input = Lang.class.getResourceAsStream("/bongoutils/lang/" + name + ".properties")) {
            if (input == null) throw new IllegalStateException("Missing language: " + name);
            result.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
        return result;
    }
    public static String language(String locale) {
        return locale != null && locale.toLowerCase(Locale.ROOT).startsWith("pl_") ? "pl" : "en";
    }
    public static String locale(ServerPlayer player) { return player.clientInformation().language(); }
    public static String locale(ServerCommonPacketListenerImpl listener) {
        if (listener instanceof ServerGamePacketListenerImpl game) return locale(game.player);
        if (listener instanceof ServerConfigurationPacketListenerImpl configuration)
            return ((ConfigurationInfoAccessor) configuration).bongo$clientInformation().language();
        return null;
    }
    public static String text(String locale, String key, Object... args) {
        String pattern = LANGUAGES.get(language(locale)).getProperty(key);
        if (pattern == null) throw new IllegalArgumentException("Missing translation: " + key);
        return String.format(Locale.ROOT, pattern, args);
    }
    public static String text(ServerPlayer player, String key, Object... args) { return text(locale(player), key, args); }
    public static String text(ServerCommonPacketListenerImpl listener, String key, Object... args) { return text(locale(listener), key, args); }
    public static String text(CommandSourceStack source, String key, Object... args) {
        return text(source.getPlayer() == null ? null : locale(source.getPlayer()), key, args);
    }
    public static Set<String> keys(String language) { return LANGUAGES.get(language).stringPropertyNames(); }
    /** Preserve vanilla admin feedback rules, but render the message for each recipient. */
    public static void success(CommandSourceStack source, String key, Object... args) {
        source.sendSuccess(() -> Component.literal(text(source,key,args)),false);
        CommandSourceAccessor access=(CommandSourceAccessor)source;
        if (access.bongo$silent() || !access.bongo$source().shouldInformAdmins()) return;
        var server=source.getServer(); var rules=source.getLevel().getGameRules();
        if (rules.get(GameRules.SEND_COMMAND_FEEDBACK)) {
            for (ServerPlayer recipient:server.getPlayerList().getPlayers()) {
                if (recipient.commandSource()!=access.bongo$source() && server.getPlayerList().isOp(recipient.nameAndId()))
                    recipient.sendSystemMessage(Component.translatable("chat.type.admin",source.getDisplayName(),
                            Component.literal(text(recipient,key,args))).withStyle(ChatFormatting.GRAY,ChatFormatting.ITALIC));
            }
        }
        if (access.bongo$source()!=server && rules.get(GameRules.LOG_ADMIN_COMMANDS))
            server.sendSystemMessage(Component.translatable("chat.type.admin",source.getDisplayName(),
                    Component.literal(text((String)null,key,args))).withStyle(ChatFormatting.GRAY,ChatFormatting.ITALIC));
    }
    public static String error(ServerPlayer player, Exception error) {
        if (error instanceof Failure failure) return failure.text(locale(player));
        return text(player, "skin_failed");
    }
    /** Does not capture a worker thread's language or expose arbitrary network error text. */
    public static final class Failure extends IOException {
        private final String key;
        private final Object[] args;
        public Failure(String key, Object... args) {
            super(Lang.text((String) null, key, renderArgs(null, key, args)));
            this.key = key; this.args = args.clone();
        }
        private static Object[] renderArgs(String locale, String key, Object[] args) {
            if (!key.equals("skin_http")) return args;
            return new Object[] { args[0], Boolean.TRUE.equals(args[1]) ? Lang.text(locale, "skin_http_limit") : "." };
        }
        public String text(String locale) { return Lang.text(locale, key, renderArgs(locale, key, args)); }
    }
}
