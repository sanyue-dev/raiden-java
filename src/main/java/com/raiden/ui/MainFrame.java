package com.raiden.ui;

import com.raiden.domain.ChargingPortSnapshot;
import com.raiden.domain.ChargingStation;
import com.raiden.mqtt.ChargingApplicationListener;
import com.raiden.mqtt.ChargingApplicationService;
import com.raiden.mqtt.ConnectionListener;
import com.raiden.mqtt.MqttService;
import com.raiden.mqtt.RaidenProtocolCodec;
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
    onConnectionStateChanged(ConnectionState.DISCONNECTED);
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

    onConnectionStateChanged(ConnectionState.CONNECTING);
    myLogPanel.appendLog("正在连接 " + broker);

    Thread connectThread = new Thread(() -> {
      ChargingApplicationService appService = new ChargingApplicationService(myStation, new RaidenProtocolCodec(), MainFrame.this);
      MqttService service = new MqttService(broker, clientId, appService, MainFrame.this);
      appService.setMessagePublisher(service);

      try {
        service.connect();
      }
      catch (Exception ex) {
        service.closeForcibly();
        if (myConnectThread == Thread.currentThread()) {
          SwingUtilities.invokeLater(() -> {
            myLogPanel.appendLog("连接失败：" + ex.getMessage());
            if (ex.getCause() != null) {
              myLogPanel.appendLog("  原因：" + ex.getCause().getMessage());
            }
            onConnectionStateChanged(ConnectionState.DISCONNECTED);
          });
        }
        return;
      }

      if (myConnectThread != Thread.currentThread()) {
        service.closeForcibly();
        return;
      }

      myMqttService = service;
      Disposer.register(myFrameDisposable, service);
    }, "mqtt-connect-thread");

    myConnectThread = connectThread;
    connectThread.start();
  }

  private void onDisconnect() {
    MqttService service = myMqttService;
    myMqttService = null;
    if (service != null) {
      onConnectionStateChanged(ConnectionState.DISCONNECTING);
      myLogPanel.appendLog("正在断开连接...");
      Thread t = new Thread(() -> {
        service.disconnect();
        Disposer.dispose(service);
        onConnectionStateChanged(ConnectionState.DISCONNECTED);
        myLogPanel.appendLog("已断开连接");
      }, "mqtt-disconnect-thread");
      t.setDaemon(true);
      t.start();
    }
    else {
      onConnectionStateChanged(ConnectionState.DISCONNECTED);
    }
  }

  private void onCancelConnect() {
    myConnectThread = null;
    myLogPanel.appendLog("已取消连接");
    onConnectionStateChanged(ConnectionState.DISCONNECTED);
  }

  private void onConnectionStateChanged(@NotNull ConnectionState state) {
    if (SwingUtilities.isEventDispatchThread()) {
      mySessionPanel.onConnectionStateChanged(state);
      myInspectorPanel.onConnectionStateChanged(state);
    }
    else {
      SwingUtilities.invokeLater(() -> {
        mySessionPanel.onConnectionStateChanged(state);
        myInspectorPanel.onConnectionStateChanged(state);
      });
    }
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
    onConnectionStateChanged(ConnectionState.CONNECTED);
  }

  @Override
  public void onMqttDisconnected(@Nullable Throwable cause) {
    myMqttService = null;
    onConnectionStateChanged(ConnectionState.DISCONNECTED);
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
