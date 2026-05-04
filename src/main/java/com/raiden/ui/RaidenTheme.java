package com.raiden.ui;

import com.raiden.model.ChargingPortState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

public final class RaidenTheme {

  private RaidenTheme() {}

  public static final Color COLOR_CANVAS = new Color(0xE9EEE9);
  public static final Color COLOR_SURFACE = new Color(0xFFFDF7);
  public static final Color COLOR_SURFACE_ALT = new Color(0xEEF4EF);
  public static final Color COLOR_BORDER = new Color(0xC7D1C8);
  public static final Color COLOR_TEXT = new Color(0x1E2723);
  public static final Color COLOR_MUTED = new Color(0x65736E);
  public static final Color COLOR_ACCENT = new Color(0x007C70);
  public static final Color COLOR_ACCENT_SOFT = new Color(0xD8EEE8);
  public static final Color COLOR_WARNING = new Color(0xB45432);
  public static final Color COLOR_WARNING_SOFT = new Color(0xF6E0D6);
  public static final Color COLOR_PENDING = new Color(0x5A7D8A);
  public static final Color COLOR_PENDING_SOFT = new Color(0xDEE8ED);
  public static final Color COLOR_IDLE = new Color(0x7B877F);
  public static final Color COLOR_ROW_ALT = new Color(0xF7FAF6);
  public static final Color COLOR_SELECTION = new Color(0xDCEBE4);
  public static final Color COLOR_LOG_BG = new Color(0xF8F6EE);

  public static final int SIDEBAR_WIDTH = 300;
  public static final int INSPECTOR_WIDTH = 280;
  public static final int MAX_LOG_LINES = 500;

  public static void applyCardStyle(@NotNull JPanel panel) {
    panel.setBackground(COLOR_SURFACE);
    panel.setBorder(BorderFactory.createCompoundBorder(
        new LineBorder(COLOR_BORDER, 1, true),
        new EmptyBorder(12, 12, 12, 12)
    ));
  }

  @NotNull
  public static JPanel createCardPanel() {
    JPanel panel = new JPanel();
    applyCardStyle(panel);
    return panel;
  }

  @NotNull
  public static JPanel createCardHeader(@NotNull String title) {
    return createCardHeader(title, null);
  }

  @NotNull
  public static JPanel createCardHeader(@NotNull String title, @Nullable JComponent accessory) {
    JPanel header = new JPanel(new BorderLayout(10, 0));
    header.setOpaque(false);

    JLabel titleLabel = new JLabel(title);
    titleLabel.setFont(MainFrameFonts.bold(13f));
    titleLabel.setForeground(COLOR_TEXT);

    header.add(titleLabel, BorderLayout.CENTER);
    if (accessory != null) {
      header.add(accessory, BorderLayout.EAST);
    }
    return header;
  }

  @NotNull
  public static JPanel createFieldBlock(@NotNull String title, @NotNull JComponent component) {
    JPanel block = new JPanel(new BorderLayout(0, 6));
    block.setOpaque(false);

    JLabel titleLabel = new JLabel(title);
    titleLabel.setFont(MainFrameFonts.bold(11f));
    titleLabel.setForeground(COLOR_MUTED);

    block.add(titleLabel, BorderLayout.NORTH);
    block.add(component, BorderLayout.CENTER);
    block.setMaximumSize(new Dimension(Integer.MAX_VALUE, block.getPreferredSize().height));
    return block;
  }

  @NotNull
  public static JLabel createBadgeLabel() {
    JLabel label = new JLabel();
    label.setFont(MainFrameFonts.bold(11f));
    label.setOpaque(true);
    label.setBorder(new EmptyBorder(5, 8, 5, 8));
    return label;
  }

  @NotNull
  public static JLabel createInspectorValueLabel() {
    JLabel label = new JLabel();
    label.setFont(MainFrameFonts.bold(16f));
    label.setForeground(COLOR_TEXT);
    return label;
  }

  @NotNull
  public static JLabel createHintLabel() {
    JLabel label = new JLabel("<html>请选择表格中的端口，查看状态和可用操作。</html>");
    label.setFont(MainFrameFonts.plain(11f));
    label.setForeground(COLOR_MUTED);
    label.setVerticalAlignment(SwingConstants.TOP);
    return label;
  }

  public static void styleInput(@NotNull JTextField field) {
    field.setFont(MainFrameFonts.plain(12f));
    field.setForeground(COLOR_TEXT);
    field.setBackground(COLOR_SURFACE_ALT);
    field.setCaretColor(COLOR_ACCENT);
    field.setPreferredSize(new Dimension(0, 32));
    field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
    field.setBorder(BorderFactory.createCompoundBorder(
        new LineBorder(COLOR_BORDER, 1, true),
        new EmptyBorder(0, 8, 0, 8)
    ));
  }

  public static void styleButton(@NotNull JButton button,
                                 @NotNull Color background,
                                 @NotNull Color foreground,
                                 @NotNull Color borderColor) {
    button.setFont(MainFrameFonts.bold(12f));
    button.setForeground(foreground);
    button.setBackground(background);
    button.setOpaque(true);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.setFocusPainted(false);
    button.setBorder(new LineBorder(borderColor, 1, true));
    button.setMargin(new Insets(8, 10, 8, 10));
    button.setPreferredSize(new Dimension(0, 34));
  }

  @NotNull
  public static Color resolveStatusColor(@NotNull ChargingPortState state) {
    if (state == ChargingPortState.CHARGING) return COLOR_ACCENT;
    if (state == ChargingPortState.STOPPED) return COLOR_WARNING;
    return COLOR_IDLE;
  }
}
