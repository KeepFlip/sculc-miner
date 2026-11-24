# Sculc Miner (Fabric 1.21.4)

Mod **tylko kliencki**. Postac stoi w miejscu i kopie podloge, a gdy nic nie ma w zasiegu,
idzie w lewo/prawo (os X lub Z) do najblizszego bloku. Sam dobiera narzedzie z paska skrotow.

## Komendy
| Komenda | Opis |
|---|---|
| `/sculc-ustaw-pozycja-1` / `/sculc-ustaw-pozycja-2` | Konce **trasy**, po ktorej postac chodzi lewo/prawo |
| `/sculc-kop-1` / `/sculc-kop-2` | Narozniki **terenu kopania** |
| `/sculc-block <blok>` | Dodaj/usun blok do kopania (np. `sculk`) - TAB podpowiada |
| `/sculc-block` | Pokaz wybrane bloki |
| `/sculc-block-wyczysc` | Wyczysc liste |
| `/sculc-start` / `/sculc-stop` | Start / stop |
| `/sculc-status` | Podglad ustawien |

Postac chodzi tam i z powrotem miedzy pozycja 1 i 2 (os X lub Z), a po drodze kopie wybrane bloki
z terenu kop-1/kop-2, ktore ma w zasiegu (ok. 4 bloki). Konczy, gdy w terenie nic nie zostanie
albo gdy reszta jest poza zasiegiem trasy. Teren: max 300 000 blokow.

## Budowanie
**GitHub:** wrzuc repo, zakladka *Actions* -> *build* -> pobierz artefakt `sculc-miner` (plik `.jar`).
**Lokalnie:** JDK 21 + zainstalowany Gradle 8.12: `gradle build` -> `build/libs/sculc-miner-1.0.0.jar`.

## Instalacja
Fabric Loader 0.16.10+ i Fabric API dla 1.21.4, jar do folderu `mods`.

## Uwaga
Automatyczne kopanie jest zabronione na wielu serwerach - uzywaj tam, gdzie regulamin na to pozwala.
