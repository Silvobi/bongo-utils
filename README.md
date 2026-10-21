# BongoUtils

Serwerowy mod Fabric dla **Minecraft Java 26.3**: logowanie offline/MSA, skiny, whitelista IGN, poczta offline, ignorowanie i vanish, AFK i statusy, wzmianki, wiadomości prywatne, siadanie/leżenie, regulamin oraz panel działek GP3D. **Gracze korzystają z klienta vanilla, bez dodatkowego moda i paczki zasobów.**

> **Zakres dokumentacji:** opis obejmuje połączoną wersję **1.7.0** z gałęzi [`codex/rules-config-reload`](https://github.com/Silvobi/bongo-utils/tree/codex/rules-config-reload), przygotowaną w [PR #4](https://github.com/Silvobi/bongo-utils/pull/4). W chwili aktualizacji README (21 października 2026) `main` nadal zawiera kod **1.3.0**. Aby korzystać ze wszystkich opisanych funkcji, użyj kodu/JAR-a 1.7.0; sama aktualizacja dokumentacji nie dodaje funkcji do starego wydania.

## Spis treści

- [Wymagania i instalacja](#wymagania-i-instalacja)
- [Komendy i uprawnienia](#komendy-i-uprawnienia)
- [Logowanie offline i MSA](#logowanie-offline-i-msa)
- [Whitelista IGN](#whitelista-ign)
- [Skiny](#skiny)
- [Poczta offline](#poczta-offline)
- [Ignorowanie graczy](#ignorowanie-graczy)
- [Vanish i semi-vanish](#vanish-i-semi-vanish)
- [Maskowanie trybu obserwatora](#maskowanie-trybu-obserwatora)
- [AFK i własne statusy](#afk-i-własne-statusy)
- [Wzmianki i wiadomości prywatne](#wzmianki-i-wiadomości-prywatne)
- [Siadanie, leżenie i wiki](#siadanie-leżenie-i-wiki)
- [Działki GP3D](#działki-gp3d)
- [Regulamin i konfiguracja](#regulamin-i-konfiguracja)
- [Zmiana i reset hasła](#zmiana-i-reset-hasła)
- [Migracja kont](#migracja-kont)
- [Język i okna dialogowe](#język-i-okna-dialogowe)
- [Dane i aktualizacja](#dane-i-aktualizacja)
- [Budowanie i testy](#budowanie-i-testy)

## Wymagania i instalacja

| Składnik | Wersja |
| --- | --- |
| Minecraft Java Edition | 26.3 |
| Java / JDK do budowania | 25 |
| Fabric Loader | 0.19.5+ |
| Fabric API | 0.161.0+26.3 lub zgodny nowszy dla 26.3 |
| BongoUtils opisany w tym README | 1.7.0 |

Opcjonalne integracje: **GriefPrevention3D 19.0.0** (uniwersalny JAR), [BongosTeleports 1.6.0](https://github.com/Silvobi/bongos-teleports) i [BongosCommunityClans 1.1.2](https://github.com/Silvobi/bongos-community-clans). Są potrzebne odpowiednio do panelu działek, współpracy z TPA/padami i funkcji klanowych. Pozostałe narzędzia Utils działają samodzielnie.

1. Zatrzymaj serwer i wykonaj kopię świata oraz `config/bongoutils/`.
2. Umieść `BongoUtils-1.7.0.jar` i Fabric API w `mods/` serwera Fabric 26.3. Usuń poprzedni JAR Utils, zachowując konfigurację i dane.
3. W `server.properties` ustaw:

   ```properties
   online-mode=true
   enforce-secure-profile=false
   ```

4. Uruchom serwer. Mod utworzy `config/bongoutils/`.
5. Jeśli chcesz ograniczyć dostęp według nicków, najpierw dodaj siebie: `/ign-whitelist add TwojNick`, potem `/ign-whitelist on`.

Mod sam wybiera weryfikację sesji Mojang albo szyfrowane logowanie offline. `online-mode=true` nie wyklucza offline przy `allowOffline=true`. `enforce-secure-profile=false` jest potrzebne dla kont bez podpisanych kluczy profilu. W mieszanym środowisku sprawdź działanie czatu; w dotychczasowym zestawie używano [No Chat Reports](https://modrinth.com/mod/no-chat-reports).

Obsługiwane są bezpośrednie połączenia z serwerem. Velocity/BungeeCord/Floodgate, alternatywne usługi uwierzytelniania i inne mody przechwytujące logowanie nie były implementowane ani testowane.

## Komendy i uprawnienia

`<argument>` jest wymagany, `[argument]` — opcjonalny. **OP 3+** oznacza poziom uprawnień 3 lub 4. Osobiste formularze wymagają gracza w grze. W konsoli/RCON pomija się początkowy `/`.

| Komenda | Działanie | Dostęp |
| --- | --- | --- |
| `/skin` | Wybór skina z IGN lub HTTPS PNG | Gracz |
| `/mail` | Nieprzeczytana poczta | Gracz |
| `/mail send <nick>` | Formularz maila, także do gracza offline | Gracz |
| `/mail read new`, `/mail read all` | Nieprzeczytane / wszystkie widoczne maile | Gracz |
| `/mail send *` | Ważny mail do wszystkich znanych kont | Gracz, OP 3+ |
| `/mail send-multiple <nick1> <nick2> ...` | Ważny mail do wybranych kont | Gracz, OP 3+ |
| `/ignore [nick]`, `/ingore [nick]` | Lista lub przełączenie ignorowania | Gracz |
| `/vanish [fake]`, `/semi-vanish` | Przełączenie własnej widoczności | Gracz, OP 3+ |
| `/afk` | Ręczne przełączenie AFK | Gracz |
| `/status [tekst]` | Ustawienie statusu TAB; bez tekstu usuwa status | Gracz |
| `/m <gracz> <wiadomość>` | Alias `/msg` | Gracz |
| `/r <wiadomość>` | Odpowiedź ostatniemu rozmówcy | Gracz |
| `/sit`, `/lay` | Przełączenie siadania / leżenia | Gracz |
| `/wiki` | Okno z linkiem do wiki | Gracz |
| `/rules` | Regulamin jako dialog lub link | Gracz |
| `/manageclaim` | Flagi bieżącej działki/poddziałki | Właściciel lub Manage Trust; GP3D |
| `/manageclaim global` | Domyślne flagi działek danego właściciela | Gracz; GP3D |
| `/ign-whitelist [on\|off\|list\|reload]` | Stan i obsługa whitelisty IGN | OP 3+, konsola/RCON |
| `/ign-whitelist add <nick> [nick ...]` | Dodanie nicków | OP 3+, konsola/RCON |
| `/ign-whitelist remove <nick> [nick ...]` | Usunięcie nicków | OP 3+, konsola/RCON |
| `/changepass` | Zmiana lub wyczyszczenie hasła offline | Gracz, OP 3+ |
| `/bongoutils reload` | Przeładowanie `config.json` | OP 3+, konsola/RCON |
| `/bongoutils migrate move <z> <do>` | Przeniesienie na wolne konto | OP 3+, konsola/RCON |
| `/bongoutils migrate swap <z> <do>` | Zamiana danych kont | OP 3+, konsola/RCON |
| `/bongoutils migrate override <z> <do>` | Przeniesienie z nadpisaniem celu | OP 3+, konsola/RCON |
| `bongoutils visibility <nick> visible\|semi\|vanish` | Ustawienie widoczności znanego konta | Tylko konsola/RCON |
| `bongoutils resetpassword <nick>` | Usunięcie hasha hasła offline | Tylko konsola/RCON |

Mod dostosowuje również vanilla `/list` i `/list uuids`, aby respektowały ukrycie graczy. Szczegóły i ograniczenia poszczególnych komend opisano poniżej.

## Logowanie offline i MSA

Przy połączeniu mod sprawdza profil nicku w Mojang. UUID podany przez klienta wybiera rodzaj połączenia, ale sam nie potwierdza tożsamości.

- **MSA/premium:** oficjalny UUID musi przejść standardową weryfikację sesji Mojang. Dopiero poprawna sesja daje wejście bez hasła. Nieważna lub podrobiona sesja oznacza odrzucenie, bez przełączania na offline.
- **Offline:** dla nicku niezajętego przez MSA mod szyfruje połączenie i pokazuje formularz przed wejściem do świata. Pierwsze wejście wymaga hasła i powtórzenia; kolejne — podania hasła.
- **Pierwszeństwo MSA:** offline nie może używać nicku zajętego w Mojang, nawet jeśli właściciel nigdy nie odwiedził serwera. Dotyczy to także starszych kont offline pod takim nickiem. Ich dane nie są automatycznie łączone z MSA.
- Pierwsze wejście do świata z poprawną sesją MSA zapisuje nick w `premium.json` i wysyła prywatne zielone powitanie. Wyszukanie profilu, nieudane logowanie, ban lub odmowa whitelisty nie tworzą rejestracji.
- Awaria API Mojang blokuje nowe połączenie. Cache profilu trwa 5 minut, a odpowiedzi o braku profilu — 1 minutę.

Hasła mają **8–128 znaków**. Są przesyłane formularzem, bez komend na czacie. Na dysku zapisuje się PBKDF2-HMAC-SHA256, 600 000 iteracji i losową sól 16 bajtów. **Vanilla nie maskuje pola hasła: wpisywane znaki są widoczne. Używaj osobnego hasła serwerowego.**

Przed uwierzytelnieniem klient nie ma dostępu do świata, ekwipunku ani komend. Zamknięcie formularza nie omija blokady. Czas na logowanie: 120 sekund. Limity: 5 błędnych prób na połączenie, 10 poprawnie zbudowanych przesłań formularza na konto w 5 minut i 20 połączeń z IP na minutę. Limity przeżywają ponowne połączenie, ale restart je zeruje. Drugie nieuwierzytelnione połączenie nie wyrzuca zalogowanego gracza.

Offline UUID jest liczone z `OfflinePlayer:<nick małymi literami>`, więc `Bongo` i `bongo` to jedno konto. Na starszym serwerze liczącym UUID z oryginalną wielkością liter może być potrzebne ręczne przeniesienie danych, uprawnień i list dostępu. Mod nie migruje automatycznie baz innych modów logowania; własne narzędzia opisuje [migracja kont](#migracja-kont).

## Whitelista IGN

Lista porównuje wyłącznie nicki, bez UUID, zapytań do Mojang i wymogu wcześniejszego wejścia gracza. Wielkość liter nie ma znaczenia. Nick ma 1–16 znaków: litery A–Z, cyfry lub `_`. `add` i `remove` przyjmują wiele nicków oddzielonych spacjami, bez selektorów `@a`/`@p`.

```text
/ign-whitelist add Bongo GraczOffline
/ign-whitelist on
/ign-whitelist list
/ign-whitelist remove GraczOffline
/ign-whitelist reload
/ign-whitelist off
```

- `on` zastępuje standardową kontrolę whitelisty listą IGN. Wpis w `whitelist.json` nie jest wymagany i sam nie wystarcza. **Operatorzy też muszą mieć wpis IGN.**
- `off` przywraca zwykłą whitelistę według `white-list` w `server.properties`. Aby wtedy otworzyć serwer dla wszystkich, użyj też `/whitelist off`.
- Sama komenda lub `list` pokazuje stan i wpisy. `reload` odczytuje plik po ręcznej edycji; błędny plik zachowuje poprzednią poprawną listę w pamięci.

Mod nie zmienia `whitelist.json` ani `white-list`. Bany, limit graczy i logowanie nadal obowiązują. Przy `enforce-whitelist=true` operacje `on`, `remove` i `reload` wyrzucają również połączonych graczy bez wpisu; przy `false` ograniczają nowe wejścia.

Stan i wpisy zapisują się atomowo w `config/bongoutils/ign-whitelist.json`. Nowy plik ma `enabled=false` i pustą listę:

```json
{
  "enabled": false,
  "names": []
}
```

## Skiny

`/skin` otwiera wybór jednej z metod:

- **Nick (IGN):** pobranie podpisanej tekstury konta przez Mojang. Wybór przeżywa restart. To kopia skina z chwili wyboru; późniejsza zmiana na koncie źródłowym wymaga ponownego wskazania IGN.
- **HTTPS PNG:** obraz 64×64 lub 64×32, z wyborem klasycznych albo wąskich ramion. Działają linki Discorda z parametrami i adresy bez `.png`, jeśli zawartość jest poprawnym PNG.

PNG ma limit **1 MiB**. Mod zapisuje go lokalnie według SHA-256 i przesyła do **MineSkin** z widocznością `unlisted`, aby uzyskać podpis tekstury akceptowany przez vanilla. Zachowuje PNG, wartość i podpis — wygaśnięcie linku Discorda nie usuwa wybranego skina. Klient pobiera teksturę z `textures.minecraft.net`; parametry podpisanego linku Discorda nie trafiają do metadanych skina.

Pobieranie ma limit czasu i maksymalnie 4 przekierowania, sprawdzane na każdym kroku. Odrzucane są prywatne/lokalne adresy sieciowe, protokoły inne niż HTTPS, dane logowania w URL i porty inne niż 443. TLS jest przypięte do sprawdzonego publicznego IP z weryfikacją certyfikatu i nazwy hosta. MineSkin może być niedostępny lub ograniczać żądania; opcjonalny klucz ustawia się przez `mineSkinApiKey`.

Po zapisaniu mod rekonfiguruje połączenie i odświeża wygląd bez ręcznego wyjścia i bez ponownego hasła. Może pojawić się krótki ekran ładowania. Limit zmiany: **raz na 30 sekund na konto**, także po ponownym połączeniu.

## Poczta offline

Poczta działa między kontami zapamiętanymi przez BongoUtils, także gdy odbiorca jest offline. Nicki są porównywane bez rozróżniania wielkości liter.

1. Wpisz `/mail send NickOdbiorcy`.
2. Wypełnij wielowierszowy formularz i wybierz **Wyślij** lub **Anuluj**. Treść ma 1–4000 znaków i nie może być pusta po usunięciu białych znaków z brzegów.
3. Odbiorca dostaje powiadomienie od razu, jeśli jest online, albo przy następnym wejściu. Pomarańczowy tekst kończy czerwony, podkreślony licznik; kliknięcie uruchamia `/mail read new`.
4. `/mail` lub `/mail read new` otwiera nieprzeczytane, `/mail read all` — również przeczytane. Lista pokazuje **8 maili na stronie**, od najnowszych, z nadawcą, datą UTC i podglądem. Dopiero otwarcie konkretnego maila oznacza go jako przeczytany.

**Poczta administracyjna:** OP 3+ może użyć `/mail send *` do wszystkich znanych kont albo `/mail send-multiple Nick1 Nick2 ...` do co najmniej dwóch wskazanych nicków. Powtórzone nicki nie otrzymują duplikatów. Wiadomości są oznaczone **WAŻNE** i omijają ignorowanie oraz ograniczenia adresowania graczy w vanish. Broadcast obejmuje konta znane w chwili otwarcia formularza, bez przyszłych graczy.

**Załączniki:** operator może dodać do 5 pełnych adresów HTTP/HTTPS, po jednym w wierszu, do 2048 znaków każdy. Odbiorca widzi przyciski otwarcia i pełny URL po najechaniu. Minecraft używa standardowego potwierdzenia linku. Serwer zapisuje adresy, bez pobierania plików; nie są to załączniki przedmiotowe. Błędny adres zachowuje szkic do poprawienia.

Zwykły nadawca może wysłać **5 maili na minutę**; operator jest zwolniony z limitu. Uprawnienia, odbiorcy i prywatność są sprawdzane ponownie przy wysyłaniu. Odebranie OP unieważnia wysyłkę administracyjną lub z załącznikami. Formularz jest związany z połączeniem i tokenem, wygasa po 5 minutach; ponowienie starego przycisku nie duplikuje maila.

Zwykła poczta respektuje obustronne `/ignore`: blokuje wysyłanie, ukrywa stare maile tej pary i pomija je w liczniku. Odblokowanie przywraca historię. Pełny vanish blokuje zwykłe adresowanie odbiorcy; semi-vanish dopuszcza dokładnie wpisany nick. Oba tryby ukrywają nicki w podpowiedziach.

Treść, URL i stan odczytu zapisują się atomowo w `config/bongoutils/mail.json`, według UUID. Wysyłka zbiorowa zapisuje się w całości albo wcale. Skrzynki i tożsamości nadawców uczestniczą w MOVE/SWAP/OVERRIDE i wycofaniu migracji. Starszy prototyp zachowuje tekst i odczyt, ale jego załączniki przedmiotowe nie są odtwarzane.

## Ignorowanie graczy

`/ignore Nick` przełącza ignorowanie znanego gracza, ponowne użycie odblokowuje; samo `/ignore` pokazuje własną listę. Działa też alias z literówką: `/ingore`. Można odblokować zapamiętanego gracza offline.

Ignorowanie działa **w obie strony**: gracze nie widzą wzajemnie publicznego czatu, `/msg`, `/m`, `/tell`, `/w`, `/r`, czatu drużynowego ani Community Clans. Prywatne wiadomości tej pary są odrzucane. Poczta działa według zasad powyżej.

W TAB osoba ignorująca widzi oryginalny nick drugiej osoby w szarym, pochylonym stylu; druga osoba widzi zamiast nicku ignorującego napis `Ignorowany` / `Ignored`. Obie widzą ikonę braku połączenia zamiast pingu, a wpisy trafiają na dół. Postacie, skiny, nicki nad głową i rzeczywiste tryby gry pozostają normalne.

Z BongosTeleports ignorowanie blokuje TPA w obie strony i anuluje istniejącą prośbę, potwierdzenie kosztu lub odliczanie do teleportacji. Lista jest trwała, według UUID w `config/bongoutils/social.json`.

## Vanish i semi-vanish

Komendy wymagają **OP 3+** i dotyczą wykonującego je gracza. Ponowne użycie tego samego trybu przywraca widoczność; wybór innego przełącza tryb. Stan przeżywa wyjście i restart.

| Zachowanie wobec innych graczy | `/vanish` i `/vanish fake` | `/semi-vanish` |
| --- | --- | --- |
| TAB i podpowiedzi nicku | Ukryte | Ukryte |
| `/list`, `/list uuids`, publiczny status serwera | Ukryte | Ukryte |
| Postać, skin i nick nad głową | Postać nie jest wysyłana innym klientom | Normalnie widoczne |
| Moby, kolizje, dźwięki i wibracje gracza | Wyłączone wykrywanie/celowanie, kolizje, dźwięki i wibracje | Normalne zachowanie |
| Prywatna wiadomość po wpisaniu nicku | Niedostępna | Dostępna, jeśli nie blokuje jej ignore |
| Zwykły mail do ukrytego odbiorcy | Zablokowany | Dostępny po wpisaniu nicku |
| TPA z Teleports | Zablokowane | Normalne zasady, w tym ignore |

Pełny vanish blokuje publiczny czat i ukrywa zwykłe komunikaty dołączenia, wyjścia, śmierci i postępów. Ukrycie respektują nowi odbiorcy i zmiany wymiaru. Vanilla selektory wykonywane przez innych graczy filtrują pełny vanish; semi-vanish dopuszcza dokładnie wpisany nick. Konsola zachowuje dostęp administracyjny i logi.

`/vanish fake` wysyła pozorowany zwykły komunikat wyjścia przy ukryciu i wejścia przy powrocie, tłumaczony przez klienta. Jeśli ukrycie rozpoczęto z `fake`, powrót zwykłym `/vanish` również wyśle wejście. Zwykły vanish bez `fake` nie generuje tych komunikatów.

W pełnym vanish skrzynie, skrzynie kresu, beczki i shulkery otwierają się bez publicznej animacji, odgłosu i zdarzeń licznika otwarcia. Piece działają normalnie. Zmiany zawartości i świata nadal są rzeczywiste i widoczne.

Do ustawienia widoczności znanego konta, także offline, służy **wyłącznie konsola/RCON**:

```text
bongoutils visibility Nick visible
bongoutils visibility Nick semi
bongoutils visibility Nick vanish
```

## Maskowanie trybu obserwatora

Rzeczywisty `SPECTATOR` jest przedstawiany **innym klientom w TAB jako `SURVIVAL`**, bez automatycznej kursywy i sortowania obserwatorów. Właściciel otrzymuje prawdziwy tryb i zachowuje sterowanie obserwatorem. Jego postać jest ukryta przed wszystkimi pozostałymi graczami, także obserwatorami; wyjście z trybu przywraca śledzenie postaci.

Maskowanie działa przy zmianie trybu, dołączeniu kolejnego odbiorcy, ponownym połączeniu i zmianie wymiaru. Ignorowanie i vanish nadal obowiązują. Zmiana prezentacji TAB nie zmienia rzeczywistego trybu gry na serwerze.

## AFK i własne statusy

`/afk` ręcznie przełącza AFK. Po **5 minutach bez zmiany pozycji** AFK włącza się automatycznie według czasu rzeczywistego, niezależnie od TPS. Zmiana X/Y/Z lub wymiaru wyłącza także ręczne AFK. Obrót kamery, pisanie na czacie i otwieranie ekwipunku nie zerują czasu. Pomijany jest szum pozycji do 0,001 bloku.

TAB pokazuje szary, kursywny prefiks `[AFK]`. Zmiana stanu widocznego gracza wysyła szary komunikat w języku odbiorcy, np. `Nick jest teraz AFK.` / `Nick nie jest już AFK.`. Ukryte konta nie rozsyłają ogłoszeń. Wyjście usuwa AFK bez dodatkowej wiadomości o powrocie; po dołączeniu czas liczy się od nowa.

`/status Buduję bazę` ustawia w TAB `[Buduję bazę] Nick`. Tekst ma 1–32 znaki, może zawierać spacje i jest traktowany dosłownie, bez kodów formatowania. Samo `/status` go usuwa. Prefiks klanu pozostaje widoczny, np. `[AFK] [TAG] Nick`.

AFK ma pierwszeństwo przed statusem, ale go nie kasuje: po powrocie status wraca, również jeśli zmieniono go podczas AFK. Statusy przeżywają restart w `<świat>/bongo-statuses.json`; AFK jest tymczasowe. W 1.7.0 działa jedna wspólna implementacja AFK; dawny opis ręcznego sufiksu z 1.6.0 nie opisuje obecnego prefiksu i automatycznego wykrywania.

## Wzmianki i wiadomości prywatne

`@Nickname` na publicznym czacie rozpoznaje pełny nick gracza online bez rozróżniania wielkości liter. Wzmianka jest **żółta dla pozostałych odbiorców, niebieska dla wspomnianego**, który dostaje dźwięk `minecraft:block.note_block.pling` i komunikat nad hotbarem z nickiem autora. Powtórzenia jednej osoby w wiadomości dają jedno powiadomienie.

Nieznane nicki, fragmenty dłuższych nazw i `email@Nickname` nie pingują. Prywatne wiadomości, czat klanu i wiadomości systemowe nie uruchamiają publicznych wzmianek. Uwzględniane są filtrowanie czatu, ignorowanie i ukrycie nicków; mechanizm nie odtwarza odfiltrowanej treści.

`/m Nick wiadomość` jest aliasem vanilla `/msg`. `/r wiadomość` odpowiada ostatniemu rozmówcy zapamiętanemu po wysłaniu lub odebraniu `/msg`, `/m`, `/tell`, `/w` albo `/r`. Rozmówca jest identyfikowany przez UUID; odpowiedź wymaga jego obecności online i respektuje ignore/vanish. Wiadomość do siebie nie zmienia rozmówcy. Przy wielu odbiorcach `/msg` nadawca zapamiętuje ostatniego w kolejności Minecrafta. Po wyjściu gracz traci własnego zapamiętanego rozmówcę; restart usuwa całą pamięć odpowiedzi.

## Siadanie, leżenie i wiki

`/sit` siada w bieżącym miejscu, `/lay` kładzie postać; powtórzenie komendy kończy pozę. Nie potrzeba krzesła ani łóżka. Siadanie obsługuje bezpieczne powierzchnie, płyty, schody i dywany; leżenie wymaga równego podparcia pod całą poziomą postacią. Sprawdzane są kształty kolizji, miejsce na wstanie, granica świata i załadowanie chunków.

Odpoczynek jest blokowany w powietrzu, podczas jazdy, pływania, lotu, palenia się, zamarzania, w trybie obserwatora i bezpośrednio po obrażeniach. Zagrożenia obejmują płyny, ogień, lawę, kaktusy, magmę, zapalone ogniska, krzaki jagód, róże Withera, sypki śnieg, nacieki, kotły z lawą i portale. Zgaszone ognisko jest dopuszczalne. Dodatkowe niebezpieczne bloki można wskazać datapackiem przez tag `bongoutils:unsafe_rest`.

Ruch, skok, Shift lub komenda kończą pozę; przy leżeniu działa też **Opuść łóżko**. Obrażenia, teleportacja, zmiana wymiaru/trybu gry, śmierć, wyjście i rekonfiguracja klienta również ją przerywają. Bezpieczeństwo jest sprawdzane co tick: utrata podłoża lub nowe zagrożenie powodują wstanie. Jeśli pierwotne miejsce zablokowano, mod szuka bezpiecznego miejsca w pobliżu.

`/lay` używa animacji klienta bez rzeczywistego snu: nie przewija nocy, nie zmienia respawnu i nie zajmuje łóżka. Tymczasowe siedziska nie zapisują się w świecie; zapis siedzącego gracza zachowuje bezpieczną wysokość stania.

`/wiki` pokazuje lokalizowany przycisk do `https://tinyurl.com/bxw-smp`, ze standardowym potwierdzeniem otwarcia linku. Adres wiki jest stały w kodzie; osobny konfigurowalny link udostępnia `/rules`.

## Działki GP3D

Panel wymaga opcjonalnego **GriefPrevention3D 19.0.0**. `/manageclaim` wybiera najdokładniejszą działkę/poddziałkę obejmującą bieżące **X/Y/Z**. Dostęp ma właściciel lub osoba z **Manage Trust**; samo Build Trust nie wystarcza. Bez GP3D, poza działką lub bez praw pojawia się błąd.

Okno pozostaje przypisane do działki z chwili otwarcia, także po przemieszczeniu. Zapis ponownie sprawdza prawa. Usunięcie działki, zmiana właściciela lub danych, utrata uprawnień i upływ 5 minut wymagają ponownego otwarcia. Zmianę zatwierdza **Zastosuj**.

`/manageclaim global` ustawia domyślne flagi **działek danego właściciela**, istniejących i przyszłych; można go użyć poza działką. Nie są to ustawienia całego serwera ani działek administracyjnych. Każda flaga jest rozstrzygana osobno:

1. Lokalny wyjątek dokładnie tej działki/poddziałki, także jawne „Wyłączone”.
2. Ustawienie globalne właściciela.
3. Domyślna wartość dostawcy flagi.

**Dziedzicz ustawienie globalne** usuwa wybrany lokalny wyjątek; globalne **Domyślne moda** usuwa tylko tę domyślną właściciela. Lokalna flaga rodzica nie przechodzi automatycznie na poddziałkę. Panel pokazuje wartość i źródło.

| Flaga | Dostawca | Zasady |
| --- | --- | --- |
| Eksplozje, eksplozje Withera, PvP, alerty granic | GP3D | Natywne wartości i uprawnienia GP3D; konfiguracja GP3D nadal ogranicza PvP |
| Publiczne pady teleportacyjne | BongosTeleports 1.6.0 | Domyślnie wyłączone; Access Trust pozwala używać prywatnych padów |
| Dostęp członków klanu | BongosCommunityClans 1.1.2 | Domyślnie `build`; poziomy `off`, `access`, `container`, `build` |

Build Trust klanu obejmuje budowanie, skrzynie i podstawowy dostęp, bez prawa zarządzania flagami. Ograniczone i zagnieżdżone poddziałki wymagają własnego lokalnego włączenia dostępu klanowego; jawne odmowy GP3D nadal obowiązują. Publiczne pady nie otwierają TPA i nie dają prawa zarządzania padem.

Zmiana natywną komendą GP3D zapisuje lokalny wyjątek, nawet przy ponownym wybraniu tej samej wartości. Przy pierwszym odczycie starszych działek zachowywane są wartości różne od domyślnych GP3D oraz rozpoznawalne jawne ustawienie PvP. Dawnego ustawienia identycznego z domyślnym, bez zapisanego znacznika, nie da się odróżnić od domyślnego i zaczyna ono dziedziczyć. Stare publiczne pady są importowane jednorazowo.

Zapis: `<świat>/bongo-claim-flags.json` i poprzednia wersja `.bak`; natywne wartości pozostają również w GP3D. Panel synchronizuje je bezpośrednio. Nowe działki otrzymują natywne domyślne w ciągu około sekundy; flagi niestandardowe są rozstrzygane przy użyciu. Błędna baza nie jest zastępowana pustą.

Dla innych modów dostępne jest API `pl.bongo.bongoutils.claims.ClaimFlags`: `register(...)`, `value(...)`, `hasLocal(...)`, `override(...)` i `importLegacy(...)`. Rejestracja odbywa się przed startem świata; `override(..., null)` usuwa wyjątek. Kod wywołujący zapis musi wcześniej sprawdzić uprawnienia użytkownika.

## Regulamin i konfiguracja

Przykładowy `config/bongoutils/config.json`:

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
- `mineSkinApiKey` to opcjonalny klucz podpisywania skinów z URL, dostępny w [panelu MineSkin](https://account.mineskin.org/keys). Nie publikuj go w repozytorium.
- `rules.mode="dialog"` pokazuje przez `/rules` numerowaną listę `entries`, tytuł `title` i przycisk zamknięcia.
- `rules.mode="link"` wysyła klikalny link HTTP/HTTPS z `rules.url`.
- Opcjonalne `rules.en` wybiera angielskie pola dla klientów innych niż polskie. Pominięte `title`, `entries` i `url` dziedziczą wspólne wartości; dla angielskiej strony można podać `rules.en.url`.

Nowa konfiguracja ma pustą listę zasad; treść administrator uzupełnia sam. Po edycji wykonaj **`/bongoutils reload`**. Przeładowuje cały `config.json` bez restartu i rozłączania obecnych graczy. Błędny lub brakujący plik pozostawia ostatnią poprawną konfigurację w pamięci. Otwarty regulamin zachowuje poprzednią treść do ponownego `/rules`.

Whitelista IGN ma osobny plik i `/ign-whitelist reload`. Przeładowanie konfiguracji nie odczytuje ponownie baz kont, poczty ani świata.

## Zmiana i reset hasła

`/changepass` jest narzędziem administratora **OP 3+**, dostępnym w grze:

1. Podaj IGN i wybierz **Sprawdź konto**. Gracz może być offline, wielkość liter nie ma znaczenia.
2. Dla zarejestrowanego konta offline wybierz **Zmień hasło** lub **Wyczyść hasło**. Konta MSA nie podlegają operacji.
3. Zmiana wymaga nowego hasła 8–128 znaków i powtórzenia. Wyczyszczenie ma osobny ekran potwierdzenia.

Po zapisie połączenia konta offline są rozłączane, także przed wejściem do świata. Następne wejście wymaga nowego hasła albo ponownej rejestracji. **Wyczyszczenie pozwala następnej osobie używającej tego IGN ustawić hasło**, dlatego najpierw sprawdź właściciela. MSA, świat, ekwipunek i skiny pozostają bez zmian.

Hasła nie można pokazać ani odzyskać z hasha. Formularz nie maskuje znaków. Uprawnienia są sprawdzane przy akcji i przed zapisem; token jest przypisany do połączenia i konta, sesja wygasa po 5 minutach. Stary formularz nie nadpisuje nowszej zmiany ani konta zarejestrowanego w międzyczasie jako MSA. Uszkodzony plik powoduje błąd odczytu.

Hashowanie odbywa się poza głównym wątkiem, zapis jest atomowy. Limit: 10 prób obliczenia nowego hasła na operatora na minutę. Log zapisuje operatora, konto i operację, bez haseł i hashy.

Konsola/RCON nie otwiera formularza. Może usunąć hash przez:

```text
bongoutils resetpassword Nick
```

To odrębny reset konsolowy: następne połączenie wymaga rejestracji, rezerwacja premium pozostaje. Rozłącz konto przed takim resetem — ścieżka konsolowa nie wykonuje rozłączania zapewnianego przez `/changepass`.

## Migracja kont

Administrator **OP 3+** lub konsola/RCON może przenieść dane między tożsamościami:

```text
/bongoutils migrate move StaryNick NowyNick
/bongoutils migrate swap PierwszyNick DrugiNick
/bongoutils migrate override ZrodlowyNick DocelowyNick
```

| Tryb | Działanie |
| --- | --- |
| `move` | Przenosi na wolne konto; odmawia, jeśli cel ma dane objęte kontrolą migracji, np. zapis świata, hasło, skin, skrzynkę lub dane integracji |
| `swap` | Zamienia dane; wymaga istniejących zapisów świata obu graczy |
| `override` | Zastępuje cel danymi źródła i usuwa zapis źródła; poprzednie stany pozostają w kopii |

Oba konta muszą być offline. Migrację blokują też połączenia w trakcie logowania/konfiguracji. Nieznane nicki są sprawdzane w Mojang w tle.

Zakres: zapis gracza Minecrafta (ekwipunek, enderchest, XP, pozycja, zdrowie i inne właściwości), statystyki, postępy, hasła offline, wybór skina, ignorowanie, widoczność, skrzynki i tożsamości nadawców maili. Z Teleports migrują właściciele padów, cooldowny i `/ignore-tpa`, a pady i połączenia pozostają w świecie. Z Community Clans migrują członkostwo, role, preferencje i zaproszenia; nazwy oraz TAB są odświeżane.

Nadpisanie założyciela klanu z innymi członkami jest odrzucane. Użyj `swap` lub najpierw przekaż przywództwo. Klan, który po nadpisaniu zostałby pusty, jest usuwany.

**Nie migrują:** tożsamości Mojang, rezerwacje premium, OP, bany, obie whitelisty, własność natywnych działek GP3D, globalne/lokalne flagi panelu i własne statusy TAB. Pozostają związane z dotychczasowym kontem/UUID. Hasło offline nie staje się hasłem MSA; przy przeniesieniu premium na offline nowe konto rejestruje własne hasło. Narzędzie nie importuje automatycznie baz innych modów.

Przed zapisem powstaje `config/bongoutils/migration-backups/<czas>-<id>/` z oryginalnymi plikami, manifestem i stanem operacji. Błąd zapisu/przeładowania cofa transakcję. Przerwana migracja jest automatycznie cofana przy następnym starcie, przed odczytem kont i świata. Nie ma osobnej komendy ręcznego rollbacku; zachowaj katalogi kopii i pełny backup serwera.

## Język i okna dialogowe

Klient z językiem `pl`, `pl_*` lub `pl-*` otrzymuje polskie komunikaty; pozostałe lub brakujące ustawienia — angielskie. Brak polskiego klucza korzysta z angielskiego. Dotyczy formularzy, błędów, poczty, wiki, flag i powiadomień, według języka odbiorcy.

Język logowania jest dostępny po przekazaniu ustawień klienta; wcześniejsze odrzucenia są angielskie. Większość komunikatów konsoli/RCON jest angielska, starsze teksty migracji i `visibility` pozostają polskie. Vanilla używa tłumaczeń klienta. Nicki, statusy i maile nie są tłumaczone; regulamin lokalizuje się przez `rules.en`.

W 1.7.0 usunięto przejścia formularzy logowania, skinów, haseł, poczty i flag na ekran **„Oczekiwanie na serwer”**. Logowanie zachowuje formularz podczas przetwarzania. Zamknięcie w skinach i wiki działa też bez pakietu odpowiedzi. Nadal obowiązują tokeny, limity czasu i kontrola uprawnień. Poprawka okna usuwania klanu wymaga Clans 1.1.2.

## Dane i aktualizacja

| Lokalizacja | Zawartość |
| --- | --- |
| `config/bongoutils/config.json` | Logowanie, klucz MineSkin, regulamin |
| `config/bongoutils/accounts/<nick>.json` | Solony hash hasła offline |
| `config/bongoutils/premium.json` | Nicki/UUID potwierdzone sesją Mojang |
| `config/bongoutils/ign-whitelist.json` | Stan i nicki whitelisty |
| `config/bongoutils/skins/<uuid>.json` | Wybór skina |
| `config/bongoutils/skins/<sha256>.png` | Trwała kopia PNG z URL |
| `config/bongoutils/skins/<sha256>-classic.json` lub `-slim.json` | Cache podpisanej tekstury |
| `config/bongoutils/social.json` | Znane konta, ignorowanie, vanish/semi-vanish |
| `config/bongoutils/mail.json` | Poczta, odczyt i URL załączników |
| `config/bongoutils/migration-backups/` | Oryginały i manifesty migracji |
| `<świat>/bongo-statuses.json` | Własne statusy TAB według UUID |
| `<świat>/bongo-claim-flags.json` i `.bak` | Flagi właścicieli, wyjątki i stan importu |

Kopia powinna objąć **cały świat i cały `config/bongoutils/`**, także dane opcjonalnych integracji. Przy aktualizacji zatrzymaj serwer, podmień pojedynczy JAR i uruchom ponownie. Nie usuwaj kont, skrzynek ani flag. Stare obrazy i podpisy skinów pozostają w cache; usuwaj nieużywane pliki po wykonaniu kopii.

Trwałe są hasła, rejestracje MSA, skiny, whitelista, ignorowanie, vanish, poczta, statusy i flagi. AFK, pozy, formularze, pamięć `/r` i limity w pamięci resetują się po restarcie.

## Budowanie i testy

Wymagane jest **JDK 25**. Dla kompletu funkcji najpierw wybierz kod 1.7.0:

```text
git clone https://github.com/Silvobi/bongo-utils.git
cd bongo-utils
git switch codex/rules-config-reload
```

Windows:

```powershell
.\gradlew.bat build
```

Linux:

```sh
bash gradlew build
```

Wynik dla tej wersji: `build/libs/BongoUtils-1.7.0.jar`; `-sources.jar` to kod, nie mod do instalacji. Numer artefaktu wynika z `mod_version` w `gradle.properties` wybranej gałęzi. Projekt przypina Loom 1.17.12 i Gradle 9.6.0; używa oficjalnych nazw klas Minecrafta, bez Yarn.

`build` uruchamia testy jednostkowe m.in. haseł, zapisu, MSA, whitelisty, skinów/URL/PNG, poczty, migracji, ignorowania, odpoczynku, statusów, lokalizacji i konfiguracji. Zestaw zależy od wersji kodu.

Testy integracyjne łączą się z **osobnym, już uruchomionym lokalnym serwerem testowym** i zmieniają jego dane. Przygotuj zależności, zaakceptowaną EULA, `server-ip=127.0.0.1` i np. port gry 25579. Dla RCON użyj portu 25580 i własnego hasła, dostępnych wyłącznie lokalnie.

| Zadanie Gradle w 1.7.0 | Zakres / przygotowanie |
| --- | --- |
| `integrationTest` | Rejestracja, blokada świata, hasła, ponowne połączenia, fałszywy UUID premium i skiny; wyłączona whitelista, API Mojang/MineSkin |
| `whitelistIntegrationTest` | Komendy, OP, bany, wyrzucanie, powrót do whitelisty UUID; `enforce-whitelist=true`, RCON |
| `changePassIntegrationTest` | Formularze, uprawnienia, zmiana/reset i rejestracja; wyłączona whitelista, RCON, testowy wpis `jeb_` |
| `msaIntegrationTest` | Zajęte nicki i fałszywe sesje; testowy `premium.json` |
| `mailIntegrationTest` | Poczta, prywatność, załączniki i strony; RCON |
| `restIntegrationTest` | Siadanie, leżenie, bezpieczeństwo, zapis i wiki; RCON |
| `languageTest` | Formularze PL/EN i fallback; RCON |

Przykłady na Windowsie:

```powershell
.\gradlew.bat integrationTest -PtestPort=25579
.\gradlew.bat mailIntegrationTest -PtestPort=25579 -PtestRconPort=25580 -PtestRconPassword=TWOJE_HASLO_TESTOWE
```

Pozostałe zadania z RCON przyjmują te same trzy parametry. Na Linuxie zamień `.\gradlew.bat` na `bash gradlew`. Dla testu MSA przed startem serwera testowego przygotuj jego `config/bongoutils/premium.json` z `jeb_`, bez `notch`:

```json
{"jeb_": "853c80ef-3c37-49fd-aa49-938b674adae6"}
```

To fixture imitujące istniejący zapis, nie potwierdzenie sesji MSA. Ten sam wpis jest potrzebny do kontroli MSA w `changePassIntegrationTest`. Test MSA otrzymuje ścieżkę do pliku:

```powershell
.\gradlew.bat msaIntegrationTest -PtestPort=25579 "-PtestPremiumFile=C:\SERWER_TESTOWY\config\bongoutils\premium.json"
```

Wspólne testy GP3D, AFK/statusów, wzmianek, lokalizacji i dialogów są w [BongosTeleports](https://github.com/Silvobi/bongos-teleports): `sharedClaimsTest`, `sharedClaimsWithoutGpTest`, `socialFeaturesTest`, `utilsLocalizationTest`, `dialogLifecycleTest`. Używają odizolowanego `verification-allmods`; `teleports-verification.jar` nie jest przeznaczony na serwer użytkowy.

Testy protokołu nie zastępują wizualnej kontroli okien. Udane wejście prawdziwym MSA wymaga zalogowanego gracza; testy nie mają jego tokenu i nie omijają weryfikacji. Testy skinów wymagają sieci i dostępności usług zewnętrznych. Nie uruchamiaj testów integracyjnych na świecie produkcyjnym.

## Autorstwo i licencja

Kod tego moda został napisany przez AI — OpenAI Codex — na podstawie wymagań i wskazówek Bongo.

This mod's code was written by AI — OpenAI Codex — based on Bongo's requirements and guidance.

Licencja: [MIT](LICENSE).
