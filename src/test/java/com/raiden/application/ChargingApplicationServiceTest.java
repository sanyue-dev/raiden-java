package com.raiden.application;

import com.raiden.model.ChargingPort;
import com.raiden.model.ChargingPortState;
import com.raiden.model.ChargingStation;
import com.raiden.protocol.RaidenProtocolCodec;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChargingApplicationServiceTest {

  @Test
  void startsChargingAfterStartChargingResponsePublishSucceeds() {
    Fixture fixture = new Fixture(true);

    fixture.service.handleIncomingPayload(startChargingPayload());

    assertEquals(ChargingPortState.CHARGING, fixture.port().snapshot().getState());
    assertEquals(1, fixture.listener.portsChangedCount);
    assertTrue(fixture.publisher.published.stream().anyMatch(json -> json.contains("\"cdz\":101")));
    assertTrue(fixture.listener.logs.stream().anyMatch(log -> log.contains("开始充电")));
  }

  @Test
  void keepsIdleWhenStartChargingResponsePublishFails() {
    Fixture fixture = new Fixture(false);

    fixture.service.handleIncomingPayload(startChargingPayload());

    assertEquals(ChargingPortState.IDLE, fixture.port().snapshot().getState());
    assertEquals(0, fixture.listener.portsChangedCount);
    assertTrue(fixture.listener.logs.stream().anyMatch(log -> log.contains("启动充电响应发送失败")));
    assertTrue(fixture.publisher.published.stream().anyMatch(json -> json.contains("\"data\":\"1,1\"")));
  }

  @Test
  void publishesStartChargingFailureResponseWhenPortIsNotIdle() {
    Fixture fixture = new Fixture(true);
    fixture.startPort();

    fixture.service.handleIncomingPayload(startChargingPayload());

    assertEquals(ChargingPortState.CHARGING, fixture.port().snapshot().getState());
    assertTrue(fixture.publisher.published.stream().anyMatch(json -> json.contains("\"data\":\"1,0\"")));
    assertTrue(fixture.listener.logs.stream().anyMatch(log -> log.contains("已发送启动充电失败响应")));
  }

  @Test
  void manualCloseMovesChargingPortToStoppedWhenPublishSucceeds() {
    Fixture fixture = new Fixture(true);
    fixture.startPort();

    fixture.service.manualClose(1);

    assertEquals(ChargingPortState.STOPPED, fixture.port().snapshot().getState());
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
    assertTrue(fixture.publisher.published.stream().anyMatch(json -> json.contains("\"data\":\"1,0,0,0,0,0,100\"")));
    assertTrue(fixture.listener.logs.stream().anyMatch(log -> log.contains("计费已结束")));
  }

  @Test
  void endBillingLogsPendingResponseFormatWhenPortDoesNotExist() {
    Fixture fixture = new Fixture(true);

    fixture.service.handleIncomingPayload("{\"cdz\":104,\"msg_id\":\"billing-404\",\"data\":\"9\"}");

    assertTrue(fixture.publisher.published.isEmpty());
    assertTrue(fixture.listener.logs.stream().anyMatch(log -> log.contains("不存在响应格式待服务端协议确认")));
  }

  @Test
  void logsFailureTypeMessageAndPayloadWhenIncomingPayloadCannotBeHandled() {
    Fixture fixture = new Fixture(true);
    String payload = "{\"cdz\":101,\"msg_id\":\"start-1\"}";

    fixture.service.handleIncomingPayload(payload);

    assertTrue(fixture.listener.logs.stream().anyMatch(log ->
        log.contains("type=IllegalArgumentException") &&
        log.contains("message=未找到字段：\"data\"") &&
        log.contains("payload=" + payload)
    ));
  }

  @Test
  void localMsgIdCanContinueAcrossApplicationServices() {
    AtomicLong msgId = new AtomicLong(0);
    Fixture first = new Fixture(true, msgId);
    first.startPort();
    first.service.publishPeriodicReports();

    Fixture second = new Fixture(true, msgId);
    second.startPort();
    second.service.manualClose(1);

    assertTrue(first.publisher.published.stream().anyMatch(json -> json.contains("\"msg_id\":\"0\"")));
    assertTrue(second.publisher.published.stream().anyMatch(json -> json.contains("\"msg_id\":\"1\"")));
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
      this(publishSucceeds, new AtomicLong(0));
    }

    private Fixture(boolean publishSucceeds, @NotNull AtomicLong msgId) {
      station.resetPorts(1);
      publisher = new RecordingPublisher(publishSucceeds);
      service = new ChargingApplicationService(station, new RaidenProtocolCodec(), listener, msgId);
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
