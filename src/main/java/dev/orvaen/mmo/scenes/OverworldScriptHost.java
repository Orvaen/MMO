package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.rom.GameProfile;
import dev.orvaen.mmo.rom.TextCodec;
import dev.orvaen.mmo.script.Movement;
import dev.orvaen.mmo.script.ScriptVM;
import dev.orvaen.mmo.world.DoorAnims;
import dev.orvaen.mmo.world.GameMap;
import dev.orvaen.mmo.world.MapLayout;

import java.util.List;

/**
 * Ce que les scripts de la ROM peuvent faire dans le monde.
 * <p>
 * La machine à scripts ({@link ScriptVM}) lit les commandes du jeu (« affiche ce texte », « fais marcher
 * le Professeur », « ouvre la porte »…) et demande à cette classe de les exécuter dans notre moteur.
 * Chaque méthode correspond à une ou plusieurs commandes de script.
 */
final class OverworldScriptHost implements ScriptVM.Host {

    // Fonctions « special » de Rouge Feu / Vert Feuille (n° dans la table des fonctions spéciales du jeu)
    private static final int SPECIAL_HEAL_PLAYER_PARTY = 0;           // le même dans tous les jeux Gen 3
    private static final int SPECIAL_CHANGE_NICKNAME_FRLG = 158;
    private static final int SPECIAL_FACING_FRLG_1 = 287, SPECIAL_FACING_FRLG_2 = 426;
    private static final int LOCALID_PLAYER = 0xFF;

    private final OverworldScene world;
    private final Game game;

    OverworldScriptHost(OverworldScene world) {
        this.world = world;
        this.game = world.game;
    }

    private GameMap map() {
        return world.currentMap();
    }

    private boolean fireRedLeafGreen() {
        return game.data.rom.profile() == GameProfile.FIRERED_LEAFGREEN;
    }

    // ------------------------------------------------------------------ textes

    @Override
    public void message(int textPtr) {
        if (textPtr < 0) return;
        String player = game.save.name == null || game.save.name.isBlank() ? "Dresseur" : game.save.name;
        ScriptVM vm = world.vm;
        // Codes spéciaux du texte : nom du joueur, textes mémorisés par le script, nom du rival
        List<String> pages = TextCodec.dialogue(game.data.rom, textPtr, 1024, code -> switch (code) {
            case 0x01 -> player;
            case 0x02, 0x03, 0x04 -> vm == null ? "" : vm.buffer(code - 2);
            case 0x06 -> world.rivalName();
            default -> "";
        });
        Npc who = world.speaker;
        world.box.show(pages, who == null ? null
                : () -> new DialogueScene.Speaker(world.screenBounds(who), world.player.y < who.y()));
    }

    @Override public boolean messagePrinted() { return world.box.printed(); }
    @Override public boolean takeButton() { return world.box.takeButton(); }
    @Override public void closeMessage() { world.box.close(); }
    @Override public void askYesNo() { world.box.askYesNo(); }
    @Override public int yesNoResult() { return world.box.takeResult(); }

    // ------------------------------------------------------------------ personnages

    @Override
    public Movement.Actor actor(int localId) {
        return localId == LOCALID_PLAYER ? world.player : world.npcs.byId(localId);
    }

    @Override
    public void faceplayer(int localId) {
        Npc n = world.npcs.byId(localId);
        if (n != null) n.face(world.player.facing.opposite().ordinal());
    }

    @Override public void removeObject(int localId) { world.npcs.remove(map(), localId); }
    @Override public void addObject(int localId) { world.npcs.add(map(), localId); }
    @Override public void setObjectMovementType(int localId, int type) { world.npcs.setMovementType(localId, type); }
    @Override public void showObject(int localId, boolean visible) { world.npcs.show(localId, visible); }

    @Override
    public void setObjectXY(int localId, int x, int y, boolean permanent) {
        if (localId == LOCALID_PLAYER) {
            world.player.x = x;
            world.player.y = y;
        } else {
            world.npcs.setPosition(localId, x, y, permanent);
        }
    }

    // ------------------------------------------------------------------ carte

    @Override
    public void warp(int group, int num, int warpId, int x, int y) {
        if (!game.data.maps.exists(group, num)) return;
        game.fadeThen(() -> {
            GameMap target = game.data.maps.load(group, num);
            int ax = x, ay = y;
            if ((ax < 0 || ay < 0) && warpId < target.warps.size()) {
                ax = target.warps.get(warpId).x();
                ay = target.warps.get(warpId).y();
            }
            if (ax < 0 || ay < 0) {
                int[] spawn = OverworldScene.defaultSpawn(target);
                ax = spawn[0];
                ay = spawn[1];
            }
            world.enterMap(target, ax, ay);
            world.warpedIn = true;
        });
    }

    @Override
    public void setMetatile(int x, int y, int metatile, boolean blocking) {
        GameMap m = map();
        if (!m.layout.inBounds(x, y)) return;
        int i = y * m.layout.width + x;
        int old = m.layout.blocks[i];
        m.layout.blocks[i] = (old & 0xF000) | (blocking ? 0x0C00 : 0) | (metatile & MapLayout.METATILE_MASK);
        world.view.redraw(m, new int[]{i});
    }

    @Override
    public void door(int x, int y, boolean open) {
        DoorAnims.Door d = game.data.doors.at(MapLayout.metatileId(map().layout.blockAt(x, y)));
        if (d != null) world.doors.play(d, x, y, open);
    }

    @Override public boolean doorBusy() { return world.doors.active() && !world.doors.done(); }

    @Override public void fade(int mode) { game.fadeScreen(mode == 1 || mode == 3); }
    @Override public boolean fadeBusy() { return game.fadeScreenBusy(); }

    // ------------------------------------------------------------------ combats

    @Override
    public void trainerBattle(int trainerId, int introText, int defeatText) {
        world.battling = true;
        game.push(BattleScene.trainer(game, game.data.trainers().get(trainerId), defeatText));
    }

    @Override
    public void wildBattle(int species, int level) {
        world.battling = true;
        game.push(new BattleScene(game, species, Math.max(1, level), world.battleTerrain()));
    }

    @Override public boolean battleBusy() { return world.battling; }
    @Override public void showMonPic(int species) { world.monPic = species; }
    @Override public void hideMonPic() { world.monPic = -1; }

    // ------------------------------------------------------------------ son

    @Override public void playBgm(int song) { game.data.audio.playBgm(song); }
    @Override public void saveBgm(int song) { world.savedBgm = song; }
    @Override public void fadeDefaultBgm() { game.data.audio.playBgm(world.defaultMusic()); }
    @Override public void fadeOutBgm(int speed) { game.data.audio.fadeOutBgm(Math.max(8, speed * 8)); }
    @Override public void playSe(int song) { game.data.audio.playSe(song); }
    @Override public void playFanfare(int song) { game.data.audio.playFanfare(song); }
    @Override public boolean fanfareBusy() { return game.data.audio.fanfareBusy(); }
    @Override public boolean seBusy() { return game.data.audio.effectsBusy(); }

    // ------------------------------------------------------------------ fonctions spéciales

    @Override
    public void special(int id, int arg) {
        if (id == SPECIAL_HEAL_PLAYER_PARTY) {
            dev.orvaen.mmo.battle.Party.healAll(game.data, game.save);       // Centre Pokémon, maman…
            return;
        }
        if (!fireRedLeafGreen()) return;
        if (id == SPECIAL_CHANGE_NICKNAME_FRLG) askNickname(arg);
    }

    /** Écran « donner un surnom » au Pokémon n° {@code slot} de l'équipe (le dernier par défaut). */
    private void askNickname(int slot) {
        var party = game.save.party;
        if (party.isEmpty()) return;
        int s = slot >= 0 && slot < party.size() ? slot : party.size() - 1;
        int species = party.get(s)[0];
        String speciesName = speciesName(species);
        world.naming = true;
        game.fadeScreen(false);
        game.push(new NamingScene(game, "Surnom de " + speciesName, speciesName,
                game.data.species.frontSprite(species), name -> {
                    while (game.save.partyNames.size() < party.size()) game.save.partyNames.add("");
                    game.save.partyNames.set(s, name.equals(speciesName) ? "" : name);
                    world.naming = false;
                }));
    }

    @Override public boolean stateBusy() { return world.naming; }

    @Override
    public int specialVar(int id) {
        if (fireRedLeafGreen() && (id == SPECIAL_FACING_FRLG_1 || id == SPECIAL_FACING_FRLG_2)) {
            return world.player.facing.ordinal() + 1;
        }
        return 0;
    }

    // ------------------------------------------------------------------ informations

    @Override
    public String partyName(int slot) {
        var party = game.save.party;
        if (slot < 0 || slot >= party.size()) return "";
        String n = slot < game.save.partyNames.size() ? game.save.partyNames.get(slot) : "";
        return n == null || n.isBlank() ? speciesName(party.get(slot)[0]) : n;
    }

    @Override
    public String speciesName(int species) {
        return species > 0 && species < game.data.species.count() ? game.data.species.get(species).name() : "?";
    }

    @Override public int playerGender() { return game.save.look == null ? 0 : game.save.look.body(); }
    @Override public int playerX() { return world.player.x; }
    @Override public int playerY() { return world.player.y; }
    @Override public int playerFacing() { return world.player.facing.ordinal(); }
}
