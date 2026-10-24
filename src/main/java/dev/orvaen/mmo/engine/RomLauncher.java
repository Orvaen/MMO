package dev.orvaen.mmo.engine;

import dev.orvaen.mmo.rom.RomCheck;
import dev.orvaen.mmo.rom.RomCheck.Result;
import dev.orvaen.mmo.rom.RomCheck.Slot;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fenêtre de sélection des ROM, façon PokeMMO, en mode sombre : une carte par jeu avec le chemin
 * du fichier, un bouton « Parcourir… » (boîte d'ouverture de Windows), l'état de la ROM et « Jouer ».
 * Glisser un fichier .gba sur une carte fonctionne aussi. La barre de titre reste celle de Windows.
 */
public final class RomLauncher {

    /** Choix du joueur : quel jeu lancer, avec quel fichier. */
    public record Choice(Slot slot, Path path) {}

    // Thème sombre
    static final Color BG = new Color(0x17181C);
    static final Color CARD = new Color(0x22242A);
    static final Color CARD_SELECTED = new Color(0x262C38);
    static final Color CARD_BORDER = new Color(0x33363F);
    static final Color FIELD = new Color(0x121317);
    static final Color FIELD_BORDER = new Color(0x3A3D46);
    static final Color ACCENT = new Color(0x3D7BE0);
    static final Color ACCENT_HOVER = new Color(0x4F8DF2);
    static final Color TEXT = new Color(0xE6E8EE);
    static final Color MUTED = new Color(0x9AA0AC);
    static final Color OK = new Color(0x5BD18B);
    static final Color ERROR = new Color(0xFF6B6B);

    private final JFrame frame;
    private final Map<Slot, Row> rows = new EnumMap<>(Slot.class);
    private final DarkButton play = new DarkButton("Jouer", true);
    private final AtomicReference<Choice> result = new AtomicReference<>();
    private final CountDownLatch closed = new CountDownLatch(1);

    /** Une carte de la fenêtre : un jeu, son fichier, son état. */
    private final class Row {
        final Slot slot;
        final JRadioButton choose;
        final JTextField path = darkField();
        final JLabel status = new JLabel(" ");
        final Card card = new Card();
        Result check = new Result(RomCheck.Status.EMPTY, "Aucune ROM choisie", null);

        Row(Slot slot, ButtonGroup group) {
            this.slot = slot;
            choose = new JRadioButton(slot.games);
            choose.setIcon(new RadioIcon(false));
            choose.setSelectedIcon(new RadioIcon(true));
            choose.setRolloverIcon(new RadioIcon(false));
            choose.setRolloverSelectedIcon(new RadioIcon(true));
            choose.setOpaque(false);
            choose.setFocusPainted(false);
            choose.setForeground(TEXT);
            choose.setFont(choose.getFont().deriveFont(Font.BOLD, 13f));
            choose.setIconTextGap(8);
            choose.setToolTipText("Jeu lancé en cliquant sur « Jouer » (région " + slot.region + ")");
            group.add(choose);
            path.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { refresh(); }
                public void removeUpdate(DocumentEvent e) { refresh(); }
                public void changedUpdate(DocumentEvent e) { refresh(); }
            });
            choose.addItemListener(e -> {
                card.selected = choose.isSelected();
                card.repaint();
                updatePlay();
            });
            status.setFont(status.getFont().deriveFont(12f));
            // Cliquer n'importe où sur la carte la sélectionne
            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    choose.setSelected(true);
                }
            });
        }

        Path file() {
            String t = path.getText().trim();
            if (t.length() > 1 && t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1);
            return t.isEmpty() ? null : Path.of(t);
        }

        void refresh() {
            Path f;
            try {
                f = file();
            } catch (RuntimeException e) {
                f = null;
            }
            check = RomCheck.inspect(f, slot);
            switch (check.status()) {
                case OK -> {
                    status.setText("✔  " + check.message());
                    status.setForeground(OK);
                    if (!selectedRowOk()) choose.setSelected(true);
                }
                case ERROR -> {
                    status.setText("✘  " + check.message());
                    status.setForeground(ERROR);
                }
                case EMPTY -> {
                    status.setText("Aucune ROM choisie");
                    status.setForeground(MUTED);
                }
            }
            updatePlay();
        }

        void browse() {
            FileDialog fd = new FileDialog(frame, "Choisis ta ROM " + slot.games, FileDialog.LOAD);
            Path current = file();
            if (current != null && current.getParent() != null) fd.setDirectory(current.getParent().toString());
            fd.setFile("*.gba");
            fd.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".gba"));
            fd.setVisible(true);
            if (fd.getFile() != null) {
                path.setText(new File(fd.getDirectory(), fd.getFile()).getAbsolutePath());
                choose.setSelected(check.ok() || selectedRow() == null);
            }
        }
    }

    private RomLauncher(String problem, Slot problemSlot) {
        frame = new JFrame("Orvaen MMO — ROM");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setIconImages(List.of(appIcon(16), appIcon(32), appIcon(64)));
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                closed.countDown();
            }
        });

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Configuration des ROM");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        JLabel intro = new JLabel("<html>Orvaen MMO utilise <b>ta propre cartouche</b>. Indique où se trouvent tes ROM :"
                + "<br>le jeu les lit sur ton PC, sans jamais les copier ni les envoyer.</html>");
        intro.setForeground(MUTED);
        header.add(title);
        header.add(Box.createVerticalStrut(6));
        header.add(intro);
        root.add(header, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        ButtonGroup group = new ButtonGroup();
        for (Slot slot : Slot.values()) {
            Row row = new Row(slot, group);
            rows.put(slot, row);
            list.add(buildCard(row));
            list.add(Box.createVerticalStrut(10));
        }
        root.add(list, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        JLabel hint = new JLabel("Astuce : tu peux aussi glisser un fichier .gba sur une carte.");
        hint.setForeground(MUTED);
        hint.setFont(hint.getFont().deriveFont(11f));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        DarkButton quit = new DarkButton("Quitter", false);
        quit.addActionListener(e -> frame.dispose());
        play.setPreferredSize(new Dimension(120, 34));
        quit.setPreferredSize(new Dimension(100, 34));
        play.addActionListener(e -> {
            Row r = selectedRow();
            if (r == null || !r.check.ok()) return;
            result.set(new Choice(r.slot, r.file().toAbsolutePath()));
            frame.dispose();
        });
        buttons.add(quit);
        buttons.add(play);
        footer.add(hint, BorderLayout.WEST);
        footer.add(buttons, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.getRootPane().setDefaultButton(play);
        frame.getRootPane().registerKeyboardAction(e -> frame.dispose(),
                javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Valeurs mémorisées, puis le jeu lancé la dernière fois
        for (Row r : rows.values()) {
            String saved = Storage.romPath(r.slot);
            if (saved != null) r.path.setText(saved);
            else r.refresh();
        }
        Slot last = Storage.lastSlot();
        Row pick = last != null && rows.get(last).check.ok() ? rows.get(last) : firstOk();
        (pick != null ? pick : rows.get(Slot.KANTO)).choose.setSelected(true);
        if (problem != null && problemSlot != null) {
            Row r = rows.get(problemSlot);
            r.status.setText("✘  " + problem);
            r.status.setForeground(ERROR);
        }
        updatePlay();

        frame.pack();
        frame.setMinimumSize(frame.getSize());
        frame.setLocationRelativeTo(null);
    }

    private JComponent buildCard(Row row) {
        Card card = row.card;
        card.setLayout(new GridBagLayout());
        card.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        DarkButton browse = new DarkButton("Parcourir…", false);
        browse.setPreferredSize(new Dimension(112, 30));
        browse.addActionListener(e -> row.browse());

        JLabel region = new JLabel(row.slot.region);
        region.setForeground(MUTED);
        region.setFont(region.getFont().deriveFont(11f));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        card.add(row.choose, c);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 8, 0, 0);
        card.add(region, c);

        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(8, 0, 0, 8);
        card.add(row.path, c);
        c.gridx = 2;
        c.gridwidth = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(8, 0, 0, 0);
        card.add(browse, c);

        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 3;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(6, 2, 0, 0);
        card.add(row.status, c);

        TransferHandler drop = dropHandler(row);
        for (JComponent comp : List.of(card, row.path, row.choose, row.status)) comp.setTransferHandler(drop);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    /**
     * Affiche la fenêtre et attend le choix du joueur ({@code null} s'il quitte).
     * À appeler hors du thread Swing (depuis {@code main}).
     * @param problem erreur à afficher sur la ligne {@code problemSlot} (ROM refusée au chargement), ou {@code null}
     */
    public static Choice show(String problem, Slot problemSlot) throws Exception {
        AtomicReference<RomLauncher> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            RomLauncher l = new RomLauncher(problem, problemSlot);
            ref.set(l);
            l.frame.setVisible(true);
            l.play.requestFocusInWindow();
        });
        RomLauncher l = ref.get();
        l.closed.await();
        AtomicReference<Choice> out = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            for (Row r : l.rows.values()) {
                Path f;
                try {
                    f = r.file();
                } catch (RuntimeException e) {
                    continue;
                }
                if (f == null) Storage.setRomPath(r.slot, null);
                else if (r.check.ok()) Storage.setRomPath(r.slot, f.toAbsolutePath().toString());
            }
            Choice c = l.result.get();
            if (c != null) Storage.setLastSlot(c.slot());
            out.set(c);
        });
        return out.get();
    }

    /**
     * ROM à lancer sans afficher la fenêtre : le dernier jeu joué s'il est toujours valide,
     * sinon le premier emplacement valide ; {@code null} s'il faut demander.
     */
    public static Choice remembered() {
        Slot last = Storage.lastSlot();
        if (last != null) {
            Choice c = valid(last);
            if (c != null) return c;
        }
        for (Slot s : Slot.values()) {
            Choice c = valid(s);
            if (c != null) return c;
        }
        return null;
    }

    private static Choice valid(Slot s) {
        String p = Storage.romPath(s);
        if (p == null) return null;
        Path path = Path.of(p);
        return RomCheck.inspect(path, s).ok() ? new Choice(s, path) : null;
    }

    private Row selectedRow() {
        for (Row r : rows.values()) if (r.choose.isSelected()) return r;
        return null;
    }

    private boolean selectedRowOk() {
        Row r = selectedRow();
        return r != null && r.check.ok();
    }

    private Row firstOk() {
        for (Row r : rows.values()) if (r.check.ok()) return r;
        return null;
    }

    private void updatePlay() {
        Row r = selectedRow();
        play.setEnabled(r != null && r.check.ok());
        play.setToolTipText(play.isEnabled() ? "Lancer " + r.slot.games : "Choisis une ROM valide");
    }

    private static TransferHandler dropHandler(Row row) {
        return new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport s) {
                return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                        || s.isDataFlavorSupported(DataFlavor.stringFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport s) {
                try {
                    if (s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        List<File> files = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        if (files.isEmpty()) return false;
                        row.path.setText(files.get(0).getAbsolutePath());
                    } else {
                        row.path.setText(((String) s.getTransferable().getTransferData(DataFlavor.stringFlavor)).trim());
                    }
                    row.choose.setSelected(row.check.ok());
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };
    }

    // ------------------------------------------------------------------ composants sombres

    private static JTextField darkField() {
        JTextField f = new JTextField(36);
        f.setBackground(FIELD);
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setSelectionColor(ACCENT);
        f.setSelectedTextColor(Color.WHITE);
        f.setFont(f.getFont().deriveFont(12.5f));
        Border inner = BorderFactory.createEmptyBorder(6, 8, 6, 8);
        f.setBorder(BorderFactory.createCompoundBorder(new RoundBorder(FIELD_BORDER), inner));
        f.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                f.setBorder(BorderFactory.createCompoundBorder(new RoundBorder(ACCENT), inner));
            }

            @Override
            public void focusLost(FocusEvent e) {
                f.setBorder(BorderFactory.createCompoundBorder(new RoundBorder(FIELD_BORDER), inner));
            }
        });
        return f;
    }

    /** Carte arrondie, surlignée quand son jeu est sélectionné. */
    private static final class Card extends JPanel {
        boolean selected;

        Card() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(selected ? CARD_SELECTED : CARD);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.setColor(selected ? ACCENT : CARD_BORDER);
            g2.setStroke(new BasicStroke(selected ? 1.5f : 1f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.dispose();
        }
    }

    /** Bouton plat arrondi : bleu (principal) ou gris foncé (secondaire). */
    static final class DarkButton extends JButton {
        private final boolean primary;
        private boolean hover;

        DarkButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setFont(getFont().deriveFont(primary ? Font.BOLD : Font.PLAIN, 13f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean on = isEnabled();
            boolean pressed = getModel().isPressed();
            Color fill;
            if (primary) fill = !on ? new Color(0x2B3445) : pressed ? ACCENT.darker() : hover ? ACCENT_HOVER : ACCENT;
            else fill = pressed ? new Color(0x2A2D35) : hover ? new Color(0x3A3E48) : new Color(0x30333B);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            if (!primary) {
                g2.setColor(FIELD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            }
            if (isFocusOwner()) {
                g2.setColor(new Color(255, 255, 255, 90));
                g2.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 8, 8);
            }
            g2.dispose();
            setForeground(on ? Color.WHITE : new Color(0x7A8292));
            super.paintComponent(g);
        }
    }

    /** Bordure arrondie d'un champ de texte. */
    private record RoundBorder(Color color) implements Border {
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, w - 1, h - 1, 8, 8);
            g2.dispose();
        }

        public Insets getBorderInsets(Component c) {
            return new Insets(1, 1, 1, 1);
        }

        public boolean isBorderOpaque() {
            return false;
        }
    }

    /** Bouton radio sombre : anneau gris, pastille bleue quand il est coché. */
    private record RadioIcon(boolean selected) implements Icon {
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(FIELD);
            g2.fillOval(x, y, 15, 15);
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(selected ? ACCENT : MUTED);
            g2.drawOval(x, y, 15, 15);
            if (selected) g2.fillOval(x + 4, y + 4, 8, 8);
            g2.dispose();
        }

        public int getIconWidth() {
            return 16;
        }

        public int getIconHeight() {
            return 16;
        }
    }

    /** Icône de la fenêtre et de la barre des tâches : une capsule du jeu sur fond bleu. */
    static Image appIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float s = size / 32f;
        g.setColor(ACCENT);
        g.fillRoundRect(0, 0, size, size, (int) (10 * s), (int) (10 * s));
        g.setColor(new Color(0x2A9D8F));
        g.fillArc((int) (6 * s), (int) (6 * s), (int) (20 * s), (int) (20 * s), 0, 180);
        g.setColor(new Color(0xF4F1DE));
        g.fillArc((int) (6 * s), (int) (6 * s), (int) (20 * s), (int) (20 * s), 180, 180);
        g.setColor(new Color(0x1D2433));
        g.setStroke(new BasicStroke(2 * s));
        g.drawOval((int) (6 * s), (int) (6 * s), (int) (20 * s), (int) (20 * s));
        g.drawLine((int) (6 * s), (int) (16 * s), (int) (26 * s), (int) (16 * s));
        g.setColor(new Color(0xE9C46A));
        g.fillOval((int) (13 * s), (int) (13 * s), (int) (6 * s), (int) (6 * s));
        g.dispose();
        return img;
    }

    /** Pour les tests visuels : construit la fenêtre sans attendre le choix. */
    static JFrame preview(String problem, Slot slot) {
        return new RomLauncher(problem, slot).frame;
    }
}
