package dev.orvaen.mmo.world;

import dev.orvaen.mmo.rom.GameProfile;

import java.util.List;
import java.util.Optional;

/** Une carte jouable : grille, nom, warps et liaisons, telle qu'extraite de la ROM. */
public final class GameMap {

    public static final int TYPE_INDOOR = 8;
    public static final int TYPE_UNDERGROUND = 4;

    public final int group;
    public final int num;
    public final String name;
    public final int mapType;
    public final MapLayout layout;
    public final List<Warp> warps;
    public final List<Connection> connections;
    private final GameProfile profile;

    public GameMap(int group, int num, String name, int mapType, MapLayout layout,
                   List<Warp> warps, List<Connection> connections, GameProfile profile) {
        this.group = group;
        this.num = num;
        this.name = name;
        this.mapType = mapType;
        this.layout = layout;
        this.warps = List.copyOf(warps);
        this.connections = List.copyOf(connections);
        this.profile = profile;
    }

    public int width() { return layout.width; }
    public int height() { return layout.height; }

    public String key() {
        return group + "." + num;
    }

    /** Attributs du métatile posé à (x, y). */
    public long attributesAt(int x, int y) {
        int id = MapLayout.metatileId(layout.blockAt(x, y));
        if (id < profile.metatilesInPrimary) return layout.primary.attributes(id);
        return layout.secondary == null ? 0 : layout.secondary.attributes(id - profile.metatilesInPrimary);
    }

    public int behaviorAt(int x, int y) {
        return profile.behavior(attributesAt(x, y));
    }

    public boolean isLandEncounterAt(int x, int y) {
        return layout.inBounds(x, y) && profile.isLandEncounter(attributesAt(x, y));
    }

    public Optional<Warp> warpAt(int x, int y) {
        for (Warp w : warps) {
            if (w.x() == x && w.y() == y) return Optional.of(w);
        }
        return Optional.empty();
    }

    public Optional<Connection> connection(Connection.Direction dir) {
        for (Connection c : connections) {
            if (c.direction() == dir) return Optional.of(c);
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return String.format("%s (%d.%d, %dx%d)", name, group, num, width(), height());
    }
}
