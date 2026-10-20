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
    public static String rejection(Route route,String name) { return rejection(route,name,null); }
    public static String rejection(Route route,String name,String language) {
        return switch(route) {
            case RESERVED_MSA -> I18n.t(language,"msa.reserved");
            case OCCUPIED_MSA -> I18n.t(language,"msa.occupied",name);
            case OFFLINE_DISABLED -> I18n.t(language,"msa.disabled");
            default -> throw new IllegalArgumentException("This route does not reject a connection");
        };
    }
    public static Component welcome() { return welcome(null); }
    public static Component welcome(String language) {
        return Component.literal(I18n.t(language,"msa.welcome")).withStyle(ChatFormatting.GREEN);
    }
    public static Component registerJoined(Store store,String name,UUID uuid,boolean verified) throws IOException {
        return registerJoined(store,name,uuid,verified,null);
    }
    public static Component registerJoined(Store store,String name,UUID uuid,boolean verified,String language) throws IOException {
        if(!verified || !store.reserve(name,uuid)) return null;
        return welcome(language);
    }
}
