package b5ccg.ui;

import b5ccg.model.enums.CardType;
import b5ccg.model.enums.CardSet;
import b5ccg.model.enums.Loyalty;
import b5ccg.model.enums.MarkType;
import b5ccg.model.enums.Rarity;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * B5-4107: Swing card-authoring dialog.
 *
 * <p>Provides a form for creating and editing a single card record. The model
 * logic (JSON serialize/parse/validate) lives in {@link CardEditorModel}, which
 * has zero Swing dependencies so it is unit-testable headlessly. This class
 * is purely the view layer and requires a display.
 *
 * <p>Fields match the rulebook "Anatomy of a Card" (BABYLON5_CCG_RULEBOOK.md
 * §140-163): Name (§1), Influence Cost (§2), Card Type (§3), Abilities (§4),
 * Effects (§5), Marks (§6, B5-4112), Caption (§7).
 *
 * <p>Java 6 only: no diamond, no lambdas.
 */
public class CardEditorDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final CardEditorModel model;
    private boolean saved = false;

    // ── Form fields ──────────────────────────────────────────────────────────

    private JTextField idField;
    private JTextField titleField;
    private JComboBox typeCombo;
    private JTextField subtypeField;
    private JComboBox rarityCombo;
    private JComboBox factionCombo;
    private JComboBox setCombo;
    private JTextField imageKeyField;
    private JSpinner costSpinner;
    private JCheckBox mercenaryCheck;
    private JTextArea textArea;
    private JTextArea flavorTextArea;

    // ── CHARACTER-specific stats (rulebook §4) ───────────────────────────────

    private JPanel statsPanel;
    private JSpinner diplomacySpinner;
    private JSpinner intrigueSpinner;
    private JSpinner psiSpinner;
    private JSpinner leadershipSpinner;
    private JCheckBox ambassadorCheck;

    // ── Printed marks (rulebook §6, B5-4112) ─────────────────────────────────
    // One spinner per MarkType, in MarkType.values() order. A change listener
    // refreshes the opposing-marks warning label only; it never blocks saving.

    private JPanel marksPanel;
    private JSpinner[] markSpinners;
    private JLabel marksWarningLabel;

    private final JPanel formPanel;

    // ── Colors (matching the app's dark theme) ───────────────────────────────

    private static final Color BG = new Color(10, 20, 10);
    private static final Color FORM_BG = new Color(20, 30, 20);
    private static final Color FG = new Color(200, 230, 200);
    private static final Color LABEL_FG = new Color(180, 200, 180);

    /**
     * Creates a dialog for editing an existing card.
     */
    public CardEditorDialog(Window parent, CardEditorModel existing) {
        super(parent, "Babylon 5 CCG -- Card Editor", Dialog.ModalityType.APPLICATION_MODAL);
        this.model = (existing != null) ? existing : new CardEditorModel();
        this.formPanel = new JPanel();
        initGui();
        populateFromModel();
        setSize(560, 620);
        setLocationRelativeTo(parent);
    }

    /**
     * Creates a dialog for a new blank card.
     */
    public CardEditorDialog(Window parent) {
        this(parent, null);
    }

    // ── GUI construction ────────────────────────────────────────────────────

    private void initGui() {
        setBackground(BG);
        setLayout(new BorderLayout(8, 8));
        getContentPane().setBackground(BG);

        formPanel.setLayout(new GridBagLayout());
        formPanel.setBackground(FORM_BG);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 6, 4, 6);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // ── Card ID ──
        row = addLabeledField(formPanel, gc, row, "Card ID:", idField = new JTextField(18));

        // ── Card Name (required) ──
        JLabel nameLabel = new JLabel("Card Name (*):");
        nameLabel.setForeground(FG);
        row = addLabeledField(formPanel, gc, row, nameLabel, titleField = new JTextField(18));

        // ── Card Type (required) ──
        typeCombo = new JComboBox();
        CardType[] types = CardType.values();
        for (int i = 0; i < types.length; i++) {
            typeCombo.addItem(types[i]);
        }
        typeCombo.addActionListener(new TypeChangeListener());
        row = addLabeledField(formPanel, gc, row, "Card Type:", typeCombo);

        // ── Subtype ──
        row = addLabeledField(formPanel, gc, row, "Subtype:", subtypeField = new JTextField(18));

        // ── Rarity ──
        rarityCombo = new JComboBox();
        Rarity[] rarities = Rarity.values();
        for (int i = 0; i < rarities.length; i++) {
            rarityCombo.addItem(rarities[i]);
        }
        row = addLabeledField(formPanel, gc, row, "Rarity:", rarityCombo);

        // ── Loyalty ──
        factionCombo = new JComboBox();
        Loyalty[] factions = Loyalty.values();
        for (int i = 0; i < factions.length; i++) {
            factionCombo.addItem(factions[i]);
        }
        row = addLabeledField(formPanel, gc, row, "Loyalty:", factionCombo);

        // ── Set ──
        setCombo = new JComboBox();
        CardSet[] sets = CardSet.values();
        for (int i = 0; i < sets.length; i++) {
            setCombo.addItem(sets[i]);
        }
        row = addLabeledField(formPanel, gc, row, "Set:", setCombo);

        // ── Image Key ──
        row = addLabeledField(formPanel, gc, row, "Image Key:", imageKeyField = new JTextField(18));

        // ── Cost ──
        costSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 99, 1));
        row = addLabeledField(formPanel, gc, row, "Cost:", costSpinner);

        // ── Mercenary ──
        mercenaryCheck = new JCheckBox("Mercenary");
        mercenaryCheck.setBackground(FORM_BG);
        mercenaryCheck.setForeground(FG);
        gc.gridy = row; gc.gridx = 0; gc.gridwidth = 2; gc.weightx = 0;
        formPanel.add(mercenaryCheck, gc);
        gc.gridwidth = 1;
        row++;

        // ── Effect Text (§5: Effects / card effect text) ──
        gc.gridy = row; gc.gridx = 0; gc.gridwidth = 2; gc.weightx = 1.0; gc.weighty = 0.2;
        JLabel effectLabel = new JLabel("Effect Text:");
        effectLabel.setForeground(FG);
        effectLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        formPanel.add(effectLabel, gc);
        gc.gridy = row + 1;
        textArea = new JTextArea(5, 40);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBackground(new Color(28, 38, 28));
        textArea.setForeground(FG);
        textArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        formPanel.add(new JScrollPane(textArea), gc);
        gc.gridwidth = 1; gc.weighty = 0;
        row += 2;

        // ── Flavor Text (§7: Caption / card flavor text) ──
        gc.gridy = row; gc.gridx = 0; gc.gridwidth = 2;
        JLabel flavorLabel = new JLabel("Flavor Text (Caption):");
        flavorLabel.setForeground(FG);
        flavorLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        formPanel.add(flavorLabel, gc);
        gc.gridy = row + 1;
        flavorTextArea = new JTextArea(3, 40);
        flavorTextArea.setLineWrap(true);
        flavorTextArea.setWrapStyleWord(true);
        flavorTextArea.setBackground(new Color(28, 38, 28));
        flavorTextArea.setForeground(FG);
        flavorTextArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        formPanel.add(new JScrollPane(flavorTextArea), gc);
        gc.gridwidth = 1;
        row += 2;

        // ── CHARACTER stats panel (rulebook §4) ──
        statsPanel = buildStatsPanel();
        statsPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(80, 130, 80)),
            "Character Stats (\u00a74 Abilities)",
            TitledBorder.DEFAULT_JUSTIFICATION,
            TitledBorder.DEFAULT_POSITION,
            new Font("SansSerif", Font.BOLD, 10),
            new Color(180, 200, 180)));
        statsPanel.setBackground(FORM_BG);
        gc.gridy = row; gc.gridx = 0; gc.gridwidth = 2; gc.weightx = 1.0; gc.weighty = 1.0;
        formPanel.add(statsPanel, gc);
        gc.gridwidth = 1; gc.weighty = 0;
        row++;

        // ── Printed marks panel (rulebook §6, B5-4112) ──
        marksPanel = buildMarksPanel();
        marksPanel.setBackground(FORM_BG);
        gc.gridy = row; gc.gridx = 0; gc.gridwidth = 2; gc.weightx = 1.0; gc.weighty = 0;
        formPanel.add(marksPanel, gc);
        gc.gridwidth = 1; gc.weighty = 0;
        row++;

        add(new JScrollPane(formPanel), BorderLayout.CENTER);

        // ── Button panel ──
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        buttonPanel.setBackground(BG);
        JButton saveButton = new JButton("Save Card");
        JButton loadFileButton = new JButton("Load...");
        JButton saveFileButton = new JButton("Save to File...");
        JButton cancelButton = new JButton("Cancel");
        styleButton(saveButton);
        styleButton(loadFileButton);
        styleButton(saveFileButton);
        styleButton(cancelButton);
        saveButton.addActionListener(new SaveAction());
        loadFileButton.addActionListener(new LoadFromFileAction());
        saveFileButton.addActionListener(new SaveToFileAction());
        cancelButton.addActionListener(new CancelAction());
        buttonPanel.add(saveButton);
        buttonPanel.add(loadFileButton);
        buttonPanel.add(saveFileButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // Initially show stats panel only for CHARACTER
        updateStatsVisibility();
    }

    /** Adds a labeled row to the form. Returns the next row index. */
    private static int addLabeledField(JPanel panel, GridBagConstraints gc, int row,
                                       String labelText, JComponent... field) {
        JLabel label = new JLabel(labelText);
        label.setForeground(LABEL_FG);
        gc.gridy = row; gc.gridx = 0; gc.gridwidth = 1; gc.weightx = 0;
        panel.add(label, gc);
        gc.gridx = 1; gc.weightx = 1.0;
        for (int i = 0; i < field.length; i++) {
            panel.add(field[i], gc);
        }
        return row + 1;
    }

    /** Overload that accepts a pre-built JLabel. */
    private static int addLabeledField(JPanel panel, GridBagConstraints gc, int row,
                                       JLabel label, JComponent... field) {
        label.setForeground(LABEL_FG);
        gc.gridy = row; gc.gridx = 0; gc.gridwidth = 1; gc.weightx = 0;
        panel.add(label, gc);
        gc.gridx = 1; gc.weightx = 1.0;
        for (int i = 0; i < field.length; i++) {
            panel.add(field[i], gc);
        }
        return row + 1;
    }

    /** Builds the CHARACTER stats sub-panel. */
    private JPanel buildStatsPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(FORM_BG);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(3, 3, 3, 3);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.NONE;

        diplomacySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 99, 1));
        intrigueSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 99, 1));
        psiSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 99, 1));
        leadershipSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 99, 1));
        ambassadorCheck = new JCheckBox("Is Ambassador");
        ambassadorCheck.setBackground(FORM_BG);
        ambassadorCheck.setForeground(FG);

        int r = 0;
        gc.gridy = r; gc.gridx = 0;
        p.add(new JLabel("Diplomacy (Green):"), gc);
        gc.gridx = 1;
        p.add(diplomacySpinner, gc);
        gc.gridx = 2;
        p.add(new JLabel("Intrigue (Blue):"), gc);
        gc.gridx = 3;
        p.add(intrigueSpinner, gc);
        r++;

        gc.gridy = r; gc.gridx = 0;
        p.add(new JLabel("Psi (Purple):"), gc);
        gc.gridx = 1;
        p.add(psiSpinner, gc);
        gc.gridx = 2;
        p.add(new JLabel("Leadership (Orange):"), gc);
        gc.gridx = 3;
        p.add(leadershipSpinner, gc);
        r++;

        gc.gridy = r; gc.gridx = 0; gc.gridwidth = 4;
        p.add(ambassadorCheck, gc);

        return p;
    }

    /** Builds the printed-marks sub-panel (rulebook §6, B5-4112). */
    private JPanel buildMarksPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(FORM_BG);
        p.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(80, 130, 80)),
            "Printed Marks (\u00a76 Marks)",
            TitledBorder.DEFAULT_JUSTIFICATION,
            TitledBorder.DEFAULT_POSITION,
            new Font("SansSerif", Font.BOLD, 10),
            new Color(180, 200, 180)));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(3, 3, 3, 3);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.NONE;

        MarkType[] types = MarkType.values();
        markSpinners = new JSpinner[types.length];
        int cols = 3;
        for (int i = 0; i < types.length; i++) {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, 0, 99, 1));
            spinner.addChangeListener(new MarksChangeListener());
            markSpinners[i] = spinner;
            JLabel label = new JLabel(types[i].name() + ":");
            label.setForeground(LABEL_FG);
            gc.gridy = i / cols; gc.gridx = (i % cols) * 2;
            p.add(label, gc);
            gc.gridx = (i % cols) * 2 + 1;
            p.add(spinner, gc);
        }

        // Loyalty-level opposing-marks warning (rulebook Anatomy item 6:
        // a faction cannot possess Shadow and Vorlon marks at the same
        // time). Warning only — saving is never blocked.
        marksWarningLabel = new JLabel("");
        marksWarningLabel.setForeground(new Color(255, 180, 120));
        int warnRow = (types.length + cols - 1) / cols;
        gc.gridy = warnRow; gc.gridx = 0; gc.gridwidth = cols * 2;
        p.add(marksWarningLabel, gc);

        return p;
    }

    /** Refreshes the opposing-marks warning from the current spinner values. */
    private void updateMarksWarning() {
        if (marksWarningLabel == null || markSpinners == null) return;
        int shadow = spinnerValue(markSpinners[MarkType.SHADOW.ordinal()]);
        int vorlon = spinnerValue(markSpinners[MarkType.VORLON.ordinal()]);
        if (shadow > 0 && vorlon > 0) {
            marksWarningLabel.setText(
                "Warning: Shadow + Vorlon on one card violates the faction rule (opposing marks).");
        } else {
            marksWarningLabel.setText("");
        }
    }

    /** Reads a non-negative int from a spinner, tolerating a null slot. */
    private static int spinnerValue(JSpinner spinner) {
        if (spinner == null) return 0;
        Object val = spinner.getValue();
        if (val instanceof Integer) {
            return Math.max(0, ((Integer) val).intValue());
        }
        return 0;
    }

    private class MarksChangeListener implements javax.swing.event.ChangeListener {
        public void stateChanged(javax.swing.event.ChangeEvent e) {
            updateMarksWarning();
        }
    }

    // ── Data binding ───────────────────────────────────────────────────────

    /** Loads model values into the form fields. */
    private void populateFromModel() {
        idField.setText(model.getId() != null ? model.getId() : "");
        titleField.setText(model.getTitle() != null ? model.getTitle() : "");
        typeCombo.setSelectedItem(model.getType());
        subtypeField.setText(model.getSubtype() != null ? model.getSubtype() : "");
        rarityCombo.setSelectedItem(model.getRarity());
        factionCombo.setSelectedItem(model.getLoyalty());
        setCombo.setSelectedItem(model.getSet());
        imageKeyField.setText(model.getImageKey() != null ? model.getImageKey() : "");
        costSpinner.setValue(new Integer(model.getCost()));
        mercenaryCheck.setSelected(model.isMercenary());
        textArea.setText(model.getText() != null ? model.getText() : "");
        flavorTextArea.setText(model.getFlavorText() != null ? model.getFlavorText() : "");
        diplomacySpinner.setValue(new Integer(model.getDiplomacy()));
        intrigueSpinner.setValue(new Integer(model.getIntrigue()));
        psiSpinner.setValue(new Integer(model.getPsi()));
        leadershipSpinner.setValue(new Integer(model.getLeadership()));
        ambassadorCheck.setSelected(model.isAmbassador());

        // Printed marks (rulebook §6): one spinner per MarkType.
        MarkType[] markTypes = MarkType.values();
        if (markSpinners != null && markSpinners.length == markTypes.length) {
            for (int i = 0; i < markTypes.length; i++) {
                markSpinners[i].setValue(new Integer(model.getMarkCount(markTypes[i])));
            }
        }
        updateMarksWarning();

        updateStatsVisibility();
    }

    /** Reads form field values into the model. */
    private void saveToModel() {
        model.setId(idField.getText());
        model.setTitle(titleField.getText());
        Object sel = typeCombo.getSelectedItem();
        if (sel instanceof CardType) {
            model.setType((CardType) sel);
        }
        model.setSubtype(subtypeField.getText());
        Object rSel = rarityCombo.getSelectedItem();
        if (rSel instanceof Rarity) {
            model.setRarity((Rarity) rSel);
        }
        Object fSel = factionCombo.getSelectedItem();
        if (fSel instanceof Loyalty) {
            model.setLoyalty((Loyalty) fSel);
        }
        Object sSel = setCombo.getSelectedItem();
        if (sSel instanceof CardSet) {
            model.setSet((CardSet) sSel);
        }
        model.setImageKey(imageKeyField.getText());
        Object costVal = costSpinner.getValue();
        if (costVal instanceof Integer) {
            model.setCost(((Integer) costVal).intValue());
        }
        model.setMercenary(mercenaryCheck.isSelected());
        model.setText(textArea.getText());
        model.setFlavorText(flavorTextArea.getText());

        // CHARACTER stats — always read into the model; toJson() only emits
        // them when type == CHARACTER (see CardEditorModel.toJson).
        Object dVal = diplomacySpinner.getValue();
        if (dVal instanceof Integer) model.setDiplomacy(((Integer) dVal).intValue());
        Object iVal = intrigueSpinner.getValue();
        if (iVal instanceof Integer) model.setIntrigue(((Integer) iVal).intValue());
        Object pVal = psiSpinner.getValue();
        if (pVal instanceof Integer) model.setPsi(((Integer) pVal).intValue());
        Object lVal = leadershipSpinner.getValue();
        if (lVal instanceof Integer) model.setLeadership(((Integer) lVal).intValue());
        model.setAmbassador(ambassadorCheck.isSelected());

        // Printed marks (rulebook §6): always read spinners into the model;
        // toJson() only emits nonzero types (see CardEditorModel.toJson).
        if (markSpinners != null) {
            MarkType[] markTypes = MarkType.values();
            int n = Math.min(markSpinners.length, markTypes.length);
            for (int i = 0; i < n; i++) {
                model.setMarkCount(markTypes[i], spinnerValue(markSpinners[i]));
            }
        }
    }

    // ── Type selection handler ─────────────────────────────────────────────

    private void updateStatsVisibility() {
        CardType t = (CardType) typeCombo.getSelectedItem();
        boolean isCharacter = (t == CardType.CHARACTER);
        statsPanel.setVisible(isCharacter);
        formPanel.revalidate();
        formPanel.repaint();
    }

    private class TypeChangeListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            updateStatsVisibility();
        }
    }

    // ── Button actions ──────────────────────────────────────────────────────

    private class SaveAction implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            saveToModel();
            List<String> missing = model.validateRequired();
            if (missing.isEmpty()) {
                saved = true;
                dispose();
            } else {
                StringBuilder msg = new StringBuilder();
                msg.append("Missing required fields:\n");
                for (int i = 0; i < missing.size(); i++) {
                    msg.append("  - ").append(missing.get(i)).append("\n");
                }
                msg.append("\n(*) fields are required.");
                JOptionPane.showMessageDialog(CardEditorDialog.this,
                    msg.toString(), "Validation Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private class CancelAction implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            saved = false;
            dispose();
        }
    }

    // ── File I/O actions (B5-4109) ───────────────────────────────────────────
    //
    // Load reads an existing card JSON file (DeckLoader tokenizer shape)
    // into the form with all fields populated via populateFromModel().
    // Save to File writes the current form to a .json file via
    // CardEditorModel.saveToFile. Both surface errors in a message dialog
    // rather than throwing out of the event dispatch thread.

    /** B5-4109: file filter shared by the Load and Save choosers. */
    private static FileNameExtensionFilter jsonFilter() {
        return new FileNameExtensionFilter("Card JSON files (*.json)", "json");
    }

    private class LoadFromFileAction implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Load Card JSON");
            chooser.setFileFilter(jsonFilter());
            int choice = chooser.showOpenDialog(CardEditorDialog.this);
            if (choice != JFileChooser.APPROVE_OPTION) return;
            java.io.File file = chooser.getSelectedFile();
            if (file == null) return;
            try {
                CardEditorModel loaded = CardEditorModel.fromFile(file);
                // Copy every field onto the live model, then repopulate the
                // form so all fields show the loaded values.
                model.setId(loaded.getId());
                model.setTitle(loaded.getTitle());
                model.setType(loaded.getType());
                model.setSubtype(loaded.getSubtype());
                model.setRarity(loaded.getRarity());
                model.setLoyalty(loaded.getLoyalty());
                model.setSet(loaded.getSet());
                model.setImageKey(loaded.getImageKey());
                model.setText(loaded.getText());
                model.setFlavorText(loaded.getFlavorText());
                model.setCost(loaded.getCost());
                model.setMercenary(loaded.isMercenary());
                model.setDiplomacy(loaded.getDiplomacy());
                model.setIntrigue(loaded.getIntrigue());
                model.setPsi(loaded.getPsi());
                model.setLeadership(loaded.getLeadership());
                model.setAmbassador(loaded.isAmbassador());
                MarkType[] markTypes = MarkType.values();
                for (int i = 0; i < markTypes.length; i++) {
                    model.setMarkCount(markTypes[i], loaded.getMarkCount(markTypes[i]));
                }
                populateFromModel();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(CardEditorDialog.this,
                    "Could not load card file:\n" + ex.getMessage(),
                    "Load Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private class SaveToFileAction implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            saveToModel();
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Save Card JSON");
            chooser.setFileFilter(jsonFilter());
            int choice = chooser.showSaveDialog(CardEditorDialog.this);
            if (choice != JFileChooser.APPROVE_OPTION) return;
            java.io.File file = chooser.getSelectedFile();
            if (file == null) return;
            // Append .json when the user typed a bare name (the filter
            // shows *.json but JFileChooser does not enforce the suffix).
            String path = file.getPath();
            if (path.indexOf('.') < 0) {
                file = new java.io.File(path + ".json");
            }
            if (file.exists()) {
                int overwrite = JOptionPane.showConfirmDialog(CardEditorDialog.this,
                    "File exists. Overwrite?\n" + file.getPath(),
                    "Confirm Overwrite", JOptionPane.YES_NO_OPTION);
                if (overwrite != JOptionPane.YES_OPTION) return;
            }
            try {
                model.saveToFile(file);
                JOptionPane.showMessageDialog(CardEditorDialog.this,
                    "Card saved to:\n" + file.getPath(),
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(CardEditorDialog.this,
                    "Could not save card file:\n" + ex.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ── Result ─────────────────────────────────────────────────────────────

    /**
     * Returns the edited model, or null if the dialog was cancelled.
     * Only valid after the dialog has been closed.
     */
    public CardEditorModel getCard() {
        return saved ? model : null;
    }

    private void styleButton(JButton btn) {
        btn.setBackground(BG);
        btn.setForeground(FG);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
    }
}
