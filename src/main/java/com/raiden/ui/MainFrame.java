package com.raiden.ui;

import com.raiden.application.ChargingApplicationListener;
import com.raiden.model.ChargingPortSnapshot;
import com.raiden.model.ChargingStation;
import com.raiden.mqtt.ConnectionListener;
import com.raiden.mqtt.MqttConnectionController;
import com.raiden.mqtt.MqttConnectionStatus;
import com.raiden.platform.Disposable;
import com.raiden.platform.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class MainFrame extends JFrame implements ChargingApplicationListener, ConnectionListener {

  @NotNull
  private final ChargingStation myStation;
  @NotNull
  private final PortTableModel myTableModel;
  @NotNull
  private final Disposable myFrameDisposable;
  @NotNull
  private final MqttConnectionController myConnectionController;
  @NotNull
  private ConnectionState myConnectionState = ConnectionState.DISCONNECTED;

  @NotNull
  private final SessionPanel mySessionPanel;
  @NotNull
  private final PortsPanel myPortsPanel;
  @NotNull
  private final InspectorPanel myInspectorPanel;
  @NotNull
  private final LogPanel myLogPanel;

  public MainFrame() {
    super("Raiden 模拟器");
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent e) {
        Disposer.dispose(myFrameDisposable);
        dispose();
        System.exit(0);
      }
    });
    setMinimumSize(new Dimension(1080, 640));
    setSize(1240, 760);
    setLocationRelativeTo(null);

    myStation = new ChargingStation();
    myTableModel = new PortTableModel(myStation);
    myFrameDisposable = Disposer.newDisposable("MainFrame");
    myConnectionController = new MqttConnectionController(myStation, myFrameDisposable, this, this);

    myLogPanel = new LogPanel();
    myPortsPanel = new PortsPanel(myTableModel);
    myInspectorPanel = new InspectorPanel(myPortsPanel);
    mySessionPanel = new SessionPanel();

    wirePanels();
    layoutComponents();

    initPorts(mySessionPanel.getPortCount());
    onConnectionStateChanged(ConnectionState.DISCONNECTED);
  }

  private void wirePanels() {
    myPortsPanel.setSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        myInspectorPanel.updateFromSelection();
      }
    });

    mySessionPanel.getConnectButton().addActionListener(e -> {
      if (myConnectionState == ConnectionState.CONNECTING) {
        onCancelConnect();
      }
      else {
        onConnect();
      }
    });

    mySessionPanel.getDisconnectButton().addActionListener(e -> onDisconnect());

    mySessionPanel.getPortCountSpinner().addChangeListener(e -> {
      if (myConnectionState == ConnectionState.DISCONNECTED) {
        initPorts(mySessionPanel.getPortCount());
      }
    });

    myInspectorPanel.setCloseOrderAction(portNumber -> myConnectionController.manualClose(portNumber));
  }

  private void initPorts(int count) {
    myStation.resetPorts(count);
    myTableModel.fireDataChanged();
    myPortsPanel.clearSelection();
    myInspectorPanel.updateFromSelection();
  }

  private void onConnect() {
    String broker = mySessionPanel.getBrokerText();
    String clientId = mySessionPanel.getClientIdText();
    if (broker.isEmpty() || clientId.isEmpty()) {
      myLogPanel.appendLog("服务器和客户端不能为空");
      return;
    }

    if (myConnectionController.connect(broker, clientId)) {
      myPortsPanel.clearSelection();
      myInspectorPanel.updateFromSelection();
    }
  }

  private void onDisconnect() {
    myConnectionController.disconnect();
  }

  private void onCancelConnect() {
    myConnectionController.cancelConnect();
  }

  @Override
  public void onMqttConnectionStatusChanged(@NotNull MqttConnectionStatus status, @Nullable Throwable cause) {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> onMqttConnectionStatusChanged(status, cause));
      return;
    }

    switch (status) {
      case CONNECTING:
        onConnectionStateChanged(ConnectionState.CONNECTING);
        break;
      case CONNECTED:
        onConnectionStateChanged(ConnectionState.CONNECTED);
        break;
      case DISCONNECTING:
        onConnectionStateChanged(ConnectionState.DISCONNECTING);
        break;
      case DISCONNECTED:
      case CONNECTION_FAILED:
      case CONNECTION_LOST:
        onConnectionStateChanged(ConnectionState.DISCONNECTED);
        break;
    }
  }

  @Override
  public void onMqttLog(@NotNull String message) {
    myLogPanel.appendLog(message);
  }

  private void onConnectionStateChanged(@NotNull ConnectionState state) {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> onConnectionStateChanged(state));
      return;
    }
    myConnectionState = state;
    mySessionPanel.onConnectionStateChanged(state);
    myInspectorPanel.onConnectionStateChanged(state);
  }

  @Override
  public void onApplicationLog(@NotNull String message) {
    myLogPanel.appendLog("APP      " + message);
  }

  @Override
  public void onPortsChanged() {
    myPortsPanel.refreshTable();
    SwingUtilities.invokeLater(() -> myInspectorPanel.updateFromSelection());
  }

  private void layoutComponents() {
    JPanel contentPane = new JPanel(new BorderLayout(14, 14));
    contentPane.setBackground(RaidenTheme.COLOR_CANVAS);
    contentPane.setBorder(new EmptyBorder(14, 14, 14, 14));
    contentPane.add(createWorkspacePanel(), BorderLayout.CENTER);
    setContentPane(contentPane);
  }

  @NotNull
  private JComponent createWorkspacePanel() {
    JPanel workspace = new JPanel(new BorderLayout(0, 10));
    workspace.setOpaque(false);
    workspace.add(createTopRowPanel(), BorderLayout.CENTER);
    workspace.add(myLogPanel, BorderLayout.SOUTH);
    return workspace;
  }

  @NotNull
  private JComponent createTopRowPanel() {
    JPanel topRow = new JPanel(new GridBagLayout());
    topRow.setOpaque(false);

    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridy = 0;
    constraints.fill = GridBagConstraints.BOTH;
    constraints.anchor = GridBagConstraints.NORTH;
    constraints.weighty = 1;
    constraints.insets = new Insets(0, 0, 0, 10);

    constraints.gridx = 0;
    constraints.weightx = 0;
    topRow.add(createFixedWidthPanel(mySessionPanel, RaidenTheme.SIDEBAR_WIDTH), constraints);

    constraints.gridx = 1;
    constraints.weightx = 1;
    topRow.add(myPortsPanel, constraints);

    constraints.gridx = 2;
    constraints.weightx = 0;
    constraints.insets = new Insets(0, 0, 0, 0);
    topRow.add(createFixedWidthPanel(myInspectorPanel, RaidenTheme.INSPECTOR_WIDTH), constraints);

    return topRow;
  }

  @NotNull
  private JComponent createFixedWidthPanel(@NotNull JComponent component, int width) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setOpaque(false);
    panel.setPreferredSize(new Dimension(width, component.getPreferredSize().height));
    panel.setMinimumSize(new Dimension(width, 0));
    panel.add(component, BorderLayout.CENTER);
    return panel;
  }

}
