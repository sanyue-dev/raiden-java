package com.raiden.application.session;

import com.raiden.model.ChargingPort;
import com.raiden.model.ChargingPortSnapshot;
import com.raiden.model.ChargingPortState;
import com.raiden.model.ChargingStation;
import org.jetbrains.annotations.NotNull;

public final class ChargingSessionLifecycle {

  @NotNull
  private final ChargingStation myStation;

  public ChargingSessionLifecycle(@NotNull ChargingStation station) {
    myStation = station;
  }

  public boolean willCompleteActiveBilling(int portNumber) {
    ChargingPort port = myStation.findPort(portNumber);
    if (port == null) {
      return false;
    }
    ChargingPortSnapshot snapshot = port.snapshot();
    return snapshot.getState() == ChargingPortState.CHARGING || snapshot.getState() == ChargingPortState.STOPPED;
  }

  @NotNull
  public ChargingSessionLifecycleResult startCharging(int portNumber,
                                                      int orderType,
                                                      int duration,
                                                      int kwhFee,
                                                      int unit,
                                                      int balance,
                                                      @NotNull StartChargingResponsePublisher publisher) {
    ChargingPort port = myStation.findPort(portNumber);
    if (port == null) {
      boolean published = publisher.publishStartChargingResponse(portNumber, false);
      return result(ChargingSessionLifecycleResult.Type.START_REJECTED_PORT_NOT_FOUND, portNumber, published, false, null, null);
    }

    ChargingPortSnapshot startedSnapshot = port.tryStartChargingFromIdle(orderType, duration, kwhFee, unit, balance);
    if (startedSnapshot == null) {
      ChargingPortSnapshot currentSnapshot = port.snapshot();
      boolean published = publisher.publishStartChargingResponse(portNumber, false);
      return result(ChargingSessionLifecycleResult.Type.START_REJECTED_PORT_NOT_IDLE, portNumber, published, false, null, currentSnapshot);
    }

    if (!publisher.publishStartChargingResponse(portNumber, true)) {
      if (port.resetChargingIfSameSession(startedSnapshot)) {
        return result(ChargingSessionLifecycleResult.Type.START_RESPONSE_PUBLISH_FAILED_ROLLED_BACK, portNumber, false, false, startedSnapshot, null);
      }
      return result(ChargingSessionLifecycleResult.Type.START_RESPONSE_PUBLISH_FAILED_NOT_ROLLED_BACK, portNumber, false, false, startedSnapshot, port.snapshot());
    }

    return result(ChargingSessionLifecycleResult.Type.START_ACCEPTED, portNumber, true, true, startedSnapshot, null);
  }

  @NotNull
  public ChargingSessionLifecycleResult manualStop(int portNumber, @NotNull ManualStopPublisher publisher) {
    ChargingPort port = myStation.findPort(portNumber);
    if (port == null) {
      return result(ChargingSessionLifecycleResult.Type.MANUAL_STOP_REJECTED_PORT_NOT_FOUND, portNumber, false, false, null, null);
    }

    ChargingPortSnapshot snapshot = port.tryStopFromCharging();
    if (snapshot == null) {
      return result(ChargingSessionLifecycleResult.Type.MANUAL_STOP_REJECTED_NOT_CHARGING, portNumber, false, false, null, port.snapshot());
    }

    if (!publisher.publishManualStop(snapshot)) {
      if (port.restoreChargingIfStillStopped(snapshot)) {
        return result(ChargingSessionLifecycleResult.Type.MANUAL_STOP_PUBLISH_FAILED_RESTORED, portNumber, false, true, snapshot, port.snapshot());
      }
      return result(ChargingSessionLifecycleResult.Type.MANUAL_STOP_PUBLISH_FAILED_NOT_RESTORED, portNumber, false, false, snapshot, port.snapshot());
    }

    return result(ChargingSessionLifecycleResult.Type.MANUAL_STOPPED, portNumber, true, true, snapshot, port.snapshot());
  }

  @NotNull
  public ChargingSessionLifecycleResult completeBilling(int portNumber, @NotNull BillingResponsePublisher publisher) {
    ChargingPort port = myStation.findPort(portNumber);
    if (port == null) {
      return result(ChargingSessionLifecycleResult.Type.BILLING_PORT_NOT_FOUND_RESPONSE_PENDING, portNumber, false, false, null, null);
    }

    ChargingPortSnapshot currentSnapshot = port.snapshot();
    if (currentSnapshot.getState() == ChargingPortState.CHARGING || currentSnapshot.getState() == ChargingPortState.STOPPED) {
      ChargingPortSnapshot billingSnapshot = port.finishBillingIfActive();
      if (billingSnapshot == null) {
        ChargingPortSnapshot idleSnapshot = port.snapshot();
        boolean published = publisher.publishIdleBillingResponse(idleSnapshot);
        return result(
            published ? ChargingSessionLifecycleResult.Type.BILLING_IDLE_RESPONSE_SENT : ChargingSessionLifecycleResult.Type.BILLING_IDLE_RESPONSE_PUBLISH_FAILED,
            portNumber,
            published,
            false,
            idleSnapshot,
            currentSnapshot
        );
      }

      if (!publisher.publishActiveBillingResponse(billingSnapshot)) {
        return result(ChargingSessionLifecycleResult.Type.BILLING_RESPONSE_PUBLISH_FAILED, portNumber, false, true, billingSnapshot, port.snapshot());
      }
      return result(ChargingSessionLifecycleResult.Type.BILLING_COMPLETED, portNumber, true, true, billingSnapshot, port.snapshot());
    }

    boolean published = publisher.publishIdleBillingResponse(currentSnapshot);
    return result(
        published ? ChargingSessionLifecycleResult.Type.BILLING_IDLE_RESPONSE_SENT : ChargingSessionLifecycleResult.Type.BILLING_IDLE_RESPONSE_PUBLISH_FAILED,
        portNumber,
        published,
        false,
        currentSnapshot,
        null
    );
  }

  @NotNull
  private static ChargingSessionLifecycleResult result(ChargingSessionLifecycleResult.Type type,
                                                       int portNumber,
                                                       boolean responsePublished,
                                                       boolean portsChanged,
                                                       ChargingPortSnapshot snapshot,
                                                       ChargingPortSnapshot currentSnapshot) {
    return new ChargingSessionLifecycleResult(type, portNumber, responsePublished, portsChanged, snapshot, currentSnapshot);
  }

  public interface StartChargingResponsePublisher {
    boolean publishStartChargingResponse(int portNumber, boolean success);
  }

  public interface ManualStopPublisher {
    boolean publishManualStop(@NotNull ChargingPortSnapshot snapshot);
  }

  public interface BillingResponsePublisher {
    boolean publishActiveBillingResponse(@NotNull ChargingPortSnapshot snapshot);

    boolean publishIdleBillingResponse(@NotNull ChargingPortSnapshot snapshot);
  }
}
