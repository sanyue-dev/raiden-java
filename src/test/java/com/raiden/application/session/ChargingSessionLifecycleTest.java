package com.raiden.application.session;

import com.raiden.model.ChargingPort;
import com.raiden.model.ChargingPortSnapshot;
import com.raiden.model.ChargingPortState;
import com.raiden.model.ChargingStation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChargingSessionLifecycleTest {

  @Test
  void rollsBackStartChargingWhenSuccessResponsePublishFails() {
    Fixture fixture = new Fixture();

    ChargingSessionLifecycleResult result = fixture.lifecycle.startCharging(
        1,
        2,
        30,
        5,
        1,
        100,
        (portNumber, success) -> false
    );

    assertEquals(ChargingSessionLifecycleResult.Type.START_RESPONSE_PUBLISH_FAILED_ROLLED_BACK, result.getType());
    assertEquals(ChargingPortState.IDLE, fixture.port().snapshot().getState());
    assertFalse(result.isPortsChanged());
  }

  @Test
  void restoresChargingWhenManualStopPublishFails() {
    Fixture fixture = new Fixture();
    fixture.startPort();

    ChargingSessionLifecycleResult result = fixture.lifecycle.manualStop(1, snapshot -> false);

    assertEquals(ChargingSessionLifecycleResult.Type.MANUAL_STOP_PUBLISH_FAILED_RESTORED, result.getType());
    assertEquals(ChargingPortState.CHARGING, fixture.port().snapshot().getState());
    assertTrue(result.isPortsChanged());
  }

  @Test
  void billingCompletionReleasesChargingPort() {
    Fixture fixture = new Fixture();
    fixture.startPort();
    RecordingBillingPublisher publisher = new RecordingBillingPublisher(true, true);

    ChargingSessionLifecycleResult result = fixture.lifecycle.completeBilling(1, publisher);

    assertEquals(ChargingSessionLifecycleResult.Type.BILLING_COMPLETED, result.getType());
    assertEquals(ChargingPortState.IDLE, fixture.port().snapshot().getState());
    assertEquals(1, publisher.activeResponses);
    assertEquals(0, publisher.idleResponses);
    assertTrue(result.isPortsChanged());
  }

  @Test
  void billingCompletionReleasesStoppedPort() {
    Fixture fixture = new Fixture();
    fixture.startPort();
    assertNotNull(fixture.port().tryStopFromCharging());
    RecordingBillingPublisher publisher = new RecordingBillingPublisher(true, true);

    ChargingSessionLifecycleResult result = fixture.lifecycle.completeBilling(1, publisher);

    assertEquals(ChargingSessionLifecycleResult.Type.BILLING_COMPLETED, result.getType());
    assertEquals(ChargingPortState.IDLE, fixture.port().snapshot().getState());
    assertEquals(1, publisher.activeResponses);
  }

  @Test
  void billingCompletionDoesNotReleaseNewSessionWhenOriginalSessionChangesBeforeCompletion() {
    Fixture fixture = new Fixture();
    fixture.startPort();
    RecordingBillingPublisher publisher = new RecordingBillingPublisher(true, true);

    ChargingSessionLifecycleResult result = fixture.lifecycle.completeBilling(
        1,
        snapshot -> {
          assertNotNull(fixture.port().finishBillingIfActive());
          if (fixture.port().tryStartChargingFromIdle(2, 30, 5, 1, 200) == null) {
            throw new IllegalStateException("测试端口未能进入新的充电状态");
          }
        },
        publisher
    );

    assertEquals(ChargingSessionLifecycleResult.Type.BILLING_IDLE_RESPONSE_SENT, result.getType());
    ChargingPortSnapshot currentSnapshot = fixture.port().snapshot();
    assertEquals(ChargingPortState.CHARGING, currentSnapshot.getState());
    assertEquals(200, currentSnapshot.getBalance());
    assertEquals(0, publisher.activeResponses);
    assertEquals(1, publisher.idleResponses);
    assertFalse(result.isPortsChanged());
  }

  @Test
  void startChargingUnknownPortPublishesFailureResponse() {
    Fixture fixture = new Fixture();
    RecordingStartPublisher publisher = new RecordingStartPublisher(true);

    ChargingSessionLifecycleResult result = fixture.lifecycle.startCharging(9, 2, 30, 5, 1, 100, publisher);

    assertEquals(ChargingSessionLifecycleResult.Type.START_REJECTED_PORT_NOT_FOUND, result.getType());
    assertEquals(1, publisher.calls);
    assertEquals(9, publisher.portNumber);
    assertFalse(publisher.success);
    assertTrue(result.isResponsePublished());
  }

  @Test
  void billingCompletionUnknownPortDoesNotPublishResponse() {
    Fixture fixture = new Fixture();
    RecordingBillingPublisher publisher = new RecordingBillingPublisher(true, true);

    ChargingSessionLifecycleResult result = fixture.lifecycle.completeBilling(9, publisher);

    assertEquals(ChargingSessionLifecycleResult.Type.BILLING_PORT_NOT_FOUND_RESPONSE_PENDING, result.getType());
    assertEquals(0, publisher.activeResponses);
    assertEquals(0, publisher.idleResponses);
    assertFalse(result.isResponsePublished());
  }

  private static final class Fixture {
    @NotNull
    private final ChargingStation station = new ChargingStation();
    @NotNull
    private final ChargingSessionLifecycle lifecycle;

    private Fixture() {
      station.resetPorts(1);
      lifecycle = new ChargingSessionLifecycle(station);
    }

    @NotNull
    private ChargingPort port() {
      ChargingPort port = station.findPort(1);
      if (port == null) {
        throw new IllegalStateException("测试端口不存在");
      }
      return port;
    }

    private void startPort() {
      if (port().tryStartChargingFromIdle(2, 30, 5, 1, 100) == null) {
        throw new IllegalStateException("测试端口未能进入充电状态");
      }
    }
  }

  private static final class RecordingStartPublisher implements ChargingSessionLifecycle.StartChargingResponsePublisher {
    private final boolean myPublishSucceeds;
    private int calls;
    private int portNumber;
    private boolean success;

    private RecordingStartPublisher(boolean publishSucceeds) {
      myPublishSucceeds = publishSucceeds;
    }

    @Override
    public boolean publishStartChargingResponse(int portNumber, boolean success) {
      calls++;
      this.portNumber = portNumber;
      this.success = success;
      return myPublishSucceeds;
    }
  }

  private static final class RecordingBillingPublisher implements ChargingSessionLifecycle.BillingResponsePublisher {
    private final boolean myActivePublishSucceeds;
    private final boolean myIdlePublishSucceeds;
    private int activeResponses;
    private int idleResponses;

    private RecordingBillingPublisher(boolean activePublishSucceeds, boolean idlePublishSucceeds) {
      myActivePublishSucceeds = activePublishSucceeds;
      myIdlePublishSucceeds = idlePublishSucceeds;
    }

    @Override
    public boolean publishActiveBillingResponse(@NotNull ChargingPortSnapshot snapshot) {
      activeResponses++;
      return myActivePublishSucceeds;
    }

    @Override
    public boolean publishIdleBillingResponse(@NotNull ChargingPortSnapshot snapshot) {
      idleResponses++;
      return myIdlePublishSucceeds;
    }
  }
}
