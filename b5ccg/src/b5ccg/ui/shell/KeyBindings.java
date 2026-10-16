package b5ccg.ui.shell;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.text.JTextComponent;

public final class KeyBindings {
    private KeyBindings() { }

    public static void bind(JComponent c, String name, KeyStroke ks, Action a) {
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, name);
        c.getActionMap().put(name, a);
    }

    /** Wraps an action so plain-key shortcuts are ignored while typing in a text field. */
    public static Action guarded(final Action inner) {
        return new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                Component f = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                        .getFocusOwner();
                if (f instanceof JTextComponent) {
                    return;
                }
                inner.actionPerformed(e);
            }
        };
    }
}
