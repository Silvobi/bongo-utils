package pl.bongo.bongoutils;

import com.mojang.brigadier.CommandDispatcher;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.*;

/** Session-only, manually toggled AFK. Team overlays exist only on each viewer's client. */
public final class Afk {
    private static final Set<UUID> active = new HashSet<>();
    private static final Map<ServerPlayer, Map<UUID, View>> views = new IdentityHashMap<>();
    private record View(String playerName, PlayerTeam team, ClientboundSetPlayerTeamPacket.Parameters parameters, String sourceTeam) {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("afk").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            boolean enabled = active.add(player.getUUID());
            if (!enabled) active.remove(player.getUUID());
            player.sendSystemMessage(Component.literal(Lang.text(player, enabled ? "afk_on" : "afk_off")));
            Social.refresh();
            return 1;
        }));
    }
    public static boolean active(UUID id) { return active.contains(id); }
    public static Component suffix() { return Component.literal(" AFK").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC); }
    public static Component append(Component name) { return name.copy().append(suffix()); }

    /** Preserve prefixes, colors and existing suffixes without changing the real scoreboard. */
    static PlayerTeam overlay(String name, String playerName, PlayerTeam original, PlayerTeam viewerTeam) {
        PlayerTeam team = new PlayerTeam(new Scoreboard(), name);
        if (original != null) {
            team.setDisplayName(original.getDisplayName());
            team.setPlayerPrefix(original.getPlayerPrefix());
            team.setPlayerSuffix(original.getPlayerSuffix());
            team.setColor(original.getColor());
            team.unpackOptions(original.packOptions());
            team.setDeathMessageVisibility(original.getDeathMessageVisibility());
            // The virtual team has a different identity. Resolve relative rules using real teams.
            team.setNameTagVisibility(visibility(original.getNameTagVisibility(), original.isAlliedTo(viewerTeam)));
            team.setCollisionRule(collision(original.getCollisionRule(), original.isAlliedTo(viewerTeam)));
        }
        team.setPlayerSuffix(append(team.getPlayerSuffix()));
        team.getPlayers().add(playerName);
        return team;
    }
    static Team.Visibility visibility(Team.Visibility rule, boolean allied) {
        return switch (rule) {
            case HIDE_FOR_OTHER_TEAMS -> allied ? Team.Visibility.ALWAYS : Team.Visibility.NEVER;
            case HIDE_FOR_OWN_TEAM -> allied ? Team.Visibility.NEVER : Team.Visibility.ALWAYS;
            default -> rule;
        };
    }
    static Team.CollisionRule collision(Team.CollisionRule rule, boolean allied) {
        return switch (rule) {
            // Match EntitySelector.pushableBy, including vanilla's counterintuitive enum names.
            case PUSH_OTHER_TEAMS -> allied ? Team.CollisionRule.ALWAYS : Team.CollisionRule.NEVER;
            case PUSH_OWN_TEAM -> allied ? Team.CollisionRule.NEVER : Team.CollisionRule.ALWAYS;
            default -> rule;
        };
    }
    private static boolean visible(ServerPlayer viewer, ServerPlayer target) {
        return viewer == target || !Social.full(target) && !Social.blocked(viewer.getUUID(), target.getUUID());
    }
    /** Reconcile joins, team edits, ignore and vanish changes; unchanged views send no packets. */
    public static void tick(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        views.keySet().removeIf(viewer -> !players.contains(viewer));
        for (ServerPlayer viewer : players) {
            Map<UUID, View> cached = views.computeIfAbsent(viewer, ignored -> new HashMap<>());
            Set<UUID> wanted = new HashSet<>();
            for (ServerPlayer target : players) {
                UUID id = target.getUUID();
                if (!active(id) || !visible(viewer, target)) continue;
                wanted.add(id);
                String teamName = "bu_afk_" + id.toString().replace("-", "");
                // Avoid a collision with an administrator-created real team.
                while (server.getScoreboard().getPlayerTeam(teamName) != null) teamName += "_";
                PlayerTeam real = target.getTeam();
                PlayerTeam team = overlay(teamName, target.getGameProfile().name(), real, viewer.getTeam());
                var parameters = new ClientboundSetPlayerTeamPacket.Parameters(team);
                View old = cached.get(id);
                String source = real == null ? null : real.getName();
                if (old != null && old.team().getName().equals(teamName) && old.parameters().equals(parameters)
                        && Objects.equals(old.sourceTeam(), source) && old.playerName().equals(target.getGameProfile().name())) continue;
                if (old != null && !old.team().getName().equals(teamName)) viewer.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(old.team()));
                cached.put(id, new View(target.getGameProfile().name(), team, parameters, source));
                viewer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, old == null || !old.team().getName().equals(teamName)));
                // Team membership changes can also change vanilla's fallback TAB formatting.
                if (old != null) viewer.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket(
                        net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, target));
            }
            for (var iterator = cached.entrySet().iterator(); iterator.hasNext();) {
                var entry = iterator.next();
                if (wanted.contains(entry.getKey())) continue;
                View old = entry.getValue(); iterator.remove();
                viewer.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(old.team()));
                // Restore current membership (which may have changed while AFK), never an old snapshot.
                PlayerTeam real = server.getScoreboard().getPlayersTeam(old.playerName());
                if (real != null) viewer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(real, old.playerName(), ClientboundSetPlayerTeamPacket.Action.ADD));
            }
        }
    }
    public static void disconnected(ServerPlayer player) {
        active.remove(player.getUUID()); views.remove(player);
        // No online player object is retained after disconnect.
        for (var entry : views.entrySet()) {
            View old = entry.getValue().remove(player.getUUID());
            if (old != null) {
                entry.getKey().connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(old.team()));
                PlayerTeam real = player.getTeam();
                if (real != null) entry.getKey().connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(real, old.playerName(), ClientboundSetPlayerTeamPacket.Action.ADD));
            }
        }
    }
    public static void clear() { active.clear(); views.clear(); }

    /** Real team updates must not steal members from their temporary client overlay. */
    public static ClientboundSetPlayerTeamPacket filter(ServerPlayer viewer, ClientboundSetPlayerTeamPacket packet) {
        Map<UUID, View> cached = views.get(viewer);
        if (cached == null || cached.isEmpty() || cached.values().stream().anyMatch(v -> v.team().getName().equals(packet.getName()))) return packet;
        Set<String> overlaid = new HashSet<>();
        cached.values().forEach(v -> overlaid.add(v.playerName()));
        var players = packet.getPlayers().stream().filter(name -> !overlaid.contains(name)).toList();
        if (players.size() == packet.getPlayers().size()) return packet;
        int method = packet.getTeamAction() == ClientboundSetPlayerTeamPacket.Action.ADD ? 0
                : packet.getTeamAction() == ClientboundSetPlayerTeamPacket.Action.REMOVE ? 1
                : packet.getPlayerAction() == ClientboundSetPlayerTeamPacket.Action.ADD ? 3
                : packet.getPlayerAction() == ClientboundSetPlayerTeamPacket.Action.REMOVE ? 4 : 2;
        return new ClientboundSetPlayerTeamPacket(packet.getName(), method, packet.getParameters(), players);
    }
}
