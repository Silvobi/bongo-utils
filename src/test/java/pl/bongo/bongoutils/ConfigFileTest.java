package pl.bongo.bongoutils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConfigFileTest {
    @TempDir Path directory;

    @Test void createsDefaultsAndAcceptsExistingConfigWithoutRules() throws Exception {
        Path file = directory.resolve("config.json");
        ConfigFile config = new ConfigFile(file);
        assertEquals("dialog", config.current().rules.mode);
        assertTrue(Files.readString(file).contains("\"rules\""));
        Files.writeString(file, "{\"allowOffline\":false,\"mineSkinApiKey\":\"old-key\"}");
        config.reload();
        assertFalse(config.current().allowOffline);
        assertEquals("old-key", config.current().mineSkinApiKey);
        assertTrue(config.current().rules.entries.isEmpty());
    }

    @Test void reloadSwitchesModesAndAllSettingsWithoutRestart() throws Exception {
        Path file = directory.resolve("config.json");
        ConfigFile config = new ConfigFile(file);
        Files.writeString(file, """
                {"allowOffline":false,"mineSkinApiKey":"new-key",
                 "rules":{"mode":"link","url":"https://example.org/rules?lang=pl","title":"Zasady"}}
                """);
        config.reload();
        assertFalse(config.current().allowOffline);
        assertEquals("new-key", config.current().mineSkinApiKey);
        assertEquals("link", config.current().rules.mode);
        assertEquals("https://example.org/rules?lang=pl", config.current().rules.url);
        Files.writeString(file, """
                {"rules":{"mode":"dialog","title":"Regulamin","entries":["Szanuj innych.","Nie oszukuj."]}}
                """);
        config.reload();
        assertEquals("dialog", config.current().rules.mode);
        assertEquals(List.of("Szanuj innych.", "Nie oszukuj."), config.current().rules.entries);
        assertEquals("Regulamin", new ConfigFile(file).current().rules.title);
    }

    @Test void badReloadKeepsEntirePreviousSnapshotAndDoesNotOverwriteFile() throws Exception {
        Path file = directory.resolve("config.json");
        ConfigFile config = new ConfigFile(file);
        var previous = config.current();
        for (String bad : List.of("broken", "null", "[]", "{\"allowOffline\":\"false\"}",
                "{\"mineSkinApiKey\":null}", "{\"rules\":null}", "{\"rules\":{\"mode\":\"invalid\"}}",
                "{\"rules\":{\"mode\":\"link\"}}", "{\"rules\":{\"url\":\"javascript:alert(1)\"}}",
                "{\"rules\":{\"url\":\"https://\"}}", "{\"rules\":{\"title\":null}}",
                "{\"rules\":{\"entries\":null}}", "{\"rules\":{\"entries\":[null]}}",
                "{\"rules\":{\"entries\":[123]}}", "{\"rules\":{\"entries\":[\" \" ]}}")) {
            Files.writeString(file, bad);
            assertThrows(IOException.class, config::reload, bad);
            assertSame(previous, config.current(), bad);
            assertEquals(bad, Files.readString(file));
        }
        Files.delete(file);
        assertThrows(IOException.class, config::reload);
        assertSame(previous, config.current());
    }
}
