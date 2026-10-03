package gamestore.client.ui;

import java.awt.*;

/** FlowLayout with a preferred height matching its wrapped rows. */
final class WrapLayout extends FlowLayout {
    WrapLayout() { super(LEFT,Ui.scale(8),Ui.scale(6)); }
    @Override public Dimension preferredLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            int width = target.getWidth(); Container parent = target.getParent();
            while (width <= 0 && parent != null) { width = parent.getWidth(); parent = parent.getParent(); }
            if (width <= 0) width = Ui.scale(1000);
            Insets insets = target.getInsets(); int available = Math.max(1,width - insets.left - insets.right - getHgap() * 2);
            int rowWidth = 0,rowHeight = 0,totalHeight = getVgap(),maxWidth = 0;
            for (Component child : target.getComponents()) {
                if (!child.isVisible()) continue; Dimension size = child.getPreferredSize();
                if (rowWidth > 0 && rowWidth + getHgap() + size.width > available) {
                    maxWidth = Math.max(maxWidth,rowWidth); totalHeight += rowHeight + getVgap(); rowWidth = 0; rowHeight = 0;
                }
                rowWidth += (rowWidth == 0 ? 0 : getHgap()) + size.width; rowHeight = Math.max(rowHeight,size.height);
            }
            return new Dimension(Math.max(maxWidth,rowWidth) + insets.left + insets.right + 2 * getHgap(),totalHeight + rowHeight + getVgap() + insets.top + insets.bottom);
        }
    }
}
