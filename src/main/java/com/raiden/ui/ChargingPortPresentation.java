package com.raiden.ui;

import com.raiden.domain.ChargingPortSnapshot;
import com.raiden.domain.ChargingPortState;
import org.jetbrains.annotations.NotNull;

final class ChargingPortPresentation {

  private ChargingPortPresentation() {}

  @NotNull
  static String getStateLabel(@NotNull ChargingPortState state) {
    return state.getLabel();
  }

  @NotNull
  static String getBalanceText(@NotNull ChargingPortSnapshot port) {
    return port.getState() == ChargingPortState.IDLE ? "--" : String.valueOf(port.getBalance());
  }
}
