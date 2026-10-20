# BongoUtils 1.7.0

Serwerowy mod Fabric dla **Minecraft Java 26.3**: logowanie offline/MSA, skiny, whitelista IGN, poczta offline, ignorowanie i vanish, siadanie/leżenie, regulamin, AFK i statusy, wzmianki oraz panel działek GP3D. Gracze korzystają z klienta vanilla — bez dodatkowego moda i paczki zasobów.

## Wymagania i instalacja

- **Java 25**, Fabric Loader **0.19.5+**, Fabric API **0.161.0+26.3**.
- Opcjonalnie: **GriefPrevention3D 19.0.0** (uniwersalny JAR), [BongosTeleports 1.6.0](https://github.com/Silvobi/bongos-teleports) i [BongosCommunityClans 1.1.2](https://github.com/Silvobi/bongos-community-clans).

1. Zatrzymaj serwer i wykonaj kopię świata oraz `config/bongoutils/`.
2. Włóż `BongoUtils-1.7.0.jar` i Fabric API do `mods/`. Usuń poprzedni JAR BongoUtils; zachowaj konfigurację i dane.
3. W `server.properties` ustaw:

   ```properties
   online-mode=true
   enforce-secure-profile=false
   ```

4. Uruchom serwer. Konfiguracja powstaje w `config/bongoutils/`.

Mod wybiera weryfikację sesji Mojang lub szyfrowane logowanie offline według rodzaju połączenia i `allowOffline`. Konto offline nie może używać nicku zajętego przez konto MSA. Nieudana weryfikacja sesji premium nie przełącza połączenia na tryb offline. Przy serwerach mieszanych sprawdź także konfigurację podpisanego czatu; mod używany w dotychczasowym zestawie to [No Chat Reports](https://modrinth.com/mod/no-chat-reports).

## Komendy

| Komenda | Działanie / wymagania |
| --- | --- |
| `/mail` | Otwiera własne nieprzeczytane wiadomości |
| `/mail send <nick>` | Mail do znanego gracza, także offline; natywny formularz |
| `/mail read new\|all` | Własna skrzynka nieprzeczytanych lub wszystkich wiadomości |
| `/mail send *` | Ważna poczta do znanych kont; gracz z OP 3+ |
| `/mail send-multiple <nick> <nick> ...` | Ważna poczta do wskazanych kont; gracz z OP 3+ |
| `/sit`, `/lay` | Przełącza siadanie lub leżenie; ruch przerywa pozę |
| `/wiki` | Dialog z linkiem do wiki serwera |
| `/ignore [nick]`, `/ingore [nick]` | Lista lub przełączenie obustronnego ignorowania |
| `/vanish [fake]`, `/semi-vanish` | Tryby widoczności; OP 3+ |
| `/bongoutils migrate move\|swap\|override <z> <do>` | Ręczna migracja danych kont; OP 3+ lub konsola |
| `bongoutils visibility <nick> visible\|semi\|vanish` | Odzyskanie widoczności konta; wyłącznie konsola/RCON |
| `/skin` | Dialog wyboru skina z IGN lub adresu HTTPS PNG |
| `/rules` | Regulamin w dialogu lub wiadomości z klikalnym linkiem |
| `/afk` | Ręczne przełączenie AFK; działa też automatycznie po 5 minutach bez ruchu |
| `/status <tekst>` | Własny status w TAB, maksymalnie 32 znaki |
| `/status` | Usuwa własny status |
| `/m <gracz> <wiadomość>` | Alias natywnego `/msg` |
| `/r <wiadomość>` | Odpowiedź ostatniemu rozmówcy |
| `/manageclaim` | Flagi działki/poddziałki w bieżącym X/Y/Z; właściciel lub Manage Trust |
| `/manageclaim global` | Domyślne flagi działek danego właściciela; dostępne także poza działką |
| `/ign-whitelist [on\|off\|list\|reload]` | Whitelista według nicków; OP 3+ lub konsola/RCON |
| `/ign-whitelist add\|remove <nick> [nick ...]` | Dodaje/usuwa nicki, także graczy offline; OP 3+ lub konsola/RCON |
| `/changepass` | Dialog zmiany lub wyczyszczenia hasła offline; gracz z OP 3+ |
| `/bongoutils reload` | Przeładowuje cały `config.json`; OP 3+ lub konsola/RCON |
| `bongoutils resetpassword <nick>` | Reset hasła offline, **wyłącznie konsola/RCON** |

Zmiana hasła nie dotyczy kont MSA. Formularze haseł są dostępne w vanilla, ale **pola nie maskują wpisywanych znaków**. Szczegóły logowania, uprawnień, limitów i migracji starszych UUID: [dokumentacja uwierzytelniania](docs/authentication.md).

## Poczta, widoczność i pozostałe narzędzia

`/mail` obsługuje konta offline, wielowierszowe formularze, listy z podziałem na strony i trwały stan przeczytania. Operatorzy mogą wysyłać ważne ogłoszenia i dołączać adresy URL. Zwykła poczta respektuje ignorowanie i vanish. [Komendy, limity i zapis poczty](docs/mail.md).

`/ignore` działa w obie strony dla czatu, prywatnych wiadomości, poczty i TPA. Pełny `/vanish` ukrywa wpis TAB i postać; `/semi-vanish` ukrywa wpis i podpowiedzi, pozostawiając widoczną postać. Tryby są trwałe. [Zasady widoczności, maskowania Spectator i migracji kont](docs/social-tools.md).

`/sit` i `/lay` są tymczasowymi pozami vanilla, bez zmiany respawnu ani głosowania nad nocą. Ruch, skok, obrażenia, teleportacja lub niebezpieczne podłoże przerywają pozę. `/wiki` otwiera okno z odnośnikiem do wiki.

## Poprawka okien dialogowych

W 1.7.0 logowanie zachowuje formularz podczas przetwarzania, a okna skinów, haseł, poczty i flag nie przełączają klienta na „Oczekiwanie na serwer”. Przycisk Zamknij w skinach i wiki działa także bez pakietu odpowiedzi. Usuwanie klanu korzysta z poprawki w Clans 1.1.2. Serwer nadal weryfikuje tokeny, uprawnienia i zakończenie logowania. [Lista okien, przyczyn i poprawek](docs/dialog-audit.md).

## AFK, statusy i wzmianki

Po **5 minutach stania w miejscu** AFK włącza się automatycznie. Ruch lub zmiana wymiaru wyłącza również AFK ustawione komendą. Obracanie kamery i pisanie na czacie nie zerują czasu. Czas jest liczony niezależnie od TPS.

TAB pokazuje szary, kursywny prefix `[AFK]` lub własny `[status]`. AFK ma pierwszeństwo; po powrocie wraca zapisany status. Prefix klanu pozostaje widoczny. Własny status przeżywa restart, AFK jest tymczasowe.

Zmiana AFK rozsyła komunikat kolorem `&7`: `<nickname> jest teraz AFK.` lub `<nickname> nie jest już AFK.` — w języku każdego odbiorcy.

`@Nickname` na publicznym czacie podświetla dokładny nick gracza online: **żółto** dla pozostałych, **niebiesko** dla wspomnianego. Wspomniany dostaje dźwięk i komunikat nad hotbarem. Wielkość liter nie ma znaczenia; powtórzenia w jednej wiadomości dają jedno powiadomienie. Prywatne wiadomości i czat klanu nie uruchamiają pingów.

`/r` zapamiętuje wysyłanie i odbieranie `/msg`, `/m`, `/tell`, `/w` i `/r`. Rozmówca jest identyfikowany po UUID; pamięć rozmowy nie przeżywa wyjścia ani restartu. [Dokładne reguły i ograniczenia](docs/social.md).

## Działki GP3D: lokalne wyjątki i ustawienia właściciela

`/manageclaim` otwiera flagi najdokładniejszej działki/poddziałki, w której gracz stał **w momencie otwarcia**. Okno pozostaje przypisane do niej po przemieszczeniu. Poza działką lub bez wymaganych praw pojawia się błąd. Uprawnienia są ponownie sprawdzane przy zapisie.

`/manageclaim global` dotyczy **działek danego gracza**, istniejących i przyszłych. Każda flaga ma kolejność:

1. Lokalny wyjątek tej konkretnej działki/poddziałki, także jawne „Wyłączone”.
2. Ustawienie globalne właściciela.
3. Domyślna wartość moda.

Lokalne ustawienie rodzica nie przenosi się automatycznie na poddziałkę. Opcja dziedziczenia usuwa wybrany wyjątek. Zmianę zatwierdza przycisk **Zastosuj**.

| Flaga | Dostawca | Domyślna polityka |
| --- | --- | --- |
| Eksplozje, eksplozje Withera, PvP, alerty granic | GP3D | Według konfiguracji i uprawnień GP3D |
| Publiczne pady teleportacyjne | BongosTeleports 1.6.0 | Wyłączone; nadal działa Access Trust |
| Dostęp członków klanu | BongosCommunityClans 1.1.2 | `build`: budowanie, skrzynie i podstawowy dostęp |

Poziomy dostępu klanu: `off`, `access`, `container`, `build`. Ograniczone i zagnieżdżone poddziałki wymagają własnego lokalnego włączenia. Build Trust nie daje Manage Trust ani prawa zarządzania flagami.

Przy pierwszym odczycie starszych działek zachowane jako wyjątki są wartości **różne od domyślnych GP3D**, a także rozpoznawalne jawne ustawienie PvP. Pozostałe dziedziczą ustawienia właściciela. Stare publiczne pady są importowane jednorazowo. [Szczegóły migracji, natywnych komend GP3D, zapisu i API](docs/claims.md).

## Konfiguracja i `/rules`

Plik `config/bongoutils/config.json`, przykładowa konfiguracja:

```json
{
  "allowOffline": true,
  "mineSkinApiKey": "",
  "rules": {
    "mode": "dialog",
    "title": "Regulamin serwera",
    "url": "",
    "entries": ["Szanuj innych graczy.", "Nie używaj cheatów."],
    "en": {
      "title": "Server rules",
      "entries": ["Respect other players.", "Do not cheat."]
    }
  }
}
```

- `allowOffline=false` dopuszcza wyłącznie konta zweryfikowane sesją Mojang.
- `mineSkinApiKey` to opcjonalny prywatny klucz API MineSkin do podpisywania skinów z URL.
- `rules.mode="dialog"` wyświetla numerowaną listę; `"link"` wymaga adresu HTTP(S) w `rules.url`.
- `rules.en` jest opcjonalne. Pominięte pola dziedziczą wspólne `title`, `entries` i `url`. Dla angielskiej strony można podać `rules.en.url`.

Po edycji wykonaj `/bongoutils reload`. Błędny lub brakujący plik zachowuje ostatnią poprawną konfigurację. Zmiana nie rozłącza obecnych graczy; otwarty regulamin zachowuje treść do ponownego `/rules`. Nowy plik ma pustą listę zasad. Whitelista IGN używa osobnego `/ign-whitelist reload`.

## Polski i angielski fallback

Język klienta `pl`, `pl_*` lub `pl-*` wybiera polski; inne lub brakujące wartości wybierają angielski. Brak polskiego klucza także korzysta z angielskiego. Dotyczy to własnych komunikatów, dialogów, ogłoszeń AFK per odbiorca i błędów.

Logowanie używa języka przekazanego przed wejściem do świata. Wcześniejsze odrzucenie połączenia, zanim klient przekaże język, oraz większość komunikatów konsoli/RCON korzystają z angielskiego. Starsze komunikaty administracyjne `migrate` i konsolowego `visibility` zachowują polski tekst. Zmiana języka działa dla kolejnych komunikatów i ponownie otwartych okien. Nicki, własne statusy i zasady administratora pozostają treścią użytkownika; regulamin tłumaczy się przez `rules.en`.

Komunikaty przywróconych funkcji poczty i narzędzi społecznościowych znajdują się dodatkowo w `src/main/resources/bongoutils/lang/`.

Katalogi tłumaczeń: [`pl_pl.json`](src/main/resources/assets/bongoutils/lang/pl_pl.json), [`en_us.json`](src/main/resources/assets/bongoutils/lang/en_us.json).

## Dane i aktualizacja

| Lokalizacja | Zawartość |
| --- | --- |
| `config/bongoutils/` | Konfiguracja, konta, rejestracja MSA, whitelista IGN, skiny, `social.json`, `mail.json`, kopie migracji |
| `<świat>/bongo-statuses.json` | Własne statusy według UUID |
| `<świat>/bongo-claim-flags.json` i `.bak` | Domyślne właścicieli, lokalne wyjątki i informacje o migracji |

Zachowaj cały katalog konfiguracji i świat. Aktualizacja wymaga podmiany jednego JAR-a i restartu. Nie usuwaj baz kont ani flag. Szczegóły starszych UUID i danych skinów opisuje [dokumentacja](docs/authentication.md).

## Budowanie i weryfikacja

Z JDK 25: `bash gradlew build` na Linuxie lub `gradlew.bat build` na Windowsie. Wynik: `build/libs/BongoUtils-1.7.0.jar`. `-sources.jar` zawiera kod źródłowy.

Testy jednostkowe uruchamia `build`. Dostępne są również testy protokołu logowania, whitelisty i zmiany haseł oraz wspólne testy GP3D, AFK i PL/EN w repozytorium Teleports. [Instrukcje testów](docs/testing.md).

## Autorstwo AI / AI disclosure

Kod tego moda został napisany przez AI — OpenAI Codex — na podstawie wymagań i wskazówek Bongo.

This mod's code was written by AI — OpenAI Codex — based on Bongo's requirements and guidance.
