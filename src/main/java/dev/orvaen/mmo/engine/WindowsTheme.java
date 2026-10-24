package dev.orvaen.mmo.engine;

import java.awt.Frame;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

/**
 * Barre de titre sombre sous Windows 10/11, comme PokeMMO.
 * <p>
 * Swing ne sait pas le demander : on appelle directement Windows (user32 et dwmapi) avec l'API
 * d'appels natifs de Java (java.lang.foreign), par réflexion pour rester compilable en Java 21 :
 * <pre>
 *   HWND h = FindWindowW(NULL, titre);
 *   DwmSetWindowAttribute(h, DWMWA_USE_IMMERSIVE_DARK_MODE, &amp;TRUE, 4);
 * </pre>
 * À appeler après {@code pack()} et avant {@code setVisible(true)} : la fenêtre existe déjà pour
 * Windows mais n'est pas encore affichée, donc elle apparaît directement en sombre.
 * Partout ailleurs (Linux, macOS, Windows trop ancien, Java sans cette API) : ne fait rien.
 */
public final class WindowsTheme {

    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;          // Windows 10 20H1+ et 11
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19;      // Windows 10 1809-1909

    private WindowsTheme() {}

    /** Demande la barre de titre sombre ; renvoie vrai si Windows l'a acceptée. */
    public static boolean darkTitleBar(Frame frame) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) return false;
        try {
            if (!frame.isDisplayable()) frame.addNotify();
            return Native.darkTitleBar(frame.getTitle());
        } catch (Throwable t) {
            return false; // purement cosmétique : jamais bloquant
        }
    }

    /**
     * Sonde de test, utilisable hors Windows : appelle {@code getpid} de la libc par le même
     * mécanisme que FindWindowW / DwmSetWindowAttribute (réflexion + appel natif).
     */
    public static long nativeProbe(String library) throws Throwable {
        return ((Number) Native.downcall(library, "getpid", Native.JAVA_INT).invokeWithArguments()).longValue();
    }

    /** Appels natifs, isolés pour ne charger java.lang.foreign que sous Windows. */
    private static final class Native {
        private static final Class<?> LINKER;
        private static final Class<?> SEGMENT;
        private static final Class<?> LAYOUT;
        private static final Class<?> DESCRIPTOR;
        private static final Class<?> LOOKUP;
        private static final Class<?> ARENA;
        private static final Class<?> OPTION;
        private static final Object ADDRESS;
        private static final Object JAVA_INT;
        private static final Object NULL;
        private static final Object ARENA_GLOBAL;
        private static MethodHandle findWindow;
        private static MethodHandle setAttribute;

        static {
            try {
                LINKER = Class.forName("java.lang.foreign.Linker");
                SEGMENT = Class.forName("java.lang.foreign.MemorySegment");
                LAYOUT = Class.forName("java.lang.foreign.MemoryLayout");
                DESCRIPTOR = Class.forName("java.lang.foreign.FunctionDescriptor");
                LOOKUP = Class.forName("java.lang.foreign.SymbolLookup");
                ARENA = Class.forName("java.lang.foreign.Arena");
                OPTION = Class.forName("java.lang.foreign.Linker$Option");
                Class<?> values = Class.forName("java.lang.foreign.ValueLayout");
                ADDRESS = values.getField("ADDRESS").get(null);
                JAVA_INT = values.getField("JAVA_INT").get(null);
                NULL = SEGMENT.getField("NULL").get(null);
                ARENA_GLOBAL = ARENA.getMethod("global").invoke(null);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        static synchronized boolean darkTitleBar(String title) throws Throwable {
            if (findWindow == null) {
                findWindow = downcall("user32", "FindWindowW", ADDRESS, ADDRESS, ADDRESS);
                setAttribute = downcall("dwmapi", "DwmSetWindowAttribute", JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT);
            }
            Object hwnd = findWindow.invokeWithArguments(NULL, wideString(title));
            if ((long) SEGMENT.getMethod("address").invoke(hwnd) == 0) return false;
            Object on = allocate(4);
            buffer(on).putInt(0, 1);
            int hr = (int) setAttribute.invokeWithArguments(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, on, 4);
            if (hr != 0) hr = (int) setAttribute.invokeWithArguments(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, on, 4);
            return hr == 0;
        }

        private static MethodHandle downcall(String library, String function, Object result, Object... args)
                throws ReflectiveOperationException {
            Object lookup = LOOKUP.getMethod("libraryLookup", String.class, ARENA).invoke(null, library, ARENA_GLOBAL);
            Object symbol = ((Optional<?>) LOOKUP.getMethod("find", String.class).invoke(lookup, function)).orElseThrow();
            Object layouts = Array.newInstance(LAYOUT, args.length);
            for (int i = 0; i < args.length; i++) Array.set(layouts, i, args[i]);
            Object descriptor = DESCRIPTOR.getMethod("of", LAYOUT, layouts.getClass()).invoke(null, result, layouts);
            Object noOptions = Array.newInstance(OPTION, 0);
            Object linker = LINKER.getMethod("nativeLinker").invoke(null);
            Method downcall = LINKER.getMethod("downcallHandle", SEGMENT, DESCRIPTOR, noOptions.getClass());
            return (MethodHandle) downcall.invoke(linker, symbol, descriptor, noOptions);
        }

        /** Chaîne UTF-16 terminée par un zéro, comme l'attend FindWindowW. */
        private static Object wideString(String s) throws ReflectiveOperationException {
            Object seg = allocate((s.length() + 1) * 2L);
            ByteBuffer b = buffer(seg);
            for (int i = 0; i < s.length(); i++) b.putChar(i * 2, s.charAt(i));
            b.putChar(s.length() * 2, '\0');
            return seg;
        }

        private static Object allocate(long bytes) throws ReflectiveOperationException {
            return ARENA.getMethod("allocate", long.class).invoke(ARENA_GLOBAL, bytes);
        }

        private static ByteBuffer buffer(Object segment) throws ReflectiveOperationException {
            return ((ByteBuffer) SEGMENT.getMethod("asByteBuffer").invoke(segment)).order(ByteOrder.nativeOrder());
        }
    }
}
