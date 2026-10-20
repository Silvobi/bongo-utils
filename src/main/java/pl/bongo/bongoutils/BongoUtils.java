package pl.bongo.bongoutils;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.util.concurrent.*;

public final class BongoUtils implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("BongoUtils");
    public static Store store;
    private static ConfigFile configFile;
    public static Config config() { return configFile.current(); }
    public static SkinService skins;
    public static SkinUi skinUi;
    public static ChangePassUi changePassUi;
    public static IgnWhitelist ignWhitelist;
    public static MailStore mailStore;
    public static MailUi mailUi;
    public static final pl.bongo.bongoutils.claims.ManageClaimUi claimUi=new pl.bongo.bongoutils.claims.ManageClaimUi();
    public static final LoginLimits limits = new LoginLimits();
    private static ThreadPoolExecutor workers, skinWorkers;
    public void onInitialize() {
        try {
            Path root = FabricLoader.getInstance().getConfigDir().resolve("bongoutils");
            Files.createDirectories(root);
            FileTransaction.recover(root.resolve("migration-backups"));
            configFile = new ConfigFile(root.resolve("config.json"));
            store = new Store(root); skins = new SkinService(); skinUi = new SkinUi(); changePassUi = new ChangePassUi();
            ignWhitelist = new IgnWhitelist(root.resolve("ign-whitelist.json"));
            Social.state = new SocialState(root.resolve("social.json"));
            mailStore = new MailStore(root.resolve("mail.json")); mailUi = new MailUi(mailStore);
            workers = pool(4, 32, "BongoUtils-auth"); skinWorkers = pool(2, 16, "BongoUtils-skin");
        } catch (Exception e) { throw new IllegalStateException("BongoUtils cannot read its configuration or storage", e); }
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> registerCommands(dispatcher, configFile));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Social.server = server;
            try { Social.state.remember(handler.player.getUUID(), handler.player.getGameProfile().name()); }
            catch (java.io.IOException e) { LOG.error("Cannot remember player for social tools", e); }
            Social.refresh();
            if(pl.bongo.bongoutils.social.SocialFeatures.active!=null) pl.bongo.bongoutils.social.SocialFeatures.active.joined(handler.player);
            ConnectionState state = (ConnectionState) ((ListenerConnection) handler).bongo$connection();
            try {
                var profile = handler.player.getGameProfile();
                Component welcome = MsaLogin.registerJoined(store, profile.name(), profile.id(), state.bongo$verified(), I18n.language(handler.player));
                if (welcome != null) handler.player.sendSystemMessage(welcome);
                mailUi.notifyUnread(handler.player);
            } catch (java.io.IOException e) {
                LOG.error("Cannot persist verified MSA registration", e);
                handler.disconnect(Component.literal(I18n.t(handler.player,"data.error")));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Rest.stop(handler.player, true);
            mailUi.disconnected(handler.player.getUUID());
            claimUi.forget(handler.player.getUUID());
            if(pl.bongo.bongoutils.social.SocialFeatures.active!=null) pl.bongo.bongoutils.social.SocialFeatures.active.disconnected(handler.player.getUUID());
            skinUi.disconnected(handler.player.getUUID()); changePassUi.disconnected(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STARTING.register(server->{
            try { pl.bongo.bongoutils.social.SocialFeatures.active=new pl.bongo.bongoutils.social.SocialFeatures(server); }
            catch(java.io.IOException e) { throw new IllegalStateException("Cannot load player statuses",e); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->pl.bongo.bongoutils.social.SocialFeatures.active=null);
        ServerLifecycleEvents.SERVER_STARTING.register(pl.bongo.bongoutils.claims.ClaimFlags::start);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{
            if(pl.bongo.bongoutils.social.SocialFeatures.active!=null) pl.bongo.bongoutils.social.SocialFeatures.active.tick();
            if(pl.bongo.bongoutils.claims.ClaimFlags.active!=null) pl.bongo.bongoutils.claims.ClaimFlags.active.tick();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->pl.bongo.bongoutils.claims.ClaimFlags.active=null);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> Social.server = server);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(Rest::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(Rest::stopAll);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { workers.shutdownNow(); skinWorkers.shutdownNow(); Social.server = null; });
        LOG.info("BongoUtils: server-only authentication dialogs and skins enabled.");
    }
    static void registerCommands(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher, ConfigFile configuration) {
        IgnWhitelistCommand.register(dispatcher);
        Social.register(dispatcher);
        if (mailUi != null) mailUi.register(dispatcher);
        Rest.register(dispatcher); WikiCommand.register(dispatcher); MigrationCommand.register(dispatcher);
        pl.bongo.bongoutils.social.SocialFeatures.register(dispatcher);
        dispatcher.register(Commands.literal("manageclaim").executes(c->claimUi.open(c.getSource().getPlayerOrException(),false))
            .then(Commands.literal("global").executes(c->claimUi.open(c.getSource().getPlayerOrException(),true))));
        dispatcher.register(Commands.literal("rules").executes(context -> RulesCommand.show(context.getSource(), configuration.current().rules)));
        dispatcher.register(Commands.literal("skin").executes(context -> skinUi.open(context.getSource().getPlayerOrException())));
        dispatcher.register(Commands.literal("changepass").requires(ChangePassUi::allowed)
                .executes(context -> changePassUi.open(context.getSource().getPlayerOrException())));
        dispatcher.register(Commands.literal("bongoutils").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.literal("reload").executes(context -> {
                    try {
                        configuration.reload();
                        context.getSource().sendSuccess(() -> Component.literal(I18n.t(context.getSource(),"reload.ok")), true);
                        return 1;
                    } catch (java.io.IOException e) {
                        LOG.error("Cannot reload BongoUtils config.json", e);
                        context.getSource().sendFailure(Component.literal(I18n.t(context.getSource(),"reload.error")));
                        return 0;
                    }
                }))
                .then(Commands.literal("resetpassword").requires(source -> source.getEntity() == null)
                        .then(Commands.argument("nick", StringArgumentType.word()).executes(context -> {
                    String nick = StringArgumentType.getString(context, "nick");
                    try {
                        store.reset(nick);
                        context.getSource().sendSuccess(() -> Component.literal(I18n.t(context.getSource(),"reset.ok",nick)), false);
                        return 1;
                    } catch (Exception e) { context.getSource().sendFailure(Component.literal(I18n.t(context.getSource(),"reset.error"))); return 0; }
                }))));
    }
    private static ThreadPoolExecutor pool(int count, int queue, String prefix) {
        return new ThreadPoolExecutor(count, count, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queue), runnable -> {
            Thread thread = new Thread(runnable, prefix); thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }
    public static boolean submit(Runnable task) { return submit(workers, task); }
    public static boolean submitSkin(Runnable task) { return submit(skinWorkers, task); }
    private static boolean submit(ThreadPoolExecutor executor, Runnable task) {
        try { executor.execute(task); return true; } catch (RejectedExecutionException e) { return false; }
    }
    public static final class Config {
        public boolean allowOffline = true;
        public String mineSkinApiKey = "";
        public Rules rules = new Rules();
    }
    public static final class RuleTranslation {
        public String title, url;
        public java.util.List<String> entries;
    }
    public static final class Rules {
        public String mode = "dialog";
        public String url = "";
        public String title = "Regulamin serwera";
        public java.util.List<String> entries = java.util.List.of();
        public RuleTranslation en;
    }
}
