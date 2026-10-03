package gamestore.client.ui;

import java.awt.*;
import javax.swing.*;

/** A viewport-width grid: rows grow vertically, never forcing a horizontal scrollbar. */
final class CoverGrid extends JPanel implements Scrollable {
    CoverGrid() { super(new GridLayout(0,3,Ui.scale(16),Ui.scale(16))); }
    private int columns() { int width = getParent() instanceof JViewport viewport ? viewport.getExtentSize().width : getWidth(); return Math.max(1,(width + Ui.scale(16)) / Ui.scale(240)); }
    @Override public void setBounds(int x,int y,int width,int height) { boolean resized = width != getWidth(); super.setBounds(x,y,width,height); if (resized) revalidate(); }
    @Override public void doLayout() { ((GridLayout)getLayout()).setColumns(columns()); super.doLayout(); }
    @Override public Dimension getPreferredSize() {
        int count = getComponentCount(), columns = columns(), height = Ui.scale(350);
        for (Component child : getComponents()) height = Math.max(height,child.getPreferredSize().height);
        int rows = (count + columns - 1) / columns;
        return new Dimension(0,rows * height + Math.max(0,rows - 1) * Ui.scale(16));
    }
    @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(Ui.scale(760),Ui.scale(500)); }
    @Override public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction) { return Ui.scale(24); }
    @Override public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction) { return Math.max(24,r.height - Ui.scale(24)); }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
