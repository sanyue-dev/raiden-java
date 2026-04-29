package com.raiden.domain;

import org.jetbrains.annotations.NotNull;

public final class ChargingPort {

  private final int myPortNumber;
  @NotNull
  private final Object myLock = new Object();
  @NotNull
  private ChargingPortState myState = ChargingPortState.IDLE;
  private int myOrderType;
  private int myDuration;
  private int myKwhFee;
  private int myUnit;
  private int myStartBalance;
  private int myBalance;
  private long myStartTime;

  public ChargingPort(int portNumber) {
    myPortNumber = portNumber;
  }

  public int getPortNumber() {
    return myPortNumber;
  }

  @NotNull
  public ChargingPortSnapshot snapshot() {
    synchronized (myLock) {
      return new ChargingPortSnapshot(
          myPortNumber,
          myState,
          myOrderType,
          myDuration,
          myKwhFee,
          myUnit,
          myStartBalance,
          myBalance,
          myStartTime
      );
    }
  }

  public void startCharging(int orderType, int duration, int kwhFee, int unit, int balance) {
    synchronized (myLock) {
      myState = ChargingPortState.CHARGING;
      myStartTime = System.currentTimeMillis();
      myOrderType = orderType;
      myDuration = duration;
      myKwhFee = kwhFee;
      myUnit = unit;
      myStartBalance = balance;
      myBalance = balance;
    }
  }

  public void beginClosing() {
    synchronized (myLock) {
      myState = ChargingPortState.CLOSING;
    }
  }

  public void reset() {
    synchronized (myLock) {
      myState = ChargingPortState.IDLE;
      myOrderType = 0;
      myDuration = 0;
      myKwhFee = 0;
      myUnit = 0;
      myStartBalance = 0;
      myBalance = 0;
      myStartTime = 0;
    }
  }
}
