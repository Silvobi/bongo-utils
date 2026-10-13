package pl.bongo.bongoutils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.input.TextInput;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class LangTest {
    @BeforeAll static void boot() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void localeSelectionAndFallback() {
        assertEquals("pl", Lang.language("PL_pl"));
        for (String locale : new String[] {null, "", "en_us", "de_de", "pl"}) {
            assertEquals("en", Lang.language(locale));
            assertEquals("Enter password", Lang.text(locale, "login_label"));
        }
        assertEquals("Podaj hasło", Lang.text("pl_pl", "login_label"));
    }
    @Test void everyTranslationHasMatchingArgumentsAndFormats() throws Exception {
        assertEquals(Lang.keys("pl"), Lang.keys("en"));
        Properties pl=load("pl"), en=load("en");
        for (String key : Lang.keys("pl")) {
            int count=pl.getProperty(key).split("%s",-1).length-1;
            assertEquals(count,en.getProperty(key).split("%s",-1).length-1,key);
            Object[] args=new Object[count]; Arrays.fill(args,"VALUE");
            for (String locale : List.of("pl_pl","en_us")) {
                String value=Lang.text(locale,key,args);
                assertFalse(value.isBlank(),key);
                assertFalse(value.contains("%s"),key);
            }
        }
    }
    private static Properties load(String language) throws Exception {
        Properties result=new Properties();
        try(var input=LangTest.class.getResourceAsStream("/bongoutils/lang/"+language+".properties")) {
            result.load(new InputStreamReader(input,StandardCharsets.UTF_8));
        }
        return result;
    }
    @Test void registrationAndPasswordDialogsUseTheViewerLanguage() {
        for(String locale:List.of("pl_pl","en_us")) {
            var registration=(MultiActionDialog)Dialogs.auth(locale,true,"token",Lang.text(locale,"auth_intro")).value();
            assertEquals(Lang.text(locale,"register_title"),registration.common().title().getString());
            assertEquals(Lang.text(locale,"password_label"),((TextInput)registration.common().inputs().getFirst().control()).label().getString());
            assertEquals(Lang.text(locale,"register_button"),registration.actions().getFirst().button().label().getString());
            var clear=(MultiActionDialog)Dialogs.changePassClear(locale,"token","MiXeD").value();
            assertEquals(Lang.text(locale,"clear_title"),clear.common().title().getString());
            assertEquals(Lang.text(locale,"clear_confirm"),clear.actions().getFirst().button().label().getString());
            var menu=(MultiActionDialog)Dialogs.skinMenu(locale,"token").value();
            assertEquals(Lang.text(locale,"skin_title"),menu.common().title().getString());
            assertEquals(Lang.text(locale,"url_button"),menu.actions().get(1).button().label().getString());
        }
    }
    @Test void backgroundSkinFailuresCanBeRenderedForEitherRecipient() {
        var failure=new Lang.Failure("skin_http",429,true);
        assertEquals("Usługa skina zwróciła HTTP 429 (limit zapytań).",failure.text("pl_pl"));
        assertEquals("The skin service returned HTTP 429 (rate limit).",failure.text("de_de"));
        assertEquals("The skin service returned HTTP 500.",new Lang.Failure("skin_http",500,false).text(null));
        assertEquals("Invalid URL.",new Lang.Failure("url_invalid").getMessage());
    }
    @Test void earlyMsaRejectionUsesEnglishAndPreservesTheUsername() {
        assertTrue(MsaLogin.rejection(MsaLogin.Route.OCCUPIED_MSA,"MiXeD").startsWith("IGN MiXeD is already taken"));
        assertEquals(3,MsaLogin.rejection(MsaLogin.Route.RESERVED_MSA,"name").lines().count());
        assertTrue(MsaLogin.welcome("en_us").getString().startsWith("Connected with an MSA account."));
    }
}
