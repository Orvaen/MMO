# Orvaen MMO

Moteur de jeu en Java qui fonctionne **comme PokeMMO** : chaque joueur fournit sa propre ROM,
le moteur en extrait les données au lancement (cartes, tilesets, espèces, sprites, rencontres)
et fait tourner son propre gameplay par-dessus. **Aucun fichier du jeu original n'est dans ce dépôt**
et rien n'est jamais copié depuis la ROM : tout est lu en mémoire sur la machine du joueur.

> Prototype 0.1 — solo pour l'instant. Le mode en ligne est la prochaine grosse étape (voir la feuille de route).

## Jeux pris en charge

| Jeu | Codes ROM | État |
| --- | --- | --- |
| Rouge Feu / Vert Feuille (toutes langues, dont la VF) | `BPRx`, `BPGx` | cible principale |
| Émeraude (toutes langues) | `BPEx` | structures gérées, moins testé |

Utilise **ta propre** copie de la cartouche. Le moteur ne télécharge ni ne fournit aucune ROM.

## Lancer le jeu

Prérequis : **Java 21+** (aucune autre dépendance).

```bat
build.bat                        :: Windows
java -jar build\orvaen-mmo.jar
```

```sh
./build.sh                       # Linux / macOS
java -jar build/orvaen-mmo.jar
```

Au premier lancement une fenêtre te demande ta ROM ; son chemin est mémorisé dans
`~/.orvaen-mmo/config.properties`. Tu peux aussi la passer directement : `--rom chemin/vers/jeu.gba`.

Avec Maven : `mvn package` puis `java -jar target/orvaen-mmo.jar`.

### Vérifier ta ROM avant de jouer

```sh
java -jar build/orvaen-mmo.jar --rom jeu.gba --info               # ce que le moteur a trouvé
java -jar build/orvaen-mmo.jar --rom jeu.gba --extract export      # sprites + cartes en PNG, especes.csv
```

`export/cartes.txt` liste chaque carte lue (dimensions, warps, liaisons) ou l'erreur rencontrée.
Le dossier `export/` est ignoré par git.

## Commandes

| Action | Touches |
| --- | --- |
| Se déplacer | Flèches ou ZQSD |
| Valider (A) | Entrée, Espace, K |
| Retour / courir (B) | Échap, Maj, L |
| Menu (Pokédex, cartes, sauvegarde) | X |
| Liste de toutes les cartes (téléportation, outil de dev) | M |
| Page précédente / suivante dans les listes | A / E, Page↑ / Page↓ |

## Ce qui marche déjà

- Lecture et validation de l'en-tête de la ROM, détection du jeu et de la langue
- Découverte des tables par les pointeurs internes de la ROM (méthode du Universal Pokemon Randomizer),
  donc indépendante de la langue et de la révision
- Décompression LZ77 du BIOS GBA, palettes BGR555, tuiles 4bpp, jeu de caractères Gen 3
- **Monde** : rendu des cartes (tilesets primaire + secondaire, métatiles 2 calques, palettes),
  bordures répétées, cartes voisines affichées et traversées sans transition,
  collisions et niveaux d'élévation (on ne marche pas sur l'eau), corniches,
  portes / escaliers / tapis de sortie, nom de la zone à l'entrée
- **Rencontres** : tables d'herbe de la ROM avec les probabilités officielles par emplacement
- **Combat (prototype)** : apparition du Pokémon sauvage avec son sprite extrait, capture ou fuite
- **Pokédex** : toutes les espèces de la ROM (sprite, types, statistiques), vues / capturées
- Sauvegarde locale par jeu dans `~/.orvaen-mmo/save-<code>.properties`

L'avatar du joueur, la capsule de capture et l'interface sont dessinés par le moteur (design original).

## Architecture

```
src/main/java/dev/orvaen/mmo/
├── rom/      Lecture de la ROM : Rom, GameProfile (FR/LG vs Émeraude), Lz77, TextCodec
├── gfx/      Formats graphiques GBA → images Java
├── data/     Espèces, sprites, rencontres sauvages (GameData = tout ce qui est extrait)
├── world/    Cartes : MapRepository, Tileset, MapLayout, warps, liaisons, MapRenderer
├── engine/   Fenêtre, boucle 60 i/s, entrées, interface, sauvegarde
├── scenes/   Monde, combat, Pokédex, menus
└── tools/    Extracteur (--extract)
```

Les formats sont documentés dans les classes, d'après la décompilation
[pret/pokefirered](https://github.com/pret/pokefirered) et le
[Universal Pokemon Randomizer](https://github.com/Ajarmar/universal-pokemon-randomizer-zx).

## Tests

`build.sh` / `build.bat` lancent `SelfTest` (17 tests, sans JUnit). Ils tournent sur une **ROM synthétique**
fabriquée par `SyntheticRom` : un faux Rouge Feu minuscule (2 espèces, 2 cartes, données inventées)
qui exerce toute la chaîne — pointeurs, LZ77, texte, tilesets, warps, liaisons, rencontres, rendu,
déplacements et combat — sans aucune donnée Nintendo.

## Feuille de route

1. **Serveur en ligne** : serveur Java (sockets) qui synchronise positions et cartes, autres joueurs visibles
2. Sprites du joueur et des PNJ lus depuis la ROM (overworld), PNJ et panneaux des cartes
3. Vrai système de combat : équipe, attaques, types, expérience (données d'attaques de la ROM)
4. Surf, pêche, rencontres d'eau ; musique et cris
5. Émeraude complètement validé, puis Gen 4/5 (ROM NDS) comme PokeMMO

## Licence et contenu

Le code de ce dépôt est original. Les noms, graphismes et données de jeu affichés à l'écran
proviennent uniquement de la ROM fournie par le joueur et restent la propriété de leurs ayants droit.
Ne publie jamais de ROM ni d'export de ROM dans ce dépôt (le `.gitignore` les bloque déjà).
