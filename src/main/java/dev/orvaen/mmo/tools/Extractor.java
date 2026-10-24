package dev.orvaen.mmo.tools;

import dev.orvaen.mmo.data.GameData;
import dev.orvaen.mmo.data.PlayerLook;
import dev.orvaen.mmo.data.Species;
import dev.orvaen.mmo.player.CosmeticMasks;
import dev.orvaen.mmo.player.PlayerGraphics;
import dev.orvaen.mmo.player.Recolor;
import dev.orvaen.mmo.rom.RomException;
import dev.orvaen.mmo.world.GameMap;
import dev.orvaen.mmo.world.MapRenderer;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;

/**
 * Exporte ce que le moteur lit dans la ROM, pour vérifier l'extraction à l'œil.
 * Les fichiers produits restent sur la machine du joueur (dossier ignoré par git).
 */
public final class Extractor {

    private final GameData data;

    public Extractor(GameData data) {
        this.data = data;
    }

    public void exportAll(Path dir) throws IOException {
        Path sprites = Files.createDirectories(dir.resolve("sprites"));
        Path maps = Files.createDirectories(dir.resolve("maps"));

        int spriteCount = 0;
        try (PrintWriter csv = new PrintWriter(Files.newBufferedWriter(dir.resolve("especes.csv"), StandardCharsets.UTF_8))) {
            csv.println("id;nom;type1;type2;pv;atq;def;vit;ats;dfs;capture");
            for (Species s : data.species.all()) {
                csv.printf("%d;%s;%s;%s;%d;%d;%d;%d;%d;%d;%d%n", s.id(), s.name(),
                        Species.typeName(s.type1()), s.hasSecondType() ? Species.typeName(s.type2()) : "",
                        s.hp(), s.attack(), s.defense(), s.speed(), s.spAttack(), s.spDefense(), s.catchRate());
                ImageIO.write(data.species.frontSprite(s.id()), "png",
                        sprites.resolve(String.format("%03d_%s.png", s.id(), safe(s.name()))).toFile());
                spriteCount++;
            }
        }

        int mapCount = 0, failures = 0;
        try (PrintWriter log = new PrintWriter(Files.newBufferedWriter(dir.resolve("cartes.txt"), StandardCharsets.UTF_8))) {
            for (int g = 0; g < data.maps.groupCount(); g++) {
                for (int n = 0; n < data.maps.mapCount(g); n++) {
                    try {
                        GameMap m = data.maps.load(g, n);
                        MapRenderer.Rendered r = data.renderer.render(m);
                        BufferedImage out = new BufferedImage(r.below().getWidth(), r.below().getHeight(),
                                BufferedImage.TYPE_INT_RGB);
                        Graphics2D gfx = out.createGraphics();
                        gfx.drawImage(r.below(), 0, 0, null);
                        gfx.drawImage(r.above(), 0, 0, null);
                        gfx.dispose();
                        ImageIO.write(out, "png", maps.resolve(String.format("%02d.%03d_%s.png", g, n, safe(m.name))).toFile());
                        log.printf("%d.%d\t%s\t%dx%d\t%d warps\t%d liaisons%n", g, n, m.name, m.width(), m.height(),
                                m.warps.size(), m.connections.size());
                        mapCount++;
                    } catch (RomException | IllegalArgumentException e) {
                        log.printf("%d.%d\tERREUR\t%s%n", g, n, e.getMessage());
                        failures++;
                    }
                }
            }
        }
        String player = exportPlayer(dir);
        System.out.printf("Export terminé dans %s : %d sprites, %d cartes (%d illisibles), joueur : %s.%n",
                dir.toAbsolutePath(), spriteCount, mapCount, failures, player);
    }

    /** Sprites du joueur tels que lus dans la ROM (d'origine), pour vérifier la localisation. */
    private String exportPlayer(Path dir) throws IOException {
        if (!data.player.available()) return "non trouvé (" + data.player.problem() + ")";
        Path out = Files.createDirectories(dir.resolve("joueur"));
        String[] names = {"garcon", "fille"};
        for (int body = 0; body < 2; body++) {
            PlayerLook look = PlayerLook.defaults().withBody(body);
            BufferedImage strip = new BufferedImage(16 * PlayerGraphics.OW_FRAMES, 32, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = strip.createGraphics();
            for (int f = 0; f < PlayerGraphics.OW_FRAMES; f++) {
                g.drawImage(Recolor.render(data.player.overworld(body, f), List.of(), look, CosmeticMasks.ALL, 0, false),
                        f * 16, 0, null);
            }
            g.dispose();
            ImageIO.write(strip, "png", out.resolve(names[body] + "_marche.png").toFile());
            ImageIO.write(Recolor.render(data.player.front(body), List.of(), look, CosmeticMasks.ALL, 0, false),
                    "png", out.resolve(names[body] + "_face.png").toFile());
            ImageIO.write(Recolor.render(data.player.back(body), List.of(), look, CosmeticMasks.ALL, 0, false),
                    "png", out.resolve(names[body] + "_dos.png").toFile());
        }
        return "exporté dans joueur/";
    }

    /** Nom de fichier portable : sans accents ni caractères spéciaux. */
    private static String safe(String name) {
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.replace('♂', 'M').replace('♀', 'F').replaceAll("[^A-Za-z0-9._-]+", "_");
        return s.isEmpty() ? "sans_nom" : s;
    }
}
