package b5ccg.ui.dialog;

import b5ccg.ui.shell.KeyBindings;
import b5ccg.ui.theme.Theme;
import b5ccg.ui.theme.ThemedButton;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;

/**
 * Shared shell for Setup / DeckBuilder / CardEditor.
 * problems() block OK; warnings() never do. Models stay headless and unchanged.
 * Subclasses MUST call init() at the end of their constructor (not here: buildBody()
 * uses subclass fields that don't exist yet while this constructor runs).
 */
public abstract class ValidatedDialog extends JDialog {
    private final JTextArea problemsArea = new JTextArea(3, 40);
    private final JLabel warningLabel = new JLabel(" ");
    private final ThemedButton okButton;
    private final ThemedButton cancelButton = new ThemedButton("Cancel", ThemedButton.NORMAL);
    private boolean accepted;

    protected ValidatedDialog(Window owner, String title, String okLabel) {
        super(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        this.okButton = new ThemedButton(okLabel, ThemedButton.PRIMARY);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    protected abstract JComponent buildBody();

    /** Messages that must be fixed before OK is enabled. Empty list = valid. */
    protected abstract List<String> problems();

    /** Advisory messages (e.g. opposing marks). Never block OK. */
    protected List<String> warnings() {
        return Collections.<String>emptyList();
    }

    /** Apply the result. Called only when problems() is empty. */
    protected abstract void onAccept();

    protected final void init() {
        JPanel root = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        root.setBackground(Theme.BG_APP);
        root.setBorder(Theme.pad(Theme.PAD));
        root.add(buildBody(), BorderLayout.CENTER);

        problemsArea.setEditable(false);
        problemsArea.setFont(Theme.FONT_SMALL);
        problemsArea.setBackground(Theme.BG_PANEL);
        problemsArea.setForeground(Theme.DANGER);
        warningLabel.setFont(Theme.FONT_SMALL);
        warningLabel.setForeground(Theme.ACCENT);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.GAP, 0));
        buttons.setOpaque(false);
        buttons.add(cancelButton);
        buttons.add(okButton);

        JPanel feedback = new JPanel(new BorderLayout(0, 4));
        feedback.setOpaque(false);
        feedback.add(problemsArea, BorderLayout.NORTH);
        feedback.add(warningLabel, BorderLayout.CENTER);
        feedback.add(buttons, BorderLayout.SOUTH);
        root.add(feedback, BorderLayout.SOUTH);

        okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (problems().isEmpty()) {
                    onAccept();
                    accepted = true;
                    dispose();
                }
            }
        });
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        KeyBindings.bind(getRootPane(), "dialog.cancel",
                KeyStroke.getKeyStroke("ESCAPE"), new AbstractAction() {
                    public void actionPerformed(ActionEvent e) {
                        dispose();
                    }
                });
        getRootPane().setDefaultButton(okButton);

        setContentPane(root);
        revalidateForm();
        pack();
        setLocationRelativeTo(getOwner());
    }

    /** Call from field listeners whenever the form changes. */
    protected final void revalidateForm() {
        List<String> p = problems();
        StringBuilder sb = new StringBuilder();
        for (String s : p) {
            sb.append("\u2022 ").append(s).append('\n');
        }
        problemsArea.setText(sb.toString());
        problemsArea.setVisible(!p.isEmpty());
        okButton.setEnabled(p.isEmpty());

        List<String> w = warnings();
        warningLabel.setText(w.isEmpty() ? " " : "! " + w.get(0));
    }

    public boolean isAccepted() {
        return accepted;
    }
}
