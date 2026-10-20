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
    private static int enabled(CommandSourceStack source,boolean enabled) throws CommandSyntaxException {
        try {
            if(!BongoUtils.ignWhitelist.setEnabled(enabled)) throw error(source,enabled?"whitelist.already.on":"whitelist.already.off");
            source.sendSuccess(()->Component.literal(I18n.t(source,enabled?"whitelist.on":"whitelist.off")),true);
            enforce(source);return 1;
        } catch(IOException e) { throw ioError(source,e); }
    }
    private static int list(CommandSourceStack source) {
        var whitelist=BongoUtils.ignWhitelist;var names=whitelist.names();
        source.sendSuccess(()->Component.literal(I18n.t(source,"whitelist.list",I18n.t(source,whitelist.enabled()?"whitelist.enabled":"whitelist.disabled"),
            names.size(),names.isEmpty()?".":". "+String.join(", ",names))),false);
        return names.size();
    }
    private static int edit(CommandSourceStack source,String raw,boolean add) throws CommandSyntaxException {
        List<String> names=Arrays.asList(raw.strip().split("\\s+"));
        try {
            int count=add?BongoUtils.ignWhitelist.add(names):BongoUtils.ignWhitelist.remove(names);
            if(count==0) throw error(source,add?"whitelist.duplicate":"whitelist.missing");
            source.sendSuccess(()->Component.literal(I18n.t(source,add?"whitelist.add":"whitelist.remove",count,String.join(", ",names))),true);
            if(!add && BongoUtils.ignWhitelist.enabled()) BongoUtils.ignWhitelist.kickUnlisted(source.getServer());
            return count;
        } catch(IllegalArgumentException e) { throw error(source,"whitelist.invalid"); }
        catch(IOException e) { throw ioError(source,e); }
    }
    private static int reload(CommandSourceStack source) throws CommandSyntaxException {
        try {
            BongoUtils.ignWhitelist.reload();source.sendSuccess(()->Component.literal(I18n.t(source,"whitelist.reload")),true);
            enforce(source);return 1;
        } catch(IOException e) { throw ioError(source,e); }
    }
    private static void enforce(CommandSourceStack source) {
        if(BongoUtils.ignWhitelist.enabled()) BongoUtils.ignWhitelist.kickUnlisted(source.getServer());
        else source.getServer().kickUnlistedPlayers();
    }
    private static CommandSyntaxException ioError(CommandSourceStack source,IOException e) {
        BongoUtils.LOG.error("Cannot update IGN whitelist",e);return error(source,"whitelist.error");
    }
    private static CommandSyntaxException error(CommandSourceStack source,String key,Object... args) {
        return new SimpleCommandExceptionType(Component.literal(I18n.t(source,key,args))).create();
    }
}
