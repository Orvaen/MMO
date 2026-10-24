package dev.orvaen.mmo.world;

/** Liaison sans transition entre deux cartes extérieures (ville ↔ route). */
public record Connection(Direction direction, int offset, int targetGroup, int targetNum) {

    public enum Direction {
        SOUTH, NORTH, WEST, EAST;

        /** Codes du jeu : 1 sud, 2 nord, 3 ouest, 4 est (5 et 6 = plongée, ignorés). */
        public static Direction fromCode(int code) {
            return switch (code) {
                case 1 -> SOUTH;
                case 2 -> NORTH;
                case 3 -> WEST;
                case 4 -> EAST;
                default -> null;
            };
        }
    }
}
