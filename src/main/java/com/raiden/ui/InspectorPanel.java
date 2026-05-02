package com.raiden.ui;

import com.raiden.domain.ChargingPortSnapshot;
import com.raiden.domain.ChargingPortState;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.function.IntConsumer;

public final class InspectorPanel extends JPanel {

  @NotNull
  private final JLabel mySelectedPortValue;
  @NotNull
  private final JLabel mySelectedStatusValue;
  @NotNull
  private final JLabel mySelectedBalanceValue;
  @NotNull
  private final JLabel mySelectionHintLabel;
  @NotNull
  private final JButton myCloseOrderButton;
  @NotNull
  private final PortsPanel myPortsPanel;

  private boolean myConnected;
  @NotNull
  private IntConsumer myCloseOrderAction = portNum -> {};

  public InspectorPanel(@NotNull PortsPanel portsPanel) {
    myPortsPanel = portsPanel;
    myConnected = false;

    setLayout(new BorderLayout(0, 12));
    RaidenTheme.applyCardStyle(this);
    setMaximumSize(new Dimension(RaidenTheme.INSPECTOR_WIDTH, Integer.MAX_VALUE));
    add(RaidenTheme.createCardHeader("当前选择"), BorderLayout.NORTH);

    mySelectedPortValue = RaidenTheme.createInspectorValueLabel();
    mySelectedStatusValue = RaidenTheme.createInspectorValueLabel();
    mySelectedBalanceValue = RaidenTheme.createInspectorValueLabel();
    mySelectionHintLabel = RaidenTheme.createHintLabel();

    JPanel body = new JPanel();
    body.setOpaque(false);
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.add(RaidenTheme.createFieldBlock("端口", mySelectedPortValue));
    body.add(Box.createVerticalStrut(12));
    body.add(RaidenTheme.createFieldBlock("状态", mySelectedStatusValue));
    body.add(Box.createVerticalStrut(12));
    body.add(RaidenTheme.createFieldBlock("余额", mySelectedBalanceValue));
    body.add(Box.createVerticalStrut(16));
    body.add(createHintPanel());

    myCloseOrderButton = new JButton("结束订单");
    myCloseOrderButton.setEnabled(false);
    RaidenTheme.styleButton(myCloseOrderButton,
        RaidenTheme.COLOR_WARNING_SOFT, RaidenTheme.COLOR_WARNING, new Color(0xD9A08B));
    myCloseOrderButton.addActionListener(e -> {
      ChargingPortSnapshot snapshot = myPortsPanel.getSelectedPortSnapshot();
      if (snapshot != null) {
        myCloseOrderAction.accept(snapshot.getPortNumber());
      }
    });

    JPanel footer = new JPanel(new BorderLayout(0, 0));
    footer.setOpaque(false);
    footer.add(myCloseOrderButton, BorderLayout.CENTER);

    add(body, BorderLayout.CENTER);
    add(footer, BorderLayout.SOUTH);
  }

  public void setCloseOrderAction(@NotNull IntConsumer action) {
    myCloseOrderAction = action;
  }

  public void updateFromSelection() {
    ChargingPortSnapshot snapshot = myPortsPanel.getSelectedPortSnapshot();
    if (snapshot == null) {
      mySelectedPortValue.setText("未选择端口");
      mySelectedStatusValue.setText("—");
      mySelectedStatusValue.setForeground(RaidenTheme.COLOR_IDLE);
      mySelectedBalanceValue.setText("—");
      myCloseOrderButton.setEnabled(false);
      setSelectionHint("请选择表格中的端口，查看状态和可用操作。");
      return;
    }

    ChargingPortState state = snapshot.getState();

    mySelectedPortValue.setText("端口 " + snapshot.getPortNumber());
    mySelectedStatusValue.setText(ChargingPortPresentation.getStateLabel(state));
    mySelectedStatusValue.setForeground(RaidenTheme.resolveStatusColor(state));
    mySelectedBalanceValue.setText(ChargingPortPresentation.getBalanceText(snapshot));

    boolean canClose = myConnected && state == ChargingPortState.CHARGING;
    myCloseOrderButton.setEnabled(canClose);

    if (!myConnected) {
      setSelectionHint("请先连接服务器，再发送手动结束订单。");
    }
    else if (state == ChargingPortState.CHARGING) {
      setSelectionHint("该端口正在充电。点击\"结束订单\"会立即发送手动结束命令。");
    }
    else if (state == ChargingPortState.CLOSING) {
      setSelectionHint("该端口正在结束订单，请等待计费结束消息完成重置。");
    }
    else {
      setSelectionHint("只有\"充电中\"状态的端口可以手动结束订单。");
    }
  }

  public void onConnectionStateChanged(boolean connected) {
    myConnected = connected;
    updateFromSelection();
  }

  private void setSelectionHint(@NotNull String text) {
    mySelectionHintLabel.setText("<html>" + text + "</html>");
  }

  @NotNull
  private JComponent createHintPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setOpaque(true);
    panel.setBackground(RaidenTheme.COLOR_SURFACE_ALT);
    panel.setBorder(BorderFactory.createCompoundBorder(
        new LineBorder(RaidenTheme.COLOR_BORDER, 1, true),
        new EmptyBorder(10, 10, 10, 10)
    ));
    panel.add(mySelectionHintLabel, BorderLayout.CENTER);
    panel.setPreferredSize(new Dimension(0, 64));
    panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
    return panel;
  }
}
