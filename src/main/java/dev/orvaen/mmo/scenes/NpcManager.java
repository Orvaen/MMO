package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.data.GameData;
import dev.orvaen.mmo.engine.Storage;
import dev.orvaen.mmo.world.GameMap;
import dev.orvaen.mmo.world.MapLayout;
import dev.orvaen.mmo.world.ObjectEvent;
import dev.orvaen.mmo.world.ObjectGraphics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Les personnages (PNJ) de la carte affichée : apparition, déplacements, et ce que les scripts leur font
 * (les retirer, les remettre, les déplacer…).
 * <p>
 * Chaque carte de la ROM liste ses personnages ({@link ObjectEvent}). Un personnage peut avoir un
 * « drapeau » : tant que ce drapeau est levé dans la sauvegarde, il est caché (c'est comme ça que
 * le jeu fait disparaître le Professeur ou le rival au bon moment de l'histoire).
 */
final class NpcManager {

    /** Graphismes « variables » (choisis par les scripts) : pas encore gérés, ces PNJ ne sont pas affichés. */
    private static final int FIRST_VAR_GRAPHICS = 0xEF;

    private final GameData data;
    private final Storage.Save save;
    private final List<Npc> npcs = new ArrayList<>();

    // Changements faits par les scripts sur la carte courante (oubliés en changeant de carte)
    private final Map<Integer, int[]> positions = new HashMap<>();     // setobjectxyperm
    private final Map<Integer, Integer> movements = new HashMap<>();   // setobjectmovementtype
    private final Set<Integer> removed = new HashSet<>();              // retirés sans drapeau

    NpcManager(GameData data, Storage.Save save) {
        this.data = data;
        this.save = save;
    }

    /** Tous les personnages affichés. */
    List<Npc> all() {
        return npcs;
    }

    /** Nouvelle carte : on oublie les changements faits par les scripts sur l'ancienne. */
    void forgetScriptChanges() {
        positions.clear();
        movements.clear();
        removed.clear();
    }

    /** Fait apparaître les personnages de la carte (sauf ceux que l'histoire cache). */
    void load(GameMap map) {
        npcs.clear();
        for (ObjectEvent e : map.objects) {
            if (e.flag() != 0 && save.flags.get(e.flag())) continue;
            spawn(map, e);
        }
    }

    /**
     * Comme sur GBA : un personnage dont le drapeau vient d'être effacé par un script réapparaît
     * (à sa place, éventuellement changée par setobjectxyperm). C'est ainsi que le Professeur, sorti de
     * scène à l'entrée du labo, se retrouve à son bureau.
     */
    void respawn(GameMap map) {
        for (ObjectEvent e : map.objects) {
            if (e.flag() == 0 || save.flags.get(e.flag()) || removed.contains(e.localId())) continue;
            if (byId(e.localId()) == null) spawn(map, e);
        }
    }

    private void spawn(GameMap map, ObjectEvent e) {
        if (e.graphicsId() >= FIRST_VAR_GRAPHICS) return;
        ObjectGraphics.Sprite sprite = data.objects.get(e.graphicsId());
        if (sprite == null) return;
        Npc n = new Npc(e, sprite, (long) map.key().hashCode() * 31 + e.localId());
        int[] pos = positions.get(e.localId());
        if (pos != null) n.setPosition(pos[0], pos[1]);
        Integer type = movements.get(e.localId());
        if (type != null) n.setMovementType(type);
        npcs.add(n);
    }

    /** Une frame : chaque personnage marche (ou reste immobile pendant un script). */
    void update(GameMap map, OverworldPlayer player, boolean frozen) {
        for (Npc n : npcs) n.update((x, y) -> canEnter(map, player, n, x, y), frozen);
    }

    /** Personnage par son numéro dans la carte (celui qu'utilisent les scripts), ou null. */
    Npc byId(int localId) {
        for (Npc n : npcs) if (n.event.localId() == localId) return n;
        return null;
    }

    /** Personnage qui occupe cette case, ou null. */
    Npc at(int x, int y) {
        for (Npc n : npcs) if (n.occupies(x, y)) return n;
        return null;
    }

    /**
     * Un PNJ peut-il marcher sur cette case ? Comme sur GBA : pas d'obstacle, même niveau d'élévation
     * que lui (l'eau est à un autre niveau : un PNJ à pied n'y entre pas), pas de corniche, ni joueur,
     * ni autre PNJ, ni warp.
     */
    private boolean canEnter(GameMap map, OverworldPlayer player, Npc n, int x, int y) {
        if (!map.layout.inBounds(x, y)) return false;
        int block = map.layout.blockAt(x, y);
        if (MapLayout.collision(block) != 0) return false;
        int z = MapLayout.elevation(block), own = n.event.elevation();
        if (z != 0 && z != 15 && own != 0 && own != 15 && z != own) return false;
        int behavior = map.behaviorAt(x, y);
        if (behavior >= 0x38 && behavior <= 0x3B) return false;      // corniches
        if (behavior >= 0x10 && behavior <= 0x15) return false;      // étang, eau profonde, cascade, mer
        if (x == player.x && y == player.y) return false;
        if (player.moving && x == player.x + player.facing.dx && y == player.y + player.facing.dy) return false;
        if (map.warpAt(x, y).isPresent()) return false;
        return at(x, y) == null;
    }

    // ------------------------------------------------------------------ commandes des scripts

    /** removeobject : le personnage disparaît (et son drapeau est levé, s'il en a un). */
    void remove(GameMap map, int localId) {
        boolean flagged = false;
        for (ObjectEvent e : map.objects) {
            if (e.localId() == localId && e.flag() != 0) {
                save.flags.set(e.flag());
                flagged = true;
            }
        }
        Npc n = byId(localId);
        if (n != null) npcs.remove(n);
        if (!flagged) removed.add(localId);
    }

    /** addobject : le personnage réapparaît. */
    void add(GameMap map, int localId) {
        removed.remove(localId);
        if (byId(localId) != null) return;
        for (ObjectEvent e : map.objects) if (e.localId() == localId) spawn(map, e);
    }

    /** setobjectxy (tout de suite) / setobjectxyperm (pour la prochaine apparition). */
    void setPosition(int localId, int x, int y, boolean permanent) {
        if (permanent) positions.put(localId, new int[]{x, y});
        Npc n = byId(localId);
        if (n != null && !permanent) n.setPosition(x, y);
    }

    /** setobjectmovementtype : façon de bouger (immobile, tourne sur place, se promène…). */
    void setMovementType(int localId, int type) {
        movements.put(localId, type);
        Npc n = byId(localId);
        if (n != null) n.setMovementType(type);
    }

    /** showobjectat / hideobjectat. */
    void show(int localId, boolean visible) {
        Npc n = byId(localId);
        if (n != null) n.setVisible(visible);
    }
}
