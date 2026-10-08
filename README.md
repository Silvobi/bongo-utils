# BongoUtils


## Autorstwo AI / AI disclosure

Kod tego moda został napisany przez AI — OpenAI Codex — na podstawie wymagań i wskazówek Bongo.

This mod's code was written by AI — OpenAI Codex — based on Bongo's requirements and guidance.

## Nowości w 1.4.0

### Ignorowanie graczy

- `/ignore <nick>` przełącza ignorowanie; ponowne użycie odblokowuje gracza. `/ignore` pokazuje własną listę.
- Ignorowanie działa w obie strony: obaj gracze nie widzą swojego zwykłego czatu, `/msg`, `/tell`, `/w`, czatu drużynowego ani czatu Bongo's Community Clans. Wiadomości prywatne między tą parą są odrzucane.
- W TAB osoba ignorująca nadal widzi oryginalny nick drugiego gracza, w szarym, pochylonym stylu widza. Drugi gracz widzi zamiast jej nicku `Ignorowany` / `Ignored`, według języka klienta. Obaj otrzymują ikonę braku połączenia zamiast rzeczywistego pingu.
- Postacie, skiny, oryginalne nicki nad głową i faktyczny tryb gry pozostają normalne. Zmiana wyglądu TAB nie zmienia trybu gry gracza.
- Integracja z **Bongo's Teleports 1.4.0** blokuje TPA w obie strony. Zmiana ignorowania anuluje również istniejącą prośbę, potwierdzenie kosztu lub warm-up.
- Lista jest zapisywana w `config/bongoutils/social.json`, według UUID. Można odblokować także zapamiętanego gracza offline.

### Vanish i semi-vanish

Komendy wymagają **OP poziomu 4** i przełączają stan gracza wykonującego komendę:

| Komenda | TAB i podpowiedzi | Postać i moby | Wiadomość prywatna po wpisaniu nicku |
|---|---|---|---|
| `/vanish` | Ukryty | Postać nie jest wysyłana innym klientom; moby nie wykrywają ani nie obierają go za cel | Niedostępna |
| `/vanish fake` | Jak powyżej | Jak powyżej | Niedostępna |
| `/semi-vanish` | Ukryty | Normalnie widoczny, z oryginalnym nickiem; moby działają normalnie | Dostępna, o ile nie blokuje jej `/ignore` |

`/vanish fake` dodatkowo wysyła zwykły, tłumaczony przez klienta komunikat wyjścia lub wejścia. Gdy ukrycie zaczęło się z `fake`, zwykłe `/vanish` przy wyjściu z tego trybu również wysyła komunikat wejścia. Bez `fake` nie ma takich komunikatów.

Pełny vanish usuwa także fizyczne kolizje, dźwięki gracza i zdarzenia wibracji, blokuje publiczny czat i TPA oraz ukrywa zwykłe komunikaty dołączenia, wyjścia, śmierci i postępów. Skrzynie, skrzynie kresu, beczki i shulkery można obsługiwać bez publicznej animacji pokrywy, odgłosu otwierania i zdarzeń liczników otwarcia. Piece działają normalnie. Zmiana zawartości lub stanu świata jest rzeczywistą zmianą — pozostaje widoczna po zakończeniu operacji.

Oba tryby ukrywają nick także w `/list`, `/list uuids` i publicznej próbce graczy/statusie serwera. Pełny vanish jest filtrowany przy rozwiązywaniu vanilla selektorów wykonywanych przez innych graczy. Semi-vanish dopuszcza dokładnie wpisany nick. Tryby zachowują się po ponownym połączeniu i restarcie; nowi obserwatorzy nie dostają postaci ani wpisu TAB pełnego vanish.

Konsola zachowuje dostęp administracyjny i logi. Dla przywrócenia widoczności konta offline lub po migracji dostępne jest **wyłącznie z konsoli**:

```text
bongoutils visibility <nick> visible
bongoutils visibility <nick> semi
bongoutils visibility <nick> vanish
```

### Migracja danych kont

Komendy działają z konsoli lub konta z **OP poziomu 4**. Oba konta muszą być offline, także po zakończeniu sprawdzania tożsamości:

```text
/bongoutils migrate move StaryNick NowyNick
/bongoutils migrate swap PierwszyNick DrugiNick
/bongoutils migrate override ZrodlowyNick DocelowyNick
```

- **MOVE** przenosi dane na wolne konto; odmawia, jeżeli cel ma zapisane dane.
- **SWAP** zamienia dane obu kont. Wymaga istniejących zapisów świata dla obu graczy.
- **OVERRIDE** przenosi źródło na cel i zastępuje jego poprzedni zapis. Źródłowy zapis zostaje usunięty, więc kolejna gra na źródłowym koncie zaczyna się od nowa. Poprzedni stan obu kont pozostaje w kopii.

Zakres: cały zapis gracza Minecrafta (ekwipunek, enderchest, XP, pozycja, zdrowie i pozostałe zapisane właściwości), statystyki, postępy, hasła kont offline, wybór skina Bongo Utils, ignorowanie i tryby widoczności. Przy obecności Bongo's Teleports 1.4.0 migrują właściciele padów, cooldowny i `/ignore-tpa`. Istniejące pady i ich połączenia w świecie są zachowane. Przy Community Clans 1.0.3 migrują członkostwo, role, preferencje i zaproszenia; nazwy członków oraz TAB są odświeżane bez restartu.

Nadpisanie założyciela klanu z innymi członkami jest odrzucane, aby nie zostawić klanu bez lidera. W takim przypadku użyj SWAP lub najpierw przekaż przywództwo klanu. Pojedynczy pusty po nadpisaniu klan jest usuwany.

Rezerwacje nazw premium, tożsamości Mojang, bany, OP i obie whitelisty pozostają przypisane do dotychczasowych kont. Hasło offline nie staje się hasłem premium. Gdy konto premium przenosi się na offline, użytkownik nowego konta rejestruje własne hasło przy następnym wejściu. Nicki offline mają tę samą, niewrażliwą na wielkość liter tożsamość co logowanie Bongo Utils. Weryfikacja nieznanego nicku w Mojang odbywa się w tle.

Przed pierwszą zmianą powstaje `config/bongoutils/migration-backups/<czas>-<id>/` z oryginalnymi plikami, manifestem ścieżek, opisem operacji i statusem. Błąd zapisu lub przeładowania cofa całą operację. Niezakończona migracja po przerwaniu procesu jest automatycznie cofana przy następnym starcie, przed odczytem kont i świata. Pamięci podręczne statystyk i postępów są czyszczone po sukcesie.

### Zgodność i weryfikacja 1.4.0

Bongo Utils może działać samodzielnie. Jeśli instalujesz także Bongo's Teleports, zaktualizuj **oba** mody do 1.4.0; loader odrzuci mieszankę starej i nowej wersji. Community Clans 1.0.3 nie wymaga wymiany JAR-a. Klientowi nadal wystarczy vanilla 26.3.

Testy jednostkowe obejmują relacje ignorowania, zapis trybów, zamianę UUID, nadpisanie, kopie zapasowe, cofanie błędnej migracji, odtworzenie przerwanej transakcji i ochronę liderów klanu. Testy protokołu na izolowanym serwerze z trzema modami obejmują TAB, czat i czat klanowy, aliasy `/msg`, TPA i anulowanie warm-up, podpowiedzi, selekcję mobów, kolizje, znikanie i ponowne pojawienie postaci, status serwera, ciche skrzynie, rzeczywistą migrację ekwipunku/XP oraz restart i ponowne logowanie.

Weryfikacja podpisanego czatu kont premium nadal wymaga zalogowanego klienta premium. Filtrowanie wykonuje się przed dodaniem wiadomości do łańcucha `last seen`, bez usuwania już wysłanych podpisanych pakietów.

**Bongo's server-side utility tool.**

Wersja **1.4.0**. Mod wyłącznie serwerowy dla **Minecraft Java 26.3**, **Fabric Loader 0.19.5** i **Fabric API 0.161.0+26.3**. Wymaga **Java 25**. Klient może być vanilla — nie instaluje BongoUtils ani Fabric API.

## Instalacja

1. Zatrzymaj serwer i wykonaj kopię świata oraz danych graczy.
2. Umieść `BongoUtils-1.4.0.jar` i `fabric-api-0.161.0+26.3.jar` w katalogu `mods/` serwera Fabric 26.3. Przy aktualizacji usuń stary JAR BongoUtils; nie zostawiaj dwóch wersji moda. Zachowaj katalog `config/bongoutils/`.
3. W `server.properties` ustaw:

   ```properties
   online-mode=true
   enforce-secure-profile=false
   ```

4. Uruchom serwer. Mod utworzy `config/bongoutils/`.
5. W mieszanym trybie offline/premium możesz użyć whitelisty po nickach: najpierw `ign-whitelist add TWÓJ_NICK`, potem `ign-whitelist on`. Jest niezależna od UUID i po włączeniu zastępuje standardową kontrolę whitelisty. Domyślnie jest wyłączona — wtedy obowiązuje zwykła whitelista Minecrafta.

Mod sam wybiera uwierzytelnianie Mojang lub logowanie offline. Zalecane `online-mode=true` nie uniemożliwia połączeń offline przy włączonym `allowOffline`. `enforce-secure-profile=false` jest potrzebne, ponieważ konta offline nie mają podpisanych kluczy profilu.

## Logowanie

- Przy połączeniu mod sprawdza profil nicku przez API Mojang. UUID z pakietu klienta służy jedynie do wyboru rodzaju połączenia.
- Konto przedstawiające oficjalny UUID przechodzi standardową weryfikację **sesji Mojang**. Dopiero udana weryfikacja daje wejście bez hasła.
- UUID premium bez prawidłowej sesji zostaje odrzucony. Nie ma awaryjnego przełączenia takiego połączenia na dostęp offline.
- Połączenie offline z nickiem niezajętym przez konto MSA otrzymuje szyfrowanie, a potem natywny dialog w fazie konfiguracji, **przed wejściem do świata**.
- Pierwszy raz: `Hasło` i `Powtórz hasło`. Następne połączenie: `Podaj hasło`. Hasło ma 8–128 znaków.
- Klient nie otrzymuje dostępu do świata, ekwipunku ani komend przed udanym logowaniem. Zamknięcie formularza nie omija tej blokady. Czas na logowanie: 120 sekund.
- Hasła trafiają przez pakiet formularza, bez komend czatu. Na dysku jest wyłącznie PBKDF2-HMAC-SHA256 (600 000 iteracji, losowa sól 16 bajtów).
- Limit: 5 nieudanych prób na połączenie, 10 przesłań poprawnie zbudowanego formularza na konto w ciągu 5 minut oraz 20 połączeń z IP na minutę. Limity w pamięci przeżywają ponowne połączenie, ale restart serwera je zeruje.
- Różna wielkość liter nicku nie tworzy drugiego konta offline.
- Drugie połączenie nie wyrzuca już połączonego gracza przed uwierzytelnieniem.
- Przy pierwszym wejściu do gry po poprawnym uwierzytelnieniu MSA nick zostaje trwale zarejestrowany w `premium.json`, a gracz otrzymuje prywatny zielony komunikat o automatycznej rejestracji. Kolejne wejścia ani odświeżanie skina nie powtarzają komunikatu.
- Konta MSA mają pierwszeństwo: połączenie offline z oficjalnie zajętym nickiem jest odrzucane także wtedy, gdy właściciel nigdy nie odwiedził serwera. Kontrola odbywa się przy każdym połączeniu i nie rozróżnia wielkości liter.
- Sam wynik wyszukiwania nicku lub UUID z klienta nie zapisuje rejestracji MSA. Wymagana jest poprawna sesja Mojang; podszycie się pod oficjalny UUID kończy się rozłączeniem z odpowiednim komunikatem MSA.
- Awaria API Mojang blokuje nowe połączenie zamiast nadawać mu status premium. Wyniki sprawdzenia profilu mają krótki cache (5 minut; brak profilu: 1 minuta).

**Ograniczenie vanilla 26.3:** natywne pole tekstowe dialogu nie maskuje znaków hasła. Tekst jest widoczny podczas wpisywania. Formularz wyraźnie o tym informuje. Używaj osobnego hasła serwerowego. Maskowanie wymagałoby zmiany klienta.

### MSA i próby wejścia offline

Pierwsza rejestracja potwierdzona sesją MSA wyświetla graczowi na czacie, zielonym kolorem:

```text
Połączono z konta MSA. Gracz został zarejestrowany automatycznie
```

Połączenie offline z IGN zarejestrowanym już na serwerze jako MSA zostaje odrzucone przed ekranem hasła. Ekran rozłączenia:

```text
Połączenie odrzucone! (czy łączysz się w trybie OFFLINE?)
IGN, z którego korzystasz jest zarejestrowane jako "Konto MSA".
Zaloguj się do swojego konta MSA i spróbuj ponownie lub użyj innego IGN.
```

Jeżeli nick istnieje w Mojang, ale nie został jeszcze zarejestrowany na serwerze, połączenie offline również zostaje odrzucone. Przykład próby z nickiem `NoTcH`:

```text
IGN NoTcH jest już zajęty przez inne konto MSA!
Zmień IGN (in-game nickname) aby móc połączyć się w trybie OFFLINE, lub zaloguj się do swojego konta MSA.
```

Gracz mający starsze konto offline pod nickiem zajętym przez MSA również zostanie zablokowany, nawet jeżeli zna poprzednie hasło. Jego dane pozostają na dysku; mod nie łączy danych konta offline i MSA. Właściciel z poprawną sesją MSA wchodzi bez hasła offline. Rejestracja i zielony komunikat następują po wejściu do świata; wcześniejsza odmowa przez whitelistę lub ban nie zużywa pierwszego powitania. Do czasu pierwszego wejścia właściciela nick nadal chroni kontrola zajętości w Mojang.

### Tożsamość i istniejący serwer

Offline UUID jest wyliczany z `OfflinePlayer:<nick małymi literami>`. Dzięki temu `Bongo` i `bongo` oznaczają jedno konto. Może to różnić się od UUID starego serwera offline, który liczył UUID z oryginalną wielkością liter. Przed użyciem na istniejącym serwerze trzeba świadomie przenieść dane graczy, whitelistę, uprawnienia i banlistę. Mod nie migruje istniejących UUID ani baz innych modów logowania.

Obsługiwane są bezpośrednie połączenia z serwerem. Integracje Velocity/BungeeCord/Floodgate, alternatywne usługi uwierzytelniania i inne mody przechwytujące logowanie nie były implementowane ani testowane.

## `/ign-whitelist`

Whitelistą zarządza konsola, RCON lub gracz mający takie same uprawnienia jak do standardowej `/whitelist` (poziom administratora 3). Lista porównuje **wyłącznie IGN**, bez UUID, zapytań do Mojang czy wymagania, aby gracz wcześniej wszedł na serwer. Wielkość liter nie ma znaczenia: `Bongo`, `bongo` i `BONGO` oznaczają ten sam wpis.

```text
/ign-whitelist add Bongo
/ign-whitelist add GraczOffline InnyGracz
/ign-whitelist on
/ign-whitelist list
/ign-whitelist remove GraczOffline
/ign-whitelist reload
/ign-whitelist off
```

- `add NICK [NICK ...]` — dodaje jeden lub więcej nicków, również offline i nigdy niepołączonych. Duplikaty nie tworzą kolejnych wpisów.
- `remove NICK [NICK ...]` — usuwa po nicku, niezależnie od wielkości liter.
- `on` — włącza listę IGN jako obowiązującą kontrolę whitelisty. Wpis do standardowego `whitelist.json` nie jest wtedy wymagany, a wpis wyłącznie na zwykłej whiteliście nie wystarczy.
- `off` — wyłącza listę IGN i **przywraca działanie zwykłej whitelisty** według `white-list` w `server.properties`. Jeśli chcesz wtedy otworzyć serwer dla wszystkich, dodatkowo użyj `/whitelist off`.
- `list` — pokazuje stan oraz zapisane nicki. Sama `/ign-whitelist` również wyświetla listę.
- `reload` — przeładowuje zapis z pliku; błędny plik pozostawia poprzednią poprawną listę i stan w pamięci.

Włączenie/wyłączenie listy IGN nie zmienia `whitelist.json` ani ustawienia `white-list`. Przy aktywnej liście IGN sprawdzanie nicku obowiązuje także operatorów: status OP/UUID nie omija listy. Hasło offline, weryfikacja konta premium, bany kont/IP i limit graczy nadal obowiązują. Lista IGN nie jest sposobem na potwierdzenie własności nicku — od tego pozostaje uwierzytelnianie BongoUtils.

Nicki mają 1–16 znaków: litery A–Z, cyfry lub `_`. Można podać kilka nicków oddzielonych spacjami. Ta komenda przyjmuje nicki, a nie selektory `@a`/`@p`.

Podobnie jak przy zwykłej whiteliście, **`enforce-whitelist=true`** powoduje wyrzucenie połączonych graczy bez wpisu po `on`, `remove` i `reload`. Z `enforce-whitelist=false` zmiany ograniczają nowe wejścia, bez odłączania obecnych graczy. Ponowna kontrola połączenia przed wejściem do świata również używa aktualnej listy.

Stan i wpisy zapisują się atomowo w `config/bongoutils/ign-whitelist.json` i przeżywają restart serwera:

```json
{
  "enabled": true,
  "names": ["Bongo", "GraczOffline"]
}
```

Po ręcznej edycji pliku użyj `/ign-whitelist reload`. Domyślny nowy plik ma `enabled=false` i pustą listę.

## `/skin`

Komenda otwiera natywny ekran z wyborem:

- **Nick (IGN)** — pobiera podpisaną teksturę konta Minecraft przez Mojang. Zapisywany jest wybrany podpisany profil tekstury, aby wybór przetrwał restart i był dostępny bez każdorazowego zapytania. PNG nie jest zapisywany. Jest to kopia skina wybranego w danym momencie; późniejsze zmiany na koncie źródłowym wymagają ponownego wyboru IGN.
- **Adres URL PNG** — pobiera obraz z HTTPS, sprawdza faktyczny format PNG i wymiary 64×64 albo 64×32. Obsługuje parametry w linkach Discorda (`?ex=...&is=...&hm=...`) i linki bez rozszerzenia `.png`, jeżeli zawartość jest poprawnym PNG. Można wybrać klasyczne lub wąskie ramiona.

Obraz z URL jest zapisywany lokalnie według SHA-256, a następnie przesyłany do **MineSkin** w celu otrzymania podpisu tekstury akceptowanego przez klienta vanilla. Zachowane zostają PNG oraz wartość i podpis tekstury — wygasły link Discorda nie jest potrzebny podczas następnego wejścia. Samo zapisanie PNG na dysku nie umożliwia zwykłemu klientowi odczytania go; klient pobiera podpisaną teksturę z `textures.minecraft.net`.

MineSkin może nałożyć limit, zmienić zasady dostępu albo być niedostępny. Anonimowe żądanie kolejki działało w teście; opcjonalny klucz API można wpisać w konfiguracji. Adresy Discorda z podpisem nie są zapisywane w metadanych skina. Obraz przesłany do MineSkin ma widoczność `unlisted`.

PNG ma limit 1 MiB, pobieranie limit czasu i do 4 przekierowań. Każde przekierowanie ponownie podlega weryfikacji. Odrzucane są sieci lokalne/prywatne, inne protokoły, dane logowania w URL i porty inne niż 443. Połączenie TLS jest przypięte do sprawdzonego publicznego IP z weryfikacją certyfikatu i nazwy hosta.

Po zapisaniu skina mod automatycznie przeprowadza krótką rekonfigurację połączenia. Wygląd odświeża się u gracza i odbiorców nowego profilu bez ręcznego rozłączania i bez ponownego hasła. Może być widoczny krótki ekran ładowania. Limit zmiany: raz na 30 sekund na konto, również po ponownym połączeniu.

## Konfiguracja

`config/bongoutils/config.json`:

```json
{
  "allowOffline": true,
  "mineSkinApiKey": ""
}
```

`allowOffline=false` zezwala wyłącznie na zweryfikowane konta Mojang. Opcjonalny klucz: https://account.mineskin.org/keys. Po zmianie konfiguracji zrestartuj serwer. Klucza nie umieszczaj w publicznym repozytorium.

## `/changepass`

Komenda działa w grze dla operatorów z uprawnieniami takimi jak `/whitelist` (poziom 3 lub 4). Klient vanilla nie potrzebuje dodatkowego moda.

1. Wpisz `/changepass`, podaj IGN i wybierz **Sprawdź konto**.
2. Mod sprawdzi lokalny zapis hasła offline i brak rejestracji MSA. Nie trzeba, aby gracz był aktualnie online; wielkość liter nicku nie ma znaczenia.
3. Dla zarejestrowanego konta offline kolejny ekran oferuje **Zmień hasło** oraz **Wyczyść hasło**.
4. Zmiana wymaga nowego hasła (8–128 znaków) i jego powtórzenia. Wyczyszczenie nie wymaga wypełniania pól hasła, ale otwiera osobny ekran potwierdzenia.

Po skutecznej zmianie lub wyczyszczeniu połączenia konta offline są rozłączane, także podczas logowania przed wejściem do świata. Następne wejście wymaga nowego hasła, a po wyczyszczeniu — ponownej rejestracji. Wyczyszczenie umożliwia następnej osobie używającej tego IGN ustawienie nowego hasła; ekran potwierdzenia o tym informuje. MSA, dane świata, ekwipunek i skiny pozostają bez zmian.

Aktualnego hasła nie można pokazać ani odzyskać: na dysku jest solony hash PBKDF2, nie tekst hasła. Nowe hasło jest przesyłane formularzem, bez komendy na czacie. **Vanilla 26.3 nie maskuje pola hasła**, więc tekst widać podczas wpisywania; używaj osobnego hasła serwerowego.

Każdy formularz jest związany z połączeniem operatora, losowym tokenem oraz wybranym kontem. Nie da się zmienić konta dodatkowym polem pakietu ani użyć przycisku innego gracza. Uprawnienia są sprawdzane przy akcji i ponownie przed zapisem po obliczeniu hasha. Formularz wygasa po 5 minutach. Jeżeli w międzyczasie inny administrator zmienił/resetował hasło albo konto zostało zarejestrowane jako MSA, stary formularz nie nadpisuje nowych danych — wymaga ponownego wskazania nicku. Błędny plik konta powoduje błąd odczytu, zamiast być traktowany jako konto bez hasła.

Hash jest obliczany poza głównym wątkiem serwera, a zapis jest atomowy. Limit obliczania nowych haseł: 10 prób na operatora w ciągu minuty. Log serwera zapisuje nick operatora, nick konta i rodzaj operacji; nie zapisuje hasła ani hasha. Niezalogowany klient offline nie może wejść do gry ani użyć komendy.

Konsola/RCON nie otwierają ekranów `/changepass`. Dostępna wcześniej komenda `bongoutils resetpassword NICK` nadal pozwala resetować hash z konsoli/RCON; jej dotychczasowe działanie pozostaje bez zmian.

## Dane i reset hasła

W `config/bongoutils/`:

- `accounts/<nick>.json` — solony hash hasła offline;
- `premium.json` — nicki potwierdzone sesją Mojang;
- `ign-whitelist.json` — stan whitelisty IGN i nicki;
- `skins/<uuid>.json` — wybór skina gracza;
- `skins/<sha256>.png` — trwała kopia obrazu z URL;
- `skins/<sha256>-classic.json` lub `-slim.json` — cache podpisanej tekstury.

Kopia zapasowa powinna objąć cały ten katalog. Stare obrazy i podpisy pozostają w cache; administrator może usuwać nieużywane pliki po wykonaniu kopii.

Wyłącznie z konsoli serwera/RCON:

```text
bongoutils resetpassword NICK
```

Usuwa hash konta offline; następne połączenie wymaga rejestracji. Ta operacja pozwala następnej osobie korzystającej z nicku ustawić nowe hasło, więc wykonuj ją tylko po sprawdzeniu właściciela. Nie usuwa rezerwacji nicku premium.

## Budowanie i testy

Z Java 25:

```powershell
.\gradlew.bat build
```

Na Linuxie: `bash gradlew build`. Wynik: `build/libs/BongoUtils-1.4.0.jar`. Plik `-sources.jar` jest archiwum kodu, nie modem do instalacji. Loom 1.17.12 i Gradle 9.6.0 są przypięte w projekcie. Minecraft 26.3 używa oficjalnych nazw klas i nie wymaga Yarn.

`build` uruchamia 33 testy dotyczące haseł, trwałości danych, normalizacji nicków, URL Discorda, sieci prywatnych, PNG, limitów, whitelisty IGN, pierwszeństwa MSA, zmiany i wyczyszczenia hasła, ignorowania i migracji. Sprawdzane są trwałość zmian po restarcie, odrzucenie starego hasła, brak nadpisania nowszego zapisu przez stary formularz, ochrona MSA, uszkodzony plik konta i rejestracja po wyczyszczeniu. Testy whitelisty obejmują duplikaty z różną wielkością liter i odrzucenie błędnego pliku bez utraty poprzedniej listy.

Dołączony `IntegrationHarness` łączy się jako klient zwykłego protokołu Minecrafta, bez BongoUtils po stronie klienta. Na osobnym **lokalnym** serwerze testowym, z akceptacją EULA, odpowiednimi zależnościami, `server-ip=127.0.0.1`, `server-port=25579` i wyłączoną whitelistą:

```powershell
.\gradlew.bat integrationTest -PtestPort=25579
```

Test tworzy nowe konto testowe, sprawdza rejestrację, blokadę świata, błędne hasła, logowanie po ponownym połączeniu i ze zmianą wielkości liter, ochronę przed wyrzuceniem istniejącego gracza oraz odrzucenie podrobionego UUID premium. Otwiera również dialogi `/skin`, kopiuje publiczny skin Notcha przez IGN i URL, sprawdza podpis oraz rekonfigurację bez kolejnego logowania. Wykonuje rzeczywiste zapytania do Mojang i MineSkin; wymaga sieci i zajmuje co najmniej około 40 sekund ze względu na limit zmiany skina. Uruchamiaj go na serwerze testowym, ponieważ zapisuje konto i skin.

Testy potwierdzają protokół i uruchomienie serwera. Nie obejmują wizualnego obejrzenia ekranu w kliencie graficznym ani udanego logowania prawdziwą sesją konta premium — do tego potrzebny jest zalogowany gracz. Odrzucenie fałszywej sesji premium zostało sprawdzone.

Osobny test whitelisty działa na **tymczasowym lokalnym serwerze**, z `enforce-whitelist=true`, RCON dostępnym wyłącznie lokalnie i hasłem testowym:

```powershell
.\gradlew.bat whitelistIntegrationTest -PtestPort=25579 -PtestRconPort=25580 -PtestRconPassword=TWOJE_HASLO_TESTOWE
```

Test sprawdza faktyczne podkomendy, wejście offline mimo aktywnej whitelisty UUID, zmianę klientowego UUID, wielkość liter, uprawnienia, filtrowanie operatorów, wyrzucanie usuniętego gracza, zachowanie banów i powrót do zwykłej whitelisty po `off`. Modyfikuje ustawienia whitelisty oraz nadaje/odbiera OP i bany kontu testowemu, więc uruchamiaj go wyłącznie na serwerze testowym.

`ChangePassIntegrationHarness` działa na tymczasowym lokalnym serwerze z wyłączoną whitelistą i lokalnym RCON:

```powershell
.\gradlew.bat changePassIntegrationTest -PtestPort=25579 -PtestRconPort=25580 -PtestRconPassword=TWOJE_HASLO_TESTOWE
```

Test tworzy dwa konta offline, nadaje/odbiera OP kontu operatora i sprawdza rzeczywiste dialogi, błędny nick, brak hasła, niedostępność MSA, powtórzenie hasła, potwierdzenie/anulowanie resetu, odebranie uprawnień oraz przycisk przesłany z innego połączenia. Potwierdza rozłączenie konta, odrzucenie starego hasła, przyjęcie nowego i rejestrację po wyczyszczeniu. Kontrola MSA w tym teście używa istniejącego wpisu `jeb_` z opisanej niżej konfiguracji testowej; pozostałe konta są tworzone przez prawdziwy protokół logowania offline. Test zapisuje konta testowe, więc nie uruchamiaj go na serwerze produkcyjnym. Logowanie oczekujące w tle dodatkowo sprawdza aktualność zweryfikowanego hasha tuż przed wpuszczeniem do świata, aby zmiana administratora nie dopuściła starego hasła sprawdzonego wcześniej.

Osobny `MsaIntegrationHarness` sprawdza na rzeczywistym serwerze rozłączenie dla nicku globalnie zajętego, lokalnie zarejestrowanego oraz obu przypadków podszycia się pod oficjalny UUID. Porównuje pełne komunikaty i potwierdza, że fałszywa sesja nie tworzy rejestracji. Przed uruchomieniem **tymczasowego serwera testowego** przygotuj jego `config/bongoutils/premium.json` z wpisem testowym `jeb_` i bez wpisu `notch`:

```json
{"jeb_": "853c80ef-3c37-49fd-aa49-938b674adae6"}
```

To jawna imitacja istniejącego zapisu, a nie uwierzytelnienie konta MSA. Następnie uruchom lokalny serwer i test:

```powershell
.\gradlew.bat msaIntegrationTest -PtestPort=25579 "-PtestPremiumFile=C:\SCIEZKA\SERWER_TESTOWY\config\bongoutils\premium.json"
```

Test używa rzeczywistych odpowiedzi Mojang. Prawidłowe wejście kontem MSA wraz z chatem wymaga dodatkowo zalogowanego gracza; test automatyczny nie posiada jego tokenu sesji i nie omija weryfikacji.

Źródła API: [Fabric 26.3](https://www.fabricmc.net/2026/09/15/263.html), [natywne dialogi Minecrafta](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-6), [kolejka MineSkin](https://docs.mineskin.org/docs/mineskin-api/queue-skin-generation/).
