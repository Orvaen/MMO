package dev.orvaen.mmo.engine;

import dev.orvaen.mmo.rom.RomCheck;
import dev.orvaen.mmo.rom.RomCheck.Result;
import dev.orvaen.mmo.rom.RomCheck.Slot;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fenêtre de sélection des ROM, façon PokeMMO : une ligne par jeu, le chemin du fichier,
 * un bouton « Parcourir… » (boîte d'ouverture de Windows), l'état de la ROM et « Jouer ».
 * Glisser un fichier .gba sur une ligne fonctionne aussi.
 */
public final class RomLauncher {

    /** Choix du joueur : quel jeu lancer, avec quel fichier. */
    public record Choice(Slot slot, Path path) {}

    private static final Color OK = new Color(0x2E7D32);
    private static final Color ERROR = new Color(0xC62828);
    private static final Color MUTED = new Color(0x6B7280);

    private final JDialog dialog;
    private final Map<Slot, Row> rows = new EnumMap<>(Slot.class);
    private final JButton play = new JButton("Jouer");
    private final AtomicReference<Choice> result = new AtomicReference<>();

    /** Une ligne de la fenêtre : un jeu, son fichier, son état. */
    private final class Row {
        final Slot slot;
        final JRadioButton choose;
        final JTextField path = new JTextField(34);
        final JLabel status = new JLabel(" ");
        Result check = new Result(RomCheck.Status.EMPTY, "Aucune ROM choisie", null);

        Row(Slot slot, ButtonGroup group) {
            this.slot = slot;
            choose = new JRadioButton(slot.games);
            choose.setFont(choose.getFont().deriveFont(Font.BOLD));
            choose.setToolTipText("Jeu lancé en cliquant sur « Jouer » (région " + slot.region + ")");
            group.add(choose);
            path.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { refresh(); }
                public void removeUpdate(DocumentEvent e) { refresh(); }
                public void changedUpdate(DocumentEvent e) { refresh(); }
            });
            choose.addActionListener(e -> updatePlay());
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
            FileDialog fd = new FileDialog(dialog, "Choisis ta ROM " + slot.games, FileDialog.LOAD);
            Path current = file();
            if (current != null && current.getParent() != null) fd.setDirectory(current.getParent().toString());
            fd.setFile("*.gba");
            fd.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".gba"));
            fd.setVisible(true);
            if (fd.getFile() != null) path.setText(new File(fd.getDirectory(), fd.getFile()).getAbsolutePath());
        }
    }

    private RomLauncher(String problem, Slot problemSlot) {
        dialog = new JDialog((java.awt.Frame) null, "Orvaen MMO — ROM", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Configuration des ROM");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 17f));
        JLabel intro = new JLabel("<html>Orvaen MMO utilise <b>ta propre cartouche</b>. Indique où se trouvent tes ROM : "
                + "le jeu les lit sur ton PC, sans jamais les copier ni les envoyer.</html>");
        intro.setForeground(MUTED);
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(intro);
        root.add(header, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridBagLayout());
        ButtonGroup group = new ButtonGroup();
        int y = 0;
        for (Slot slot : Slot.values()) {
            Row row = new Row(slot, group);
            rows.put(slot, row);
            JButton browse = new JButton("Parcourir…");
            browse.addActionListener(e -> row.browse());

            GridBagConstraints c = new GridBagConstraints();
            c.gridy = y++;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(y == 1 ? 0 : 12, 0, 2, 8);
            c.gridx = 0;
            grid.add(row.choose, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            grid.add(row.path, c);
            c.gridx = 2;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            c.insets = new Insets(c.insets.top, 0, 2, 0);
            grid.add(browse, c);

            GridBagConstraints s = new GridBagConstraints();
            s.gridy = y++;
            s.gridx = 1;
            s.gridwidth = 2;
            s.anchor = GridBagConstraints.WEST;
            s.insets = new Insets(0, 2, 0, 0);
            grid.add(row.status, s);

            TransferHandler drop = dropHandler(row);
            row.path.setTransferHandler(drop);
            row.choose.setTransferHandler(drop);
            row.status.setTransferHandler(drop);
        }
        root.add(grid, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        JLabel hint = new JLabel("Astuce : tu peux aussi glisser un fichier .gba sur une ligne.");
        hint.setForeground(MUTED);
        hint.setFont(hint.getFont().deriveFont(11f));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton quit = new JButton("Quitter");
        quit.addActionListener(e -> dialog.dispose());
        play.setFont(play.getFont().deriveFont(Font.BOLD));
        play.setPreferredSize(new Dimension(110, play.getPreferredSize().height + 6));
        play.addActionListener(e -> {
            Row r = selectedRow();
            if (r == null || !r.check.ok()) return;
            result.set(new Choice(r.slot, r.file().toAbsolutePath()));
            dialog.dispose();
        });
        buttons.add(quit);
        buttons.add(play);
        footer.add(hint, BorderLayout.WEST);
        footer.add(buttons, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.getRootPane().setDefaultButton(play);

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

        dialog.pack();
        dialog.setMinimumSize(dialog.getSize());
        dialog.setLocationRelativeTo(null);
    }

    /**
     * Affiche la fenêtre et attend le choix du joueur ({@code null} s'il quitte).
     * @param problem erreur à afficher sur la ligne {@code problemSlot} (ROM refusée au chargement), ou {@code null}
     */
    public static Choice show(String problem, Slot problemSlot) throws Exception {
        AtomicReference<Choice> out = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            RomLauncher l = new RomLauncher(problem, problemSlot);
            l.dialog.setVisible(true); // modal : rend la main à la fermeture
            Choice c = l.result.get();
            for (Row r : l.rows.values()) {
                Path f = r.file();
                if (f == null) Storage.setRomPath(r.slot, null);
                else if (r.check.ok()) Storage.setRomPath(r.slot, f.toAbsolutePath().toString());
            }
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
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };
    }

    /** Pour les tests visuels : construit la fenêtre sans l'afficher en modal. */
    static JComponent preview(String problem, Slot slot) {
        RomLauncher l = new RomLauncher(problem, slot);
        return (JComponent) l.dialog.getContentPane();
    }
}
