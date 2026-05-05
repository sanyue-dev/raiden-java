package com.raiden.application.session;

import com.raiden.model.ChargingPortSnapshot;
import org.jetbrains.annotations.Nullable;

public final class ChargingSessionLifecycleResult {

  public enum Type {
    START_ACCEPTED,
    START_REJECTED_PORT_NOT_FOUND,
    START_REJECTED_PORT_NOT_IDLE,
    START_RESPONSE_PUBLISH_FAILED_ROLLED_BACK,
    START_RESPONSE_PUBLISH_FAILED_NOT_ROLLED_BACK,
    MANUAL_STOP_REJECTED_PORT_NOT_FOUND,
    MANUAL_STOP_REJECTED_NOT_CHARGING,
    MANUAL_STOPPED,
    MANUAL_STOP_PUBLISH_FAILED_RESTORED,
    MANUAL_STOP_PUBLISH_FAILED_NOT_RESTORED,
    BILLING_COMPLETED,
    BILLING_IDLE_RESPONSE_SENT,
    BILLING_PORT_NOT_FOUND_RESPONSE_PENDING,
    BILLING_RESPONSE_PUBLISH_FAILED,
    BILLING_IDLE_RESPONSE_PUBLISH_FAILED
  }

  private final int myPortNumber;
  private final boolean myResponsePublished;
  private final boolean myPortsChanged;
  @Nullable
  private final ChargingPortSnapshot mySnapshot;
  @Nullable
  private final ChargingPortSnapshot myCurrentSnapshot;
  private final Type myType;

  public ChargingSessionLifecycleResult(Type type,
                                        int portNumber,
                                        boolean responsePublished,
                                        boolean portsChanged,
                                        @Nullable ChargingPortSnapshot snapshot,
                                        @Nullable ChargingPortSnapshot currentSnapshot) {
    myType = type;
    myPortNumber = portNumber;
    myResponsePublished = responsePublished;
    myPortsChanged = portsChanged;
    mySnapshot = snapshot;
    myCurrentSnapshot = currentSnapshot;
  }

  public Type getType() {
    return myType;
  }

  public int getPortNumber() {
    return myPortNumber;
  }

  public boolean isResponsePublished() {
    return myResponsePublished;
  }

  public boolean isPortsChanged() {
    return myPortsChanged;
  }

  @Nullable
  public ChargingPortSnapshot getSnapshot() {
    return mySnapshot;
  }

  @Nullable
  public ChargingPortSnapshot getCurrentSnapshot() {
    return myCurrentSnapshot;
  }
}
