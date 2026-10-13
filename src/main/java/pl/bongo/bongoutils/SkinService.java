package pl.bongo.bongoutils;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinService {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String, CachedUuid> names = new ConcurrentHashMap<>();
    private long nextMineSkin;
    public UUID lookupUuid(String ign) throws IOException, InterruptedException {
        String key = Store.key(ign);
        CachedUuid cached = names.get(key);
        if (cached != null && cached.expires > System.currentTimeMillis()) return cached.uuid;
        HttpRequest request = request("https://api.mojang.com/users/profiles/minecraft/" + key).GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        UUID uuid;
        try (InputStream body = response.body()) {
            if (response.statusCode() == 204 || response.statusCode() == 404) uuid = null;
            else {
                requireSuccess(response.statusCode());
                JsonObject data = json(body);
                String raw = data.get("id").getAsString();
                uuid = parseUuid(raw);
            }
        }
        if (names.size() > 4096) names.clear();
        names.put(key, new CachedUuid(uuid, System.currentTimeMillis() + (uuid == null ? 60_000 : 300_000)));
        return uuid;
    }
    public Store.SkinRecord fromIgn(String ign) throws Exception {
        Store.key(ign);
        UUID uuid = lookupUuid(ign);
        if (uuid == null) throw new Lang.Failure("skin_not_found");
        JsonObject profile = get("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", "") + "?unsigned=false");
        for (JsonElement element : profile.getAsJsonArray("properties")) {
            JsonObject property = element.getAsJsonObject();
            if ("textures".equals(property.get("name").getAsString()) && property.has("signature")) {
                return checked(new Store.SkinRecord("ign:" + ign, property.get("value").getAsString(), property.get("signature").getAsString(), null));
            }
        }
        throw new Lang.Failure("skin_unsigned");
    }
    public synchronized Store.SkinRecord fromUrl(String url, boolean slim) throws Exception {
        if (System.currentTimeMillis() < nextMineSkin) throw new Lang.Failure("mineskin_limit");
        byte[] png = SafePng.download(url);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png));
        Path image = BongoUtils.store.image(hash);
        if (!Files.exists(image)) {
            Path temp = Files.createTempFile(image.getParent(), ".png-", ".tmp");
            try { Files.write(temp, png); Files.move(temp, image, StandardCopyOption.REPLACE_EXISTING); }
            finally { Files.deleteIfExists(temp); }
        }
        Path cached = image.resolveSibling(hash + (slim ? "-slim" : "-classic") + ".json");
        if (Files.exists(cached)) return checked(Store.JSON.fromJson(Files.readString(cached), Store.SkinRecord.class));
        String boundary = "Bongo" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream multipart = new ByteArrayOutputStream();
        field(multipart, boundary, "visibility", "unlisted");
        field(multipart, boundary, "variant", slim ? "slim" : "classic");
        multipart.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        multipart.write(png); multipart.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder builder = request("https://api.mineskin.org/v2/queue").timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary).POST(HttpRequest.BodyPublishers.ofByteArray(multipart.toByteArray()));
        authorize(builder);
        JsonObject data = send(builder.build());
        long deadline = System.nanoTime() + 90_000_000_000L;
        while (!data.has("skin") || !data.get("skin").isJsonObject() || !data.getAsJsonObject("skin").has("texture")) {
            if (System.nanoTime() > deadline) throw new Lang.Failure("mineskin_timeout");
            if (data.has("job")) {
                JsonObject job = data.getAsJsonObject("job");
                String status = job.has("status") ? job.get("status").getAsString() : "";
                if (status.equals("failed")) throw new Lang.Failure("mineskin_rejected");
                if (status.equals("completed") && job.has("result")) {
                    data = getMineSkin("https://api.mineskin.org/v2/skins/" + safeId(job.get("result").getAsString()));
                    continue;
                }
                String id = safeId(job.get("id").getAsString());
                Thread.sleep(2000);
                data = getMineSkin("https://api.mineskin.org/v2/queue/" + id);
            } else throw new Lang.Failure("mineskin_response");
        }
        JsonObject texture = data.getAsJsonObject("skin").getAsJsonObject("texture").getAsJsonObject("data");
        Store.SkinRecord result = checked(new Store.SkinRecord("url", texture.get("value").getAsString(), texture.get("signature").getAsString(), hash));
        Store.atomic(cached, Store.JSON.toJson(result));
        return result;
    }
    public GameProfile restore(GameProfile profile) throws Exception {
        Store.SkinRecord skin = BongoUtils.store.skin(profile.id());
        if (skin == null) return profile;
        // IGN choices keep a signed snapshot for availability; images are only stored for URL choices.
        return apply(profile, checked(skin));
    }
    public static GameProfile apply(GameProfile profile, Store.SkinRecord skin) {
        ImmutableMultimap.Builder<String, Property> properties = ImmutableMultimap.builder();
        profile.properties().entries().stream().filter(e -> !e.getKey().equals("textures")).forEach(e -> properties.put(e.getKey(), e.getValue()));
        properties.put("textures", new Property("textures", skin.value(), skin.signature()));
        return new GameProfile(profile.id(), profile.name(), new PropertyMap(properties.build()));
    }
    private Store.SkinRecord checked(Store.SkinRecord skin) throws IOException {
        if (skin == null || skin.value() == null || skin.signature() == null || skin.value().length() > 16384 || skin.signature().length() > 8192)
            throw new Lang.Failure("texture_data_invalid");
        try {
            JsonObject decoded = JsonParser.parseString(new String(Base64.getDecoder().decode(skin.value()), StandardCharsets.UTF_8)).getAsJsonObject();
            URI url = URI.create(decoded.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString());
            if (!url.getHost().equals("textures.minecraft.net") || !Set.of("https", "http").contains(url.getScheme())
                    || !url.getPath().matches("/texture/[a-f0-9]{32,128}") || url.getUserInfo() != null || url.getPort() != -1)
                throw new Lang.Failure("texture_host");
            Base64.getDecoder().decode(skin.signature());
            return skin;
        } catch (RuntimeException e) { throw new Lang.Failure("texture_invalid"); }
    }
    private JsonObject get(String url) throws IOException, InterruptedException { return send(request(url).GET().build()); }
    private JsonObject getMineSkin(String url) throws IOException, InterruptedException {
        var builder = request(url).GET(); authorize(builder); return send(builder.build());
    }
    private void authorize(HttpRequest.Builder builder) {
        String key = BongoUtils.config.mineSkinApiKey.strip();
        if (!key.isEmpty()) builder.header("Authorization", "Bearer " + key);
    }
    private JsonObject send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() == 429 && request.uri().getHost().equals("api.mineskin.org")) {
                long seconds = 60;
                try { seconds = Long.parseLong(response.headers().firstValue("Retry-After").orElse("60")); } catch (NumberFormatException ignored) {}
                nextMineSkin = System.currentTimeMillis() + Math.min(600, Math.max(1, seconds)) * 1000;
            }
            requireSuccess(response.statusCode()); return json(body);
        }
    }
    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "BongoUtils/1.0").header("Accept", "application/json").timeout(Duration.ofSeconds(12));
    }
    private static void requireSuccess(int status) throws IOException {
        if (status < 200 || status >= 300) throw new Lang.Failure("skin_http", status, status == 429);
    }
    private static JsonObject json(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(131073);
        if (bytes.length > 131072) throw new Lang.Failure("api_large");
        try { return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException e) { throw new Lang.Failure("api_invalid"); }
    }
    private static void field(OutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
    private static String safeId(String id) throws IOException {
        if (!id.matches("[a-zA-Z0-9-]{8,64}")) throw new Lang.Failure("mineskin_id"); return id;
    }
    public static UUID parseUuid(String raw) {
        if (raw.matches("[a-fA-F0-9]{32}")) raw = raw.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5");
        return UUID.fromString(raw);
    }
    private record CachedUuid(UUID uuid, long expires) {}
}
