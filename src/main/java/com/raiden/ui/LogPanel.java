package com.raiden.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import java.awt.*;

public final class LogPanel extends JPanel {

  @NotNull
  private final JTextArea myLogArea;

  public LogPanel() {
    setLayout(new BorderLayout(0, 8));
    RaidenTheme.applyCardStyle(this);
    setPreferredSize(new Dimension(0, 220));
    add(RaidenTheme.createCardHeader("事件日志", createClearButton()), BorderLayout.NORTH);

    myLogArea = new JTextArea(9, 50);
    myLogArea.setEditable(false);
    myLogArea.setLineWrap(false);
    myLogArea.setWrapStyleWord(false);
    myLogArea.setFont(MainFrameFonts.plain(11f));
    myLogArea.setForeground(RaidenTheme.COLOR_TEXT);
    myLogArea.setBackground(RaidenTheme.COLOR_LOG_BG);
    myLogArea.setCaretColor(RaidenTheme.COLOR_ACCENT);
    myLogArea.setBorder(new EmptyBorder(8, 10, 8, 10));

    JScrollPane logScroll = new JScrollPane(myLogArea);
    logScroll.setPreferredSize(new Dimension(0, 160));
    logScroll.setBorder(new LineBorder(RaidenTheme.COLOR_BORDER, 1, true));
    logScroll.getViewport().setBackground(RaidenTheme.COLOR_LOG_BG);

    add(logScroll, BorderLayout.CENTER);
  }

  @NotNull
  private JButton createClearButton() {
    JButton button = new JButton("清空");
    button.setFont(MainFrameFonts.bold(11f));
    button.setForeground(RaidenTheme.COLOR_MUTED);
    button.setBackground(RaidenTheme.COLOR_SURFACE_ALT);
    button.setOpaque(true);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.setFocusPainted(false);
    button.setBorder(new LineBorder(RaidenTheme.COLOR_BORDER, 1, true));
    button.setMargin(new Insets(4, 10, 4, 10));
    button.setPreferredSize(new Dimension(62, 26));
    button.addActionListener(e -> clearLog());
    return button;
  }

  public void appendLog(@NotNull String message) {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> appendLog(message));
      return;
    }
    String timestamp = java.time.LocalTime.now().withNano(0).toString();
    myLogArea.append("[" + timestamp + "] " + message + "\n");
    trimLogArea();
    myLogArea.setCaretPosition(myLogArea.getDocument().getLength());
  }

  private void clearLog() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(this::clearLog);
      return;
    }
    myLogArea.setText("");
  }

  private void trimLogArea() {
    Document document = myLogArea.getDocument();
    Element root = document.getDefaultRootElement();
    while (root.getElementCount() > RaidenTheme.MAX_LOG_LINES + 1) {
      Element firstLine = root.getElement(0);
      try {
        document.remove(0, firstLine.getEndOffset());
      }
      catch (BadLocationException e) {
        throw new IllegalStateException("日志裁剪失败", e);
      }
    }
  }
}
