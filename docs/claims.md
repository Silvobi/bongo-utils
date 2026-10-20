# GP3D: flagi, dziedziczenie, migracja i API

[Powrót do README](../README.md)

## Zasady panelu

Opcjonalna integracja z uniwersalnym **GriefPrevention3D 19.0.0**, Fabric **26.3**. Pozostałe funkcje BongoUtils działają również bez GP3D. Panel jest natywnym dialogiem Minecrafta; nie wymaga instalowania moda na kliencie.

- `/manageclaim` otwiera flagi najdokładniejszej działki/poddziałki obejmującej bieżące X/Y/Z gracza. Poza działką zgłasza błąd. Wymagane jest prawo właściciela lub **Manage Trust**, sprawdzane ponownie przy każdym kliknięciu. Sam Build Trust z klanu nie daje dostępu do panelu.
- `/manageclaim global` otwiera **domyślne ustawienia właściciela**, a nie ustawienia całego serwera. Nie trzeba stać na działce. Dotyczą istniejących i przyszłych działek/poddziałek gracza, również kiedy właściciel jest offline. Nie obejmują działek administracyjnych ani poddziałek oznaczonych w GP3D jako administracyjne.

Okno lokalne pozostaje przypisane do działki wybranej **w momencie otwarcia**, nawet po przejściu lub teleportacji do innego miejsca. Usunięcie działki, zmiana właściciela, utrata uprawnień, zmiana danych w międzyczasie lub upływ 5 minut wymagają ponownego otwarcia. Przyciski są przypisane do gracza i pojedynczej strony formularza; powtórzenie starego pakietu nie zapisuje ponownie zmiany.

### Flagi i pierwszeństwo

Każda flaga jest rozpatrywana niezależnie:

1. **Lokalny wyjątek dokładnie tej działki lub poddziałki**, także jawne „Wyłączone”.
2. **Ustawienie globalne właściciela**.
3. **Domyślna wartość dostawcy flagi**.

Indywidualna flaga rodzica nie staje się automatycznie flagą jego poddziałki. Wybór **Dziedzicz ustawienie globalne** usuwa tylko wybrany lokalny wyjątek. **Domyślne moda** w panelu globalnym usuwa tylko wybraną domyślną właściciela. Pozostałe flagi i lokalne wyjątki pozostają zachowane. Panel pokazuje wartość i źródło ustawienia. Każda zmiana wymaga wybrania wartości i naciśnięcia **Zastosuj**.

Panel obejmuje wszystkie cztery flagi natywnego API GP3D: eksplozje, eksplozje Withera, PvP i alerty granic. Korzystają one z rzeczywistego zapisu GP3D, a nie niezależnej kopii niepowiązanej z ochroną. Nadal obowiązują uprawnienia GP3D dla danej komendy/flagi; zmiana alertów lokalnych wymaga prawa edycji właścicielskiej. PvP pozostaje zablokowane dla typów działek wyłączonych w konfiguracji GP3D. Przywrócenie domyślnej polityki PvP usuwa znacznik jawnego przełączenia, zamiast niechcący włączać walkę w domyślnie chronionej działce.

Zmiana natywną komendą GP3D zapisuje **lokalny wyjątek**, również kiedy gracz ponownie ustawi tę samą wartość. Komendy GP3D zachowują swoje własne wymagania; przykładowo GP3D pozwala graczom z Build Trust przełączać eksplozje, mimo że sam panel wymaga Manage Trust.

Dostępne po zainstalowaniu odpowiednich aktualizacji:

| Flaga | Mod | Domyślnie |
| --- | --- | --- |
| Publiczne pady teleportacyjne | BongosTeleports **1.6.0** | Wyłączone; Access Trust nadal pozwala używać prywatnych padów |
| Dostęp członków klanu | BongosCommunityClans **1.1.2** | **Build Trust**, obejmujący skrzynie i podstawowy dostęp |

Dostęp klanowy ma poziomy `off`, `access`, `container`, `build`. Nie dziedziczy się automatycznie do ograniczonych lub zagnieżdżonych poddziałek; wymagają one własnego lokalnego ustawienia. Panel pokazuje wtedy „Wyłączone • brak dziedziczenia”. Jawne odmowy GP3D nadal blokują dodatkowe prawo klanowe. Publiczne pady nie otwierają TPA ani nie dają prawa zarządzania padem.

### Migracja i zapis

Przy pierwszym rozpoznaniu działki stare natywne flagi **różniące się od domyślnych GP3D** stają się lokalnymi wyjątkami. Jawne stare ustawienie PvP rozpoznawane po znaczniku GP3D również jest zachowywane. Pozostałe wartości dziedziczą ustawienia właściciela. GP3D nie przechowuje historii dawnych zmian wszystkich flag; ręcznie ustawione dawniej „Wyłączone” identyczne z domyślnym GP3D jest zatem traktowane jak domyślne — zgodnie z wybranym sposobem migracji.

Stare publiczne pady z Teleports są importowane jednorazowo jako lokalne wyjątki. Przywrócenie dziedziczenia nie powoduje ponownego odtworzenia starej flagi po restarcie.

Wspólny zapis: `<świat>/bongo-claim-flags.json`, kopia poprzedniego zapisu: `.bak`. Zmiany są zapisywane atomowo; błędny plik nie jest zastępowany pustą bazą. Natywne wartości pozostają dodatkowo w danych GP3D. Zmiana w panelu synchronizuje je bezpośrednio; nowe działki są wykrywane i otrzymują natywne domyślne w ciągu maksymalnie około sekundy. Flagi niestandardowe są rozstrzygane przy użyciu, bez takiego opóźnienia.

### API dla kolejnych modów

`pl.bongo.bongoutils.claims.ClaimFlags.register(id, labelPL, labelEN, values, fallback)` rejestruje flagę przed uruchomieniem świata. Wariant z dodatkowym `boolean inheritIndependent` pozwala wyłączyć automatyczne dziedziczenie w ograniczonych i zagnieżdżonych poddziałkach. `value(claim, id, fallback)` zwraca obowiązującą wartość, `hasLocal(claim, id)` sprawdza wyjątek, a `override(claim, id, value)` ustawia go (`null` przywraca dziedziczenie). `importLegacy` służy jednorazowej migracji. Rejestracja i odczyt mogą odbywać się refleksyjnie, aby uniknąć twardej zależności od BongoUtils. API zapisu jest przeznaczone dla kodu serwera, który wcześniej zweryfikował uprawnienia użytkownika.

Testy wspólnej integracji znajdują się w projekcie BongosTeleports: `sharedClaimsTest` oraz `sharedClaimsWithoutGpTest`. Używają osobnego katalogu `verification-allmods` z markerem `claim-suite-fixture.txt`, nigdy świata docelowego. Testowy JAR `teleports-verification.jar` dodaje fixture nieistniejących kont Mojang do cache wyłącznie na takim serwerze; szyfrowanie i formularze haseł pozostają rzeczywiste. Tego testowego JAR-a nie instalować na serwerze użytkowym. Testy protokołu sprawdzają strukturę dialogów, przyciski i zachowanie serwera; nie zastępują wizualnej oceny okna w graficznym kliencie Minecrafta.
