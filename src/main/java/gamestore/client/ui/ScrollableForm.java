package gamestore.client.ui;

import java.awt.*;
import javax.swing.*;

/** A vertically scrolling form that always follows the viewport width. */
final class ScrollableForm extends JPanel implements Scrollable {
    ScrollableForm() { setLayout(new BoxLayout(this,BoxLayout.Y_AXIS)); }
    @Override public void doLayout() {
        for (Component child : getComponents()) if (child instanceof JComponent component) {
            Dimension maximum = new Dimension(Integer.MAX_VALUE,component.getPreferredSize().height);
            if (!maximum.equals(component.getMaximumSize())) component.setMaximumSize(maximum);
        }
        super.doLayout();
    }
    @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(Ui.scale(650),Ui.scale(500)); }
    @Override public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction) { return Ui.scale(24); }
    @Override public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction) { return Math.max(Ui.scale(24),visible.height - Ui.scale(24)); }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
