package com.raiden.ui;

import com.raiden.application.ChargingApplicationListener;
import com.raiden.domain.ChargingPortSnapshot;
import com.raiden.domain.ChargingPortState;
import com.raiden.domain.ChargingStation;
import com.raiden.infrastructure.mqtt.MqttService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import java.awt.*;

public final class MainFrame extends JFrame implements ChargingApplicationListener, MqttService.MqttConnectionListener {

  private static final Color COLOR_CANVAS = new Color(0xE9EEE9);
  private static final Color COLOR_SURFACE = new Color(0xFFFDF7);
  private static final Color COLOR_SURFACE_ALT = new Color(0xEEF4EF);
  private static final Color COLOR_BORDER = new Color(0xC7D1C8);
  private static final Color COLOR_TEXT = new Color(0x1E2723);
  private static final Color COLOR_MUTED = new Color(0x65736E);
  private static final Color COLOR_ACCENT = new Color(0x007C70);
  private static final Color COLOR_ACCENT_SOFT = new Color(0xD8EEE8);
  private static final Color COLOR_WARNING = new Color(0xB45432);
  private static final Color COLOR_WARNING_SOFT = new Color(0xF6E0D6);
  private static final Color COLOR_IDLE = new Color(0x7B877F);
  private static final Color COLOR_ROW_ALT = new Color(0xF7FAF6);
  private static final Color COLOR_SELECTION = new Color(0xDCEBE4);
  private static final int SIDEBAR_WIDTH = 300;
  private static final int INSPECTOR_WIDTH = 280;
  private static final int MAX_LOG_LINES = 500;

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
  private final JTable myPortTable;
  @NotNull
  private final PortTableModel myTableModel;
  @NotNull
  private final JButton myCloseOrderButton;
  @NotNull
  private final JTextArea myLogArea;
  @NotNull
  private final JLabel myConnectionBadge;
  @NotNull
  private final JLabel mySelectedPortValue;
  @NotNull
  private final JLabel mySelectedStatusValue;
  @NotNull
  private final JLabel mySelectedBalanceValue;
  @NotNull
  private final JLabel mySelectionHintLabel;
  @NotNull
  private final ChargingStation myStation;
  @Nullable
  private volatile MqttService myMqttService;

  public MainFrame() {
    super("Raiden 模拟器");
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setMinimumSize(new Dimension(1080, 640));
    setSize(1240, 760);
    setLocationRelativeTo(null);

    myStation = new ChargingStation();
    myTableModel = new PortTableModel(myStation);
    myPortTable = new JTable(myTableModel);
    configureTable();

    myBrokerField = new JTextField("tcp://192.168.2.66:23110", 25);
    myClientIdField = new JTextField("1000000520", 12);
    myPortCountSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 12, 1));
    configureSpinner();

    myConnectButton = new JButton("连接");
    myDisconnectButton = new JButton("断开");
    myDisconnectButton.setEnabled(false);
    myCloseOrderButton = new JButton("结束订单");
    myCloseOrderButton.setEnabled(false);
    styleButtons();

    myLogArea = new JTextArea(9, 50);
    configureLogArea();

    myConnectionBadge = createBadgeLabel();
    mySelectedPortValue = createInspectorValueLabel();
    mySelectedStatusValue = createInspectorValueLabel();
    mySelectedBalanceValue = createInspectorValueLabel();
    mySelectionHintLabel = createHintLabel();

    styleInputs();
    initPorts((Integer) myPortCountSpinner.getValue());
    layoutComponents();
    bindActions();
    updateConnectionBadge(false);
    updateSelectionDetails();
  }

  private void configureTable() {
    myPortTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myPortTable.setRowHeight(34);
    myPortTable.setFillsViewportHeight(true);
    myPortTable.setShowGrid(false);
    myPortTable.setIntercellSpacing(new Dimension(0, 0));
    myPortTable.setFocusable(false);
    myPortTable.setDefaultEditor(Object.class, null);
    myPortTable.setFont(MainFrameFonts.plain(13f));
    myPortTable.setForeground(COLOR_TEXT);
    myPortTable.setBackground(COLOR_SURFACE);
    myPortTable.setSelectionBackground(COLOR_SELECTION);
    myPortTable.setSelectionForeground(COLOR_TEXT);
    myPortTable.setDefaultRenderer(Object.class, new PortTableCellRenderer());

    JTableHeader tableHeader = myPortTable.getTableHeader();
    tableHeader.setReorderingAllowed(false);
    tableHeader.setResizingAllowed(false);
    tableHeader.setPreferredSize(new Dimension(0, 30));
    tableHeader.setFont(MainFrameFonts.bold(11f));
    tableHeader.setForeground(COLOR_MUTED);
    tableHeader.setBackground(COLOR_SURFACE_ALT);
    tableHeader.setBorder(new LineBorder(COLOR_BORDER, 1));

    myPortTable.getColumnModel().getColumn(0).setPreferredWidth(72);
    myPortTable.getColumnModel().getColumn(1).setPreferredWidth(220);
    myPortTable.getColumnModel().getColumn(2).setPreferredWidth(112);
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
      defaultEditor.getTextField().setBackground(COLOR_SURFACE_ALT);
      defaultEditor.getTextField().setForeground(COLOR_TEXT);
      defaultEditor.getTextField().setCaretColor(COLOR_ACCENT);
      defaultEditor.getTextField().setFont(MainFrameFonts.bold(14f));
    }
    myPortCountSpinner.setBorder(new LineBorder(COLOR_BORDER, 1, true));
  }

  private void configureLogArea() {
    myLogArea.setEditable(false);
    myLogArea.setLineWrap(false);
    myLogArea.setWrapStyleWord(false);
    myLogArea.setFont(MainFrameFonts.plain(11f));
    myLogArea.setForeground(COLOR_TEXT);
    myLogArea.setBackground(new Color(0xF8F6EE));
    myLogArea.setCaretColor(COLOR_ACCENT);
    myLogArea.setBorder(new EmptyBorder(8, 10, 8, 10));
  }

  private void styleInputs() {
    styleInput(myBrokerField);
    styleInput(myClientIdField);
  }

  private void styleInput(@NotNull JTextField field) {
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

  private void styleButtons() {
    styleButton(myConnectButton, COLOR_ACCENT, Color.WHITE, COLOR_ACCENT);
    styleButton(myDisconnectButton, COLOR_SURFACE_ALT, COLOR_TEXT, COLOR_BORDER);
    styleButton(myCloseOrderButton, COLOR_WARNING_SOFT, COLOR_WARNING, new Color(0xD9A08B));
  }

  private void styleButton(@NotNull JButton button,
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

  private void initPorts(int count) {
    myStation.resetPorts(count);
    myTableModel.fireDataChanged();
    myPortTable.clearSelection();
    updateSelectionDetails();
  }

  private void layoutComponents() {
    JPanel contentPane = new JPanel(new BorderLayout(14, 14));
    contentPane.setBackground(COLOR_CANVAS);
    contentPane.setBorder(new EmptyBorder(14, 14, 14, 14));
    contentPane.add(createWorkspacePanel(), BorderLayout.CENTER);
    setContentPane(contentPane);
  }

  @NotNull
  private JComponent createWorkspacePanel() {
    JPanel workspace = new JPanel(new BorderLayout(0, 10));
    workspace.setOpaque(false);
    workspace.add(createTopRowPanel(), BorderLayout.CENTER);
    workspace.add(createLogCard(), BorderLayout.SOUTH);
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
    topRow.add(createFixedWidthPanel(createSessionCard(), SIDEBAR_WIDTH), constraints);

    constraints.gridx = 1;
    constraints.weightx = 1;
    topRow.add(createPortsCard(), constraints);

    constraints.gridx = 2;
    constraints.weightx = 0;
    constraints.insets = new Insets(0, 10, 0, 0);
    topRow.add(createFixedWidthPanel(createInspectorCard(), INSPECTOR_WIDTH), constraints);

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

  @NotNull
  private JPanel createSessionCard() {
    JPanel card = createCardPanel();
    card.setLayout(new BorderLayout(0, 12));
    card.setMaximumSize(new Dimension(SIDEBAR_WIDTH, Integer.MAX_VALUE));
    card.add(createCardHeader("会话", myConnectionBadge), BorderLayout.NORTH);

    JPanel formPanel = new JPanel();
    formPanel.setOpaque(false);
    formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
    formPanel.add(createFieldBlock("服务器", myBrokerField));
    formPanel.add(Box.createVerticalStrut(10));
    formPanel.add(createFieldBlock("客户端", myClientIdField));
    formPanel.add(Box.createVerticalStrut(10));
    formPanel.add(createFieldBlock("端口数量", myPortCountSpinner));

    card.add(formPanel, BorderLayout.CENTER);
    card.add(createSessionActionStrip(), BorderLayout.SOUTH);
    return card;
  }

  @NotNull
  private JComponent createSessionActionStrip() {
    JPanel actionPanel = new JPanel(new GridLayout(1, 2, 8, 0));
    actionPanel.setOpaque(false);
    actionPanel.add(myConnectButton);
    actionPanel.add(myDisconnectButton);
    return actionPanel;
  }

  @NotNull
  private JPanel createPortsCard() {
    JPanel card = createCardPanel();
    card.setLayout(new BorderLayout(0, 8));
    card.add(createCardHeader("端口列表"), BorderLayout.NORTH);

    JScrollPane tableScroll = new JScrollPane(myPortTable);
    tableScroll.setPreferredSize(new Dimension(0, 312));
    tableScroll.setBorder(new LineBorder(COLOR_BORDER, 1, true));
    tableScroll.getViewport().setBackground(COLOR_SURFACE);

    card.add(tableScroll, BorderLayout.CENTER);
    return card;
  }

  @NotNull
  private JPanel createInspectorCard() {
    JPanel card = createCardPanel();
    card.setLayout(new BorderLayout(0, 12));
    card.setMaximumSize(new Dimension(INSPECTOR_WIDTH, Integer.MAX_VALUE));
    card.add(createCardHeader("当前选择"), BorderLayout.NORTH);

    JPanel body = new JPanel();
    body.setOpaque(false);
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.add(createInspectorRow("端口", mySelectedPortValue));
    body.add(Box.createVerticalStrut(12));
    body.add(createInspectorRow("状态", mySelectedStatusValue));
    body.add(Box.createVerticalStrut(12));
    body.add(createInspectorRow("余额", mySelectedBalanceValue));
    body.add(Box.createVerticalStrut(16));
    body.add(createHintPanel());

    JPanel footer = new JPanel(new BorderLayout(0, 0));
    footer.setOpaque(false);
    footer.add(myCloseOrderButton, BorderLayout.CENTER);

    card.add(body, BorderLayout.CENTER);
    card.add(footer, BorderLayout.SOUTH);
    return card;
  }

  @NotNull
  private JComponent createHintPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setOpaque(true);
    panel.setBackground(COLOR_SURFACE_ALT);
    panel.setBorder(BorderFactory.createCompoundBorder(
        new LineBorder(COLOR_BORDER, 1, true),
        new EmptyBorder(10, 10, 10, 10)
    ));
    panel.add(mySelectionHintLabel, BorderLayout.CENTER);
    panel.setPreferredSize(new Dimension(0, 64));
    panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
    return panel;
  }

  @NotNull
  private JPanel createInspectorRow(@NotNull String title, @NotNull JComponent valueComponent) {
    JPanel row = new JPanel(new BorderLayout(0, 6));
    row.setOpaque(false);

    JLabel titleLabel = new JLabel(title);
    titleLabel.setFont(MainFrameFonts.bold(10f));
    titleLabel.setForeground(COLOR_MUTED);

    row.add(titleLabel, BorderLayout.NORTH);
    row.add(valueComponent, BorderLayout.CENTER);
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
    return row;
  }

  @NotNull
  private JPanel createLogCard() {
    JPanel card = createCardPanel();
    card.setLayout(new BorderLayout(0, 8));
    card.setPreferredSize(new Dimension(0, 220));
    card.add(createCardHeader("事件日志"), BorderLayout.NORTH);

    JScrollPane logScroll = new JScrollPane(myLogArea);
    logScroll.setPreferredSize(new Dimension(0, 160));
    logScroll.setBorder(new LineBorder(COLOR_BORDER, 1, true));
    logScroll.getViewport().setBackground(new Color(0xF8F6EE));

    card.add(logScroll, BorderLayout.CENTER);
    return card;
  }

  @NotNull
  private JPanel createCardPanel() {
    JPanel panel = new JPanel();
    panel.setBackground(COLOR_SURFACE);
    panel.setBorder(BorderFactory.createCompoundBorder(
        new LineBorder(COLOR_BORDER, 1, true),
        new EmptyBorder(12, 12, 12, 12)
    ));
    return panel;
  }

  @NotNull
  private JPanel createCardHeader(@NotNull String title) {
    return createCardHeader(title, null);
  }

  @NotNull
  private JPanel createCardHeader(@NotNull String title, JComponent accessory) {
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
  private JPanel createFieldBlock(@NotNull String title, @NotNull JComponent component) {
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
  private JLabel createBadgeLabel() {
    JLabel label = new JLabel();
    label.setFont(MainFrameFonts.bold(11f));
    label.setOpaque(true);
    label.setBorder(new EmptyBorder(5, 8, 5, 8));
    return label;
  }

  @NotNull
  private JLabel createInspectorValueLabel() {
    JLabel label = new JLabel();
    label.setFont(MainFrameFonts.bold(16f));
    label.setForeground(COLOR_TEXT);
    return label;
  }

  @NotNull
  private JLabel createHintLabel() {
    JLabel label = new JLabel("<html>请选择表格中的端口，查看状态和可用操作。</html>");
    label.setFont(MainFrameFonts.plain(11f));
    label.setForeground(COLOR_MUTED);
    label.setVerticalAlignment(SwingConstants.TOP);
    return label;
  }

  private void bindActions() {
    myConnectButton.addActionListener(e -> onConnect());
    myDisconnectButton.addActionListener(e -> onDisconnect());
    myCloseOrderButton.addActionListener(e -> onCloseOrder());

    myPortCountSpinner.addChangeListener(e -> {
      if (myMqttService == null) {
        initPorts((Integer) myPortCountSpinner.getValue());
      }
    });

    myPortTable.getSelectionModel().addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        updateSelectionDetails();
      }
    });
  }

  private void updateSelectionDetails() {
    ChargingPortSnapshot snapshot = getSelectedPortSnapshot();
    if (snapshot == null) {
      mySelectedPortValue.setText("未选择端口");
      mySelectedStatusValue.setText("—");
      mySelectedStatusValue.setForeground(COLOR_IDLE);
      mySelectedBalanceValue.setText("—");
      myCloseOrderButton.setEnabled(false);
      setSelectionHint("请选择表格中的端口，查看状态和可用操作。");
      return;
    }

    ChargingPortState state = snapshot.getState();

    mySelectedPortValue.setText("端口 " + snapshot.getPortNumber());
    mySelectedStatusValue.setText(ChargingPortPresentation.getStateLabel(state));
    mySelectedStatusValue.setForeground(resolveStatusColor(state));
    mySelectedBalanceValue.setText(ChargingPortPresentation.getBalanceText(snapshot));

    boolean canClose = myMqttService != null && state == ChargingPortState.CHARGING;
    myCloseOrderButton.setEnabled(canClose);

    if (myMqttService == null) {
      setSelectionHint("请先连接服务器，再发送手动结束订单。");
    }
    else if (state == ChargingPortState.CHARGING) {
      setSelectionHint("该端口正在充电。点击“结束订单”会立即发送手动结束命令。");
    }
    else if (state == ChargingPortState.CLOSING) {
      setSelectionHint("该端口正在结束订单，请等待计费结束消息完成重置。");
    }
    else {
      setSelectionHint("只有“充电中”状态的端口可以手动结束订单。");
    }
  }

  @Nullable
  private ChargingPortSnapshot getSelectedPortSnapshot() {
    int row = myPortTable.getSelectedRow();
    if (row < 0 || row >= myTableModel.getRowCount()) {
      return null;
    }
    return myTableModel.getPortSnapshot(row);
  }

  private void setSelectionHint(@NotNull String text) {
    mySelectionHintLabel.setText("<html>" + text + "</html>");
  }

  @NotNull
  private Color resolveStatusColor(@NotNull ChargingPortState state) {
    if (state == ChargingPortState.CHARGING) {
      return COLOR_ACCENT;
    }
    if (state == ChargingPortState.CLOSING) {
      return COLOR_WARNING;
    }
    return COLOR_IDLE;
  }

  private void updateConnectionBadge(boolean connected) {
    myConnectionBadge.setText(connected ? "在线" : "离线");
    myConnectionBadge.setBackground(connected ? COLOR_ACCENT_SOFT : COLOR_WARNING_SOFT);
    myConnectionBadge.setForeground(connected ? COLOR_ACCENT : COLOR_WARNING);
    myConnectionBadge.setBorder(BorderFactory.createCompoundBorder(
        new LineBorder(connected ? COLOR_ACCENT : COLOR_WARNING, 1, true),
        new EmptyBorder(5, 8, 5, 8)
    ));
  }

  private void onConnect() {
    String broker = myBrokerField.getText().trim();
    String clientId = myClientIdField.getText().trim();
    if (broker.isEmpty() || clientId.isEmpty()) {
      appendLog("服务器和客户端不能为空");
      return;
    }

    int portCount = (Integer) myPortCountSpinner.getValue();
    initPorts(portCount);

    myConnectButton.setEnabled(false);
    appendLog("正在连接 " + broker);

    new Thread(() -> {
      MqttService service = new MqttService(broker, clientId, myStation, this, this);
      myMqttService = service;
      try {
        service.connect();
      }
      catch (Exception ex) {
        myMqttService = null;
        service.disconnect();
        SwingUtilities.invokeLater(() -> {
          appendLog("连接失败：" + ex.getMessage());
          if (ex.getCause() != null) {
            appendLog("  原因：" + ex.getCause().getMessage());
          }
          myConnectButton.setEnabled(true);
          updateSelectionDetails();
        });
      }
    }, "mqtt-connect-thread").start();
  }

  private void onDisconnect() {
    MqttService service = myMqttService;
    myMqttService = null;
    if (service != null) {
      service.disconnect();
    }
    onDisconnected();
    appendLog("已断开连接");
  }

  @Override
  public void onApplicationLog(@NotNull String message) {
    appendLog(message);
  }

  @Override
  public void onPortsChanged() {
    refreshPortTable();
  }

  @Override
  public void onMqttConnected(@NotNull String brokerUrl, @NotNull String clientId) {
    appendLog("已连接到 " + brokerUrl + "，客户端 " + clientId);
    onConnected();
  }

  @Override
  public void onMqttDisconnected(@Nullable Throwable cause) {
    myMqttService = null;
    onDisconnected();
  }

  @Override
  public void onMqttLog(@NotNull String message) {
    appendLog(message);
  }

  public void onConnected() {
    SwingUtilities.invokeLater(() -> {
      myConnectButton.setEnabled(false);
      myDisconnectButton.setEnabled(true);
      myBrokerField.setEnabled(false);
      myClientIdField.setEnabled(false);
      myPortCountSpinner.setEnabled(false);
      updateConnectionBadge(true);
      updateSelectionDetails();
    });
  }

  public void onDisconnected() {
    SwingUtilities.invokeLater(() -> {
      myConnectButton.setEnabled(true);
      myDisconnectButton.setEnabled(false);
      myBrokerField.setEnabled(true);
      myClientIdField.setEnabled(true);
      myPortCountSpinner.setEnabled(true);
      updateConnectionBadge(false);
      updateSelectionDetails();
    });
  }

  private void onCloseOrder() {
    ChargingPortSnapshot port = getSelectedPortSnapshot();
    if (port == null) {
      appendLog("请先选择端口");
      return;
    }

    MqttService service = myMqttService;
    if (service == null) {
      appendLog("请先连接服务器");
      return;
    }

    service.manualClose(port.getPortNumber());
  }

  public void refreshPortTable() {
    SwingUtilities.invokeLater(() -> {
      myTableModel.fireDataChanged();
      updateSelectionDetails();
    });
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

  private void trimLogArea() {
    Document document = myLogArea.getDocument();
    Element root = document.getDefaultRootElement();
    while (root.getElementCount() > MAX_LOG_LINES + 1) {
      Element firstLine = root.getElement(0);
      try {
        document.remove(0, firstLine.getEndOffset());
      }
      catch (BadLocationException e) {
        throw new IllegalStateException("日志裁剪失败", e);
      }
    }
  }

  private final class PortTableCellRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table,
                                                   Object value,
                                                   boolean isSelected,
                                                   boolean hasFocus,
                                                   int row,
                                                   int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      ChargingPortSnapshot port = myTableModel.getPortSnapshot(row);
      boolean active = port.getState() == ChargingPortState.CHARGING;
      boolean closing = port.getState() == ChargingPortState.CLOSING;

      setFont(column == 1 ? MainFrameFonts.bold(13f) : MainFrameFonts.plain(13f));
      setBorder(new EmptyBorder(0, 10, 0, 10));
      setForeground(COLOR_TEXT);
      setHorizontalAlignment(column == 2 ? RIGHT : LEFT);

      if (column == 0) {
        setHorizontalAlignment(CENTER);
      }

      if (!isSelected) {
        setBackground(row % 2 == 0 ? COLOR_SURFACE : COLOR_ROW_ALT);
        if (column == 1) {
          setForeground(resolveStatusColor(port.getState()));
          setText((active || closing ? "● " : "○ ") + value);
        }
        else if (column == 2 && (active || closing)) {
          setForeground(resolveStatusColor(port.getState()));
        }
      }
      else {
        setBackground(COLOR_SELECTION);
        setForeground(COLOR_TEXT);
        if (column == 1) {
          setText((active || closing ? "● " : "○ ") + value);
        }
      }

      return this;
    }
  }
}
