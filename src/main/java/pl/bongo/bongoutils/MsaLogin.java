package pl.bongo.bongoutils;

import java.util.UUID;
import java.io.IOException;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** A client UUID selects a handshake, never proves ownership of an MSA account. */
public final class MsaLogin {
    private MsaLogin() {}
    public enum Route { VERIFY_SESSION, OFFLINE, RESERVED_MSA, OCCUPIED_MSA, OFFLINE_DISABLED }
    public static Route route(UUID official, UUID claimed, boolean reserved, boolean allowOffline) {
        if (official != null && official.equals(claimed)) return Route.VERIFY_SESSION;
        if (reserved) return Route.RESERVED_MSA;
        if (official != null) return Route.OCCUPIED_MSA;
        return allowOffline ? Route.OFFLINE : Route.OFFLINE_DISABLED;
    }
    public static String rejection(Route route, String name) {
        return rejection(null, route, name);
    }
    public static String rejection(String locale, Route route, String name) {
        return switch (route) {
            case RESERVED_MSA -> Lang.text(locale, "msa_reserved");
            case OCCUPIED_MSA -> Lang.text(locale, "msa_occupied", name);
            case OFFLINE_DISABLED -> Lang.text(locale, "msa_required");
            default -> throw new IllegalArgumentException("This route does not reject a connection");
        };
    }
    public static Component welcome(String locale) {
        return Component.literal(Lang.text(locale, "msa_welcome")).withStyle(ChatFormatting.GREEN);
    }
    public static Component registerJoined(Store store, String name, UUID uuid, boolean verified) throws IOException {
        return registerJoined(store, name, uuid, verified, null);
    }
    public static Component registerJoined(Store store, String name, UUID uuid, boolean verified, String locale) throws IOException {
        if (!verified || !store.reserve(name, uuid)) return null;
        return welcome(locale);
    }
}
