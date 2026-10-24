package dev.orvaen.mmo.data;

import dev.orvaen.mmo.engine.Storage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sprites des Pokémon qui marchent derrière le joueur (4 directions, 2 images par direction).
 * <p>
 * La GBA n'en a pas : ce sont des sprites faits par la communauté pour le projet de fans
 * <a href="https://github.com/rh-hideout/pokeemerald-expansion">pokeemerald-expansion</a>. Comme pour la ROM,
 * <b>rien n'est livré avec le jeu</b> : au premier besoin, chaque sprite est téléchargé sur l'ordinateur du joueur
 * (une image PNG par espèce), puis gardé dans {@code ~/.orvaen-mmo/followers/}. Sans Internet, le jeu se rabat
 * sur la petite icône de la ROM.
 * <p>
 * Une image fait 6 cases carrées (32x32, ou 64x64 pour les grands Pokémon) : bas ×2, haut ×2, gauche ×2
 * (la droite est la gauche retournée).
 */
public final class FollowerSprites {

    /** Version figée du dépôt, pour que les images ne changent pas d'une partie à l'autre. */
    public static final String DEFAULT_URL =
            "https://raw.githubusercontent.com/rh-hideout/pokeemerald-expansion/44bc3e0473b85d8e254c8dbc9004ac2d7b0a5fb3/graphics/pokemon/";

    /** Dossiers des espèces 1 à 386, dans l'ordre du Pokédex national. */
    private static final String[] FOLDERS = (
            "bulbasaur ivysaur venusaur charmander charmeleon charizard squirtle wartortle blastoise caterpie "
            + "metapod butterfree weedle kakuna beedrill pidgey pidgeotto pidgeot rattata raticate spearow "
            + "fearow ekans arbok pikachu raichu sandshrew sandslash nidoran_f nidorina nidoqueen nidoran_m "
            + "nidorino nidoking clefairy clefable vulpix ninetales jigglypuff wigglytuff zubat golbat oddish "
            + "gloom vileplume paras parasect venonat venomoth diglett dugtrio meowth persian psyduck golduck "
            + "mankey primeape growlithe arcanine poliwag poliwhirl poliwrath abra kadabra alakazam machop "
            + "machoke machamp bellsprout weepinbell victreebel tentacool tentacruel geodude graveler golem "
            + "ponyta rapidash slowpoke slowbro magnemite magneton farfetchd doduo dodrio seel dewgong grimer "
            + "muk shellder cloyster gastly haunter gengar onix drowzee hypno krabby kingler voltorb "
            + "electrode exeggcute exeggutor cubone marowak hitmonlee hitmonchan lickitung koffing weezing "
            + "rhyhorn rhydon chansey tangela kangaskhan horsea seadra goldeen seaking staryu starmie mr_mime "
            + "scyther jynx electabuzz magmar pinsir tauros magikarp gyarados lapras ditto eevee vaporeon "
            + "jolteon flareon porygon omanyte omastar kabuto kabutops aerodactyl snorlax articuno zapdos "
            + "moltres dratini dragonair dragonite mewtwo mew chikorita bayleef meganium cyndaquil quilava "
            + "typhlosion totodile croconaw feraligatr sentret furret hoothoot noctowl ledyba ledian spinarak "
            + "ariados crobat chinchou lanturn pichu cleffa igglybuff togepi togetic natu xatu mareep flaaffy "
            + "ampharos bellossom marill azumarill sudowoodo politoed hoppip skiploom jumpluff aipom sunkern "
            + "sunflora yanma wooper quagsire espeon umbreon murkrow slowking misdreavus unown wobbuffet "
            + "girafarig pineco forretress dunsparce gligar steelix snubbull granbull qwilfish scizor shuckle "
            + "heracross sneasel teddiursa ursaring slugma magcargo swinub piloswine corsola remoraid "
            + "octillery delibird mantine skarmory houndour houndoom kingdra phanpy donphan porygon2 stantler "
            + "smeargle tyrogue hitmontop smoochum elekid magby miltank blissey raikou entei suicune larvitar "
            + "pupitar tyranitar lugia ho_oh celebi treecko grovyle sceptile torchic combusken blaziken "
            + "mudkip marshtomp swampert poochyena mightyena zigzagoon linoone wurmple silcoon beautifly "
            + "cascoon dustox lotad lombre ludicolo seedot nuzleaf shiftry taillow swellow wingull pelipper "
            + "ralts kirlia gardevoir surskit masquerain shroomish breloom slakoth vigoroth slaking nincada "
            + "ninjask shedinja whismur loudred exploud makuhita hariyama azurill nosepass skitty delcatty "
            + "sableye mawile aron lairon aggron meditite medicham electrike manectric plusle minun volbeat "
            + "illumise roselia gulpin swalot carvanha sharpedo wailmer wailord numel camerupt torkoal spoink "
            + "grumpig spinda trapinch vibrava flygon cacnea cacturne swablu altaria zangoose seviper "
            + "lunatone solrock barboach whiscash corphish crawdaunt baltoy claydol lileep cradily anorith "
            + "armaldo feebas milotic castform kecleon shuppet banette duskull dusclops tropius chimecho "
            + "absol wynaut snorunt glalie spheal sealeo walrein clamperl huntail gorebyss relicanth luvdisc "
            + "bagon shelgon salamence beldum metang metagross regirock regice registeel latias latios kyogre "
            + "groudon rayquaza jirachi deoxys").split(" ");

    public static final int DOWN = 0, UP = 1, LEFT = 2, RIGHT = 3;

    private final Path cache;
    private final String baseUrl;
    private final boolean download;
    private final Map<Integer, BufferedImage[]> loaded = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> requested = new ConcurrentHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Sprites des Pokémon qui suivent");
        t.setDaemon(true);
        return t;
    });

    public FollowerSprites(Path cache, String baseUrl, boolean download) {
        this.cache = cache;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.download = download;
    }

    /** Réglages du joueur ({@code followers.download}, {@code followers.url} dans config.properties). */
    public static FollowerSprites fromConfig() {
        return new FollowerSprites(Storage.DIR.resolve("followers"), Storage.followerUrl(), Storage.followerDownload());
    }

    /** Dossier de l'espèce n° {@code national} du Pokédex, ou null. */
    public static String folder(int national) {
        return national >= 1 && national <= FOLDERS.length ? FOLDERS[national - 1] : null;
    }

    /**
     * Les 8 images (bas, haut, gauche, droite × 2) de l'espèce, ou null tant qu'elles ne sont pas prêtes
     * (le chargement est alors lancé en arrière-plan).
     */
    public BufferedImage[] frames(int national) {
        BufferedImage[] f = loaded.get(national);
        if (f != null) return f;
        if (folder(national) != null && requested.putIfAbsent(national, true) == null) {
            worker.submit(() -> load(national));
        }
        return null;
    }

    /** Charge depuis le cache, sinon télécharge (une seule fois par lancement). */
    void load(int national) {
        try {
            String name = folder(national);
            Path file = cache.resolve(name.replace('/', '_') + ".png");
            byte[] png = Files.exists(file) ? Files.readAllBytes(file) : null;
            if (png == null && download) {
                png = fetch(baseUrl + name + "/overworld.png");
                if (png != null) {
                    Files.createDirectories(cache);
                    Files.write(file, png);
                }
            }
            if (png == null) return;
            BufferedImage[] frames = cut(ImageIO.read(new ByteArrayInputStream(png)));
            if (frames != null) loaded.put(national, frames);
        } catch (Exception | LinkageError ignored) {
            // pas d'Internet, image abîmée… : l'icône de la ROM reste affichée
        }
    }

    private static byte[] fetch(String url) throws Exception {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();
        HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        return res.statusCode() == 200 && res.body().length > 8 ? res.body() : null;
    }

    /** Découpe la planche en images ; la couleur 0 de la palette est transparente. */
    public static BufferedImage[] cut(BufferedImage sheet) {
        if (sheet == null) return null;
        int size = sheet.getHeight();
        if (size <= 0 || sheet.getWidth() < size * 6) return null;
        int transparent = 0;
        boolean indexed = sheet.getColorModel() instanceof IndexColorModel;
        if (!indexed) transparent = sheet.getRGB(0, 0);
        BufferedImage[] out = new BufferedImage[8];
        for (int f = 0; f < 6; f++) {
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int sx = f * size + x;
                    boolean clear = indexed ? sheet.getRaster().getSample(sx, y, 0) == 0 : sheet.getRGB(sx, y) == transparent;
                    if (!clear) img.setRGB(x, y, sheet.getRGB(sx, y) | 0xFF000000);
                }
            }
            out[f] = img;
        }
        for (int f = 0; f < 2; f++) {                       // droite = gauche retournée
            BufferedImage left = out[4 + f], right = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) right.setRGB(size - 1 - x, y, left.getRGB(x, y));
            out[6 + f] = right;
        }
        return out;
    }

    /** Image pour une direction ({@link #DOWN}…) et un pas (0 ou 1). */
    public static BufferedImage pick(BufferedImage[] frames, int direction, int step) {
        return frames[direction * 2 + (step & 1)];
    }
}
