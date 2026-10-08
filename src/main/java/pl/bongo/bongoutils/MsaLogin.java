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
        return switch (route) {
            case RESERVED_MSA -> "Połączenie odrzucone! (czy łączysz się w trybie OFFLINE?)\n"
                    + "IGN, z którego korzystasz jest zarejestrowane jako \"Konto MSA\".\n"
                    + "Zaloguj się do swojego konta MSA i spróbuj ponownie lub użyj innego IGN.";
            case OCCUPIED_MSA -> "IGN " + name + " jest już zajęty przez inne konto MSA!\n"
                    + "Zmień IGN (in-game nickname) aby móc połączyć się w trybie OFFLINE, lub zaloguj się do swojego konta MSA.";
            case OFFLINE_DISABLED -> "Ten nick wymaga zalogowanego konta Minecraft.";
            default -> throw new IllegalArgumentException("This route does not reject a connection");
        };
    }
    public static Component welcome() {
        return Component.literal("Połączono z konta MSA. Gracz został zarejestrowany automatycznie")
                .withStyle(ChatFormatting.GREEN);
    }
    public static Component registerJoined(Store store, String name, UUID uuid, boolean verified) throws IOException {
        if (!verified || !store.reserve(name, uuid)) return null;
        return welcome();
    }
}
