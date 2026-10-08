package pl.bongo.bongoutils;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.*;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.*;
import java.io.IOException;
import java.util.*;

public final class Social {
    public static SocialState state;
    public static volatile MinecraftServer server;
    public static boolean announcing;
    public static boolean full(Entity entity) { return entity instanceof ServerPlayer p && full(p.getUUID()); }
    public static boolean full(UUID id) { return state != null && state.mode(id) == SocialState.Mode.VANISH; }
    public static boolean hidden(UUID id) { return state != null && state.mode(id) != SocialState.Mode.VISIBLE; }
    public static boolean blocked(UUID a, UUID b) { return state != null && state.blocked(a, b); }
    /** Optional-mod integration API, looked up once by Bongo's Teleports. */
    public static boolean canTeleport(UUID a, UUID b) { return !blocked(a, b) && !full(a) && !full(b); }
    public static boolean commandVisible(CommandSourceStack source, ServerPlayer target) {
        return source.getPlayer() == null || source.getPlayer() == target || !hidden(target.getUUID());
    }
    public static List<String> names(CommandSourceStack source) {
        return source.getServer().getPlayerList().getPlayers().stream().filter(p -> commandVisible(source, p))
                .map(p -> p.getGameProfile().name()).toList();
    }
    public static boolean chatAllowed(ServerPlayer recipient, UUID sender) {
        return recipient.getUUID().equals(sender) || !full(sender) && !blocked(recipient.getUUID(), sender);
    }
    public static UUID chatSender(OutgoingChatMessage message, ChatType.Bound type) {
        if (message instanceof OutgoingChatMessage.Player player && !player.message().isSystem()) return player.message().sender();
        if (server == null) return null;
        String name = type.name().getString();
        return server.getPlayerList().getPlayers().stream().filter(p -> p.getDisplayName().getString().equals(name)
                || p.getGameProfile().name().equals(name)).map(ServerPlayer::getUUID).findFirst().orElse(null);
    }
    public static boolean polish(ServerPlayer p) { return p.clientInformation().language().startsWith("pl"); }
    private static Component text(ServerPlayer p, String pl, String en) { return Component.literal(polish(p) ? pl : en); }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ignore").executes(c -> list(c.getSource().getPlayerOrException()))
                .then(Commands.argument("nick", StringArgumentType.word()).suggests((c,b) -> SharedSuggestionProvider.suggest(names(c.getSource()), b))
                        .executes(c -> ignore(c.getSource().getPlayerOrException(), StringArgumentType.getString(c,"nick")))));
        for (String command : List.of("vanish", "semi-vanish")) {
            SocialState.Mode mode = command.equals("vanish") ? SocialState.Mode.VANISH : SocialState.Mode.SEMI;
            var node = Commands.literal(command).requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                    .executes(c -> toggle(c.getSource().getPlayerOrException(), mode, false));
            if (mode == SocialState.Mode.VANISH) node.then(Commands.literal("fake")
                    .executes(c -> toggle(c.getSource().getPlayerOrException(), mode, true)));
            dispatcher.register(node);
        }
        var visibility=Commands.literal("visibility").requires(s->s.getEntity()==null)
                .then(Commands.argument("nick",StringArgumentType.word()).then(Commands.argument("mode",StringArgumentType.word())
                    .suggests((c,b)->SharedSuggestionProvider.suggest(List.of("visible","semi","vanish"),b)).executes(c->{
                        UUID id=state.find(StringArgumentType.getString(c,"nick"));
                        if(id==null)throw new SimpleCommandExceptionType(Component.literal("Nieznany gracz.")).create();
                        try {
                            SocialState.Mode mode=SocialState.Mode.valueOf(StringArgumentType.getString(c,"mode").toUpperCase(Locale.ROOT));
                            ServerPlayer target=c.getSource().getServer().getPlayerList().getPlayer(id);
                            if(target!=null){target.closeContainer();if(mode==SocialState.Mode.VANISH){target.stopRiding();target.ejectPassengers();}}
                            state.mode(id,mode,false);refresh();
                            c.getSource().sendSuccess(()->Component.literal("Widoczność "+state.name(id)+": "+mode),false);return 1;
                        } catch(IllegalArgumentException e){throw new SimpleCommandExceptionType(Component.literal("Tryby: visible, semi, vanish.")).create();}
                        catch(IOException e){throw new SimpleCommandExceptionType(Component.literal("Błąd zapisu trybu widoczności.")).create();}
                    })));
        dispatcher.register(Commands.literal("bongoutils").requires(Commands.hasPermission(Commands.LEVEL_ADMINS)).then(visibility));
        // Vanilla /list leaks hidden names and counts; replace only its execution, preserve its children.
        dispatcher.register(Commands.literal("list").executes(c -> {
            List<String> visible = names(c.getSource());
            c.getSource().sendSuccess(() -> Component.translatable("commands.list.players", visible.size(),
                    c.getSource().getServer().getMaxPlayers(), Component.literal(String.join(", ", visible))), false);
            return visible.size();
        }).then(Commands.literal("uuids").executes(c -> {
            var visible=c.getSource().getServer().getPlayerList().getPlayers().stream().filter(p->commandVisible(c.getSource(),p)).toList();
            c.getSource().sendSuccess(()->Component.literal(String.join(", ",visible.stream().map(p->p.getGameProfile().name()+" ("+p.getUUID()+")").toList())),false);
            return visible.size();
        })));
    }
    private static int list(ServerPlayer player) {
        List<String> names = state.ignoredBy(player.getUUID()).stream().map(state::name).sorted().toList();
        player.sendSystemMessage(text(player, "Ignorowani: ", "Ignored: ").copy().append(String.join(", ", names))); return names.size();
    }
    private static int ignore(ServerPlayer player, String name) throws CommandSyntaxException {
        try {
            Store.key(name);
            UUID target = state.find(name);
            ServerPlayer online = server.getPlayerList().getPlayerByName(name);
            if (online != null) target = online.getUUID();
            if (target == null || hidden(target) && !state.ignores(player.getUUID(), target)) throw error(player,"Nie ma takiego gracza.","Player not found.");
            if (player.getUUID().equals(target)) throw error(player,"Nie możesz ignorować siebie.","You cannot ignore yourself.");
            boolean enabled = state.toggleIgnore(player.getUUID(), target);
            player.sendSystemMessage(text(player, enabled ? "Ignorowanie włączone: " : "Ignorowanie wyłączone: ",
                    enabled ? "Ignore enabled: " : "Ignore disabled: ").copy().append(state.name(target)));
            refresh(); return 1;
        } catch (IOException e) { BongoUtils.LOG.error("Cannot save ignore", e); throw error(player,"Błąd zapisu listy ignorowanych.","Could not save ignore list."); }
        catch (IllegalArgumentException e) { throw error(player,"Nieprawidłowy nick.","Invalid player name."); }
    }
    public static int toggle(ServerPlayer player, SocialState.Mode requested, boolean fake) throws CommandSyntaxException {
        UUID id = player.getUUID(); SocialState.Mode old = state.mode(id);
        SocialState.Mode next = old == requested ? SocialState.Mode.VISIBLE : requested;
        boolean announce = fake || state.fakeQuit(id);
        try {
            player.closeContainer();
            state.mode(id, next, next == SocialState.Mode.VANISH && announce);
            if (next == SocialState.Mode.VANISH) { player.stopRiding(); player.ejectPassengers(); }
            refresh();
            player.sendSystemMessage(text(player,"Tryb widoczności: ","Visibility mode: ").copy().append(next.name()));
            if (announce && (old == SocialState.Mode.VANISH || next == SocialState.Mode.VANISH)) {
                Component message = Component.translatable(next == SocialState.Mode.VANISH ? "multiplayer.player.left" : "multiplayer.player.joined", player.getDisplayName()).withStyle(ChatFormatting.YELLOW);
                announcing = true;
                try {
                    for (ServerPlayer viewer : server.getPlayerList().getPlayers()) if (viewer != player) viewer.sendSystemMessage(message);
                    server.sendSystemMessage(message);
                } finally { announcing = false; }
            }
            return 1;
        } catch (IOException e) { BongoUtils.LOG.error("Cannot save vanish", e); throw error(player,"Błąd zapisu trybu ukrycia.","Could not save visibility mode."); }
    }
    private static CommandSyntaxException error(ServerPlayer p, String pl, String en) { return new SimpleCommandExceptionType(text(p,pl,en)).create(); }
    public static void refresh() {
        if (server == null) return;
        List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
        // Removal also clears previously cached names. Initialization is filtered per recipient.
        for (ServerPlayer viewer : players) {
            List<UUID> hidden = players.stream().filter(p -> p != viewer && full(p)).map(ServerPlayer::getUUID).toList();
            if (!hidden.isEmpty()) viewer.connection.send(new ClientboundPlayerInfoRemovePacket(hidden));
            viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(players));
        }
        for (var level : server.getAllLevels()) ((pl.bongo.bongoutils.mixin.ChunkMapAccess) level.getChunkSource().chunkMap).bongo$entities().values().forEach(e -> e.updatePlayers(players));
    }
    public static ClientboundPlayerInfoUpdatePacket.Entry tab(ServerPlayer viewer, ClientboundPlayerInfoUpdatePacket.Entry entry) {
        if (viewer.getUUID().equals(entry.profileId())) return entry;
        if (full(entry.profileId())) return null;
        boolean ignored = blocked(viewer.getUUID(), entry.profileId());
        Component display = entry.displayName(); int latency = entry.latency(); GameType game = entry.gameMode();
        if (ignored) {
            boolean masked = state.ignores(entry.profileId(), viewer.getUUID());
            display = Component.literal(masked ? polish(viewer) ? "Ignorowany" : "Ignored"
                    : entry.profile() != null ? entry.profile().name() : state.name(entry.profileId()))
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            // PlayerInfo gameMode also affects client-side entity rendering. Style only the TAB component.
            latency = -1;
        }
        return new ClientboundPlayerInfoUpdatePacket.Entry(entry.profileId(), entry.profile(), !hidden(entry.profileId()) && entry.listed(), latency,
                game, display, entry.showHat(), entry.listOrder(), entry.chatSession());
    }
    public static boolean hiddenSystem(Component message) {
        if (announcing || state == null) return false;
        if (message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated) {
            String key = translated.getKey();
            if (key.startsWith("multiplayer.player.") || key.startsWith("death.") || key.startsWith("chat.type.advancement")) {
                for (Object argument : translated.getArgs()) if (argument instanceof Component component) {
                    String name = component.getString();
                    for (var entry : state.snapshot().names.entrySet()) if (full(entry.getKey())
                            && java.util.regex.Pattern.compile("(?<![A-Za-z0-9_])"+java.util.regex.Pattern.quote(entry.getValue())+"(?![A-Za-z0-9_])").matcher(name).find()) return true;
                    if(server!=null)for(ServerPlayer player:server.getPlayerList().getPlayers())if(full(player)
                            && java.util.regex.Pattern.compile("(?<![A-Za-z0-9_])"+java.util.regex.Pattern.quote(player.getGameProfile().name())+"(?![A-Za-z0-9_])").matcher(name).find())return true;
                }
            }
        }
        return message.getSiblings().stream().anyMatch(Social::hiddenSystem);
    }
}
