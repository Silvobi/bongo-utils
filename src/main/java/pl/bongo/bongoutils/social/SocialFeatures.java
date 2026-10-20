package pl.bongo.bongoutils.social;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.level.storage.LevelResource;
import pl.bongo.bongoutils.BongoUtils;
import pl.bongo.bongoutils.I18n;
import pl.bongo.bongoutils.mixin.MsgCommandAccessor;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public final class SocialFeatures {
    public static SocialFeatures active;
    private final MinecraftServer server;
    private final Statuses statuses;
    private final Map<UUID,Activity> activity=new HashMap<>();
    private final Map<UUID,UUID> replies=new HashMap<>();
    private static final Pattern MENTION=Pattern.compile("(?<![A-Za-z0-9_@])@([A-Za-z0-9_]{1,16})(?![A-Za-z0-9_])");
    public SocialFeatures(MinecraftServer server) throws IOException {
        this.server=server;statuses=new Statuses(server.getWorldPath(LevelResource.ROOT).resolve("bongo-statuses.json"));
    }
    private Activity activity(ServerPlayer p) {
        return activity.computeIfAbsent(p.getUUID(),id->new Activity(p.level().dimension(),p.getX(),p.getY(),p.getZ(),System.nanoTime()));
    }
    public void joined(ServerPlayer p) { activity(p);refresh(p); }
    public void disconnected(UUID id) { activity.remove(id);replies.remove(id); }
    public void tick() {
        long now=System.nanoTime();
        for(ServerPlayer p:server.getPlayerList().getPlayers())
            if(activity(p).sample(p.level().dimension(),p.getX(),p.getY(),p.getZ(),now)) changed(p);
    }
    private void changed(ServerPlayer p) {
        String key=activity(p).afk()?"afk.on":"afk.off",name=p.getGameProfile().name();
        server.getPlayerList().broadcastSystemMessage(Component.literal(I18n.t((String)null,key,name)).withStyle(ChatFormatting.GRAY),
            recipient->Component.literal(I18n.t(recipient,key,name)).withStyle(ChatFormatting.GRAY),false);
        refresh(p);
    }
    private void refresh(ServerPlayer p) {
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,p));
    }
    /** Compose at the packet boundary, after other mods (including clan prefixes) resolved their TAB name. */
    public Component tab(UUID id,Component original) {
        ServerPlayer p=server.getPlayerList().getPlayer(id);if(p==null) return original;
        Activity state=activity.get(id);
        String status=state!=null && state.afk()?"AFK":statuses.get(id);
        if(status==null) return original;
        Component base=original!=null?original:PlayerTeam.formatNameForTeam(p.getTeam(),Component.literal(p.getGameProfile().name()));
        return Component.empty().append(Component.literal("["+status+"] ").withStyle(ChatFormatting.GRAY,ChatFormatting.ITALIC)).append(base);
    }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("afk").executes(c->{
            var p=c.getSource().getPlayerOrException();var social=active;
            social.activity(p).toggle(p.level().dimension(),p.getX(),p.getY(),p.getZ(),System.nanoTime());social.changed(p);return 1;
        }));
        dispatcher.register(Commands.literal("status").executes(c->active.status(c.getSource().getPlayerOrException(),null))
            .then(Commands.argument("status",StringArgumentType.greedyString()).executes(c->active.status(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"status").strip()))));
        dispatcher.register(Commands.literal("m").redirect(dispatcher.getRoot().getChild("msg")));
        dispatcher.register(Commands.literal("r").then(Commands.argument("message",MessageArgument.message()).executes(c->{
            var p=c.getSource().getPlayerOrException();var social=active;UUID targetId=social.replies.get(p.getUUID());
            var target=targetId==null?null:social.server.getPlayerList().getPlayer(targetId);
            if(target==null) { c.getSource().sendFailure(Component.literal(I18n.t(p,targetId==null?"reply.none":"reply.offline")));return 0; }
            MessageArgument.resolveChatMessage(c,"message",message->{
                var current=social.server.getPlayerList().getPlayer(targetId);
                if(current==null) c.getSource().sendFailure(Component.literal(I18n.t(p,"reply.offline")));
                else MsgCommandAccessor.bongo$sendMessage(c.getSource(),List.of(current),message);
            });return 1;
        })));
    }
    private int status(ServerPlayer p,String text) {
        try {
            statuses.set(p.getUUID(),text);refresh(p);
            p.sendSystemMessage(Component.literal(I18n.t(p,text==null?"status.clear":"status.set",text)).withStyle(ChatFormatting.GRAY));return 1;
        } catch(IllegalArgumentException e) { p.sendSystemMessage(Component.literal(I18n.t(p,"status.invalid")).withStyle(ChatFormatting.RED)); }
        catch(IOException e) { BongoUtils.LOG.error("Cannot save custom status",e);p.sendSystemMessage(Component.literal(I18n.t(p,"status.error")).withStyle(ChatFormatting.RED)); }
        return 0;
    }
    public void conversation(CommandSourceStack source,Collection<ServerPlayer> targets,PlayerChatMessage message) {
        if(!(source.getEntity() instanceof ServerPlayer sender)) return;
        for(var target:targets) {
            if(sender.getUUID().equals(target.getUUID()) || (source.shouldFilterMessageTo(target) && message.isFullyFiltered())) continue;
            replies.put(sender.getUUID(),target.getUUID());replies.put(target.getUUID(),sender.getUUID());
        }
    }
    public PlayerChatMessage chat(ServerPlayer recipient,PlayerChatMessage message,ChatType.Bound type) {
        // Only public player chat. A filtered body must never be reconstructed from its unfiltered decoration.
        if(!type.chatType().is(ChatType.CHAT) || !message.filterMask().isEmpty()) return message;
        var online=new HashMap<String,UUID>();
        for(var p:server.getPlayerList().getPlayers()) online.put(p.getGameProfile().name().toLowerCase(Locale.ROOT),p.getUUID());
        boolean[] found={false},ping={false};var result=Component.empty();
        message.decoratedContent().visit((style,text)->{
            var matcher=MENTION.matcher(text);int end=0;
            while(matcher.find()) {
                UUID target=online.get(matcher.group(1).toLowerCase(Locale.ROOT));if(target==null) continue;
                found[0]=true;boolean mentioned=target.equals(recipient.getUUID());ping[0]|=mentioned;
                result.append(Component.literal(text.substring(end,matcher.start())).setStyle(style));
                result.append(Component.literal(matcher.group()).setStyle(style.withColor(mentioned?ChatFormatting.BLUE:ChatFormatting.YELLOW)));end=matcher.end();
            }
            result.append(Component.literal(text.substring(end)).setStyle(style));return Optional.empty();
        },Style.EMPTY);
        if(ping[0]) {
            var sender=server.getPlayerList().getPlayer(message.sender());
            String name=sender==null?type.name().getString():sender.getGameProfile().name();
            recipient.sendOverlayMessage(Component.literal(I18n.t(recipient,"mention",name)).withStyle(ChatFormatting.BLUE));
            recipient.connection.send(new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_PLING,SoundSource.MASTER,recipient.getX(),recipient.getY(),recipient.getZ(),1f,1f,recipient.getRandom().nextLong()));
        }
        return found[0]?message.withUnsignedContent(result):message;
    }
}
