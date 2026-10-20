package pl.bongo.bongoutils;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.body.PlainMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class I18nTest {
    @TempDir Path directory;
    @org.junit.jupiter.api.BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap(); }
    private Map<String,String> catalog(String language) throws Exception {
        try(var reader=new InputStreamReader(getClass().getResourceAsStream("/assets/bongoutils/lang/"+language+".json"),StandardCharsets.UTF_8)) {
            var result=new HashMap<String,String>();JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(e->result.put(e.getKey(),e.getValue().getAsString()));return result;
        }
    }
    @Test void englishIsFallbackAndPolishUsesClientLocale() {
        for(String language:Arrays.asList(null,"","en_us","de_de","ja_jp","zz_zz","planet"))
            assertEquals("Steve is now AFK.",I18n.t(language,"afk.on","Steve"));
        for(String language:List.of("pl","pl_pl","PL_PL","pl-PL"))
            assertEquals("Steve jest teraz AFK.",I18n.t(language,"afk.on","Steve"));
    }
    @Test void missingPolishMessageUsesEnglishAndArgumentsAreNotReinterpreted() {
        assertEquals("English",I18n.template("pl_pl","key",Map.of(),Map.of("key","English")));
        String raw="{0} $1 \\ §a";
        assertEquals(raw+" is no longer AFK.",I18n.t("en_us","afk.off",raw));
        assertThrows(IllegalArgumentException.class,()->I18n.t("en_us","unknown"));
        assertThrows(IllegalArgumentException.class,()->I18n.t("en_us","afk.on"));
    }
    @Test void catalogsHaveAllKeysAndMatchingPlaceholders() throws Exception {
        var pl=catalog("pl_pl");var en=catalog("en_us");assertEquals(en.keySet(),pl.keySet());assertTrue(en.size()>100);
        Pattern args=Pattern.compile("\\{\\d+\\}");
        for(String key:en.keySet()) {
            assertFalse(en.get(key).isBlank(),key);assertFalse(pl.get(key).isBlank(),key);
            assertEquals(args.matcher(en.get(key)).results().map(java.util.regex.MatchResult::group).toList(),args.matcher(pl.get(key)).results().map(java.util.regex.MatchResult::group).toList(),key);
        }
    }
    @Test void serviceErrorsKeepLocalizedDetailsWithoutExposingUnknownFailures() {
        var error=new I18n.Failure("png.http.status",404);
        assertTrue(I18n.error("pl_pl",error,"skin.error").contains("Link mógł wygasnąć"));
        assertTrue(I18n.error("de_de",error,"skin.error").contains("link may have expired"));
        assertEquals("Could not set your skin. Try again.",I18n.error("en_us",new java.io.IOException("private path"),"skin.error"));
    }
    @Test void englishRulesOverrideOnlyConfiguredFieldsAndKeepUserTextLiteral() throws Exception {
        Path file=directory.resolve("config.json");var config=new ConfigFile(file);
        Files.writeString(file,"""
            {"rules":{"title":"Moje zasady","url":"https://example.org/pl","entries":["Treść {0}"],
                "en":{"title":"Our rules","url":"https://example.org/en","entries":["Literal {0}"]}}}
            """);config.reload();
        assertEquals("Moje zasady",RulesCommand.text(config.current().rules,"pl_pl").title());
        assertEquals(List.of("Treść {0}"),RulesCommand.text(config.current().rules,"pl_pl").entries());
        var selected=RulesCommand.text(config.current().rules,"de_de");assertEquals("Our rules",selected.title());assertEquals("https://example.org/en",selected.url());assertEquals(List.of("Literal {0}"),selected.entries());
        Files.writeString(file,"{\"rules\":{\"entries\":[\"Unchanged\"],\"en\":{\"title\":\"English\"}}}");config.reload();
        assertEquals(List.of("Unchanged"),RulesCommand.text(config.current().rules,"en_us").entries());
    }
    @Test void invalidEnglishReloadPreservesPreviousSnapshot() throws Exception {
        Path file=directory.resolve("config.json");var config=new ConfigFile(file);var previous=config.current();
        for(String english:List.of("null","[]","{\"title\":null}","{\"entries\":null}","{\"entries\":[4]}","{\"title\":\" \"}","{\"url\":\"javascript:bad\"}")) {
            Files.writeString(file,"{\"rules\":{\"en\":"+english+"}}");assertThrows(java.io.IOException.class,config::reload,english);assertSame(previous,config.current());
        }
    }
    @Test void defaultRulesDialogHasEnglishFallbackControls() {
        var rules=new BongoUtils.Rules();
        NoticeDialog dialog=(NoticeDialog)RulesCommand.dialog(rules,"de_de").value();
        assertEquals("Server rules",dialog.common().title().getString());
        assertEquals("The server rules have not been set yet.",((PlainMessage)dialog.common().body().getFirst()).contents().getString());
        assertEquals("Close",dialog.action().button().label().getString());
    }
}
