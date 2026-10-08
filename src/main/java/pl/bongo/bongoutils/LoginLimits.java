package pl.bongo.bongoutils;

import java.util.HashMap;
import java.util.Map;

/** Bounded rolling lockouts survive reconnects; they contain no passwords. */
public final class LoginLimits {
    private final Map<String, Window> attempts = new HashMap<>();
    public synchronized boolean allow(String key, int limit, long durationMillis) {
        long now = System.currentTimeMillis();
        if (attempts.size() >= 8192) attempts.entrySet().removeIf(e -> e.getValue().until < now);
        Window window = attempts.get(key);
        if (window == null || window.until < now) {
            if (attempts.size() >= 8192) return false;
            attempts.put(key, new Window(1, now + durationMillis)); return true;
        }
        if (window.count >= limit) return false;
        window.count++; return true;
    }
    private static final class Window {
        private int count; private final long until;
        private Window(int count, long until) { this.count = count; this.until = until; }
    }
}
