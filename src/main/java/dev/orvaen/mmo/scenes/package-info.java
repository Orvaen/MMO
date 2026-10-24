/**
 * Les écrans du jeu : écran titre, intro du Professeur, monde, combats, menus, Pokédex, carte de dresseur.
 * <p>
 * Le monde ({@link dev.orvaen.mmo.scenes.OverworldScene}) est découpé en petites classes :
 * le joueur ({@code OverworldPlayer}), les PNJ ({@code NpcManager}), les portes ({@code DoorAnimator}),
 * les cartes affichées ({@code MapView}), les scripts ({@code MapScripts}, {@code OverworldScriptHost})
 * et l'interface ({@code OverworldHud}). Voir {@code docs/ARCHITECTURE.md}.
 */
package dev.orvaen.mmo.scenes;
