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

Au premier lancement, la **fenêtre de configuration des ROM** (en mode sombre) s'ouvre, comme dans PokeMMO :
une ligne par jeu (Rouge Feu / Vert Feuille, Émeraude), avec **Parcourir…** pour choisir le fichier
dans l'explorateur de Windows (ou glisse le `.gba` sur la ligne). Chaque ROM est vérifiée tout de
suite (✔ jeu et langue reconnus, ✘ fichier abîmé, jeu non pris en charge ou mauvaise ligne), puis
**Jouer** lance le jeu coché. Ensuite, tu crées ton personnage et la partie commence.

Aux lancements suivants, le jeu repart directement sur la dernière ROM jouée. Pour rouvrir la
fenêtre : `java -jar build/orvaen-mmo.jar --roms`. Les chemins sont mémorisés dans
`~/.orvaen-mmo/config.properties` (les ROM elles-mêmes ne sont jamais copiées).

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
| Valider / parler / lire (A) | Entrée, Espace, K |
| Retour / courir (B) | Échap, Maj, L |
| Menu (Pokédex, personnage, cartes, sauvegarde) | X |
| Liste de toutes les cartes (téléportation, outil de dev) | M |
| Zoom du monde (mémorisé) | + / - , ou Ctrl + molette |
| Compteur d'images par seconde et diagnostic d'affichage | F3 |
| Page précédente / suivante dans les listes | A / E, Page↑ / Page↓ |

## Écran d'accueil

Au lancement, un écran titre façon PokeMMO : **Jouer**, **Modifier le personnage**, **Quitter**.
Son fond animé est construit à partir de ta ROM : ta ville de départ et ses routes en panoramique
de nuit, et le duo légendaire de ta cartouche qui flotte de part et d'autre (Ho-Oh et Lugia pour
Rouge Feu / Vert Feuille, Groudon et Kyogre pour Émeraude). Le même fond sert derrière la création
de personnage.

## Création du personnage

L'écran **Nouveau personnage** reprend celui de PokeMMO : panneau sombre en haute résolution
(texte net, à la souris comme au clavier), fond en damier en perspective, et ton personnage utilise
**les sprites du héros et de l'héroïne de ta ROM**, recolorés pièce par pièce :

- **Genre** (Homme / Femme) et **Teint de peau** (7 teintes)
- **Chapeau, Cheveux, Haut, Jambes, Chaussures, Dos (sac)** : un menu déroulant de 27 teintes
  avec pastilles, et une **roue de couleur** pour n'importe quelle teinte (carré saturation /
  luminosité + barre de teinte)
- **Nom du personnage** (12 caractères), **Région de départ** (celle de ta ROM)
- Trois aperçus animés (face, dos, profil) avec flèches de rotation, et les sprites de combat
  (face et dos) ; **Aléatoire** et **Créer personnage**

Chaque pièce peut garder sa couleur d'origine. Les ombrages du sprite sont conservés (chaque nuance
garde son écart de luminosité sur la nouvelle teinte), et les détails qui ne sont pas des vêtements
restent intacts : yeux, bande blanche de la casquette, ruban du chapeau, Poké Ball tenue en main.

Tu peux modifier ton personnage à tout moment via **X → PERSO** (Échap annule).

Comment ça marche :
- `player/PlayerGraphics` retrouve les sprites dans la ROM par la forme de leurs tables
  (aucune adresse codée en dur) ;
- `player/CosmeticMasks` dit quelle pièce occupe chaque pixel (index de palette + zone,
  relevés sur les sprites de Rouge Feu / Vert Feuille) ;
- `player/Recolor` applique les teintures.

L'apparence tient dans une courte chaîne (`v2.1.3.12.27.20.0.0.5`), prête pour le mode en ligne.
`--extract` exporte aussi les sprites du joueur lus (`export/joueur/`) pour vérifier ta ROM.

Sur Émeraude (ou une ROM modifiée où ces sprites sont introuvables), le moteur se rabat
sur un avatar dessiné qui reprend les mêmes couleurs.

## Ce qui marche déjà

- Lecture et validation de l'en-tête de la ROM, détection du jeu et de la langue
- Découverte des tables par les pointeurs internes de la ROM (méthode du Universal Pokemon Randomizer),
  donc indépendante de la langue et de la révision
- Décompression LZ77 du BIOS GBA, palettes BGR555, tuiles 4bpp, jeu de caractères Gen 3
- **Monde** : rendu des cartes (tilesets primaire + secondaire, métatiles 2 calques, palettes),
  bordures répétées, cartes voisines affichées et traversées sans transition,
  collisions et niveaux d'élévation (on ne marche pas sur l'eau), corniches,
  portes animées comme sur GBA (la porte s'ouvre, on entre, elle se referme ; à la sortie elle est
  ouverte et se referme derrière toi — animations lues dans la ROM), escaliers / tapis de sortie,
  course avec les vraies frames de course de la ROM (B), un peu plus posée que sur GBA (10 frames par
  case au lieu de 8 ; réglable avec `move.runFrames` dans `~/.orvaen-mmo/config.properties`),
  nom de la zone à l'entrée
- **PNJ** : les personnages de chaque carte, lus dans la ROM (sprite, palette, position, comportement :
  regarder autour d'eux, errer dans leur zone, rester tournés…), qui bloquent le passage ; **A** face à
  un PNJ : il se tourne vers toi et dit son texte (tiré de son script), page par page. Les panneaux se
  lisent aussi. Les personnages d'histoire (qui apparaissent selon la progression) ne sont pas encore affichés
- **Décor animé** : eau, courants, bords de plage et fleurs (Rouge Feu / Vert Feuille ; cascade et
  bord de l'eau en plus sur Émeraude), au rythme du jeu d'origine
- **Rencontres** : tables d'herbe de la ROM avec les probabilités officielles par emplacement
- **Combat (prototype)** : apparition du Pokémon sauvage avec son sprite extrait, capture ou fuite
- **Pokédex** : toutes les espèces de la ROM (sprite, types, statistiques), vues / capturées
- **Personnage** : sprites du joueur lus dans la ROM et teints pièce par pièce (gabarit, peau, cheveux,
  couvre-chef, haut, bas, chaussures, sac), à la création et via le menu
- Sauvegarde locale par jeu dans `~/.orvaen-mmo/save-<code>.properties`

La capsule de capture et l'interface sont dessinées par le moteur (design original).

## Architecture

```
src/main/java/dev/orvaen/mmo/
├── rom/      Lecture de la ROM : Rom, GameProfile (FR/LG vs Émeraude), Lz77, TextCodec
├── gfx/      Formats graphiques GBA → images Java
├── data/     Espèces, sprites, rencontres sauvages (GameData = tout ce qui est extrait)
├── world/    Cartes : MapRepository, Tileset, MapLayout, warps, liaisons, MapRenderer
├── player/   Sprites du joueur : localisation dans la ROM, masques par pièce, teinture
├── engine/   Fenêtre, boucle 60 i/s, entrées, interface, sauvegarde
├── scenes/   Monde, combat, Pokédex, menus
└── tools/    Extracteur (--extract)
```

Les formats sont documentés dans les classes, d'après la décompilation
[pret/pokefirered](https://github.com/pret/pokefirered) et le
[Universal Pokemon Randomizer](https://github.com/Ajarmar/universal-pokemon-randomizer-zx).

## Tests

`build.sh` / `build.bat` lancent `SelfTest` (25 tests, sans JUnit). Ils tournent sur une **ROM synthétique**
fabriquée par `SyntheticRom` : un faux Rouge Feu minuscule (2 espèces, 2 cartes, données inventées)
qui exerce toute la chaîne — pointeurs, LZ77, texte, tilesets, warps, liaisons, rencontres, rendu,
déplacements, combat et création de personnage — sans aucune donnée Nintendo.

## Feuille de route

1. **Serveur en ligne** : serveur Java (sockets) qui synchronise positions, cartes et apparences des personnages
2. PNJ et panneaux des cartes (sprites lus depuis la ROM) ; cosmétiques en calques (lunettes, nouveaux couvre-chefs…)
3. Vrai système de combat : équipe, attaques, types, expérience (données d'attaques de la ROM)
4. Surf, pêche, rencontres d'eau ; musique et cris
5. Émeraude complètement validé, puis Gen 4/5 (ROM NDS) comme PokeMMO

## Licence et contenu

Le code de ce dépôt est original. Les noms, graphismes et données de jeu affichés à l'écran
proviennent uniquement de la ROM fournie par le joueur et restent la propriété de leurs ayants droit.
Ne publie jamais de ROM ni d'export de ROM dans ce dépôt (le `.gitignore` les bloque déjà).
