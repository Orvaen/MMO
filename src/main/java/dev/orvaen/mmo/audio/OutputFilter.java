package dev.orvaen.mmo.audio;

/**
 * Dernière étape avant le haut-parleur : rend le son doux pour l'oreille.
 * <ol>
 *   <li><b>Filtre passe-bas</b> (~7 kHz, deux étages pour couper net) : la GBA mixe sa musique à 13 kHz et son haut-parleur ne rend pas
 *       les aigus ; sans ce filtre, les ondes carrées et le bruit sifflent dans un casque.</li>
 *   <li><b>Retrait de la composante continue</b> : évite les « pops » quand le son démarre ou s'arrête.</li>
 *   <li><b>Limiteur doux</b> : si plusieurs pistes s'additionnent trop fort, le son est arrondi au lieu
 *       de saturer (grésillement).</li>
 * </ol>
 */
final class OutputFilter {

    private static final double CUTOFF_HZ = 8000;
    private static final float LIMIT_START = 0.75f;

    // Coefficients du filtre (biquad passe-bas de Butterworth)
    private final double b0, b1, b2, a1, a2;
    private static final int STAGES = 2;
    // Mémoire du filtre, par étage et par canal : index = étage * 2 + canal (0 gauche, 1 droite)
    private final double[] x1 = new double[STAGES * 2], x2 = new double[STAGES * 2],
            y1 = new double[STAGES * 2], y2 = new double[STAGES * 2];
    private final double[] dcIn = new double[2], dcOut = new double[2];

    OutputFilter(double sampleRate) {
        double w = 2 * Math.PI * CUTOFF_HZ / sampleRate;
        double alpha = Math.sin(w) / (2 * Math.sqrt(0.5));
        double cos = Math.cos(w), a0 = 1 + alpha;
        b0 = (1 - cos) / 2 / a0;
        b1 = (1 - cos) / a0;
        b2 = b0;
        a1 = -2 * cos / a0;
        a2 = (1 - alpha) / a0;
    }

    /** Filtre {@code frames} échantillons stéréo entrelacés, sur place. */
    void process(float[] buf, int frames) {
        for (int i = 0; i < frames * 2; i++) {
            int c = i & 1;
            double y = buf[i];
            for (int st = 0; st < STAGES; st++) {
                int k = st * 2 + c;
                double x = y;
                y = b0 * x + b1 * x1[k] + b2 * x2[k] - a1 * y1[k] - a2 * y2[k];
                x2[k] = x1[k];
                x1[k] = x;
                y2[k] = y1[k];
                y1[k] = y;
            }
            double dc = y - dcIn[c] + 0.995 * dcOut[c];       // passe-haut très bas (~35 Hz)
            dcIn[c] = y;
            dcOut[c] = dc;
            buf[i] = limit((float) dc);
        }
    }

    /** Linéaire jusqu'à 0,75, puis courbe douce qui ne dépasse jamais 1. */
    private static float limit(float x) {
        float a = Math.abs(x);
        if (a <= LIMIT_START) return x;
        float over = (a - LIMIT_START) / (1 - LIMIT_START);
        float y = LIMIT_START + (1 - LIMIT_START) * (float) Math.tanh(over);
        return Math.copySign(y, x);
    }
}
