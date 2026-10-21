# Poczta offline `/mail`

[Powrót do README](../README.md)

| Komenda | Dostęp i działanie |
| --- | --- |
| `/mail` | Skrót do nieprzeczytanej skrzynki |
| `/mail send <nick>` | Każdy gracz; formularz wiadomości do znanego konta, także offline |
| `/mail read new` | Własne nieprzeczytane wiadomości |
| `/mail read all` | Odebrane i wysłane, bez podwójnych wpisów przy wysłaniu do siebie |
| `/mail read send` | Wysłane maile |
| `/mail read recive` | Wszystkie odebrane; działa także alias `receive` |
| `/mail admin-send` | Gracz z OP 3+; ważny mail do znanych kont, zastępuje `/mail send *` |
| `/mail send-multiple <nick> <nick> ...` | Gracz z OP 3+; ważny mail do wskazanych osób, bez duplikatów |

Odbiorca musi być znany BongoUtils. Wielkość liter nicku nie ma znaczenia. Broadcast wybiera istniejące konta przy otwarciu formularza; przyszłe konta nie dostają starych ogłoszeń. Zwykła wiadomość respektuje obustronne ignorowanie i pełny vanish; ważna poczta administracyjna omija te ograniczenia.

Formularz przyjmuje tytuł (1–120 znaków, jeden wiersz) i wielowierszową treść (1–4000 znaków). Ważny mail wymaga dwóch tytułów i dwóch treści: polskiej oraz angielskiej. Klient PL dostaje polską wersję; pozostałe języki — angielską. Dotyczy to listy, treści i kopii nadawcy. Zwykłe maile zachowują tekst autora. Błąd walidacji zachowuje szkic.

Adresy HTTP/HTTPS w ważnym mailu wkleja się bezpośrednio w treść. Serwer tworzy podkreślone, kolorowe linki z natywnym `open_url`, bez osobnych przycisków załączników i bez pobierania zawartości URL. Inne schematy, błędne adresy i adresy z danymi logowania pozostają tekstem. Stare załączniki URL nadal wyświetlają się jako klikalne linie tekstu. Nowe linki mieszczą się w limicie treści; dawne limity pola załączników dotyczą tylko starego formatu. Limit zwykłego nadawcy to 5 wiadomości na minutę. Przed wysłaniem serwer ponownie sprawdza OP, prywatność, odbiorców i token formularza. Cofnięcie OP blokuje otwarty formularz administracyjny.

Lista mieści po 8 wiadomości na stronie. Etykiety: `⚠ Nadawca - Tytuł` (⚠ tylko dla ważnych). Przy wysłanych widoczny jest odbiorca, a dla wysyłek grupowych lista odbiorców. Otwarcie odebranej wiadomości oznacza ją jako przeczytaną; samo otwarcie listy tego nie robi. Czytanie kopii wysłanej nie zmienia stanu u odbiorcy. Odbiorcy mają niezależne stany przeczytania. Powiadomienie po wejściu oraz po otrzymaniu maila ma pomarańczowy tekst i czerwony, podkreślony licznik, który uruchamia `/mail read new`.

Zapis: `config/bongoutils/mail.json`, schemat 3. Jedna atomowa operacja zapisuje kopie odbiorców i kopię wysłaną. Lista odbiorców jest wyłącznie w kopii nadawcy. Dane zachowują się po restartach; obie skrzynki i adresaci uczestniczą w migracjach kont oraz rollbacku. Schematy 1/2 zachowują treść, linki i stan przeczytania; tytuł powstaje z początku treści. Historia wysłanych jest odtwarzana z zachowanych kopii u odbiorców — wiadomości nieobecnych w starej bazie nie da się odzyskać. Starszy mail bez EN zachowuje oryginalny tekst dla każdego języka. Prototypowe załączniki przedmiotowe nie są odtwarzane. Zachowaj kopię przed aktualizacją; starsze wersje moda nie obsługują schematu 3.

Dialogi, listy, błędy i powiadomienia mają polski język i angielski fallback. Kompozytor i lista zamykają starą stronę bez ekranu oczekiwania, a serwer otwiera kolejną. Widok wiadomości pozostaje otwarty podczas klikania URL; jego Wróć/Zamknij nadal obsługuje serwer. Tokeny są przypisane do gracza i jednej sesji, a ponowienie wysłania nie duplikuje maila.
