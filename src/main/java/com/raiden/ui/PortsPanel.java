package com.raiden.ui;

import com.raiden.model.ChargingPortSnapshot;
import com.raiden.model.ChargingPortState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import javax.swing.event.ListSelectionListener;

public final class PortsPanel extends JPanel {

  @NotNull
  private final JTable myPortTable;
  @NotNull
  private final PortTableModel myTableModel;

  public PortsPanel(@NotNull PortTableModel tableModel) {
    myTableModel = tableModel;
    myPortTable = new JTable(myTableModel);

    setLayout(new BorderLayout(0, 8));
    RaidenTheme.applyCardStyle(this);
    add(RaidenTheme.createCardHeader("端口列表"), BorderLayout.NORTH);

    configureTable();

    JScrollPane tableScroll = new JScrollPane(myPortTable);
    tableScroll.setPreferredSize(new Dimension(0, 312));
    tableScroll.setBorder(new LineBorder(RaidenTheme.COLOR_BORDER, 1, true));
    tableScroll.getViewport().setBackground(RaidenTheme.COLOR_SURFACE);

    add(tableScroll, BorderLayout.CENTER);
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
    myPortTable.setForeground(RaidenTheme.COLOR_TEXT);
    myPortTable.setBackground(RaidenTheme.COLOR_SURFACE);
    myPortTable.setSelectionBackground(RaidenTheme.COLOR_SELECTION);
    myPortTable.setSelectionForeground(RaidenTheme.COLOR_TEXT);
    myPortTable.setDefaultRenderer(Object.class, new PortTableCellRenderer());

    JTableHeader tableHeader = myPortTable.getTableHeader();
    tableHeader.setReorderingAllowed(false);
    tableHeader.setResizingAllowed(false);
    tableHeader.setPreferredSize(new Dimension(0, 30));
    tableHeader.setFont(MainFrameFonts.bold(11f));
    tableHeader.setForeground(RaidenTheme.COLOR_MUTED);
    tableHeader.setBackground(RaidenTheme.COLOR_SURFACE_ALT);
    tableHeader.setBorder(new LineBorder(RaidenTheme.COLOR_BORDER, 1));

    myPortTable.getColumnModel().getColumn(0).setPreferredWidth(72);
    myPortTable.getColumnModel().getColumn(1).setPreferredWidth(220);
    myPortTable.getColumnModel().getColumn(2).setPreferredWidth(112);
  }

  public void refreshTable() {
    SwingUtilities.invokeLater(() -> {
      myTableModel.fireDataChanged();
    });
  }

  public void clearSelection() {
    myPortTable.clearSelection();
  }

  public void setSelectionListener(@NotNull ListSelectionListener listener) {
    myPortTable.getSelectionModel().addListSelectionListener(listener);
  }

  @Nullable
  public ChargingPortSnapshot getSelectedPortSnapshot() {
    int row = myPortTable.getSelectedRow();
    if (row < 0 || row >= myTableModel.getRowCount()) {
      return null;
    }
    return myTableModel.getPortSnapshot(row);
  }

  private final class PortTableCellRenderer extends DefaultTableCellRenderer {

    private final Font myBoldFont = MainFrameFonts.bold(13f);
    private final Font myPlainFont = MainFrameFonts.plain(13f);
    private final EmptyBorder myCellPadding = new EmptyBorder(0, 10, 0, 10);

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
      boolean stopped = port.getState() == ChargingPortState.STOPPED;
      boolean inUse = active || stopped;

      setFont(column == 1 ? myBoldFont : myPlainFont);
      setBorder(myCellPadding);
      setForeground(RaidenTheme.COLOR_TEXT);
      setHorizontalAlignment(column == 0 ? CENTER : column == 2 ? RIGHT : LEFT);

      if (column == 1) {
        setText((inUse ? "● " : "○ ") + value);
      }

      if (!isSelected) {
        setBackground(row % 2 == 0 ? RaidenTheme.COLOR_SURFACE : RaidenTheme.COLOR_ROW_ALT);
        if (column == 1 || (column == 2 && inUse)) {
          setForeground(RaidenTheme.resolveStatusColor(port.getState()));
        }
      }
      else {
        setBackground(RaidenTheme.COLOR_SELECTION);
      }

      return this;
    }
  }
}
