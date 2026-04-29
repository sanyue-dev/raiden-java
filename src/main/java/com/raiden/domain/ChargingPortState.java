package com.raiden.domain;

import org.jetbrains.annotations.NotNull;

public enum ChargingPortState {
  IDLE("空闲"),
  CHARGING("充电中"),
  CLOSING("结束中");

  @NotNull
  private final String myLabel;

  ChargingPortState(@NotNull String label) {
    myLabel = label;
  }

  @NotNull
  public String getLabel() {
    return myLabel;
  }
}
