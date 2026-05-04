package com.raiden.mqtt;

import com.raiden.domain.ChargingPort;
import com.raiden.domain.ChargingPortState;
import com.raiden.domain.ChargingStation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChargingApplicationServiceTest {

  @Test
  void startsChargingAfterAckPublishSucceeds() {
    Fixture fixture = new Fixture(true);

    fixture.service.handleIncomingPayload(startChargingPayload());

    assertEquals(ChargingPortState.CHARGING, fixture.port().snapshot().getState());
    assertEquals(1, fixture.listener.portsChangedCount);
    assertTrue(fixture.publisher.published.stream().anyMatch(json -> json.contains("\"cdz\":101")));
    assertTrue(fixture.listener.logs.stream().anyMatch(log -> log.contains("开始充电")));
  }

  @Test
  void keepsIdleWhenAckPublishFails() {
    Fixture fixture = new Fixture(false);

    fixture.service.handleIncomingPayload(startChargingPayload());

    assertEquals(ChargingPortState.IDLE, fixture.port().snapshot().getState());
    assertEquals(0, fixture.listener.portsChangedCount);
    assertTrue(fixture.listener.logs.stream().anyMatch(log -> log.contains("ACK 发送失败")));
  }

  @Test
  void manualCloseMovesChargingPortToClosingWhenPublishSucceeds() {
    Fixture fixture = new Fixture(true);
    fixture.startPort();

    fixture.service.manualClose(1);

    assertEquals(ChargingPortState.CLOSING, fixture.port().snapshot().getState());
    assertEquals(1, fixture.listener.portsChangedCount);
    assertTrue(fixture.publisher.published.stream().anyMatch(json -> json.contains("\"cdz\":203")));
  }

  @Test
  void manualCloseRestoresChargingWhenPublishFails() {
    Fixture fixture = new Fixture(false);
    fixture.startPort();

    fixture.service.manualClose(1);

    assertEquals(ChargingPortState.CHARGING, fixture.port().snapshot().getState());
    assertEquals(1, fixture.listener.portsChangedCount);
    assertTrue(fixture.listener.logs.stream().anyMatch(log -> log.contains("已恢复为充电中状态")));
  }

  @Test
  void endBillingPublishesResponseAndResetsActivePort() {
    Fixture fixture = new Fixture(true);
    fixture.startPort();

    fixture.service.handleIncomingPayload("{\"cdz\":104,\"msg_id\":\"billing-1\",\"data\":\"1\"}");

    assertEquals(ChargingPortState.IDLE, fixture.port().snapshot().getState());
    assertEquals(1, fixture.listener.portsChangedCount);
    assertTrue(fixture.publisher.published.stream().anyMatch(json -> json.contains("\"cdz\":104")));
    assertTrue(fixture.listener.logs.stream().anyMatch(log -> log.contains("计费已结束")));
  }

  @NotNull
  private static String startChargingPayload() {
    return "{\"cdz\":101,\"msg_id\":\"start-1\",\"data\":\"1,2,30,0,5,1,100\"}";
  }

  private static final class Fixture {
    @NotNull
    private final ChargingStation station = new ChargingStation();
    @NotNull
    private final RecordingPublisher publisher;
    @NotNull
    private final RecordingListener listener = new RecordingListener();
    @NotNull
    private final ChargingApplicationService service;

    private Fixture(boolean publishSucceeds) {
      station.resetPorts(1);
      publisher = new RecordingPublisher(publishSucceeds);
      service = new ChargingApplicationService(station, new RaidenProtocolCodec(), listener);
      service.setMessagePublisher(publisher);
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

  private static final class RecordingPublisher implements MessagePublisher {
    @NotNull
    private final List<String> published = new ArrayList<>();
    private final boolean myPublishSucceeds;

    private RecordingPublisher(boolean publishSucceeds) {
      myPublishSucceeds = publishSucceeds;
    }

    @Override
    public boolean publish(@NotNull String json) {
      published.add(json);
      return myPublishSucceeds;
    }
  }

  private static final class RecordingListener implements ChargingApplicationListener {
    @NotNull
    private final List<String> logs = new ArrayList<>();
    private int portsChangedCount;

    @Override
    public void onApplicationLog(@NotNull String message) {
      logs.add(message);
    }

    @Override
    public void onPortsChanged() {
      portsChangedCount++;
    }
  }
}
