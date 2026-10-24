package dev.orvaen.mmo.script;

import dev.orvaen.mmo.engine.Storage;
import dev.orvaen.mmo.rom.Rom;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * Interprète des scripts d'événements Gen 3 (Rouge Feu / Vert Feuille ; Émeraude en grande partie) :
 * c'est ce qui fait vivre l'histoire — le Professeur qui t'arrête, les PNJ qui marchent, les dialogues
 * à choix, les objets qui apparaissent, les drapeaux et variables de progression…
 * <p>
 * Le script avance jusqu'à une commande qui attend (texte, mouvement, délai, choix, combat…), puis
 * reprend à la frame suivante. Les commandes sans équivalent ici (sons, effets, menus spéciaux)
 * sont sautées proprement grâce à la table des longueurs.
 */
public final class ScriptVM {

    /** Ce que le monde fournit au script. */
    public interface Host {
        void message(int textPtr);
        boolean messagePrinted();
        /** A a été pressé après la fin du texte (consomme l'appui). */
        boolean takeButton();
        void closeMessage();
        void askYesNo();
        /** -1 tant que le joueur n'a pas choisi ; 1 oui, 0 non. */
        int yesNoResult();

        /** Personnage du script : 0xFF = joueur, sinon n° local du PNJ ; {@code null} s'il n'existe pas. */
        Movement.Actor actor(int localId);
        void faceplayer(int localId);
        void removeObject(int localId);
        void addObject(int localId);
        void setObjectXY(int localId, int x, int y, boolean permanent);
        void setObjectMovementType(int localId, int type);
        void showObject(int localId, boolean visible);

        void warp(int group, int num, int warpId, int x, int y);
        void setMetatile(int x, int y, int metatile, boolean blocking);
        void door(int x, int y, boolean open);
        boolean doorBusy();
        void fade(int mode);
        boolean fadeBusy();
        void trainerBattle(int trainerId, int introText, int defeatText);
        void wildBattle(int species, int level);
        boolean battleBusy();
        void showMonPic(int species);
        void playBgm(int song);
        void saveBgm(int song);
        void fadeDefaultBgm();
        void fadeOutBgm(int speed);
        void playSe(int song);
        void playFanfare(int song);
        boolean fanfareBusy();
        boolean seBusy();
        /** Fonction spéciale du jeu (renommer un Pokémon…) ; {@code arg} = VAR_0x8004. */
        void special(int id, int arg);
        /** Une fonction spéciale attend encore le joueur (écran de nom…) : waitstate bloque. */
        boolean stateBusy();
        int specialVar(int id);
        String partyName(int slot);
        void hideMonPic();
        String speciesName(int species);
        int playerGender();
        int playerX();
        int playerY();
        int playerFacing();
    }

    // Variables spéciales (non sauvegardées)
    public static final int VAR_RESULT = 0x800D, VAR_FACING = 0x800C, VAR_LAST_TALKED = 0x800F;
    private static final int TRAINER_FLAGS = 0x500;

    private enum Wait { NONE, PRINT, BUTTON, MOVEMENT, DELAY, CHOICE, FADE, BATTLE, DOOR, STATE, FANFARE, SE }

    private final Rom rom;
    private final Host host;
    private final Storage.Save save;
    private final int[] special = new int[0x20];
    private final String[] strings = {"", "", ""};
    private final Deque<Integer> stack = new ArrayDeque<>();
    private final Random rng = new Random();
    private final ScriptTables tables;
    private int pc;
    private int textPtr = -1;
    private int compare;
    private boolean running = true;
    private Wait wait = Wait.NONE;
    private int waitArg;
    private final java.util.Map<Integer, Movement> moves = new java.util.HashMap<>();
    private int lastMoved = 0xFF;
    // bloc « callstd » natif
    private final Deque<Runnable> pending = new ArrayDeque<>();

    public ScriptVM(Rom rom, Host host, Storage.Save save, ScriptTables tables, int script) {
        this.rom = rom;
        this.host = host;
        this.save = save;
        this.tables = tables;
        this.pc = script;
        if (script < 0) running = false;
    }

    public boolean running() {
        return running;
    }

    public void setSpecial(int var, int value) {
        if (var >= 0x8000 && var < 0x8000 + special.length) special[var - 0x8000] = value;
    }

    /** Textes préparés par le script ({STR_VAR_1} à {STR_VAR_3}). */
    public String buffer(int i) {
        return i >= 0 && i < strings.length ? strings[i] : "";
    }

    // ------------------------------------------------------------------ drapeaux et variables

    public static boolean flag(Storage.Save save, int id) {
        return save.flags.get(id);
    }

    private int varGet(int id) {
        if (id >= 0x8000 && id < 0x8000 + special.length) return special[id - 0x8000];
        if (id >= 0x4000 && id < 0x8000) return save.vars.getOrDefault(id, 0);
        return id; // pas une variable : valeur littérale
    }

    private void varSet(int id, int value) {
        value &= 0xFFFF;
        if (id >= 0x8000 && id < 0x8000 + special.length) special[id - 0x8000] = value;
        else if (id >= 0x4000 && id < 0x8000) save.vars.put(id, value);
    }

    // ------------------------------------------------------------------ boucle

    /** Fait avancer le script d'une frame (jusqu'à ce qu'il attende quelque chose). */
    public void update() {
        for (Movement m : moves.values()) m.update();
        for (int guard = 0; guard < 400 && running; guard++) {
            if (!waitDone()) return;
            if (!pending.isEmpty()) {
                pending.poll().run();
                continue;
            }
            step();
        }
    }

    /** Exécute tout ce qui ne demande pas d'attendre (scripts d'arrivée sur une carte). */
    public void runInstant(int maxSteps) {
        for (int i = 0; i < maxSteps && running; i++) {
            if (!pending.isEmpty()) {
                pending.poll().run();
                continue;
            }
            wait = Wait.NONE;
            step();
        }
        running = false;
    }

    private boolean waitDone() {
        switch (wait) {
            case NONE -> {
                return true;
            }
            case PRINT -> {
                if (!host.messagePrinted()) return false;
            }
            case BUTTON -> {
                if (!host.takeButton()) return false;
            }
            case MOVEMENT -> {
                if (waitArg == 0) {
                    for (Movement m : moves.values()) if (!m.done()) return false;
                } else {
                    Movement m = moves.get(waitArg);
                    if (m != null && !m.done()) return false;
                }
                Movement.Actor a = host.actor(waitArg == 0 ? lastMoved : waitArg);
                if (a != null && a.busy()) return false;
            }
            case DELAY -> {
                if (--waitArg > 0) return false;
            }
            case CHOICE -> {
                int r = host.yesNoResult();
                if (r < 0) return false;
                varSet(VAR_RESULT, r);
            }
            case FADE -> {
                if (host.fadeBusy()) return false;
            }
            case BATTLE -> {
                if (host.battleBusy()) return false;
            }
            case DOOR -> {
                if (host.doorBusy()) return false;
            }
            case STATE -> {
                if (host.stateBusy()) return false;
            }
            case FANFARE -> {
                if (host.fanfareBusy()) return false;
            }
            case SE -> {
                if (host.seBusy() && --waitArg > 0) return false;
            }
        }
        wait = Wait.NONE;
        return true;
    }

    private void block(Wait w, int arg) {
        wait = w;
        waitArg = arg;
    }

    private int u8() { return rom.u8(pc++); }
    private int u16() { int v = rom.u16(pc); pc += 2; return v; }
    private int u32ptr() { int v = rom.pointer(pc); pc += 4; return v; }

    private static final boolean[][] CONDITIONS = {
            {true, false, false}, {false, true, false}, {false, false, true},
            {true, true, false}, {false, true, true}, {true, false, true}};

    private boolean cond(int c) {
        return c >= 0 && c < 6 && compare >= 0 && compare < 3 && CONDITIONS[c][compare];
    }

    private void setCompare(int a, int b) {
        compare = a < b ? 0 : a == b ? 1 : 2;
    }

    private void end() {
        running = false;
        host.closeMessage();
    }

    private void step() {
        if (!rom.inRange(pc, 1)) {
            end();
            return;
        }
        int at = pc;
        int op = u8();
        switch (op) {
            case 0x27 -> block(Wait.STATE, 0);                                  // waitstate
            case 0x2f -> host.playSe(u16());                                    // playse
            case 0x30 -> block(Wait.SE, 120);                                   // waitse
            case 0x31 -> host.playFanfare(u16());                               // playfanfare
            case 0x32 -> block(Wait.FANFARE, 0);                                // waitfanfare
            case 0x33 -> { host.playBgm(u16()); u8(); }                         // playbgm
            case 0x34 -> host.saveBgm(u16());                                   // savebgm
            case 0x35 -> host.fadeDefaultBgm();                                 // fadedefaultbgm
            case 0x36 -> host.playBgm(u16());                                   // fadenewbgm
            case 0x37 -> host.fadeOutBgm(u8());                                 // fadeoutbgm
            case 0x38 -> u8();                                                  // fadeinbgm
            case 0x00, 0x01, 0x2d, 0x2e, 0x66, 0x69, 0x6a, 0x6b, 0x6c, 0x72, 0x76 -> {
                if (op == 0x66) block(Wait.PRINT, 0);      // waitmessage
                if (op == 0x76) host.hideMonPic();        // hidemonpic
                if (op == 0x6b || op == 0x6c) host.closeMessage(); // release(all) : la boîte se ferme
            }
            case 0x02, 0x0d -> end();
            case 0x03, 0x0c -> {
                if (stack.isEmpty()) end();
                else pc = stack.pop();
            }
            case 0x04 -> {
                int dest = u32ptr();
                stack.push(pc);
                pc = dest;
            }
            case 0x05 -> pc = u32ptr();
            case 0x06, 0x07 -> {
                int c = u8(), dest = u32ptr();
                if (cond(c)) {
                    if (op == 0x07) stack.push(pc);
                    pc = dest;
                }
            }
            case 0x08, 0x09 -> std(u8(), op == 0x08);
            case 0x0a, 0x0b -> {
                int c = u8(), f = u8();
                if (cond(c)) std(f, op == 0x0a);
            }
            case 0x0f -> {
                int idx = u8();
                int v = u32ptr();
                if (idx == 0) textPtr = v;
            }
            case 0x10 -> pc += 2;
            case 0x16 -> varSet(u16(), u16());                                  // setvar
            case 0x17 -> { int v = u16(); varSet(v, varGet(v) + u16()); }      // addvar
            case 0x18 -> { int v = u16(); varSet(v, varGet(v) - varGet(u16())); } // subvar
            case 0x19 -> { int d = u16(); varSet(d, varGet(u16())); }          // copyvar
            case 0x1a -> { int d = u16(); varSet(d, varGet(u16())); }          // setorcopyvar
            case 0x21 -> { int v = u16(); setCompare(varGet(v), u16()); }      // compare_var_to_value
            case 0x22 -> { int a = u16(); setCompare(varGet(a), varGet(u16())); }
            case 0x25 -> host.special(u16(), special[4]);                       // special
            case 0x26 -> { int out = u16(); varSet(out, host.specialVar(u16())); } // specialvar
            case 0x28 -> block(Wait.DELAY, Math.max(1, varGet(u16())));
            case 0x29 -> save.flags.set(varGet(u16()));
            case 0x2a -> save.flags.clear(varGet(u16()));
            case 0x2b -> compare = save.flags.get(varGet(u16())) ? 1 : 0;
            case 0x39, 0x3a, 0x3b, 0x3d -> {                                    // warp*
                int group = u8(), num = u8(), id = u8(), x = varGet(u16()), y = varGet(u16());
                host.warp(group, num, id, x == 0xFFFF ? -1 : x, y == 0xFFFF ? -1 : y);
            }
            case 0x3c, 0x41 -> pc += 2;
            case 0x3e, 0x3f, 0x40 -> pc += 7;
            case 0x42 -> { varSet(u16(), host.playerX()); varSet(u16(), host.playerY()); }
            case 0x43 -> varSet(VAR_RESULT, save.party.size());
            case 0x44, 0x45, 0x46, 0x47, 0x49, 0x4a -> { pc += 4; varSet(VAR_RESULT, op == 0x47 || op == 0x4a ? 0 : 1); }
            case 0x4f, 0x50 -> {                                                // applymovement
                int id = varGet(u16());
                int ptr = u32ptr();
                if (op == 0x50) pc += 2;
                Movement.Actor a = host.actor(id);
                Movement.Actor p = host.actor(0xFF);
                if (a != null) moves.put(id, new Movement(rom, ptr, a, p));
                lastMoved = id;
            }
            case 0x51, 0x52 -> {                                                // waitmovement
                int id = varGet(u16());
                if (op == 0x52) pc += 2;
                block(Wait.MOVEMENT, id == 0 ? 0 : id);
            }
            case 0x53, 0x54 -> { int id = varGet(u16()); if (op == 0x54) pc += 2; host.removeObject(id); }
            case 0x55, 0x56 -> { int id = varGet(u16()); if (op == 0x56) pc += 2; host.addObject(id); }
            case 0x57, 0x63 -> {
                int id = varGet(u16()), x = varGet(u16()), y = varGet(u16());
                host.setObjectXY(id, x, y, op == 0x63);
            }
            case 0x58, 0x59 -> { int id = varGet(u16()); pc += 2; host.showObject(id, op == 0x58); }
            case 0x5a -> host.faceplayer(varGet(VAR_LAST_TALKED));
            case 0x5b -> {
                int id = varGet(u16()), dir = u8();
                Movement.Actor a = host.actor(id);
                if (a != null && dir >= 1 && dir <= 4) a.face(dir - 1);
            }
            case 0x5c -> trainerBattle();
            case 0x5d, 0x5e, 0x5f -> { }
            case 0x60 -> compare = save.flags.get(TRAINER_FLAGS + varGet(u16())) ? 1 : 0;
            case 0x61 -> save.flags.set(TRAINER_FLAGS + varGet(u16()));
            case 0x62 -> save.flags.clear(TRAINER_FLAGS + varGet(u16()));
            case 0x64 -> pc += 2;
            case 0x65 -> { int id = varGet(u16()); host.setObjectMovementType(id, u8()); }
            case 0x67 -> {                                                      // message
                int ptr = u32ptr();
                host.message(ptr < 0 ? textPtr : ptr);
            }
            case 0x68 -> host.closeMessage();
            case 0x6d -> block(Wait.BUTTON, 0);
            case 0x6e -> { pc += 2; host.askYesNo(); block(Wait.CHOICE, 0); }
            case 0x6f, 0x70, 0x71 -> {                                          // menus : 1er choix
                pc += op == 0x6f ? 4 : 5;
                varSet(VAR_RESULT, 0);
            }
            case 0x75 -> { host.showMonPic(varGet(u16())); pc += 2; }
            case 0x79 -> {                                                      // givemon
                int species = varGet(u16()), level = u8();
                pc += 11;
                if (save.party.size() < 6) {
                    save.party.add(new int[]{species, level});
                    while (save.partyNames.size() < save.party.size()) save.partyNames.add("");
                    save.caught.add(species);
                    save.seen.add(species);
                    varSet(VAR_RESULT, 0);
                } else {
                    varSet(VAR_RESULT, 1);
                }
            }
            case 0x7a -> pc += 2;
            case 0x7d -> { int i = u8(); strings[Math.min(2, i)] = host.speciesName(varGet(u16())); }
            case 0x7e -> { int i = u8(); strings[Math.min(2, i)] = save.party.isEmpty() ? "" : host.speciesName(save.party.get(0)[0]); }
            case 0x7f -> { int i = u8(); strings[Math.min(2, i)] = host.partyName(varGet(u16())); }
            case 0x80, 0x81, 0x82, 0x84 -> { int i = u8(); u16(); strings[Math.min(2, i)] = ""; }
            case 0x83 -> { int i = u8(); strings[Math.min(2, i)] = String.valueOf(varGet(u16())); }
            case 0x85 -> { int i = u8(); int ptr = u32ptr(); strings[Math.min(2, i)] = ptr < 0 ? "" : dev.orvaen.mmo.rom.TextCodec.read(rom, ptr, 64); }
            case 0x8f -> { int n = varGet(u16()); varSet(VAR_RESULT, n <= 0 ? 0 : rng.nextInt(n)); }
            case 0x90 -> { save.money += rom.s32(pc); pc += 5; }
            case 0x91 -> { save.money = Math.max(0, save.money - rom.s32(pc)); pc += 5; }
            case 0x92 -> { compare = save.money >= rom.s32(pc) ? 1 : 0; varSet(VAR_RESULT, compare); pc += 5; }
            case 0x97 -> { host.fade(u8()); block(Wait.FADE, 0); }
            case 0x98 -> { host.fade(u8()); pc++; block(Wait.FADE, 0); }
            case 0xa0 -> varSet(VAR_RESULT, host.playerGender());
            case 0xa2 -> {
                int x = varGet(u16()), y = varGet(u16()), m = varGet(u16()), coll = varGet(u16());
                host.setMetatile(x, y, m, coll != 0);
            }
            case 0xac, 0xad, 0xaf, 0xb0 -> {
                int x = varGet(u16()), y = varGet(u16());
                host.door(x, y, op == 0xac || op == 0xaf);
            }
            case 0xae -> block(Wait.DOOR, 0);
            case 0xb6 -> { int sp = varGet(u16()); int lv = u8(); pc += 2; special[0x10] = sp; special[0x11] = lv; }
            case 0xb7 -> { host.wildBattle(special[0x10], special[0x11]); block(Wait.BATTLE, 0); }
            default -> {
                int len = tables.argLength(op);
                if (len < 0) {
                    System.err.printf("Script : commande 0x%02X inconnue à 0x%06X, arrêt%n", op, at);
                    end();
                } else {
                    pc += len;
                }
            }
        }
    }

    /** Scripts standard (msgbox…) exécutés directement. */
    private void std(int id, boolean thenEnd) {
        int text = textPtr;
        switch (id) {
            case 2 -> {                                                         // MSGBOX_NPC
                pending.add(() -> host.faceplayer(varGet(VAR_LAST_TALKED)));
                pending.add(() -> host.message(text));
                pending.add(() -> block(Wait.PRINT, 0));
                pending.add(() -> block(Wait.BUTTON, 0));
                pending.add(host::closeMessage);
            }
            case 3, 6 -> {                                                      // panneau, fermeture auto
                pending.add(() -> host.message(text));
                pending.add(() -> block(Wait.PRINT, 0));
                pending.add(() -> block(Wait.BUTTON, 0));
                pending.add(host::closeMessage);
            }
            case 4 -> {                                                         // MSGBOX_DEFAULT
                pending.add(() -> host.message(text));
                pending.add(() -> block(Wait.PRINT, 0));
                pending.add(() -> block(Wait.BUTTON, 0));
            }
            case 5 -> {                                                         // MSGBOX_YESNO
                pending.add(() -> host.message(text));
                pending.add(() -> block(Wait.PRINT, 0));
                pending.add(() -> {
                    host.askYesNo();
                    block(Wait.CHOICE, 0);
                });
            }
            default -> {                                                        // objets obtenus…
                if (text >= 0 && (id == 0 || id == 1 || id == 9)) {
                    pending.add(() -> host.message(text));
                    pending.add(() -> block(Wait.PRINT, 0));
                    pending.add(() -> block(Wait.BUTTON, 0));
                    pending.add(host::closeMessage);
                }
                varSet(VAR_RESULT, 1);
            }
        }
        if (thenEnd) pending.add(this::end);
    }

    /** trainerbattle : déjà battu → suite du script ; sinon intro, combat, puis suite selon le type. */
    private void trainerBattle() {
        int type = u8(), trainer = u16();
        u16(); // n° local
        int intro = -1, defeat = -1, after = -1;
        switch (type) {
            case 0, 5 -> { intro = u32ptr(); defeat = u32ptr(); }
            case 1, 2 -> { intro = u32ptr(); defeat = u32ptr(); after = u32ptr(); }
            case 3 -> defeat = u32ptr();
            case 4, 7 -> { intro = u32ptr(); defeat = u32ptr(); u32ptr(); }
            case 6, 8 -> { intro = u32ptr(); defeat = u32ptr(); u32ptr(); after = u32ptr(); }
            case 9 -> { defeat = u32ptr(); u32ptr(); }                           // rival du début
            default -> { }
        }
        boolean beaten = save.flags.get(TRAINER_FLAGS + trainer);
        if (beaten && type != 9) return;                                       // texte d'après-combat
        int introText = intro, defeatText = defeat, next = after;
        if (introText >= 0) {
            pending.add(() -> host.message(introText));
            pending.add(() -> block(Wait.PRINT, 0));
            pending.add(() -> block(Wait.BUTTON, 0));
            pending.add(host::closeMessage);
        }
        pending.add(() -> {
            host.trainerBattle(trainer, introText, defeatText);
            block(Wait.BATTLE, 0);
        });
        pending.add(() -> save.flags.set(TRAINER_FLAGS + trainer));
        if (type == 1 || type == 2 || type == 6 || type == 8) {
            pending.add(() -> pc = next);
        } else if (type != 9) {
            pending.add(this::end);
        }
    }
}
