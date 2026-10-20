# Budowanie i testy BongoUtils

[Powrót do README](../README.md)

## Budowanie i testy

Z Java 25:

```powershell
.\gradlew.bat build
```

Na Linuxie: `bash gradlew build`. Wynik: `build/libs/BongoUtils-1.7.0.jar`. Plik `-sources.jar` jest archiwum kodu, nie modem do instalacji. Loom 1.17.12 i Gradle 9.6.0 są przypięte w projekcie. Minecraft 26.3 używa oficjalnych nazw klas i nie wymaga Yarn.

`build` uruchamia 46 testów dotyczących haseł, trwałości danych, normalizacji nicków, URL Discorda, sieci prywatnych, PNG, limitów, whitelisty IGN, pierwszeństwa MSA oraz zmiany i wyczyszczenia hasła. Sprawdzane są trwałość zmian po restarcie, odrzucenie starego hasła, brak nadpisania nowszego zapisu przez stary formularz, ochrona MSA, uszkodzony plik konta i rejestracja po wyczyszczeniu. Testy whitelisty obejmują duplikaty z różną wielkością liter i odrzucenie błędnego pliku bez utraty poprzedniej listy. Testy regulaminu i konfiguracji obejmują zgodność starego configu, przełączanie trybów, klikalny URL, treść i zamykanie dialogu, rzeczywistą komendę reload w dispatcherze, uprawnienia oraz zachowanie całej poprzedniej konfiguracji po błędzie. Nie zastępują wizualnego sprawdzenia `/rules` w kliencie graficznym.

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

## Testy wspólne

Testy panelu GP3D, funkcji społecznościowych i lokalizacji uruchamia się z [repozytorium BongosTeleports](https://github.com/Silvobi/bongos-teleports), na osobnym środowisku `verification-allmods`. Zadania: `sharedClaimsTest`, `socialFeaturesTest`, `utilsLocalizationTest`. Szczegóły przygotowania i ograniczenia opisuje tamtejszy README i dokumentacja testów. Testowy `teleports-verification.jar` nie jest częścią moda produkcyjnego.

## Przywrócone funkcje i regresja dialogów

`mailIntegrationTest`, `restIntegrationTest` i `languageTest` obsługują osobny, uruchomiony serwer testowy z lokalnym RCON (parametry takie jak w poprzednich przykładach). Jednostkowe testy obejmują także skrzynki, URL, zapis i migrację. Z projektu Teleports `dialogLifecycleTest` sprawdza wszystkie używane dialogi, pocztę offline po restarcie, współdziałanie statusów z ignorowaniem i ochronę TPA.
