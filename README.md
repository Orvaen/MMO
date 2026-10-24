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
| Baisser / monter le volume (mémorisé, 40 % au départ) | F7 / F8 |
| Couper / remettre le son | F9 |
| Page précédente / suivante dans les listes | A / E, Page↑ / Page↓ |

## Écran d'accueil

Au lancement, **le vrai écran titre de ta cartouche**, lu dans ta ROM : logo, Dracaufeu (ou Florizarre
sur Vert Feuille), flammes qui montent, reflet qui balaie le logo et « PRESS START » qui clignote, avec
la musique du titre. La bande du bas affiche « ORVAEN MMO » à la place du copyright du jeu. Start (ou un clic) ouvre le menu, dans des cadres blancs façon Rouge Feu :
**Continuer** (nom du joueur, temps de jeu, Pokédex), **Modifier le personnage**, **Nouvelle partie**
(avec confirmation, une copie de secours est gardée) et **Quitter**. B revient à l'écran titre.

Sur une ROM où l'écran titre est introuvable (Émeraude pour l'instant, ou ROM modifiée), le menu
s'affiche sur un fond animé construit à partir de ta ROM (ta ville de départ en panoramique de nuit
et le duo légendaire de ta cartouche). Ce fond sert aussi derrière la création de personnage.

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
  un PNJ : il se tourne vers toi et une bulle pointée vers lui affiche son texte (tiré de son script), page par page. Les panneaux se
  lisent aussi. Les personnages d'histoire (qui apparaissent selon la progression) ne sont pas encore affichés
- **Décor animé** : eau, courants, bords de plage et fleurs (Rouge Feu / Vert Feuille ; cascade et
  bord de l'eau en plus sur Émeraude), au rythme du jeu d'origine
- **Histoire** : moteur de scripts qui joue les événements de la ROM — personnages d'histoire qui
  apparaissent selon la progression, cinématiques (le Professeur qui t'arrête à la sortie du bourg, te mène
  au labo…), mouvements scriptés, « ! » au-dessus des têtes, questions oui / non, Pokémon reçus, portes,
  fondus, drapeaux et variables sauvegardés. Nouvelle partie : accueil par le Professeur, puis (Rouge Feu /
  Vert Feuille) réveil dans ta chambre. Les combats de dresseur lancés par les scripts (le rival au labo…)
  utilisent le vrai système de combat, et « soigner l'équipe » (maman, Centre Pokémon) fonctionne
- **Musique et sons** : lecteur du moteur sonore des jeux GBA (MP2K / « Sappy ») écrit en Java — la table des
  morceaux, les pistes, les instruments (échantillons, canaux Game Boy carré / onde / bruit, percussions,
  partages de clavier) et les enveloppes sont lus dans ta ROM. Musique de chaque carte, écran titre, accueil du
  Professeur, combats, capture, fanfares et effets des scripts (playbgm, playse, playfanfare…), portes et
  escaliers. **F9** coupe / remet le son
- **Pokémon qui suit** : le premier Pokémon en forme de l'équipe marche derrière toi, avec un vrai sprite qui
  marche dans les 4 directions. La GBA n'en a pas : ces sprites, faits par la communauté du projet de fans
  [pokeemerald-expansion](https://github.com/rh-hideout/pokeemerald-expansion), sont **téléchargés au premier
  besoin** sur ton ordinateur (une image par espèce, gardée dans `~/.orvaen-mmo/followers/`) ; ils ne sont pas
  dans ce dépôt. Sans Internet, l'icône animée de ta ROM sert à la place. Pour désactiver :
  `followers.download=false` dans `config.properties` (`followers.url` change la source)
- **Rencontres** : tables d'herbe de la ROM avec les probabilités officielles par emplacement ; l'herbe haute
  s'agite sous les pas (joueur et PNJ), avec l'animation de la ROM
- **Combat** (règles de la 3e génération, données de ta ROM) :
  - fond du terrain (herbe, hautes herbes, eau, grotte, bâtiment…), boîte de texte et menus, Poké Ball,
    sprites de face et de dos, tous lus dans la ROM ; musiques de combat, de victoire et de capture, bruitages ;
  - **animations d'attaque de la ROM** : le script d'animation de chaque attaque est rejoué — ses images
    (impacts, flammes, bulles…), leur animation image par image, ses sons et son rythme ; les trajectoires
    (projectile, impact sur la cible, élan, tremblement) sont reconstituées à partir des paramètres du script ;
  - transition (flashs puis bandes noires), entrée en scène en silhouette, lancer de Poké Ball,
    clignotement des coups, barres de PV et d'EXP animées, K.O. ;
  - menus **ATTAQUE / SAC / POKéMON / FUITE**, choix des attaques avec PP et type, changement de Pokémon ;
  - attaques, PP, types et efficacités, coups critiques, même type ×1,5, précision et esquive, crans de
    stats, statuts (sommeil, poison, brûlure, gel, paralysie, confusion, Vampigraine), priorité (Vive-Attaque) ;
  - expérience, montée de niveau avec fanfare, nouvelles attaques (et choix de celle à oublier) ;
  - capture avec la formule officielle (5 Poké Balls offertes au départ), fuite, combats de dresseur
    (Pokémon suivant, argent gagné), défaite (équipe soignée, moitié de l'argent perdue)
- **Fenêtres façon PokeMMO** : Pokédex (touche P) et carte de dresseur (touche C) s'ouvrent sans bloquer
  le jeu — on continue à marcher —, plusieurs à la fois, déplaçables, redimensionnables, place mémorisée ;
  Échap ferme celle de devant
- **Pokédex** : grille dans l'ordre national (silhouettes
  pour les espèces jamais vues), choix National / Kanto / Johto / Hoenn, recherche par nom ou n°,
  fiche (types, statistiques), compteurs vus / capturés
- **Carte de dresseur** : carte aux couleurs de ta tenue, portrait, n° ID, argent, Pokédex, temps de jeu,
  date de début de l'aventure, badges
- **Personnage** : sprites du joueur lus dans la ROM et teints pièce par pièce (gabarit, peau, cheveux,
  couvre-chef, haut, bas, chaussures, sac), à la création et via le menu
- Sauvegarde locale par jeu dans `~/.orvaen-mmo/save-<code>.properties`

La capsule de capture et l'interface sont dessinées par le moteur (design original).

## Architecture

```
src/main/java/dev/orvaen/mmo/
├── rom/      Lecture de la ROM : Rom, GameProfile (FR/LG vs Émeraude), Lz77, TextCodec
├── gfx/      Formats graphiques GBA → images Java
├── data/     Tout ce qui est extrait au démarrage (GameData) : espèces, rencontres, intro, écran titre
├── world/    Cartes : MapRepository, Tileset, MapLayout, warps, liaisons, PNJ, portes, MapRenderer
├── script/   Machine à scripts : rejoue les scripts de l'histoire écrits dans la ROM
├── battle/   Combat : règles Gen 3 (Battle), Pokémon, équipe, données et images lues dans la ROM
├── audio/    Lecteur de musique GBA (moteur MP2K) en Java
├── player/   Sprites du joueur : localisation dans la ROM, masques par pièce, teinture
├── engine/   Fenêtre, boucle 60 i/s, entrées, fenêtres déplaçables, sauvegarde
├── scenes/   Écrans : titre, intro, monde (découpé en petites classes), combat, menus, Pokédex
└── tools/    Extracteur (--extract)
```

**Nouveau sur le projet ?** Commence par [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) : il explique
en français simple comment le jeu est organisé, ce qui se passe à chaque image et où modifier quoi.

Les formats sont documentés dans les classes, d'après la décompilation
[pret/pokefirered](https://github.com/pret/pokefirered) et le
[Universal Pokemon Randomizer](https://github.com/Ajarmar/universal-pokemon-randomizer-zx).

## Tests

`build.sh` / `build.bat` lancent `SelfTest` (55 tests, sans JUnit). Ils tournent sur une **ROM synthétique**
fabriquée par `SyntheticRom` : un faux Rouge Feu minuscule (2 espèces, 2 cartes, données inventées)
qui exerce toute la chaîne — pointeurs, LZ77, texte, tilesets, warps, liaisons, rencontres, rendu,
déplacements, combat et création de personnage — sans aucune donnée Nintendo.

## Feuille de route

1. **Serveur en ligne** : serveur Java (sockets) qui synchronise positions, cartes et apparences des personnages
2. PNJ et panneaux des cartes (sprites lus depuis la ROM) ; cosmétiques en calques (lunettes, nouveaux couvre-chefs…)
3. Combat : évolutions, objets du sac, PC, cris ; effets de fond des animations (écran qui s'assombrit…)
4. Surf, pêche, rencontres d'eau ; musique et cris
5. Émeraude complètement validé, puis Gen 4/5 (ROM NDS) comme PokeMMO

## Licence et contenu

Le code de ce dépôt est original. Les noms, graphismes et données de jeu affichés à l'écran
proviennent uniquement de la ROM fournie par le joueur et restent la propriété de leurs ayants droit.
Ne publie jamais de ROM ni d'export de ROM dans ce dépôt (le `.gitignore` les bloque déjà).
