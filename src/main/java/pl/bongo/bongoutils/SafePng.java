package pl.bongo.bongoutils;

import javax.imageio.ImageIO;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Downloads HTTPS PNGs with TLS verification and a socket pinned to a public IP. */
public final class SafePng {
    public static final int MAX_BYTES = 1_048_576;
    public static byte[] download(String url) throws IOException {
        long deadline = System.nanoTime() + 30_000_000_000L;
        URI uri;
        try { uri = URI.create(url); } catch (IllegalArgumentException e) { throw new IOException("Nieprawidłowy URL."); }
        for (int redirects = 0; redirects <= 4; redirects++) {
            validateUri(uri);
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            if (addresses.length == 0) throw new IOException("Brak adresu serwera obrazu.");
            for (InetAddress address : addresses) if (!publicAddress(address)) throw new IOException("URL wskazuje na niedozwoloną sieć.");
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(addresses[0], 443), 8000);
                socket.setSoTimeout(10000);
                try (SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(socket, uri.getHost(), 443, true)) {
                    SSLParameters params = tls.getSSLParameters();
                    params.setEndpointIdentificationAlgorithm("HTTPS");
                    params.setServerNames(List.of(new SNIHostName(uri.getHost())));
                    tls.setSSLParameters(params); tls.startHandshake();
                    String path = uri.getRawPath(); if (path == null || path.isEmpty()) path = "/";
                    if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
                    String request = "GET " + path + " HTTP/1.1\r\nHost: " + uri.getHost()
                            + "\r\nUser-Agent: BongoUtils/1.0\r\nAccept: image/png\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n";
                    tls.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                    InputStream input = new BufferedInputStream(new FilterInputStream(tls.getInputStream()) {
                        private void deadline() throws IOException {
                            long remaining = (deadline - System.nanoTime()) / 1_000_000;
                            if (remaining <= 0) throw new IOException("Pobieranie PNG przekroczyło 30 sekund.");
                            tls.setSoTimeout((int) Math.min(10000, Math.max(1, remaining)));
                        }
                        public int read() throws IOException { deadline(); return in.read(); }
                        public int read(byte[] bytes, int offset, int length) throws IOException { deadline(); return in.read(bytes, offset, length); }
                    });
                    String statusLine = line(input);
                    String[] parts = statusLine.split(" ");
                    if (parts.length < 2 || !parts[0].startsWith("HTTP/1.")) throw new IOException("Nieprawidłowa odpowiedź HTTP.");
                    int status = Integer.parseInt(parts[1]);
                    Map<String, String> headers = new HashMap<>();
                    int size = 0;
                    while (true) {
                        String header = line(input); size += header.length();
                        if (size > 32768) throw new IOException("Zbyt duże nagłówki HTTP.");
                        if (header.isEmpty()) break;
                        int colon = header.indexOf(':');
                        if (colon > 0) headers.put(header.substring(0, colon).toLowerCase(Locale.ROOT), header.substring(colon + 1).strip());
                    }
                    if (Set.of(301, 302, 303, 307, 308).contains(status)) {
                        if (redirects == 4 || !headers.containsKey("location")) throw new IOException("Zbyt wiele przekierowań.");
                        uri = uri.resolve(headers.get("location")); continue;
                    }
                    if (status != 200) throw new IOException("Serwer obrazu zwrócił HTTP " + status + ". Link mógł wygasnąć.");
                    if (!headers.getOrDefault("content-encoding", "identity").equalsIgnoreCase("identity")) throw new IOException("Niedozwolona kompresja HTTP.");
                    String transfer = headers.getOrDefault("transfer-encoding", "");
                    byte[] bytes;
                    if (transfer.equalsIgnoreCase("chunked")) bytes = chunks(input);
                    else if (!transfer.isEmpty()) throw new IOException("Nieobsługiwany transfer HTTP.");
                    else if (headers.containsKey("content-length")) {
                        long length = Long.parseLong(headers.get("content-length"));
                        if (length < 0 || length > MAX_BYTES) throw new IOException("PNG może mieć maksymalnie 1 MiB.");
                        bytes = input.readNBytes((int) length);
                        if (bytes.length != length) throw new IOException("Niepełny obraz PNG.");
                    } else bytes = input.readNBytes(MAX_BYTES + 1);
                    validatePng(bytes); return bytes;
                }
            } catch (IllegalArgumentException e) { throw new IOException("Nieprawidłowy URL lub odpowiedź HTTP."); }
        }
        throw new IOException("Nie udało się pobrać PNG.");
    }
    public static void validateUri(URI uri) throws IOException {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443) || uri.toASCIIString().length() > 2048)
            throw new IOException("Podaj bezpośredni adres HTTPS obrazu PNG (port 443).");
        // Extensions are intentionally ignored: Discord uses signed query strings and proxy paths.
    }
    public static boolean publicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] b = address.getAddress();
        if (b.length == 4) {
            int a = b[0] & 255, c = b[1] & 255;
            return a != 0 && a != 10 && a != 127 && a < 224 && !(a == 100 && c >= 64 && c <= 127)
                    && !(a == 169 && c == 254) && !(a == 172 && c >= 16 && c <= 31)
                    && !(a == 192 && (c == 168 || c == 0 || c == 2))
                    && !(a == 198 && (c == 18 || c == 19 || c == 51)) && !(a == 203 && c == 0 && (b[2] & 255) == 113);
        }
        // Only ordinary global-unicast IPv6; rejects mapped/compatible, ULA, NAT64 and 6to4.
        return b.length == 16 && (b[0] & 0xe0) == 0x20 && !((b[0] & 255) == 0x20 && (b[1] & 255) == 0x02)
                && !((b[0] & 255) == 0x20 && (b[1] & 255) == 0x01 && ((b[2] & 255) < 2 || ((b[2] & 255) == 0x0d && (b[3] & 255) == 0xb8)));
    }
    public static void validatePng(byte[] bytes) throws IOException {
        byte[] magic = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        if (bytes.length > MAX_BYTES || bytes.length < 24 || !Arrays.equals(magic, Arrays.copyOf(bytes, 8))) throw new IOException("Plik nie jest obrazem PNG (maks. 1 MiB).");
        try (var imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) throw new IOException("Uszkodzony PNG.");
            var reader = readers.next();
            try {
                reader.setInput(imageInput);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width != 64 || (height != 64 && height != 32)) throw new IOException("Skin musi mieć rozmiar 64×64 lub 64×32 piksele.");
                if (reader.read(0) == null) throw new IOException("Uszkodzony PNG.");
            } finally { reader.dispose(); }
        }
    }
    private static String line(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (out.size() <= 8192) {
            int b = input.read(); if (b < 0) throw new EOFException("Niepełna odpowiedź HTTP.");
            if (b == '\n') return out.toString(StandardCharsets.US_ASCII).replaceAll("\r$", "");
            out.write(b);
        }
        throw new IOException("Zbyt długa linia HTTP.");
    }
    private static byte[] chunks(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int chunks = 0; chunks < 16384; chunks++) {
            String size = line(input).split(";", 2)[0].strip();
            int length = Integer.parseInt(size, 16);
            if (length == 0) return out.toByteArray();
            if (length < 0 || length > MAX_BYTES - out.size()) throw new IOException("PNG może mieć maksymalnie 1 MiB.");
            byte[] chunk = input.readNBytes(length);
            if (chunk.length != length) throw new EOFException("Niepełny obraz PNG.");
            out.write(chunk);
            if (!line(input).isEmpty()) throw new IOException("Nieprawidłowe kodowanie HTTP.");
        }
        throw new IOException("Zbyt wiele fragmentów HTTP.");
    }
}
