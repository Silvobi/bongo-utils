package pl.bongo.bongoutils.social;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import com.google.gson.JsonParser;
import pl.bongo.bongoutils.Store;

/** Custom statuses persist; AFK and private conversation state are deliberately transient. */
public final class Statuses {
    private final Path file;
    private Map<UUID,String> values=new HashMap<>();
    public Statuses(Path file) throws IOException {
        this.file=file;
        if(Files.exists(file)) {
            try {
                JsonParser.parseString(Files.readString(file)).getAsJsonObject().entrySet().forEach(e->{
                    String text=e.getValue().getAsString();validate(text);values.put(UUID.fromString(e.getKey()),text);
                });
            } catch(RuntimeException e) { throw new IOException("Invalid statuses file",e); }
        }
    }
    public static void validate(String text) {
        if(text.isBlank() || text.codePointCount(0,text.length())>32 || text.codePoints().anyMatch(c->Character.isISOControl(c) || c==0xA7 || Character.getType(c)==Character.FORMAT))
            throw new IllegalArgumentException("Status musi mieć 1–32 znaki, bez kodów formatowania i znaków sterujących.");
    }
    public String get(UUID id) { return values.get(id); }
    public void set(UUID id,String text) throws IOException {
        if(text!=null) validate(text);
        var next=new HashMap<>(values);if(text==null) next.remove(id);else next.put(id,text);
        Store.atomic(file,Store.JSON.toJson(next));values=next;
    }
}
