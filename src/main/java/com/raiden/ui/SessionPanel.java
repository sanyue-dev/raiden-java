package com.raiden.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

public final class SessionPanel extends JPanel {

  @NotNull
  private final JTextField myBrokerField;
  @NotNull
  private final JTextField myClientIdField;
  @NotNull
  private final JSpinner myPortCountSpinner;
  @NotNull
  private final JButton myConnectButton;
  @NotNull
  private final JButton myDisconnectButton;
  @NotNull
  private final JLabel myConnectionBadge;

  public SessionPanel() {
    setLayout(new BorderLayout(0, 12));
    RaidenTheme.applyCardStyle(this);
    setMaximumSize(new Dimension(RaidenTheme.SIDEBAR_WIDTH, Integer.MAX_VALUE));

    myConnectionBadge = RaidenTheme.createBadgeLabel();
    add(RaidenTheme.createCardHeader("会话", myConnectionBadge), BorderLayout.NORTH);

    myBrokerField = new JTextField("tcp://61.145.180.130:23110", 25);
    myClientIdField = new JTextField("1000000520", 12);
    myPortCountSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 12, 1));
    RaidenTheme.styleInput(myBrokerField);
    RaidenTheme.styleInput(myClientIdField);
    configureSpinner();

    JPanel formPanel = new JPanel();
    formPanel.setOpaque(false);
    formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
    formPanel.add(RaidenTheme.createFieldBlock("服务器", myBrokerField));
    formPanel.add(Box.createVerticalStrut(10));
    formPanel.add(RaidenTheme.createFieldBlock("客户端", myClientIdField));
    formPanel.add(Box.createVerticalStrut(10));
    formPanel.add(RaidenTheme.createFieldBlock("端口数量", myPortCountSpinner));

    myConnectButton = new JButton("连接");
    myDisconnectButton = new JButton("断开");
    myDisconnectButton.setEnabled(false);
    RaidenTheme.styleButton(myConnectButton, RaidenTheme.COLOR_ACCENT, Color.WHITE, RaidenTheme.COLOR_ACCENT);
    RaidenTheme.styleButton(myDisconnectButton, RaidenTheme.COLOR_SURFACE_ALT, RaidenTheme.COLOR_TEXT, RaidenTheme.COLOR_BORDER);

    JPanel actionPanel = new JPanel(new GridLayout(1, 2, 8, 0));
    actionPanel.setOpaque(false);
    actionPanel.add(myConnectButton);
    actionPanel.add(myDisconnectButton);

    add(formPanel, BorderLayout.CENTER);
    add(actionPanel, BorderLayout.SOUTH);
  }

  private void configureSpinner() {
    myPortCountSpinner.setFont(MainFrameFonts.bold(14f));
    myPortCountSpinner.setPreferredSize(new Dimension(92, 34));
    myPortCountSpinner.setMaximumSize(new Dimension(92, 34));
    JComponent editor = myPortCountSpinner.getEditor();
    if (editor instanceof JSpinner.DefaultEditor) {
      JSpinner.DefaultEditor defaultEditor = (JSpinner.DefaultEditor) editor;
      defaultEditor.getTextField().setHorizontalAlignment(JTextField.CENTER);
      defaultEditor.getTextField().setBorder(new EmptyBorder(0, 0, 0, 0));
      defaultEditor.getTextField().setBackground(RaidenTheme.COLOR_SURFACE_ALT);
      defaultEditor.getTextField().setForeground(RaidenTheme.COLOR_TEXT);
      defaultEditor.getTextField().setCaretColor(RaidenTheme.COLOR_ACCENT);
      defaultEditor.getTextField().setFont(MainFrameFonts.bold(14f));
    }
    myPortCountSpinner.setBorder(new LineBorder(RaidenTheme.COLOR_BORDER, 1, true));
  }

  @NotNull
  public JButton getConnectButton() { return myConnectButton; }

  @NotNull
  public JButton getDisconnectButton() { return myDisconnectButton; }

  @NotNull
  public JSpinner getPortCountSpinner() { return myPortCountSpinner; }

  @NotNull
  public String getBrokerText() { return myBrokerField.getText().trim(); }

  @NotNull
  public String getClientIdText() { return myClientIdField.getText().trim(); }

  public int getPortCount() { return (Integer) myPortCountSpinner.getValue(); }

  public void onConnectionStateChanged(boolean connected) {
    SwingUtilities.invokeLater(() -> {
      updateConnectionBadge(connected);
      myConnectButton.setText("连接");
      myConnectButton.setEnabled(!connected);
      myDisconnectButton.setEnabled(connected);
      myBrokerField.setEnabled(!connected);
      myClientIdField.setEnabled(!connected);
      myPortCountSpinner.setEnabled(!connected);
    });
  }

  public void updateConnectionBadge(boolean connected) {
    myConnectionBadge.setText(connected ? "在线" : "离线");
    myConnectionBadge.setBackground(connected ? RaidenTheme.COLOR_ACCENT_SOFT : RaidenTheme.COLOR_WARNING_SOFT);
    myConnectionBadge.setForeground(connected ? RaidenTheme.COLOR_ACCENT : RaidenTheme.COLOR_WARNING);
    myConnectionBadge.setBorder(BorderFactory.createCompoundBorder(
        new LineBorder(connected ? RaidenTheme.COLOR_ACCENT : RaidenTheme.COLOR_WARNING, 1, true),
        new EmptyBorder(5, 8, 5, 8)
    ));
  }
}
