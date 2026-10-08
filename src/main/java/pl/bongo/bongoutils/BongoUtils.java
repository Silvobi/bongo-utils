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
    public static Config config;
    public static SkinService skins;
    public static SkinUi skinUi;
    public static ChangePassUi changePassUi;
    public static IgnWhitelist ignWhitelist;
    public static final LoginLimits limits = new LoginLimits();
    private static ThreadPoolExecutor workers, skinWorkers;
    public void onInitialize() {
        try {
            Path root = FabricLoader.getInstance().getConfigDir().resolve("bongoutils");
            Files.createDirectories(root);
            Path file = root.resolve("config.json");
            config = Files.exists(file) ? Store.JSON.fromJson(Files.readString(file), Config.class) : new Config();
            if (config == null || config.mineSkinApiKey == null) throw new IllegalArgumentException("Invalid config.json");
            if (!Files.exists(file)) Store.atomic(file, Store.JSON.toJson(config));
            store = new Store(root); skins = new SkinService(); skinUi = new SkinUi(); changePassUi = new ChangePassUi();
            ignWhitelist = new IgnWhitelist(root.resolve("ign-whitelist.json"));
            workers = pool(4, 32, "BongoUtils-auth"); skinWorkers = pool(2, 16, "BongoUtils-skin");
        } catch (Exception e) { throw new IllegalStateException("BongoUtils cannot read its configuration or storage", e); }
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            IgnWhitelistCommand.register(dispatcher);
            dispatcher.register(Commands.literal("skin").executes(context -> skinUi.open(context.getSource().getPlayerOrException())));
            dispatcher.register(Commands.literal("changepass").requires(ChangePassUi::allowed)
                    .executes(context -> changePassUi.open(context.getSource().getPlayerOrException())));
            dispatcher.register(Commands.literal("bongoutils").requires(source -> source.getEntity() == null)
                    .then(Commands.literal("resetpassword").then(Commands.argument("nick", StringArgumentType.word()).executes(context -> {
                        String nick = StringArgumentType.getString(context, "nick");
                        try {
                            store.reset(nick);
                            context.getSource().sendSuccess(() -> Component.literal("Usunięto hasło konta offline: " + nick + ". Kolejne połączenie poprosi o rejestrację."), false);
                            return 1;
                        } catch (Exception e) { context.getSource().sendFailure(Component.literal("Nie udało się usunąć hasła.")); return 0; }
                    }))));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ConnectionState state = (ConnectionState) ((ListenerConnection) handler).bongo$connection();
            try {
                var profile = handler.player.getGameProfile();
                Component welcome = MsaLogin.registerJoined(store, profile.name(), profile.id(), state.bongo$verified());
                if (welcome != null) handler.player.sendSystemMessage(welcome);
            } catch (java.io.IOException e) {
                LOG.error("Cannot persist verified MSA registration", e);
                handler.disconnect(Component.literal("Błąd danych BongoUtils. Skontaktuj się z administratorem."));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            skinUi.disconnected(handler.player.getUUID()); changePassUi.disconnected(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { workers.shutdownNow(); skinWorkers.shutdownNow(); });
        LOG.info("BongoUtils: server-only authentication dialogs and skins enabled.");
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
    }
}
