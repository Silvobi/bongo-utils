package pl.bongo.bongoutils;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Runs against a disposable localhost server with RCON and enforce-whitelist=true. */
public final class WhitelistIntegrationHarness {
    static int rconPort;
    static String password;
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        IntegrationHarness.port = Integer.parseInt(args[0]); rconPort = Integer.parseInt(args[1]); password = args[2];
        String name = "IgTest" + Long.toString(System.currentTimeMillis(), 36);
        String another = "Other" + Long.toString(System.currentTimeMillis(), 36);
        command("ign-whitelist off"); command("whitelist on");
        try {
            checkRejected(name, "native UUID whitelist rejects the offline test name before IGN mode");
            String added = command("ign-whitelist add " + name + " " + another);
            IntegrationHarness.check(added.contains("Dodano 2"), "console adds multiple offline IGN names without profile resolution");
            command("ign-whitelist on");
            IntegrationHarness.check(command("ign-whitelist list").contains(name), "list contains saved IGN");
            checkRejected("UnlistedTest", "IGN gate rejects an unlisted name");
            try (IntegrationHarness.Bot bot = bot(name)) {
                IntegrationHarness.check(!bot.disconnected, "IGN entry admits offline UUID despite enabled native whitelist");
                var registration = bot.dialog();
                bot.auth(registration, "integration-whitelist-password", "integration-whitelist-password"); bot.finish();
                bot.prepareGame();
                bot.send(new ServerboundChatCommandPacket("ign-whitelist add IntruderTest"));
                Thread.sleep(300);
                IntegrationHarness.check(!command("ign-whitelist list").contains("IntruderTest"), "non-admin cannot modify IGN whitelist");
                command("whitelist reload"); bot.waitActive(500);
                IntegrationHarness.check(true, "vanilla enforcement does not kick an IGN-listed player");
                IntegrationHarness.check(command("op " + name).toLowerCase().contains("operator"), "test account granted operator for bypass check");
                command("ign-whitelist remove " + name.toUpperCase());
                boolean kicked = false;
                try { bot.waitActive(1000); }
                catch (AssertionError | IOException expected) { kicked = true; }
                IntegrationHarness.check(kicked, "remove kicks an unlisted operator when enforcement is enabled");
            }
            checkRejected(name, "operator UUID does not bypass the IGN-only gate");
            command("ign-whitelist add " + name.toLowerCase());
            command("ign-whitelist reload");
            try (IntegrationHarness.Bot bot = bot(name.toUpperCase())) {
                var login = bot.dialog();
                IntegrationHarness.check(login.common().inputs().size() == 1, "IGN case variants and changed client UUID still reach saved offline login");
                bot.auth(login, "integration-whitelist-password", null); bot.finish();
            }
            command("ban " + name);
            checkRejected(name, "IGN entry does not bypass a vanilla account ban");
            command("pardon " + name); command("deop " + name);
            command("ign-whitelist off");
            checkRejected(name, "IGN off restores enabled native whitelist");
            command("whitelist off");
            try (IntegrationHarness.Bot bot = bot(another)) {
                IntegrationHarness.check(!bot.disconnected, "IGN off with native whitelist off permits ordinary offline registration");
                bot.dialog();
            }
            System.out.println("PASS: IGN whitelist commands, offline UUID handling, case variants, permissions, strict operator filtering, bans and enforcement.");
        } finally {
            command("pardon " + name); command("deop " + name);
            command("ign-whitelist off"); command("whitelist off");
            command("ign-whitelist remove " + name + " " + another);
        }
    }
    private static IntegrationHarness.Bot bot(String name) throws Exception {
        // Present a different random client UUID every time: the whitelist must care only about the IGN.
        return new IntegrationHarness.Bot(name, UUID.randomUUID());
    }
    private static void checkRejected(String name, String message) throws Exception {
        try (IntegrationHarness.Bot bot = bot(name)) { IntegrationHarness.check(bot.disconnected, message); }
    }
    static String command(String text) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", rconPort)) {
            socket.setSoTimeout(5000);
            write(socket.getOutputStream(), 77, 3, password);
            Response auth;
            do { auth = read(socket.getInputStream()); } while (auth.type != 2);
            if (auth.id != 77) throw new IOException("Local test RCON authentication failed");
            write(socket.getOutputStream(), 78, 2, text);
            Response result = read(socket.getInputStream());
            if (result.id != 78) throw new IOException("Unexpected RCON response");
            return result.text;
        }
    }
    private static void write(OutputStream stream, int id, int type, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ByteBuffer packet = ByteBuffer.allocate(bytes.length + 14).order(ByteOrder.LITTLE_ENDIAN);
        packet.putInt(bytes.length + 10).putInt(id).putInt(type).put(bytes).put((byte) 0).put((byte) 0);
        stream.write(packet.array()); stream.flush();
    }
    private static Response read(InputStream stream) throws IOException {
        int length = ByteBuffer.wrap(exact(stream, 4)).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (length < 10 || length > 1_048_576) throw new IOException("Bad RCON response length");
        ByteBuffer packet = ByteBuffer.wrap(exact(stream, length)).order(ByteOrder.LITTLE_ENDIAN);
        int id = packet.getInt(), type = packet.getInt();
        return new Response(id, type, new String(packet.array(), 8, length - 10, StandardCharsets.UTF_8));
    }
    private static byte[] exact(InputStream stream, int size) throws IOException {
        byte[] bytes = stream.readNBytes(size); if (bytes.length != size) throw new EOFException(); return bytes;
    }
    private record Response(int id, int type, String text) {}
}
