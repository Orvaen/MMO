package dev.orvaen.mmo.engine;

import java.awt.BufferCapabilities;
import java.awt.Canvas;
import java.awt.ImageCapabilities;
import java.lang.reflect.Constructor;

/**
 * Synchronisation verticale (V-Sync) pour l'affichage du jeu.
 * <p>
 * Sans elle, l'image est envoyée à l'écran au milieu d'un balayage : déchirures et cadence irrégulière.
 * Java2D sait la demander (pipeline Direct3D sous Windows, OpenGL ailleurs), mais via une classe interne
 * ({@code sun.java2d.pipe.hw.ExtendedBufferCapabilities}). Le manifeste du jar l'ouvre au jeu
 * ({@code Add-Exports: java.desktop/sun.java2d.pipe.hw}) ; si elle reste inaccessible ou refusée,
 * on revient à un double tampon classique et la boucle cadence elle-même l'affichage.
 */
final class VSync {

    private VSync() {}

    /** Crée les tampons du canevas ; renvoie vrai si la V-Sync a été acceptée. */
    static boolean createBuffers(Canvas canvas) {
        try {
            Class<?> ext = Class.forName("sun.java2d.pipe.hw.ExtendedBufferCapabilities");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object on = Enum.valueOf((Class<? extends Enum>) Class.forName(
                    "sun.java2d.pipe.hw.ExtendedBufferCapabilities$VSyncType"), "VSYNC_ON");
            Constructor<?> c = ext.getConstructor(ImageCapabilities.class, ImageCapabilities.class,
                    BufferCapabilities.FlipContents.class, on.getClass());
            BufferCapabilities caps = (BufferCapabilities) c.newInstance(new ImageCapabilities(true),
                    new ImageCapabilities(true), BufferCapabilities.FlipContents.UNDEFINED, on);
            canvas.createBufferStrategy(2, caps);
            return true;
        } catch (Throwable t) {
            canvas.createBufferStrategy(2);
            return false;
        }
    }
}
