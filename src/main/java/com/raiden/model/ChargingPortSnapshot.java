package com.raiden.model;

import org.jetbrains.annotations.NotNull;

public final class ChargingPortSnapshot {

  private final int myPortNumber;
  @NotNull
  private final ChargingPortState myState;
  private final int myOrderType;
  private final int myDuration;
  private final int myKwhFee;
  private final int myUnit;
  private final int myStartBalance;
  private final int myBalance;
  private final long myStartTime;

  public ChargingPortSnapshot(int portNumber,
                              @NotNull ChargingPortState state,
                              int orderType,
                              int duration,
                              int kwhFee,
                              int unit,
                              int startBalance,
                              int balance,
                              long startTime) {
    myPortNumber = portNumber;
    myState = state;
    myOrderType = orderType;
    myDuration = duration;
    myKwhFee = kwhFee;
    myUnit = unit;
    myStartBalance = startBalance;
    myBalance = balance;
    myStartTime = startTime;
  }

  public int getPortNumber() {
    return myPortNumber;
  }

  @NotNull
  public ChargingPortState getState() {
    return myState;
  }

  public int getOrderType() {
    return myOrderType;
  }

  public int getDuration() {
    return myDuration;
  }

  public int getKwhFee() {
    return myKwhFee;
  }

  public int getUnit() {
    return myUnit;
  }

  public int getStartBalance() {
    return myStartBalance;
  }

  public int getBalance() {
    return myBalance;
  }

  public long getStartTime() {
    return myStartTime;
  }
}
