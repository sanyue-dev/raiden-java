package com.raiden.ui;

import com.raiden.application.ChargingApplicationListener;
import com.raiden.domain.ChargingPortSnapshot;
import com.raiden.domain.ChargingStation;
import com.raiden.infrastructure.mqtt.MqttService;
import com.raiden.platform.Disposable;
import com.raiden.platform.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class MainFrame extends JFrame implements ChargingApplicationListener, MqttService.MqttConnectionListener {

  @NotNull
  private final ChargingStation myStation;
  @NotNull
  private final PortTableModel myTableModel;
  @NotNull
  private final Disposable myFrameDisposable;
  @Nullable
  private volatile MqttService myMqttService;
  @Nullable
  private volatile Thread myConnectThread;

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

    myLogPanel = new LogPanel();
    myPortsPanel = new PortsPanel(myTableModel);
    myInspectorPanel = new InspectorPanel(myPortsPanel);
    mySessionPanel = new SessionPanel();

    wirePanels();
    layoutComponents();

    initPorts(mySessionPanel.getPortCount());
    mySessionPanel.updateConnectionBadge(false);
    myInspectorPanel.updateFromSelection();
  }

  private void wirePanels() {
    myPortsPanel.setSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        myInspectorPanel.updateFromSelection();
      }
    });

    mySessionPanel.getConnectButton().addActionListener(e -> {
      if (myConnectThread != null && myConnectThread.isAlive()) {
        onCancelConnect();
      }
      else {
        onConnect();
      }
    });

    mySessionPanel.getDisconnectButton().addActionListener(e -> onDisconnect());

    mySessionPanel.getPortCountSpinner().addChangeListener(e -> {
      if (myMqttService == null) {
        initPorts(mySessionPanel.getPortCount());
      }
    });

    myInspectorPanel.setCloseOrderAction(portNumber -> {
      MqttService service = myMqttService;
      if (service == null) {
        myLogPanel.appendLog("请先连接服务器");
        return;
      }
      service.manualClose(portNumber);
    });
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

    int portCount = mySessionPanel.getPortCount();
    initPorts(portCount);

    mySessionPanel.getConnectButton().setText("取消");
    myLogPanel.appendLog("正在连接 " + broker);

    Thread connectThread = new Thread(() -> {
      MqttService service = new MqttService(broker, clientId, myStation, this, this);
      myMqttService = service;

      try {
        service.connect();
      }
      catch (Exception ex) {
        service.closeForcibly();
        if (myConnectThread == Thread.currentThread()) {
          myMqttService = null;
          SwingUtilities.invokeLater(() -> {
            myLogPanel.appendLog("连接失败：" + ex.getMessage());
            if (ex.getCause() != null) {
              myLogPanel.appendLog("  原因：" + ex.getCause().getMessage());
            }
            mySessionPanel.getConnectButton().setText("连接");
            myInspectorPanel.updateFromSelection();
          });
        }
        return;
      }

      if (myConnectThread != Thread.currentThread()) {
        service.closeForcibly();
        return;
      }

      Disposer.register(myFrameDisposable, service);
    }, "mqtt-connect-thread");

    myConnectThread = connectThread;
    connectThread.start();
  }

  private void onDisconnect() {
    MqttService service = myMqttService;
    myMqttService = null;
    if (service != null) {
      service.disconnect();
      Disposer.dispose(service);
    }
    onConnectionStateChanged(false);
    myLogPanel.appendLog("已断开连接");
  }

  private void onCancelConnect() {
    myConnectThread = null;
    myMqttService = null;
    myLogPanel.appendLog("已取消连接");
    mySessionPanel.getConnectButton().setText("连接");
    mySessionPanel.updateConnectionBadge(false);
    myInspectorPanel.updateFromSelection();
  }

  private void onConnectionStateChanged(boolean connected) {
    SwingUtilities.invokeLater(() -> {
      mySessionPanel.onConnectionStateChanged(connected);
      myInspectorPanel.onConnectionStateChanged(connected);
    });
  }

  @Override
  public void onApplicationLog(@NotNull String message) {
    myLogPanel.appendLog(message);
  }

  @Override
  public void onPortsChanged() {
    myPortsPanel.refreshTable();
    SwingUtilities.invokeLater(() -> myInspectorPanel.updateFromSelection());
  }

  @Override
  public void onMqttConnected(@NotNull String brokerUrl, @NotNull String clientId) {
    if (myConnectThread != Thread.currentThread()) return;
    myLogPanel.appendLog("已连接到 " + brokerUrl + "，客户端 " + clientId);
    onConnectionStateChanged(true);
  }

  @Override
  public void onMqttDisconnected(@Nullable Throwable cause) {
    myMqttService = null;
    onConnectionStateChanged(false);
  }

  @Override
  public void onMqttLog(@NotNull String message) {
    myLogPanel.appendLog(message);
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
    constraints.insets = new Insets(0, 10, 0, 0);
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
