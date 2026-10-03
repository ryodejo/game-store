package gamestore.client.ui;

import gamestore.client.ClientException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

final class Ui {
    static final Color BACKGROUND = new Color(0x17191D), PANEL = new Color(0x22252B), ACCENT = new Color(0x5B83B4), TEXT = new Color(0xECEEF2), MUTED = new Color(0xA0A6B0);
    private Ui() {}
    static void theme() {
        com.formdev.flatlaf.FlatDarkLaf.setup();
        Font font = new Font("Segoe UI",Font.PLAIN,13);
        UIManager.put("defaultFont",font);
        font = font.deriveFont(com.formdev.flatlaf.util.UIScale.scale(13f));
        UIManager.put("Button.arc",8); UIManager.put("Component.arc",8); UIManager.put("TextComponent.arc",8);
        UIManager.put("Component.focusColor",ACCENT); UIManager.put("Component.focusedBorderColor",ACCENT);
        UIManager.put("Button.background",PANEL); UIManager.put("Button.foreground",TEXT);
        UIManager.put("Button.hoverBackground",new Color(0x303640));
        UIManager.put("Button.default.background",ACCENT); UIManager.put("Button.default.foreground",TEXT);
        UIManager.put("Button.margin",new Insets(7,12,7,12));
        UIManager.put("Table.showHorizontalLines",false); UIManager.put("Table.showVerticalLines",false);
        UIManager.put("TableHeader.height",36); UIManager.put("ScrollBar.width",12);
        for (String key : new String[]{"Label.font","Button.font","TextField.font","PasswordField.font","ComboBox.font","Table.font","TableHeader.font","TabbedPane.font","CheckBox.font","TextArea.font"}) UIManager.put(key,font);
        for (String key : new String[]{"Panel.background","ScrollPane.background","Viewport.background","TabbedPane.background","OptionPane.background","CheckBox.background"}) UIManager.put(key,BACKGROUND);
        for (String key : new String[]{"Label.foreground","TabbedPane.foreground","CheckBox.foreground","OptionPane.messageForeground"}) UIManager.put(key,TEXT);
        for (String key : new String[]{"TextField.background","PasswordField.background","TextArea.background","ComboBox.background","Table.background","TableHeader.background"}) UIManager.put(key,PANEL);
        for (String key : new String[]{"TextField.foreground","PasswordField.foreground","TextArea.foreground","ComboBox.foreground","Table.foreground","TableHeader.foreground"}) UIManager.put(key,TEXT);
        UIManager.put("TextField.caretForeground",TEXT); UIManager.put("PasswordField.caretForeground",TEXT);
        UIManager.put("Table.selectionBackground",ACCENT); UIManager.put("Table.selectionForeground",Color.WHITE);
        UIManager.put("TabbedPane.selected",PANEL);
    }
    static JButton button(String title) {
        JButton button = new JButton(title); button.putClientProperty("html.disable",true);
        return button;
    }
    static JButton primary(String title) { JButton button = button(title); button.putClientProperty("JButton.buttonType","default"); button.setBackground(ACCENT); button.setForeground(TEXT); return button; }
    static int scale(int value) { return com.formdev.flatlaf.util.UIScale.scale(value); }
    static javax.swing.border.Border padding(int value) { int n = scale(value); return BorderFactory.createEmptyBorder(n,n,n,n); }
    static JLabel muted(String text) { JLabel label = label(text); label.setForeground(MUTED); return label; }
    static JPanel surface(LayoutManager layout) {
        JPanel panel = new JPanel(layout) {
            @Override protected void paintComponent(Graphics g) { Graphics2D copy = (Graphics2D)g.create(); try { copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON); copy.setColor(PANEL); copy.fillRoundRect(0,0,getWidth(),getHeight(),scale(8),scale(8)); } finally { copy.dispose(); } }
        }; panel.setOpaque(false); panel.setBorder(padding(12)); return panel;
    }
    static JScrollPane scroll(Component view) { JScrollPane scroll = new JScrollPane(view); scroll.setBorder(BorderFactory.createEmptyBorder()); scroll.getVerticalScrollBar().setUnitIncrement(scale(24)); return scroll; }
    static JLabel label(String title) { JLabel label = new JLabel(title); label.putClientProperty("html.disable",true); return label; }
    static JLabel heading(String title) { JLabel label = label(title); label.setFont(label.getFont().deriveFont(Font.BOLD,com.formdev.flatlaf.util.UIScale.scale(22f))); return label; }
    static JPanel row(Component... components) {
        JPanel row = new JPanel(new WrapLayout());
        for (Component component : components) row.add(component);
        return row;
    }
    static String money(BigDecimal value) { return value.setScale(2).toPlainString() + " KZT"; }
    static String date(String value) {
        try { return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").format(Instant.parse(value).atZone(ZoneId.systemDefault())); }
        catch (RuntimeException e) { return value; }
    }
    static String status(String value) {
        return switch (value) {
            case "PAID","SUCCEEDED" -> "Оплачено (демо)";
            case "DECLINED" -> "Отклонено: недостаточно средств";
            case "IMPORTED" -> "Импорт: дата и цена восстановлены";
            case "PENDING" -> "Ожидает оплаты";
            default -> value;
        };
    }
    static void message(Component owner,String text) {
        JTextArea area = new JTextArea(text,7,38); area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true); area.setCaretPosition(0); area.setMargin(new Insets(10,10,10,10));
        JOptionPane.showMessageDialog(owner,new JScrollPane(area),"Game Store",JOptionPane.INFORMATION_MESSAGE);
    }
    static boolean uncertain(ClientException e) { return java.util.Set.of("NETWORK","DATABASE","UNAUTHORIZED").contains(e.code()); }
    static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns,0) { @Override public boolean isCellEditable(int row,int col) { return false; } };
    }
    static JTable table(DefaultTableModel model) {
        JTable table = new JTable(model); table.setRowHeight(scale(38)); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true); table.setGridColor(new Color(0x333840)); table.setShowGrid(false); table.getTableHeader().setReorderingAllowed(false);
        var renderer = new javax.swing.table.DefaultTableCellRenderer(); renderer.putClientProperty("html.disable",true);
        table.setDefaultRenderer(Object.class,renderer); return table;
    }
    /** Decode and resize on a worker thread; resources work inside the packaged JAR. */
    static ImageIcon cover(String name) { return cover(name,240,145); }
    static ImageIcon cover(String name,int targetWidth,int targetHeight) {
        BufferedImage original = null;
        if (name != null && !name.isEmpty() && !name.contains("/") && !name.contains("\\")) {
            try (InputStream in = Ui.class.getResourceAsStream("/images/" + name)) {
                if (in != null) original = ImageIO.read(in);
            } catch (IOException | RuntimeException ignored) {}
        }
        BufferedImage result = new BufferedImage(targetWidth,targetHeight,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setColor(BACKGROUND); graphics.fillRect(0,0,targetWidth,targetHeight);
            if (original != null) {
                double scale = Math.min((double)targetWidth / original.getWidth(),(double)targetHeight / original.getHeight());
                int width = (int)(original.getWidth() * scale), height = (int)(original.getHeight() * scale);
                graphics.drawImage(original,(targetWidth - width) / 2,(targetHeight - height) / 2,width,height,null);
            } else {
                graphics.setColor(MUTED); graphics.setFont(new Font("Segoe UI",Font.PLAIN,Math.max(9,Math.min(13,targetWidth / 13))));
                String text = targetWidth < 100 ? "Нет обложки" : "Обложка отсутствует";
                graphics.drawString(text,Math.max(0,(targetWidth - graphics.getFontMetrics().stringWidth(text)) / 2),targetHeight / 2);
            }
        } finally { graphics.dispose(); }
        return new ImageIcon(result);
    }
}
