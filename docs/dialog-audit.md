# Przegląd „Oczekiwania na serwer” — 1.7.0 / Clans 1.1.2

[Powrót do README](../README.md)

Natywny klient 26.3 wykonuje `afterAction` także po przycisku bez akcji. `WAIT_FOR_RESPONSE` otwiera wtedy ekran oczekiwania, chociaż nie wysłano żadnego pakietu do serwera. Taki przypadek występował przy Zamknij w `/skin` i przy odliczaniu `/clan delete`. Przy wygasłych lub starych tokenach część handlerów celowo ignorowała kliknięcie — ten sam tryb oczekiwania pozostawał wtedy bez zakończenia.

| Mod i okno | Obecna polityka | Zachowanie |
| --- | --- | --- |
| Utils: logowanie/rejestracja | `NONE` | Formularz pozostaje widoczny podczas obliczeń i limitowania powtórzeń; serwer kończy go po uwierzytelnieniu lub rozłącza klienta |
| Utils: menu skina, IGN i URL | `CLOSE` | Przycisk zamyka starą stronę; serwer otwiera następny formularz lub informuje o wyniku |
| Utils: wybór konta, zmiana i reset hasła | `CLOSE` | Nawigacja, walidacja i anulowanie nie wymagają ekranu oczekiwania |
| Utils: lokalne/globalne flagi i potwierdzenie zmiany | `CLOSE` | Nowa strona lub błąd; stary token nie blokuje klienta |
| Utils: kompozytor i lista poczty | `CLOSE` | Wysłanie, błędy, strony i anulowanie zachowują działający formularz |
| Utils: odczyt maila i załączniki URL | `NONE` | Link nie blokuje wiadomości; Wróć/Zamknij mają własną akcję serwera |
| Utils: `/wiki` | `CLOSE` | Poprawione lokalne Zamknij; wcześniej `NONE` pozostawiało okno otwarte |
| Utils: `/rules` | `CLOSE` | Już poprawne; lokalne zamknięcie bez odpowiedzi serwera |
| Clans: `/clan delete` | `NONE` | Kliknięcie szarego odliczania pozostawia okno. Serwer włącza potwierdzenie po 10 sekundach i obsługuje anulowanie |
| Clans: `/clan info` | Ekwipunek vanilla | Nie używa natywnego systemu dialogów ani jego ekranu oczekiwania |
| Teleports | Brak własnych dialogów | Własne komendy używają czatu i hotbara. Panel jego flag pochodzi z Utils |

Zmiana prezentacji nie wyłącza kontroli tokenów, właściciela formularza, uprawnień, czasu potwierdzenia ani blokady wejścia do świata przed logowaniem. W produkcyjnym kodzie trzech modów żaden formularz nie używa już `WAIT_FOR_RESPONSE`.

Weryfikacja: `DialogLifecycleTest` w Utils sprawdza fabryki, obowiązkowe logowanie i kompletną wspólną rejestrację komend. `dialogLifecycleTest` w repozytorium Teleports odbiera rzeczywiste dialogi z serwera vanilla, sprawdza politykę klienta, przejścia, błędy, anulowanie, odliczanie klanu, `/mail` po restarcie oraz relacje statusów/ignorowania/pingów/TPA. Testy działają wyłącznie w oznaczonym, odizolowanym fixture. Nie jest to wizualny test klienta graficznego.
