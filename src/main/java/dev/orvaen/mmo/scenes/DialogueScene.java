package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Scene;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.function.Supplier;

/**
 * Dialogue simple (sans script) affiché par-dessus le jeu, dans la {@link MessageBox} habituelle :
 * se ferme après la dernière page.
 */
public final class DialogueScene implements Scene {

    /**
     * Qui parle : rectangle du sprite dans la fenêtre ; {@code below} : le joueur est au-dessus de lui.
     */
    public record Speaker(Rectangle2D bounds, boolean below) {}

    private final Game game;
    private final Runnable onClose;
    private final MessageBox box = new MessageBox();

    public DialogueScene(Game game, List<String> pages, Runnable onClose) {
        this(game, pages, onClose, null);
    }

    public DialogueScene(Game game, List<String> pages, Runnable onClose, Supplier<Speaker> speaker) {
        this.game = game;
        this.onClose = onClose;
        box.show(pages, speaker);
    }

    @Override
    public boolean isOverlay() {
        return true;
    }

    public int page() {
        return box.page();
    }

    public boolean pageComplete() {
        return box.pageComplete();
    }

    @Override
    public void update(Input in) {
        box.update(in);
        if (box.takeButton()) {
            box.close();
            game.pop();
            if (onClose != null) onClose.run();
        }
    }

    @Override
    public void render(Graphics2D g) {
        // haute résolution uniquement
    }

    @Override
    public void renderHiRes(Graphics2D g, int w, int h) {
        box.render(g, w, h);
    }
}
