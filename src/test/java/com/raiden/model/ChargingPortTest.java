package com.raiden.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChargingPortTest {

  @Test
  void startsChargingOnlyFromIdle() {
    ChargingPort port = new ChargingPort(1);

    ChargingPortSnapshot started = port.tryStartChargingFromIdle(2, 30, 5, 1, 100);

    assertNotNull(started);
    assertEquals(ChargingPortState.CHARGING, started.getState());
    assertEquals(100, started.getBalance());
    assertNull(port.tryStartChargingFromIdle(2, 30, 5, 1, 100));

    assertNotNull(port.tryStopFromCharging());
    assertNull(port.tryStartChargingFromIdle(2, 30, 5, 1, 100));
  }

  @Test
  void stopsOnlyFromCharging() {
    ChargingPort port = new ChargingPort(1);

    assertNull(port.tryStopFromCharging());

    assertNotNull(port.tryStartChargingFromIdle(2, 30, 5, 1, 100));
    ChargingPortSnapshot stoppedSnapshot = port.tryStopFromCharging();

    assertNotNull(stoppedSnapshot);
    assertEquals(ChargingPortState.CHARGING, stoppedSnapshot.getState());
    assertEquals(ChargingPortState.STOPPED, port.snapshot().getState());
    assertNull(port.tryStopFromCharging());
  }

  @Test
  void restoresChargingOnlyForSameStoppedSession() {
    ChargingPort port = new ChargingPort(1);

    assertNotNull(port.tryStartChargingFromIdle(2, 30, 5, 1, 100));
    ChargingPortSnapshot stoppedSnapshot = port.tryStopFromCharging();

    assertNotNull(stoppedSnapshot);
    assertTrue(port.restoreChargingIfStillStopped(stoppedSnapshot));
    assertEquals(ChargingPortState.CHARGING, port.snapshot().getState());

    ChargingPortSnapshot staleStoppedSnapshot = port.tryStopFromCharging();
    assertNotNull(staleStoppedSnapshot);
    assertNotNull(port.finishBillingIfActive());

    assertFalse(port.restoreChargingIfStillStopped(staleStoppedSnapshot));
    assertEquals(ChargingPortState.IDLE, port.snapshot().getState());
  }

  @Test
  void resetsChargingOnlyForSameChargingSession() {
    ChargingPort port = new ChargingPort(1);

    ChargingPortSnapshot startedSnapshot = port.tryStartChargingFromIdle(2, 30, 5, 1, 100);

    assertNotNull(startedSnapshot);
    assertTrue(port.resetChargingIfSameSession(startedSnapshot));
    assertEquals(ChargingPortState.IDLE, port.snapshot().getState());

    ChargingPortSnapshot staleStartedSnapshot = port.tryStartChargingFromIdle(2, 30, 5, 1, 100);
    assertNotNull(staleStartedSnapshot);
    assertNotNull(port.tryStopFromCharging());

    assertFalse(port.resetChargingIfSameSession(staleStartedSnapshot));
    assertEquals(ChargingPortState.STOPPED, port.snapshot().getState());
  }

  @Test
  void finishesBillingOnlyWhenActiveAndResetsPort() {
    ChargingPort port = new ChargingPort(1);

    assertNull(port.finishBillingIfActive());

    assertNotNull(port.tryStartChargingFromIdle(2, 30, 5, 1, 100));
    ChargingPortSnapshot billingSnapshot = port.finishBillingIfActive();

    assertNotNull(billingSnapshot);
    assertEquals(ChargingPortState.CHARGING, billingSnapshot.getState());
    assertEquals(100, billingSnapshot.getBalance());
    assertEquals(ChargingPortState.IDLE, port.snapshot().getState());
    assertEquals(0, port.snapshot().getBalance());
  }

  @Test
  void finishesBillingOnlyForSameActiveSession() {
    ChargingPort port = new ChargingPort(1);

    ChargingPortSnapshot firstSession = port.tryStartChargingFromIdle(2, 30, 5, 1, 100);
    assertNotNull(firstSession);
    assertNotNull(port.finishBillingIfActive());
    assertNotNull(port.tryStartChargingFromIdle(2, 30, 5, 1, 200));

    assertNull(port.finishBillingIfSameSession(firstSession));
    ChargingPortSnapshot currentSnapshot = port.snapshot();
    assertEquals(ChargingPortState.CHARGING, currentSnapshot.getState());
    assertEquals(200, currentSnapshot.getBalance());
  }
}
