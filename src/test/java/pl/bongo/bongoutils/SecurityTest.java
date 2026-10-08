package pl.bongo.bongoutils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class SecurityTest {
    @TempDir Path directory;
    @Test void hashesHaveRandomSaltsAndRejectOtherPasswords() {
        String first = PasswordHash.create("test-password-123");
        String second = PasswordHash.create("test-password-123");
        assertNotEquals(first, second);
        assertTrue(PasswordHash.verify("test-password-123", first));
        assertFalse(PasswordHash.verify("different-password", first));
        assertFalse(PasswordHash.verify("test-password-123", "broken"));
        assertFalse(PasswordHash.verify("x", "pbkdf2-sha256$2147483647$AA==$AA=="));
    }
    @Test void persistenceNormalizesNamesAndProtectsAccounts() throws Exception {
        Store store = new Store(directory);
        assertTrue(store.register("Bongo", "hash"));
        assertFalse(store.register("bOnGo", "other"));
        assertEquals("hash", new Store(directory).password("BONGO"));
        UUID uuid = UUID.randomUUID(); store.reserve("Premium", uuid);
        assertTrue(new Store(directory).reserved("PREMIUM"));
        assertFalse(store.register("premium", "hash"));
        assertThrows(IllegalArgumentException.class, () -> store.password("../secrets"));
        store.reset("bongo"); assertNull(store.password("Bongo"));
    }
    @Test void acceptsDiscordQueryWithoutExtensionRequirement() throws Exception {
        SafePng.validateUri(URI.create("https://cdn.discordapp.com/attachments/123/456/skin.png?ex=abc&is=def&hm=123"));
        SafePng.validateUri(URI.create("https://media.discordapp.net/attachments/123/456/image?format=png"));
        assertThrows(IOException.class, () -> SafePng.validateUri(URI.create("file:///tmp/skin.png")));
        assertThrows(IOException.class, () -> SafePng.validateUri(URI.create("http://example.com/skin.png")));
        assertThrows(IOException.class, () -> SafePng.validateUri(URI.create("https://user:password@example.com/x.png")));
        assertThrows(IOException.class, () -> SafePng.validateUri(URI.create("https://example.com:8443/x.png")));
    }
    @Test void rejectsPrivateAndSpecialNetworks() throws Exception {
        for (String address : List.of("127.0.0.1", "10.0.0.1", "192.168.1.1", "172.16.0.1", "169.254.169.254", "100.64.0.1", "0.0.0.0", "::1", "fc00::1", "fe80::1", "2002:7f00:1::"))
            assertFalse(SafePng.publicAddress(InetAddress.getByName(address)), address);
        assertTrue(SafePng.publicAddress(InetAddress.getByName("8.8.8.8")));
        assertTrue(SafePng.publicAddress(InetAddress.getByName("2606:4700::1111")));
    }
    @Test void detectsContentAndDimensionsInsteadOfFilename() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB), "png", out);
        SafePng.validatePng(out.toByteArray());
        assertThrows(IOException.class, () -> SafePng.validatePng("<html>not a png</html>".getBytes()));
        out.reset(); ImageIO.write(new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB), "png", out);
        byte[] hugeDimensions = out.toByteArray();
        assertThrows(IOException.class, () -> SafePng.validatePng(hugeDimensions));
        assertThrows(IOException.class, () -> SafePng.validatePng(new byte[SafePng.MAX_BYTES + 1]));
    }
    @Test void lockoutsSurviveRepeatedAttempts() {
        LoginLimits limits = new LoginLimits();
        for (int i = 0; i < 5; i++) assertTrue(limits.allow("same-account", 5, 300_000));
        assertFalse(limits.allow("same-account", 5, 300_000));
        assertTrue(limits.allow("different-account", 5, 300_000));
    }
}
