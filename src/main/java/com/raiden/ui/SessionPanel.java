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

  public void onConnectionStateChanged(@NotNull ConnectionState state) {
    updateConnectionBadge(state);
    switch (state) {
      case DISCONNECTED:
        myConnectButton.setText("连接");
        myConnectButton.setEnabled(true);
        myDisconnectButton.setEnabled(false);
        myBrokerField.setEnabled(true);
        myClientIdField.setEnabled(true);
        myPortCountSpinner.setEnabled(true);
        break;
      case CONNECTING:
        myConnectButton.setText("取消");
        myConnectButton.setEnabled(true);
        myDisconnectButton.setEnabled(false);
        myBrokerField.setEnabled(false);
        myClientIdField.setEnabled(false);
        myPortCountSpinner.setEnabled(false);
        break;
      case CONNECTED:
        myConnectButton.setText("连接");
        myConnectButton.setEnabled(false);
        myDisconnectButton.setEnabled(true);
        myBrokerField.setEnabled(false);
        myClientIdField.setEnabled(false);
        myPortCountSpinner.setEnabled(false);
        break;
      case DISCONNECTING:
        myConnectButton.setText("连接");
        myConnectButton.setEnabled(false);
        myDisconnectButton.setEnabled(false);
        myBrokerField.setEnabled(false);
        myClientIdField.setEnabled(false);
        myPortCountSpinner.setEnabled(false);
        break;
    }
  }

  public void updateConnectionBadge(@NotNull ConnectionState state) {
    Color fg, bg, border;
    switch (state) {
      case CONNECTED:
        fg = RaidenTheme.COLOR_ACCENT;
        bg = RaidenTheme.COLOR_ACCENT_SOFT;
        border = RaidenTheme.COLOR_ACCENT;
        break;
      default:
        fg = RaidenTheme.COLOR_WARNING;
        bg = RaidenTheme.COLOR_WARNING_SOFT;
        border = RaidenTheme.COLOR_WARNING;
        break;
    }
    myConnectionBadge.setText(state.getBadgeText());
    myConnectionBadge.setBackground(bg);
    myConnectionBadge.setForeground(fg);
    myConnectionBadge.setBorder(BorderFactory.createCompoundBorder(
        new LineBorder(border, 1, true),
        new EmptyBorder(5, 8, 5, 8)
    ));
  }
}
