package pl.bongo.bongoutils;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.*;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.common.*;
import net.minecraft.server.level.ServerPlayer;

/** Only the server chooses recipients, importance and mailbox IDs; OP-provided links are validated before delivery. */
public final class MailUi {
    static final int PAGE_SIZE = 8;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    private final MailStore store;
    private final Map<UUID, Session> sessions = new HashMap<>();
    public MailUi(MailStore store) { this.store = store; }
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mail")
                .executes(c -> list(c.getSource().getPlayerOrException(), true, 0))
                .then(Commands.literal("send")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(names(c.getSource()), b))
                                .executes(c -> sendOne(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "player")))))
                .then(Commands.literal("admin-send").requires(ChangePassUi::allowed)
                        .executes(c -> compose(c.getSource().getPlayerOrException(), "*", false)))
                .then(Commands.literal("send-multiple").requires(ChangePassUi::allowed)
                        .then(Commands.argument("players", StringArgumentType.greedyString())
                                .suggests((c, b) -> {
                                    String rest = b.getRemaining(); int last = rest.lastIndexOf(' ') + 1;
                                    Set<String> selected = new HashSet<>(Arrays.asList(rest.substring(0, last).toLowerCase(Locale.ROOT).split("\\s+")));
                                    return SharedSuggestionProvider.suggest(names(c.getSource()).stream()
                                            .filter(n -> !selected.contains(n.toLowerCase(Locale.ROOT))), b.createOffset(b.getStart() + last));
                                })
                                .executes(c -> compose(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "players"), true))))
                .then(Commands.literal("read")
                        .executes(c -> list(c.getSource().getPlayerOrException(), MailStore.Folder.NEW, 0))
                        .then(Commands.literal("all").executes(c -> list(c.getSource().getPlayerOrException(), false, 0)))
                        .then(Commands.literal("send").executes(c -> list(c.getSource().getPlayerOrException(), MailStore.Folder.SENT, 0)))
                        .then(Commands.literal("recive").executes(c -> list(c.getSource().getPlayerOrException(), MailStore.Folder.RECEIVED, 0)))
                        .then(Commands.literal("receive").executes(c -> list(c.getSource().getPlayerOrException(), MailStore.Folder.RECEIVED, 0)))
                        .then(Commands.literal("new").executes(c -> list(c.getSource().getPlayerOrException(), true, 0)))));
    }
    private int sendOne(ServerPlayer player, String name) throws CommandSyntaxException {
        if (name.equals("*")) throw error(player, "mail_admin_command");
        return compose(player, name, false);
    }
    private static List<String> names(CommandSourceStack source) {
        List<String> result = new ArrayList<>();
        UUID viewer = source.getPlayer() == null ? null : source.getPlayer().getUUID();
        Social.state.snapshot().names.forEach((id, name) -> {
            if (!Social.hidden(id) && (viewer == null || !Social.blocked(viewer, id))) result.add(name);
        });
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }
    static boolean deliverable(UUID sender, UUID recipient, boolean important) {
        return important || !Social.blocked(sender, recipient) && (sender.equals(recipient) || !Social.full(recipient));
    }
    private int compose(ServerPlayer player, String targets, boolean multiple) throws CommandSyntaxException {
        boolean broadcast = targets.equals("*");
        boolean important = broadcast || multiple;
        if (important && !ChangePassUi.allowed(player.createCommandSourceStack())) throw error(player, "mail_op_only");
        Set<UUID> recipients = new LinkedHashSet<>();
        if (broadcast) recipients.addAll(Social.state.snapshot().names.keySet());
        else {
            String[] names = targets.strip().split("\\s+");
            if (multiple && names.length < 2) throw error(player, "mail_multiple_usage");
            for (String name : names) {
                try { Store.key(name); } catch (IllegalArgumentException e) { throw error(player, "mail_unknown"); }
                UUID id = Social.state.find(name);
                if (id == null || !deliverable(player.getUUID(), id, important)) throw error(player, "mail_unknown");
                recipients.add(id);
            }
        }
        if (recipients.isEmpty()) throw error(player, "mail_empty_recipients");
        Session session = new Session(player, Stage.COMPOSE, List.copyOf(recipients), important,
                broadcast ? Lang.text(player, "mail_everyone") : (important ? Lang.text(player, "mail_admin_recipients") : "")
                        + String.join(", ", recipients.stream().map(Social.state::name).toList()), MailStore.Folder.NEW, 0, List.of(), null);
        compose(player, session, new Draft("", "", "", ""), ""); return 1;
    }
    public record Draft(String title, String body, String englishTitle, String englishBody) {}
    private void compose(ServerPlayer player, Session previous, Draft draft, String error) {
        Session next = previous.renew(Stage.COMPOSE, previous.page, List.of(), null);
        sessions.put(player.getUUID(), next);
        player.openDialog(MailDialogs.compose(Lang.locale(player), next.token, next.recipientLabel,
                next.important, draft, error));
    }
    public int list(ServerPlayer player, boolean unreadOnly, int requestedPage) {
        return list(player, unreadOnly ? MailStore.Folder.NEW : MailStore.Folder.ALL, requestedPage);
    }
    public int list(ServerPlayer player, MailStore.Folder folder, int requestedPage) {
        List<MailStore.Message> inbox = store.list(player.getUUID(), folder, Social::blocked);
        int pages = Math.max(1, (inbox.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.clamp(requestedPage, 0, pages - 1);
        List<MailStore.Message> entries = inbox.subList(Math.min(page * PAGE_SIZE, inbox.size()), Math.min((page + 1) * PAGE_SIZE, inbox.size()));
        Session next = new Session(player, Stage.LIST, List.of(), false, "", folder, page,
                entries.stream().map(MailStore.Message::id).toList(), null);
        sessions.put(player.getUUID(), next);
        List<String> labels = entries.stream().map(m -> label(m, player.getUUID(), Lang.locale(player), Social.state::name)).toList();
        player.openDialog(MailDialogs.list(Lang.locale(player), next.token, folder, page, pages, labels));
        return 1;
    }
    static String label(MailStore.Message message, UUID viewer, String locale, java.util.function.Function<UUID,String> name) {
        String person = message.sender().equals(viewer) && !message.recipients().isEmpty()
                ? String.join(", ", message.recipients().stream().map(name).toList()) : message.senderName();
        return (message.important() ? "⚠ " : "") + person + " - " + message.title(locale);
    }
    public static Component notification(String locale, int count) {
        return Component.literal(Lang.text(locale, "mail_notification")).withStyle(ChatFormatting.GOLD)
                .append(Component.literal(Integer.toString(count)).withStyle(style -> style.withColor(ChatFormatting.RED)
                        .withUnderlined(true).withClickEvent(new ClickEvent.RunCommand("/mail read new"))));
    }
    public void notifyUnread(ServerPlayer player) {
        int count = store.inbox(player.getUUID(), true, Social::blocked).size();
        if (count > 0) player.sendSystemMessage(notification(Lang.locale(player), count));
    }
    public void submit(ServerPlayer player, ServerboundCustomClickActionPacket packet) {
        if (!packet.id().getNamespace().equals("bongoutils") || !packet.id().getPath().startsWith("mail_")) return;
        Session session = sessions.get(player.getUUID());
        if (session == null || session.owner != player || !player.connection.isAcceptingMessages()) return;
        String[] action = packet.id().getPath().split("/", 2);
        if (action.length != 2 || !session.token.equals(action[1])) return;
        if (session.expires < System.nanoTime()) { close(player); say(player, "mail_expired"); return; }
        try {
            if (action[0].equals("mail_cancel")) { close(player); return; }
            switch (session.stage) {
                case COMPOSE -> {
                    if (!action[0].equals("mail_send") || !(packet.payload().orElse(null) instanceof CompoundTag data)) return;
                    Draft draft = new Draft(data.getString("title").orElse("").strip(), data.getString("body").orElse("").strip(),
                            data.getString("title_en").orElse("").strip(), data.getString("body_en").orElse("").strip());
                    if (session.important && !ChangePassUi.allowed(player.createCommandSourceStack())
                            || !data.getString("attachments").orElse("").isBlank()) {
                        close(player); say(player, "mail_op_only"); return;
                    }
                    try {
                        MailStore.validateText(draft.title, draft.body);
                        if (session.important) MailStore.validateText(draft.englishTitle, draft.englishBody);
                    }
                    catch (IllegalArgumentException e) {
                        compose(player, session, sanitized(draft), Lang.text(player, "mail_text_invalid")); return;
                    }
                    // Recheck privacy after composing: ignore or vanish may have changed while the dialog was open.
                    if (session.recipients.stream().anyMatch(id -> !deliverable(player.getUUID(), id, session.important))) {
                        close(player); say(player, "mail_unknown"); return;
                    }
                    if (!ChangePassUi.allowed(player.createCommandSourceStack()) && !BongoUtils.limits.allow("mail:" + player.getUUID(), 5, 60_000)) {
                        compose(player, session, draft, Lang.text(player, "mail_rate_limit")); return;
                    }
                    store.send(player.getUUID(), player.getGameProfile().name(), session.recipients, draft.title, draft.body,
                            draft.englishTitle, draft.englishBody, session.important);
                    close(player); say(player, "mail_sent", session.recipients.size());
                    var server = Objects.requireNonNull(player.level().getServer());
                    for (UUID id : session.recipients) {
                        ServerPlayer recipient = server.getPlayerList().getPlayer(id);
                        if (recipient != null) notifyUnread(recipient);
                    }
                }
                case LIST -> {
                    if (action[0].equals("mail_previous") && session.page > 0) list(player, session.folder, session.page - 1);
                    else if (action[0].equals("mail_next")) list(player, session.folder, session.page + 1);
                    else if (action[0].startsWith("mail_open_")) {
                        int index;
                        try { index = Integer.parseInt(action[0].substring("mail_open_".length())); } catch (NumberFormatException e) { return; }
                        if (index >= 0 && index < session.entries.size()) open(player, session, session.entries.get(index));
                    }
                }
                case MESSAGE -> {
                    if (action[0].equals("mail_back")) list(player, session.folder, session.page);
                }
            }
        } catch (IOException | RuntimeException e) {
            BongoUtils.LOG.error("Cannot access mailbox for {}", player.getUUID(), e);
            close(player); say(player, "mail_storage_error");
        }
    }
    private void open(ServerPlayer player, Session previous, UUID id) throws IOException {
        MailStore.Message message = store.list(player.getUUID(), previous.folder, Social::blocked).stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
        if (message == null) { list(player, previous.folder, previous.page); return; }
        boolean outgoing = message.sender().equals(player.getUUID()) && !message.recipients().isEmpty();
        if (!outgoing || previous.folder == MailStore.Folder.NEW || previous.folder == MailStore.Folder.RECEIVED)
            message = store.read(player.getUUID(), id);
        Session next = previous.renew(Stage.MESSAGE, previous.page, List.of(), id);
        sessions.put(player.getUUID(), next);
        player.openDialog(MailDialogs.message(Lang.locale(player), next.token, message,
                DATE.format(Instant.ofEpochMilli(message.sentAt()))));
    }
    private static Draft sanitized(Draft draft) {
        return new Draft(limit(draft.title, MailStore.MAX_TITLE), limit(draft.body, MailStore.MAX_BODY),
                limit(draft.englishTitle, MailStore.MAX_TITLE), limit(draft.englishBody, MailStore.MAX_BODY));
    }
    private static String limit(String text, int max) { return text.length() <= max ? text : text.substring(0,max); }
    private static CommandSyntaxException error(ServerPlayer player, String key) {
        return new SimpleCommandExceptionType(Component.literal(Lang.text(player, key))).create();
    }
    private static void say(ServerPlayer player, String key, Object... args) { player.sendSystemMessage(Component.literal(Lang.text(player, key, args))); }
    private void close(ServerPlayer player) { sessions.remove(player.getUUID()); player.connection.send(ClientboundClearDialogPacket.INSTANCE); }
    public void disconnected(UUID id) { sessions.remove(id); }
    private enum Stage { COMPOSE, LIST, MESSAGE }
    private static final class Session {
        final ServerPlayer owner;
        final String token = UUID.randomUUID().toString();
        final long expires = System.nanoTime() + 300_000_000_000L;
        final Stage stage;
        final List<UUID> recipients, entries;
        final boolean important;
        final MailStore.Folder folder;
        final String recipientLabel;
        final int page;
        final UUID message;
        Session(ServerPlayer owner, Stage stage, List<UUID> recipients, boolean important,
                String recipientLabel, MailStore.Folder folder, int page, List<UUID> entries, UUID message) {
            this.owner = owner; this.stage = stage; this.recipients = recipients; this.important = important;
            this.recipientLabel = recipientLabel;
            this.folder = folder; this.page = page; this.entries = entries; this.message = message;
        }
        Session renew(Stage next, int page, List<UUID> entries, UUID message) {
            return new Session(owner, next, recipients, important, recipientLabel, folder, page, entries, message);
        }
    }
}
