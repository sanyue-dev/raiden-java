package com.raiden.ui;

import com.raiden.domain.ChargingPortSnapshot;
import com.raiden.domain.ChargingPortState;
import org.jetbrains.annotations.NotNull;

final class ChargingPortPresentation {

  private ChargingPortPresentation() {}

  @NotNull
  static String getStateLabel(@NotNull ChargingPortState state) {
    switch (state) {
      case CHARGING: return "充电中";
      case CLOSING: return "结束中";
      default: return "空闲";
    }
  }

  @NotNull
  static String getBalanceText(@NotNull ChargingPortSnapshot port) {
    return port.getState() == ChargingPortState.IDLE ? "--" : String.valueOf(port.getBalance());
  }
}
