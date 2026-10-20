# Poczta offline `/mail`

[Powrót do README](../README.md)

| Komenda | Dostęp i działanie |
| --- | --- |
| `/mail` | Skrót do nieprzeczytanej skrzynki |
| `/mail send <nick>` | Każdy gracz; formularz wiadomości do znanego konta, także offline |
| `/mail read new` | Własne nieprzeczytane wiadomości |
| `/mail read all` | Cała własna skrzynka, także przeczytane wiadomości |
| `/mail send *` | Gracz z OP 3+; ważny mail do znanych kont |
| `/mail send-multiple <nick> <nick> ...` | Gracz z OP 3+; ważny mail do wskazanych osób, bez duplikatów |

Odbiorca musi być znany BongoUtils. Wielkość liter nicku nie ma znaczenia. Broadcast wybiera istniejące konta przy otwarciu formularza; przyszłe konta nie dostają starych ogłoszeń. Zwykła wiadomość respektuje obustronne ignorowanie i pełny vanish; ważna poczta administracyjna omija te ograniczenia.

Formularz przyjmuje wielowierszową treść do 4000 znaków. Limit zwykłego nadawcy to 5 wiadomości na minutę. Operator może dodać do 5 adresów HTTP(S), każdy w osobnym wierszu. To odnośniki, także do obrazów i plików; serwer zapisuje URL, bez pobierania zawartości. Odbiorca otwiera je natywnym przyciskiem i potwierdzeniem klienta. Błąd adresu zachowuje szkic do poprawienia. Przed wysłaniem serwer ponownie sprawdza OP, prywatność, odbiorców i token formularza.

Lista mieści po 8 wiadomości na stronie. Otwarcie konkretnej wiadomości oznacza ją jako przeczytaną; samo otwarcie listy tego nie robi. Powiadomienie po wejściu oraz po otrzymaniu maila ma pomarańczowy tekst i czerwony, podkreślony licznik, który uruchamia `/mail read new`.

Zapis: `config/bongoutils/mail.json`, obejmujący treść, adresy, odbiorców i stan przeczytania. Dane zachowują się po restartach i uczestniczą w migracjach kont oraz rollbacku. Starsze prototypowe załączniki przedmiotowe nie są odtwarzane; treść i stan przeczytania pozostają zachowane. Nie usuwaj pliku podczas aktualizacji JAR-a.

Dialogi, listy, błędy i powiadomienia mają polski język i angielski fallback. Kompozytor i lista zamykają starą stronę bez ekranu oczekiwania, a serwer otwiera kolejną. Widok wiadomości pozostaje otwarty podczas klikania URL; jego Wróć/Zamknij nadal obsługuje serwer. Tokeny są przypisane do gracza i jednej sesji, a ponowienie wysłania nie duplikuje maila.
