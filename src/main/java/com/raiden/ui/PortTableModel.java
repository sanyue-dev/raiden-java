package com.raiden.ui;

import com.raiden.domain.ChargingPortSnapshot;
import com.raiden.domain.ChargingStation;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public final class PortTableModel extends AbstractTableModel {

  private static final String[] COLUMNS = {"端口", "状态", "余额"};

  @NotNull
  private final ChargingStation myStation;
  @NotNull
  private List<ChargingPortSnapshot> myPortSnapshots = new ArrayList<>();

  public PortTableModel(@NotNull ChargingStation station) {
    myStation = station;
  }

  @Override
  public int getRowCount() {
    return myPortSnapshots.size();
  }

  @Override
  public int getColumnCount() {
    return COLUMNS.length;
  }

  @Override
  @NotNull
  public String getColumnName(int column) {
    return COLUMNS[column];
  }

  @Override
  @NotNull
  public Object getValueAt(int rowIndex, int columnIndex) {
    ChargingPortSnapshot port = getPortSnapshot(rowIndex);
    switch (columnIndex) {
      case 0: return port.getPortNumber();
      case 1: return ChargingPortPresentation.getStateLabel(port.getState());
      case 2: return ChargingPortPresentation.getBalanceText(port);
      default: return "";
    }
  }

  public void fireDataChanged() {
    myPortSnapshots = myStation.getPortSnapshots();
    fireTableDataChanged();
  }

  @NotNull
  public ChargingPortSnapshot getPortSnapshot(int rowIndex) {
    return myPortSnapshots.get(rowIndex);
  }
}
