package com.raiden.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ChargingStation {

  @NotNull
  private final List<ChargingPort> myPorts = new ArrayList<>();
  @NotNull
  private final Object myLock = new Object();

  public void resetPorts(int count) {
    synchronized (myLock) {
      myPorts.clear();
      for (int i = 1; i <= count; i++) {
        myPorts.add(new ChargingPort(i));
      }
    }
  }

  @NotNull
  public List<ChargingPort> getPorts() {
    synchronized (myLock) {
      return new ArrayList<>(myPorts);
    }
  }

  @NotNull
  public List<ChargingPortSnapshot> getPortSnapshots() {
    synchronized (myLock) {
      List<ChargingPortSnapshot> snapshots = new ArrayList<>();
      for (ChargingPort port : myPorts) {
        snapshots.add(port.snapshot());
      }
      return snapshots;
    }
  }

  @Nullable
  public ChargingPort findPort(int portNumber) {
    synchronized (myLock) {
      for (ChargingPort port : myPorts) {
        if (port.getPortNumber() == portNumber) return port;
      }
      return null;
    }
  }
}
