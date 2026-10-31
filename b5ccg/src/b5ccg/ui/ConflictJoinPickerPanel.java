package b5ccg.ui;

import b5ccg.engine.GameController;
import b5ccg.model.Card;
import b5ccg.model.CharacterCard;
import b5ccg.model.Conflict;
import b5ccg.model.FleetCard;
import b5ccg.model.GameAction;
import b5ccg.model.GameState;
import b5ccg.model.Participation;
import b5ccg.model.Player;
import b5ccg.model.enums.CardType;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * B5-2577 (from B5-0363) -- the human's conflict-join decision window, as one
 * panel that lights only while the controller is actually collecting this
 * human seat's answer.
 *
 * <p><b>Why a panel and not three more toolbar buttons.</b> B5-0325 F1 put
 * Support and Oppose on the toolbar next to the general action row, and B5-2433
 * made the single toolbar Pass button double as the decline for the conflict
 * round's Step 1 <em>declaration</em> window. The <em>join</em> window had no
 * dedicated surface: the human's one irreversible answer for the round sat in a
 * row of unrelated controls, with the phase banner and the toolbar as the only
 * things saying which window was open. This panel is that surface — three
 * options, one place, lit only during the window it belongs to.
 *
 * <p><b>The panel never re-derives a rule.</b> {@code update} is handed the
 * {@code joiningConflict} predicate MainWindow already computes (the B5-2261
 * phase gate plus {@code controller.isWaitingForHumanConflictJoin()} plus
 * {@code RulesEngine.canJoinConflict} plus {@code Conflict.canJoinConflict}).
 * The panel renders that answer; it does not ask its own legality questions, so
 * there is no second authority that can disagree with the engine's. The one
 * thing it reads beyond that predicate is the conflict's
 * {@link Participation} restriction, and only to tell the human the
 * <em>consequence</em> of passing — see {@link #passConsequence}.
 *
 * <p><b>Character selection (B5-0160).</b> The panel now offers a dropdown of
 * eligible characters (and fleets, when the participation restriction allows
 * them) that the human may commit to the conflict. The chosen card is submitted
 * with the JOIN_CONFLICT_SUPPORT/OPPOSE action and the engine commits it
 * (subject to canCommitCard). If no eligible card exists, the dropdown shows
 * "Ambassador only" and the engine falls back to the ambassador.
 *
 * <p><b>Pass declines the join window (engine accepts {@code PASS}).</b>
 * {@code GameController.submitHumanAction} releases
 * {@code waitingForHumanConflictJoin} on {@code JOIN_CONFLICT_SUPPORT},
 * {@code JOIN_CONFLICT_OPPOSE} <em>and</em> {@code PASS}; a PASS join resolves
 * as "no side taken" (the human sits out, subject to later mandatory-
 * participation compulsion). So this panel submits {@code GameAction.pass()}
 * and then <em>verifies</em> that the window actually closed
 * ({@link #joinWindowStillOpen}); if it did not, the panel says so in as many
 * words and re-enables all three options, so the human is never stranded
 * believing they passed. The panel reports what the engine did rather than
 * what the click intended — the same discipline the B5-2123 phase gate
 * follows one screen over.
 */
public class ConflictJoinPickerPanel extends JPanel {

    private final GameController controller;

    private final JLabel promptLabel;
    private final JLabel consequenceLabel;
    private final JLabel statusLabel;
    private final JButton supportButton;
    private final JButton opposeButton;
    private final JButton passButton;
    private final JButton forceButton;
    private final JComboBox<Card> characterChoice;

    private boolean  windowOpen = false;
    private boolean  choiceMade = false;
    private String   lastStatus = "";

    public ConflictJoinPickerPanel(GameController controller) {
        this.controller = controller;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(new Color(10, 20, 10));
        setAlignmentX(LEFT_ALIGNMENT);
        setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(80, 120, 80)),
            "Conflict Join Decision",
            0, 0, new Font("SansSerif", Font.BOLD, 10), new Color(180, 200, 180)));

        promptLabel = mkLabel("No active conflict.", Font.BOLD, 10, new Color(220, 200, 120));
        promptLabel.setMaximumSize(new Dimension(280, 18));
        add(promptLabel);

        consequenceLabel = mkLabel("", Font.PLAIN, 9, new Color(200, 180, 140));
        consequenceLabel.setMaximumSize(new Dimension(280, 34));
        add(consequenceLabel);

        // Character/fleet choice dropdown (B5-0160: join commits the chosen character).
        characterChoice = new JComboBox<>();
        characterChoice.setMaximumSize(new Dimension(280, 24));
        characterChoice.setBackground(new Color(20, 40, 20));
        characterChoice.setForeground(new Color(200, 230, 200));
        characterChoice.setFont(new Font("SansSerif", Font.PLAIN, 10));
        characterChoice.setAlignmentX(LEFT_ALIGNMENT);
        add(characterChoice);

        supportButton = mkButton("Support", new Runnable() {
            @Override
            public void run() { submitSupport(); }
        });
        opposeButton  = mkButton("Oppose", new Runnable() {
            @Override
            public void run() { submitOppose(); }
        });
        passButton    = mkButton("Pass (do not participate)", new Runnable() {
            @Override
            public void run() { submitPass(); }
        });
        // B5-XXXX: Force Continue button - submits Oppose to unblock the engine
        // even if normal buttons are frozen. This is an emergency escape hatch.
        forceButton   = mkButton("Force Continue", new Runnable() {
            @Override
            public void run() { submitForceContinue(); }
        });
        forceButton.setToolTipText("Emergency: force the game to continue by auto-submitting Oppose");

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.setBackground(new Color(10, 20, 10));
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.add(supportButton);
        buttons.add(Box.createHorizontalStrut(6));
        buttons.add(opposeButton);
        buttons.add(Box.createHorizontalStrut(6));
        buttons.add(passButton);
        buttons.add(Box.createHorizontalStrut(6));
        buttons.add(forceButton);
        buttons.setMaximumSize(new Dimension(280, 30));
        add(buttons);

        statusLabel = mkLabel("", Font.PLAIN, 9, new Color(160, 190, 160));
        statusLabel.setMaximumSize(new Dimension(280, 34));
        add(statusLabel);

        setMaximumSize(new Dimension(280, 180));
        applyEnabledness();
    }

    // ── update ────────────────────────────────────────────────────────────────

    /**
     * Re-read the window and repaint. Called from MainWindow.refresh() on the
     * EDT, like every other panel there, and safe off the EDT as well
     * (InfluenceTrackerPanel's contract: defer rather than mutate Swing state
     * from a worker thread).
     *
     * @param joiningConflict the predicate MainWindow already computed for the
     *        existing toolbar buttons — the panel renders it rather than
     *        re-deriving it.
     */
    public void update(final GameState state, final boolean joiningConflict) {
        if (SwingUtilities.isEventDispatchThread()) {
            applyUpdate(state, joiningConflict);
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    applyUpdate(state, joiningConflict);
                }
            });
        }
    }

    private void applyUpdate(GameState state, boolean joiningConflict) {
        windowOpen = joiningConflict;
        Conflict conflict = state == null ? null : state.getActiveConflict();
        if (conflict == null) {
            promptLabel.setText("No active conflict.");
            consequenceLabel.setText("");
            characterChoice.removeAllItems();
            characterChoice.addItem(null);
            choiceMade = false;
            lastStatus = "";
            statusLabel.setText("");
            applyEnabledness();
            return;
        }
        promptLabel.setText(joinPromptFor(state, conflict));
        consequenceLabel.setText("<html>" + passConsequence(conflict) + "</html>");

        // Populate eligible cards for the human player (B5-0160).
        populateCharacterChoice(state, conflict);

        if (!joiningConflict) {
            // The window closed. A choice recorded while it was open stands; a
            // bare close (the controller moved on) clears the stale "waiting"
            // text so the panel is not asserting a question nobody is asking.
            choiceMade = false;
            if (controller != null && !controller.isWaitingForHumanConflictJoin()) {
                lastStatus = "";
            }
        }
        statusLabel.setText(lastStatus);
        applyEnabledness();
    }

    /** Populate the character choice dropdown with eligible cards for the human. */
    private void populateCharacterChoice(GameState state, Conflict conflict) {
        characterChoice.removeAllItems();
        Player human = state.getHumanPlayer();
        if (human == null) {
            characterChoice.addItem(null);
            return;
        }

        List<Card> eligible = new ArrayList<>();
        // Add eligible characters from Inner Circle and Supporting Role.
        for (CharacterCard ch : human.getInnerCircle()) {
            if (!ch.isFaceDown() && ch.canActAfterNeutralization()
                    && conflict.canCommitCard(human, ch)) {
                eligible.add(ch);
            }
        }
        for (CharacterCard ch : human.getSupportingRole()) {
            if (!ch.isFaceDown() && ch.canActAfterNeutralization()
                    && conflict.canCommitCard(human, ch)) {
                eligible.add(ch);
            }
        }
        // Add eligible fleets (when participation allows fleets).
        for (FleetCard fleet : human.getFleets()) {
            if (!fleet.isRotated() && fleet.canActAfterNeutralization()
                    && conflict.canCommitCard(human, fleet)) {
                eligible.add(fleet);
            }
        }

        if (eligible.isEmpty()) {
            characterChoice.addItem(null); // "Ambassador only" fallback
        } else {
            for (Card c : eligible) {
                characterChoice.addItem(c);
            }
        }
    }

    /** Enabled iff the engine is collecting an answer right now and none is in flight. */
    private void applyEnabledness() {
        boolean live = windowOpen && !choiceMade;
        supportButton.setEnabled(live);
        opposeButton.setEnabled(live);
        passButton.setEnabled(live);
        characterChoice.setEnabled(live);
        // Force button is always enabled when window is open (escape hatch)
        forceButton.setEnabled(windowOpen);
    }

    /** Get the currently selected card from the dropdown (may be null). */
    private Card getSelectedCard() {
        Card c = (Card) characterChoice.getSelectedItem();
        return c;
    }

    // ── submissions ───────────────────────────────────────────────────────────

    private void submitSupport() {
        if (!windowOpen || choiceMade || controller == null) return;
        choiceMade = true;
        Card chosen = getSelectedCard();
        lastStatus = "Submitted Support into the join window" + (chosen != null ? " with " + chosen.getTitle() : "") + ".";
        statusLabel.setText(lastStatus);
        applyEnabledness();
        controller.submitHumanAction(GameAction.joinSupport(chosen));
    }

    private void submitOppose() {
        if (!windowOpen || choiceMade || controller == null) return;
        choiceMade = true;
        Card chosen = getSelectedCard();
        lastStatus = "Submitted Oppose into the join window" + (chosen != null ? " with " + chosen.getTitle() : "") + ".";
        statusLabel.setText(lastStatus);
        applyEnabledness();
        controller.submitHumanAction(GameAction.joinOppose(chosen));
    }

    /**
     * Pass, with the acceptance check this row exists to make visible.
     *
     * <p>Submits {@code GameAction.pass()} and then asks the controller whether
     * the window is still open. If it is, the engine refused the pass, and the
     * panel re-enables all three options and says so: a swallowed click that
     * leaves a dead panel reads to the human as "the game froze".
     */
    private void submitPass() {
        if (!windowOpen || choiceMade || controller == null) return;
        controller.submitHumanAction(GameAction.pass());
        if (joinWindowStillOpen()) {
            choiceMade = false;
            lastStatus = "Pass NOT taken: the join window is still waiting "
                    + "(GameController.submitHumanAction did not release it), "
                    + "so this panel left all three options open. Choose a side to "
                    + "continue, or wait for the table.";
        } else {
            choiceMade = true;
            lastStatus = "Submitted Pass into the join window.";
        }
        statusLabel.setText(lastStatus);
        applyEnabledness();
    }

    /**
     * Force Continue - submits JOIN_CONFLICT_OPPOSE with no card to unblock the
     * engine. This is an emergency escape hatch when normal buttons are frozen.
     * It bypasses the normal choiceMade guard and forces the submission.
     */
    private void submitForceContinue() {
        if (controller == null) return;
        lastStatus = "FORCE CONTINUE: submitted Oppose to unblock game.";
        statusLabel.setText(lastStatus);
        controller.submitHumanAction(GameAction.joinOppose(null));
    }

    /** True while the controller is still blocked collecting this seat's side. */
    boolean joinWindowStillOpen() {
        return controller == null || controller.isWaitingForHumanConflictJoin();
    }

    // ── text ──────────────────────────────────────────────────────────────────

    private String joinPromptFor(GameState state, Conflict conflict) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html>");
        Player human = state.getHumanPlayer();
        if (human != null) {
            sb.append(escape(human.getName())).append(": ");
        }
        sb.append("join ").append(escape(title(conflict)))
          .append("? support ").append(conflict.supportTotal())
          .append(" vs oppose ").append(conflict.oppositionTotal());
        Player initiator = conflict.getInitiator();
        if (initiator != null) {
            sb.append(" (initiated by ").append(escape(initiator.getName())).append(")");
        }
        return sb.append("</html>").toString();
    }

    /**
     * What passing actually costs, read off the conflict's own Participation
     * restriction rather than re-derived. This is the one thing the panel says
     * beyond the predicate it was handed, and it is a report of engine state
     * (RulesEngine.enforceMandatoryParticipation reads the same three fields),
     * never a legality opinion.
     */
    static String passConsequence(Conflict conflict) {
        if (conflict == null) return "";
        Participation part = participation(conflict);
        if (part == null || !part.isRestricted()) {
            return "Pass: you do not participate. "
                    + "No participation restriction compels you onto a side.";
        }
        if (part.isMustTakeSide() || part.hasAllPlayersMustCommit()
                || part.isMustCommitAmbassador()) {
            return "Pass: this conflict compels every eligible player onto a side "
                    + "(must-take-side / all-players-commit / ambassador-commit), so "
                    + "declining still puts you in — opposing. Pass only chooses "
                    + "WHICH side you are forced onto.";
        }
        return "Pass: you do not participate in this conflict.";
    }

    private static Participation participation(Conflict conflict) {
        if (conflict.getCard() == null) return null;
        return conflict.getCard().getParticipation();
    }

    private static String title(Conflict conflict) {
        if (conflict.getCard() == null) return "(unknown conflict)";
        return conflict.getCard().getTitle();
    }

    /** HTML labels render markup, and a card title is rulebook text. */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ── construction helpers ──────────────────────────────────────────────────

    private static JLabel mkLabel(String text, int style, int size, Color fg) {
        JLabel l = new JLabel(text);
        l.setForeground(fg);
        l.setFont(new Font("SansSerif", style, size));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    /** MainWindow.makeButton's palette, copied rather than reached for: that
     *  method is private to the frame and this panel is built standalone by its
     *  own test. */
    private static JButton mkButton(String label, final Runnable action) {
        final Color disabledFg = new Color(220, 240, 220);
        JButton btn = new JButton(label) {
            @Override
            protected void paintComponent(Graphics g) {
                if (!isEnabled()) {
                    Color saved = getForeground();
                    setForeground(disabledFg);
                    super.paintComponent(g);
                    setForeground(saved);
                } else {
                    super.paintComponent(g);
                }
            }
        };
        btn.setBackground(new Color(40, 70, 40));
        btn.setForeground(new Color(200, 230, 200));
        btn.setFocusPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 130, 80)),
            BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        btn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
        return btn;
    }

    // ── accessors (package-private: the panel's own regression test reads them) ─

    JButton supportButton()    { return supportButton; }
    JButton opposeButton()     { return opposeButton; }
    JButton passButton()       { return passButton; }
    JButton forceButton()      { return forceButton; }
    JComboBox<Card> characterChoice() { return characterChoice; }
    JLabel  promptLabel()      { return promptLabel; }
    JLabel  consequenceLabel() { return consequenceLabel; }
    String  statusText()       { return lastStatus; }
    String  promptText()       { return promptLabel.getText(); }
    String  consequenceText()  { return consequenceLabel.getText(); }
}