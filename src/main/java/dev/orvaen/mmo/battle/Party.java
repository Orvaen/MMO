package dev.orvaen.mmo.battle;

import dev.orvaen.mmo.data.GameData;
import dev.orvaen.mmo.engine.Storage;

import java.util.ArrayList;
import java.util.List;

/** Lire et écrire l'équipe du joueur dans la sauvegarde. */
public final class Party {

    public static final int MAX = 6;

    private Party() {}

    /** L'équipe du joueur, prête pour un combat. */
    public static List<Pokemon> load(GameData data, Storage.Save save) {
        List<Pokemon> out = new ArrayList<>();
        for (int i = 0; i < save.party.size(); i++) {
            String name = i < save.partyNames.size() ? save.partyNames.get(i) : "";
            try {
                out.add(Pokemon.fromSave(data, save.party.get(i), name));
            } catch (RuntimeException ignored) {
                // entrée illisible : ignorée
            }
        }
        return out;
    }

    /** Écrit l'équipe (après un combat, une capture, un soin). */
    public static void store(Storage.Save save, List<Pokemon> team) {
        save.party.clear();
        save.partyNames.clear();
        for (Pokemon p : team) {
            save.party.add(p.toSave());
            save.partyNames.add(p.nickname == null ? "" : p.nickname);
        }
    }

    /** Soigne toute l'équipe (fonction spéciale « HealPlayerParty » des scripts). */
    public static void healAll(GameData data, Storage.Save save) {
        List<Pokemon> team = load(data, save);
        for (Pokemon p : team) p.heal();
        store(save, team);
    }
}
