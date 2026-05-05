package com.raiden.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
      return snapshotLocked();
    }
  }

  @Nullable
  public ChargingPortSnapshot tryStartChargingFromIdle(int orderType, int duration, int kwhFee, int unit, int balance) {
    synchronized (myLock) {
      if (myState != ChargingPortState.IDLE) {
        return null;
      }
      myState = ChargingPortState.CHARGING;
      myStartTime = System.currentTimeMillis();
      myOrderType = orderType;
      myDuration = duration;
      myKwhFee = kwhFee;
      myUnit = unit;
      myStartBalance = balance;
      myBalance = balance;
      return snapshotLocked();
    }
  }

  @Nullable
  public ChargingPortSnapshot tryStopFromCharging() {
    synchronized (myLock) {
      if (myState != ChargingPortState.CHARGING) {
        return null;
      }
      ChargingPortSnapshot snapshot = snapshotLocked();
      myState = ChargingPortState.STOPPED;
      return snapshot;
    }
  }

  public boolean restoreChargingIfStillStopped(@NotNull ChargingPortSnapshot snapshot) {
    synchronized (myLock) {
      if (myState != ChargingPortState.STOPPED || !isSameSessionLocked(snapshot)) {
        return false;
      }
      myState = ChargingPortState.CHARGING;
      return true;
    }
  }

  public boolean resetChargingIfSameSession(@NotNull ChargingPortSnapshot snapshot) {
    synchronized (myLock) {
      if (myState != ChargingPortState.CHARGING || snapshot.getState() != ChargingPortState.CHARGING || !isSameSessionLocked(snapshot)) {
        return false;
      }
      resetLocked();
      return true;
    }
  }

  @Nullable
  public ChargingPortSnapshot finishBillingIfActive() {
    synchronized (myLock) {
      if (myState != ChargingPortState.CHARGING && myState != ChargingPortState.STOPPED) {
        return null;
      }
      ChargingPortSnapshot snapshot = snapshotLocked();
      resetLocked();
      return snapshot;
    }
  }

  @Nullable
  public ChargingPortSnapshot finishBillingIfSameSession(@NotNull ChargingPortSnapshot snapshot) {
    synchronized (myLock) {
      if ((snapshot.getState() != ChargingPortState.CHARGING && snapshot.getState() != ChargingPortState.STOPPED) ||
          (myState != ChargingPortState.CHARGING && myState != ChargingPortState.STOPPED) ||
          !isSameSessionLocked(snapshot)) {
        return null;
      }
      ChargingPortSnapshot billingSnapshot = snapshotLocked();
      resetLocked();
      return billingSnapshot;
    }
  }

  public void reset() {
    synchronized (myLock) {
      resetLocked();
    }
  }

  @NotNull
  private ChargingPortSnapshot snapshotLocked() {
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

  private boolean isSameSessionLocked(@NotNull ChargingPortSnapshot snapshot) {
    return snapshot.getPortNumber() == myPortNumber &&
           snapshot.getOrderType() == myOrderType &&
           snapshot.getDuration() == myDuration &&
           snapshot.getKwhFee() == myKwhFee &&
           snapshot.getUnit() == myUnit &&
           snapshot.getStartBalance() == myStartBalance &&
           snapshot.getBalance() == myBalance &&
           snapshot.getStartTime() == myStartTime;
  }

  private void resetLocked() {
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
