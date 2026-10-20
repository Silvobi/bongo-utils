package pl.bongo.bongoutils.claims;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Owner defaults and explicit per-flag overrides; an explicit off is different from inheritance. */
public final class ClaimFlagStore {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    public static final class State {
        int schemaVersion=1;
        Map<String,Map<String,String>> owners=new HashMap<>(),claims=new HashMap<>();
        Set<String> knownClaims=new HashSet<>(),imports=new HashSet<>();
    }
    private final Path file;
    private State state;
    private long revision;
    public ClaimFlagStore(Path file) {
        this.file=file;
        try {
            state=Files.exists(file)?JSON.fromJson(Files.readString(file),State.class):new State();
            validate(state);
        } catch(IOException | RuntimeException e) { throw new IllegalStateException("Nie można odczytać flag działek; oryginalny plik zachowano.",e); }
    }
    static void validate(State s) {
        if(s==null || s.schemaVersion!=1 || s.owners==null || s.claims==null || s.knownClaims==null || s.imports==null) throw new IllegalArgumentException("Invalid flag storage");
        for(var map:List.of(s.owners,s.claims)) for(var entry:map.entrySet()) {
            if(entry.getKey()==null || entry.getValue()==null) throw new IllegalArgumentException("Invalid flag scope");
            for(var value:entry.getValue().entrySet()) if(value.getKey()==null || value.getValue()==null || value.getValue().isBlank()) throw new IllegalArgumentException("Invalid flag value");
        }
        for(String owner:s.owners.keySet()) UUID.fromString(owner);
        if(s.knownClaims.contains(null) || s.imports.contains(null)) throw new IllegalArgumentException("Invalid claim identity");
    }
    public long revision() { return revision; }
    public String local(String claim,String flag) { return state.claims.getOrDefault(claim,Map.of()).get(flag); }
    public String owner(UUID owner,String flag) { return owner==null?null:state.owners.getOrDefault(owner.toString(),Map.of()).get(flag); }
    public String value(String claim,UUID owner,String flag,String fallback) {
        String local=local(claim,flag),global=owner(owner,flag);
        return local!=null?local:global!=null?global:fallback;
    }
    public void change(Consumer<State> change) {
        State next=JSON.fromJson(JSON.toJson(state),State.class); change.accept(next);validate(next);
        try {
            Files.createDirectories(file.getParent()); Path temp=file.resolveSibling(file.getFileName()+".tmp");
            try(FileOutputStream out=new FileOutputStream(temp.toFile())) { out.write(JSON.toJson(next).getBytes(java.nio.charset.StandardCharsets.UTF_8));out.getFD().sync(); }
            if(Files.exists(file)) Files.copy(file,file.resolveSibling(file.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
        } catch(IOException e) { throw new UncheckedIOException("Nie zapisano flag działek.",e); }
        state=next; revision++;
    }
    static void put(Map<String,Map<String,String>> scope,String key,String flag,String value) {
        if(value==null) { Map<String,String> map=scope.get(key);if(map!=null) { map.remove(flag);if(map.isEmpty()) scope.remove(key); } }
        else scope.computeIfAbsent(key,k->new HashMap<>()).put(flag,value);
    }
    public void local(String key,String flag,String value) { change(s->put(s.claims,key,flag,value)); }
    public void owner(UUID owner,String flag,String value) { change(s->put(s.owners,owner.toString(),flag,value)); }
    public void discover(Map<String,Map<String,String>> imports) {
        Map<String,Map<String,String>> fresh=new HashMap<>(); imports.forEach((key,flags)-> { if(!state.knownClaims.contains(key)) fresh.put(key,flags); });
        if(fresh.isEmpty()) return;
        change(s->fresh.forEach((key,flags)-> { s.knownClaims.add(key); flags.forEach((flag,value)->put(s.claims,key,flag,value)); }));
    }
    public void legacy(String key,String flag,String value) {
        String marker=key+"/"+flag;
        if(state.imports.contains(marker)) return;
        change(s->{s.imports.add(marker);if(!s.claims.getOrDefault(key,Map.of()).containsKey(flag)) put(s.claims,key,flag,value);});
    }
}
