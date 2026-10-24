package dev.orvaen.mmo.rom;

/** Erreur de lecture ou de format dans la ROM fournie par le joueur. */
public class RomException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RomException(String message) {
        super(message);
    }

    public RomException(String message, Throwable cause) {
        super(message, cause);
    }
}
