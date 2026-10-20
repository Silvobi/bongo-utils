package pl.bongo.bongoutils;

import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** Server-rendered text: vanilla clients need no language pack. Missing Polish keys use English. */
public final class I18n {
    private static final Map<String,String> EN=load("en_us"),PL=load("pl_pl");
    private static final Pattern ARG=Pattern.compile("\\{(\\d+)\\}");
    private I18n() {}
    private static Map<String,String> load(String language) {
        try(var input=I18n.class.getResourceAsStream("/assets/bongoutils/lang/"+language+".json")) {
            if(input==null) throw new IOException("Missing language file: "+language);
            var result=new HashMap<String,String>();
            JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject().entrySet()
                .forEach(e->result.put(e.getKey(),e.getValue().getAsString()));
            return Map.copyOf(result);
        } catch(IOException | RuntimeException e) { throw new ExceptionInInitializerError(e); }
    }
    public static boolean polish(String language) {
        if(language==null) return false;
        String value=language.toLowerCase(Locale.ROOT);
        return value.equals("pl") || value.startsWith("pl_") || value.startsWith("pl-");
    }
    public static String language(ServerPlayer player) { return player==null?null:player.clientInformation().language(); }
    public static String language(CommandSourceStack source) { return source.getEntity() instanceof ServerPlayer p?language(p):null; }
    public static String t(ServerPlayer player,String key,Object... args) { return t(language(player),key,args); }
    public static String t(CommandSourceStack source,String key,Object... args) { return t(language(source),key,args); }
    public static String t(String language,String key,Object... args) {
        return format(template(language,key,PL,EN),args);
    }
    static String template(String language,String key,Map<String,String> polish,Map<String,String> english) {
        String fallback=english.get(key);if(fallback==null) throw new IllegalArgumentException("Unknown BongoUtils message: "+key);
        return polish(language)?polish.getOrDefault(key,fallback):fallback;
    }
    static String format(String template,Object... args) {
        var matcher=ARG.matcher(template);var result=new StringBuilder();
        while(matcher.find()) {
            int index=Integer.parseInt(matcher.group(1));
            if(index>=args.length) throw new IllegalArgumentException("Missing localization argument: "+index);
            matcher.appendReplacement(result,Matcher.quoteReplacement(String.valueOf(args[index])));
        }
        return matcher.appendTail(result).toString();
    }
    public static String error(String language,Throwable error,String fallback) {
        if(error instanceof Failure e) return t(language,e.key,e.args);
        if(error instanceof UiFailure e) return t(language,e.key,e.args);
        return t(language,fallback);
    }
    public static final class Failure extends IOException {
        final String key;final Object[] args;
        public Failure(String key,Object... args) { super(t((String)null,key,args));this.key=key;this.args=args.clone(); }
    }
    public static final class UiFailure extends IllegalStateException {
        final String key;final Object[] args;
        public UiFailure(String key,Object... args) { super(t((String)null,key,args));this.key=key;this.args=args.clone(); }
    }
}
