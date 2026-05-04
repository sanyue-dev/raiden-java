package com.raiden.domain;

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

    assertNotNull(port.tryBeginClosingFromCharging());
    assertNull(port.tryStartChargingFromIdle(2, 30, 5, 1, 100));
  }

  @Test
  void beginsClosingOnlyFromCharging() {
    ChargingPort port = new ChargingPort(1);

    assertNull(port.tryBeginClosingFromCharging());

    assertNotNull(port.tryStartChargingFromIdle(2, 30, 5, 1, 100));
    ChargingPortSnapshot closingSnapshot = port.tryBeginClosingFromCharging();

    assertNotNull(closingSnapshot);
    assertEquals(ChargingPortState.CHARGING, closingSnapshot.getState());
    assertEquals(ChargingPortState.CLOSING, port.snapshot().getState());
    assertNull(port.tryBeginClosingFromCharging());
  }

  @Test
  void restoresChargingOnlyForSameClosingSession() {
    ChargingPort port = new ChargingPort(1);

    assertNotNull(port.tryStartChargingFromIdle(2, 30, 5, 1, 100));
    ChargingPortSnapshot closingSnapshot = port.tryBeginClosingFromCharging();

    assertNotNull(closingSnapshot);
    assertTrue(port.restoreChargingIfStillClosing(closingSnapshot));
    assertEquals(ChargingPortState.CHARGING, port.snapshot().getState());

    ChargingPortSnapshot staleClosingSnapshot = port.tryBeginClosingFromCharging();
    assertNotNull(staleClosingSnapshot);
    assertNotNull(port.finishBillingIfActive());

    assertFalse(port.restoreChargingIfStillClosing(staleClosingSnapshot));
    assertEquals(ChargingPortState.IDLE, port.snapshot().getState());
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
}
