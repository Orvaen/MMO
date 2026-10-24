# Comprendre le code d'Orvaen MMO

Ce guide est fait pour quelqu'un qui découvre le projet (même en débutant en Java).
Il explique **comment le jeu est organisé**, **ce qui se passe à chaque image**, et **où aller
pour changer quelque chose**.

---

## 1. L'idée en une phrase

Le joueur donne sa ROM (Rouge Feu, Vert Feuille ou Émeraude). Le moteur **lit** dans cette ROM tout ce
dont il a besoin (cartes, sprites, textes, musiques, scripts de l'histoire) et fait tourner **son propre**
jeu par-dessus. Aucune donnée de Nintendo n'est dans le dépôt.

> Règle d'or : on ne code **jamais** une adresse de la ROM en dur. On retrouve chaque donnée par
> sa *forme* (une table de pointeurs, une palette suivie d'images compressées…). C'est ce qui permet
> au jeu de marcher avec toutes les langues et toutes les révisions.

---

## 2. Les dossiers (packages)

```
src/main/java/dev/orvaen/mmo/
├── Main.java   Point de départ : choix de la ROM, puis lancement de la fenêtre
├── rom/        Lire la ROM : octets, pointeurs, texte, décompression LZ77
├── gfx/        Couleurs et tuiles de la GBA → images Java
├── data/       Tout ce qu'on extrait au démarrage (GameData) : espèces, rencontres, écran titre…
├── world/      Les cartes : dalles, collisions, warps, PNJ, panneaux, portes, tuiles animées
├── script/     La machine à scripts : rejoue les scripts de l'histoire écrits dans la ROM
├── battle/     Le combat : règles (Battle), Pokémon, équipe, données et images lues dans la ROM
├── audio/      Le lecteur de musique de la GBA (moteur « MP2K »), en Java
├── player/     Les sprites du héros et leur teinture (création de personnage)
├── engine/     La fenêtre, la boucle 60 images/s, le clavier, la sauvegarde, les fenêtres
├── scenes/     Les écrans du jeu : titre, intro, monde, combat, menus, Pokédex…
└── tools/      L'extracteur (--extract) pour vérifier sa ROM
```

Chaque dossier a un fichier `package-info.java` qui résume son rôle.

---

## 3. Du lancement à l'écran titre

1. `Main` trouve la ROM (argument `--rom`, dernière ROM utilisée, ou fenêtre de choix `RomLauncher`).
2. `new GameData(rom)` lit la ROM : espèces, cartes, sprites, musiques… (`data/GameData.java`).
3. `new Game(data)` ouvre la fenêtre, puis `game.run(...)` démarre la boucle avec la `TitleScene`.

---

## 4. Les scènes : une pile d'écrans

Le jeu est une **pile de scènes** (`engine/Scene.java`). Seule celle du dessus reçoit le clavier.

```
   ┌──────────────────┐
   │ StartMenuScene   │  ← menu ouvert par Start (en surimpression)
   ├──────────────────┤
   │ OverworldScene   │  ← le monde
   └──────────────────┘
```

- `game.push(scene)` : ouvre un écran par-dessus (un menu, un combat) ;
- `game.pop()` : le ferme et revient à celui d'en dessous ;
- `game.replace(scene)` : remplace l'écran (titre → monde) ;
- `game.fadeThen(action)` : fondu au noir, `action` au milieu, retour de l'image.

Une scène a trois méthodes principales :

| Méthode | Rôle |
| --- | --- |
| `update(Input in)` | la logique d'une image (1/60 s) : touches, déplacements, timers |
| `render(Graphics2D g)` | dessin en pixels GBA (petit écran agrandi ensuite, pixels nets) |
| `renderHiRes(Graphics2D g, w, h)` | dessin en haute résolution par-dessus (texte, interface) |

Les **fenêtres** (Pokédex, carte de dresseur) ne sont pas des scènes : ce sont des `UiWindow`
déplaçables qui restent ouvertes pendant qu'on joue (`game.toggleWindow(...)`).

---

## 5. Une image de jeu (60 fois par seconde)

`engine/Game.java`, méthode `run` :

1. lire le clavier et la souris (`Input`) ;
2. donner d'abord la souris aux fenêtres ouvertes, puis appeler `update` de la scène du dessus ;
3. dessiner : `render` (pixels GBA) → agrandissement → `renderHiRes` → fenêtres → fondu ;
4. attendre la prochaine image (synchronisé sur l'écran pour éviter les saccades).

Le son tourne à part, dans son propre fil d'exécution (`audio/AudioEngine`).

---

## 6. Le monde (`scenes/OverworldScene`) et ses aides

`OverworldScene` **coordonne** ; le travail est rangé dans de petites classes :

| Classe | S'occupe de |
| --- | --- |
| `OverworldPlayer` | position du joueur, pas en cours (marche, course, corniche), dessin du joueur |
| `NpcManager` | les PNJ de la carte : apparition, déplacements, ordres des scripts |
| `Npc` | un PNJ : sprite, façon de bouger, animation |
| `DoorAnimator` | la porte qui s'ouvre et se ferme |
| `MapView` | images des cartes, cartes voisines, bordure, eau et fleurs animées |
| `MapScripts` | trouver les scripts d'une carte (à l'arrivée, à chaque image…) |
| `OverworldScriptHost` | ce que les scripts peuvent faire : parler, faire marcher, ouvrir une porte, lancer un combat… |
| `OverworldHud` | les petits dessins : bulles « ! », nom de la zone, portrait de Pokémon |
| `GrassEffects` | l'herbe haute qui s'agite quand on y entre (images de la ROM) |
| `FollowerPokemon` | le premier Pokémon de l'équipe, qui marche derrière le joueur |
| `MessageBox` | la boîte de dialogue en bas, avec la flèche vers celui qui parle |
| `PartyBar` | l'équipe à droite de l'écran (icônes, PV) ; un clic ouvre le résumé |
| `SummaryWindow` | le résumé d'un Pokémon (touche E) : onglets en icônes (infos, stats, hexagone IV / EV, attaques), sprite sur un fond de combat de la ROM |
| `OptionsWindow` | les réglages (touche O) : volume général, musique, effets sonores, son coupé, zoom |

**Une image du monde** (`OverworldScene.update`) :

1. animations (eau, fleurs), PNJ, porte ;
2. si un script tourne → il avance, le joueur attend ;
3. sinon → Start / P / C (menus), A (parler), puis les flèches (marcher, B pour courir).

**Un pas** dure 16 images (moins en courant). Le joueur reste sur sa case de départ pendant le pas
et ne change de case qu'à l'arrivée (`OverworldPlayer.advance`). À l'arrivée (`onStep`) on regarde :
warp sous les pieds, case piège d'un script, rencontre dans les hautes herbes.

---

## 7. L'histoire : les scripts de la ROM

Les jeux GBA racontent leur histoire avec de petits programmes (« scripts ») rangés dans la ROM :
*affiche ce texte*, *fais marcher le Professeur de 3 cases*, *lève le drapeau « a reçu un Pokémon »*…

- `script/ScriptVM` lit ces commandes une par une (comme un mini processeur) ;
- `script/ScriptTables` sait combien d'octets prend chaque commande ;
- `scenes/OverworldScriptHost` exécute les commandes dans notre monde.

La progression est gardée dans la sauvegarde sous forme de **drapeaux** (vrai / faux) et de
**variables** (nombres) : `Storage.Save.flags` et `Storage.Save.vars`.

---

## 8. Le combat

Le combat est séparé en deux, pour qu'on puisse le comprendre (et le tester) morceau par morceau :

| Classe | Rôle |
| --- | --- |
| `battle/Battle` | les **règles** : qui attaque en premier, dégâts, types, statuts, expérience, capture… sans aucun dessin |
| `battle/BattleEvent` | ce qui s'est passé pendant un tour (message, dégâts, K.O., montée de niveau…) |
| `battle/Pokemon`, `battle/Party` | un Pokémon (stats, PV, attaques) et l'équipe enregistrée dans la sauvegarde |
| `battle/BattleData` | attaques, apprentissages, table des types, courbes d'expérience lues dans la ROM |
| `battle/BattleGraphics` | fonds de combat, boîte de texte, Poké Ball lus dans la ROM |
| `scenes/BattleScene` | l'**écran** : menus, puis il rejoue les `BattleEvent` un par un avec les animations |
| `scenes/BattleHud` | dessin des cadres de PV, des barres et du texte |
| `battle/MoveAnimations`, `scenes/MoveAnimPlayer` | animations d'attaque : script, images et sons de la ROM, rejoués |

Un tour : le joueur choisit dans le menu → `BattleScene` appelle par exemple `battle.useMove(0)` → le moteur
calcule tout le tour et renvoie la liste des événements → l'écran les joue (« X utilise CHARGE ! », élan,
clignotement, la barre de PV descend…) → retour au menu.

---

## 9. Où changer quoi ?

| Je veux… | Fichier |
| --- | --- |
| changer la vitesse de marche / course | `OverworldPlayer.startStep` (et `move.runFrames` dans `config.properties`) |
| changer ce qui bloque le joueur | `OverworldScene.canEnter` |
| changer où vont les PNJ | `NpcManager.canEnter`, `Npc` |
| ajouter une touche dans le monde | `OverworldScene.handleMenus` |
| modifier le menu de l'écran titre | `TitleScene` (le dessin animé de la ROM est dans `TitleArt`) |
| gérer une nouvelle commande de script | `ScriptVM` (lecture) + `OverworldScriptHost` (action) |
| ajouter une donnée lue dans la ROM | une classe dans `data/` ou `world/`, créée dans `GameData` |
| ajouter une musique | `audio/Songs` (n° des morceaux) puis `game.data.audio.playBgm(n°)` |
| changer ce qui est sauvegardé | `engine/Storage` |
| ajouter un réglage (son, affichage…) | `scenes/OptionsWindow` (+ sa valeur dans `engine/Storage`) |
| changer une règle de combat (dégâts, capture…) | `battle/Battle` |
| changer le calcul des stats, les EV, le talent | `battle/Pokemon` |
| changer le résumé d'un Pokémon | `scenes/SummaryWindow` (textes des talents / natures : `data/PokemonTexts`) |
| changer l'apparence du combat | `scenes/BattleScene`, `scenes/BattleHud` |

---

## 10. Les tests

Les tests (`src/test/java/.../SelfTest.java`) tournent sur une **ROM inventée** fabriquée par
`SyntheticRom` : un faux Rouge Feu minuscule, sans aucune donnée Nintendo. Chaque nouvelle lecture
de la ROM y ajoute une petite donnée de test, rangée exactement comme dans le vrai jeu.

```sh
./build.sh          # compile et lance les tests
```

Un test qui échoue affiche ✘ avec la raison. Tous doivent passer avant d'envoyer du code.

---

## 11. Conventions

- Le code et les commentaires sont **en français**.
- Une classe = un rôle. Si une classe dépasse ~600 lignes, on la découpe.
- Les nombres « magiques » de la ROM ont un nom (`BEHAVIOR_DOOR = 0x69`) et un commentaire.
- On ne lit jamais une adresse fixe : on cherche la donnée par sa forme.
- Jamais de ROM, d'image ou de son du jeu dans le dépôt.
