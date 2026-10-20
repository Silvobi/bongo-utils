# Ignorowanie, vanish i migracja kont

[Powrót do README](../README.md)

## Ignorowanie, widoczność i migracja

### Ignorowanie graczy

- `/ignore <nick>` przełącza ignorowanie; ponowne użycie odblokowuje gracza. `/ignore` pokazuje własną listę.
- Ignorowanie działa w obie strony: obaj gracze nie widzą swojego zwykłego czatu, `/msg`, `/tell`, `/w`, czatu drużynowego ani czatu Bongo's Community Clans. Wiadomości prywatne między tą parą są odrzucane.
- W TAB osoba ignorująca nadal widzi oryginalny nick drugiego gracza, w szarym, pochylonym stylu widza. Drugi gracz widzi zamiast jej nicku `Ignorowany` / `Ignored`, według języka klienta. Obaj otrzymują ikonę braku połączenia zamiast rzeczywistego pingu, a ignorowane wpisy trafiają na dół TAB dzięki kolejności listy.
- Postacie, skiny, oryginalne nicki nad głową i faktyczny tryb gry pozostają normalne. Zmiana wyglądu TAB nie zmienia trybu gry gracza.
- Integracja z **Bongo's Teleports 1.6.0** blokuje TPA w obie strony. Zmiana ignorowania anuluje również istniejącą prośbę, potwierdzenie kosztu lub warm-up.
- Lista jest zapisywana w `config/bongoutils/social.json`, według UUID. Można odblokować także zapamiętanego gracza offline.

### Maskowanie prawdziwego trybu obserwatora w TAB (1.4.2)

Inni gracze otrzymują zwykły tryb `SURVIVAL` w danych TAB osoby będącej faktycznie w `SPECTATOR`. Jej wpis pozostaje na liście bez automatycznej kursywy i sortowania obserwatorów. Dotyczy to aktualizacji trybu, nowych połączeń, ponownego wejścia i zmiany wymiaru. Właściciel dostaje prawdziwy tryb i zachowuje sterowanie obserwatorem.

Postać faktycznego obserwatora nie jest wysyłana pozostałym klientom, również innym obserwatorom. Przy zmianie trybu śledzenie postaci jest natychmiast odświeżane; wyjście z obserwatora ponownie pokazuje postać. To chroni przed pokazaniem jej jako zwykłego gracza wskutek zamaskowanych danych TAB. Ignorowanie i oba tryby vanish zachowują swoje działanie.

### Vanish i semi-vanish

Komendy wymagają **OP poziomu 3 lub 4** i przełączają stan gracza wykonującego komendę:

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

Komendy działają z konsoli lub konta z **OP poziomu 3 lub 4**. Oba konta muszą być offline, także po zakończeniu sprawdzania tożsamości. Oczekujące połączenia w fazie logowania lub konfiguracji również blokują migrację:

```text
/bongoutils migrate move StaryNick NowyNick
/bongoutils migrate swap PierwszyNick DrugiNick
/bongoutils migrate override ZrodlowyNick DocelowyNick
```

- **MOVE** przenosi dane na wolne konto; odmawia, jeżeli cel ma zapisane dane.
- **SWAP** zamienia dane obu kont. Wymaga istniejących zapisów świata dla obu graczy.
- **OVERRIDE** przenosi źródło na cel i zastępuje jego poprzedni zapis. Źródłowy zapis zostaje usunięty, więc kolejna gra na źródłowym koncie zaczyna się od nowa. Poprzedni stan obu kont pozostaje w kopii.

Zakres: cały zapis gracza Minecrafta (ekwipunek, enderchest, XP, pozycja, zdrowie i pozostałe zapisane właściwości), statystyki, postępy, hasła kont offline, wybór skina Bongo Utils, ignorowanie i tryby widoczności. Przy obecności Bongo's Teleports 1.6.0 migrują właściciele padów, cooldowny i `/ignore-tpa`. Istniejące pady i ich połączenia w świecie są zachowane. Przy Community Clans 1.1.2 migrują członkostwo, role, preferencje i zaproszenia; nazwy członków oraz TAB są odświeżane bez restartu.

Nadpisanie założyciela klanu z innymi członkami jest odrzucane, aby nie zostawić klanu bez lidera. W takim przypadku użyj SWAP lub najpierw przekaż przywództwo klanu. Pojedynczy pusty po nadpisaniu klan jest usuwany.

Rezerwacje nazw premium, tożsamości Mojang, bany, OP i obie whitelisty pozostają przypisane do dotychczasowych kont. Hasło offline nie staje się hasłem premium. Gdy konto premium przenosi się na offline, użytkownik nowego konta rejestruje własne hasło przy następnym wejściu. Nicki offline mają tę samą, niewrażliwą na wielkość liter tożsamość co logowanie Bongo Utils. Weryfikacja nieznanego nicku w Mojang odbywa się w tle.

Przed pierwszą zmianą powstaje `config/bongoutils/migration-backups/<czas>-<id>/` z oryginalnymi plikami, manifestem ścieżek, opisem operacji i statusem. Błąd zapisu lub przeładowania cofa całą operację. Niezakończona migracja po przerwaniu procesu jest automatycznie cofana przy następnym starcie, przed odczytem kont i świata. Pamięci podręczne statystyk i postępów są czyszczone po sukcesie.


Migracja kont nie przenosi własności natywnych claimów GP3D ani globalnych/lokalnych flag panelu i nowych statusów. Te dane pozostają powiązane z dotychczasowym UUID; migracja kont wymaga osobnego zaplanowania przeniesienia działek.
