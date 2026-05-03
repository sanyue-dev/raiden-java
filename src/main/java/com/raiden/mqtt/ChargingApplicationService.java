package com.raiden.mqtt;

import com.raiden.domain.ChargingPort;
import com.raiden.domain.ChargingPortSnapshot;
import com.raiden.domain.ChargingPortState;
import com.raiden.domain.ChargingStation;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;

public final class ChargingApplicationService {

  @NotNull
  private final ChargingStation myStation;
  @NotNull
  private final RaidenProtocolCodec myCodec;
  @NotNull
  private volatile MessagePublisher myPublisher;
  @NotNull
  private final ChargingApplicationListener myListener;
  @NotNull
  private final AtomicLong myMsgId = new AtomicLong(0);

  public ChargingApplicationService(@NotNull ChargingStation station,
                                    @NotNull RaidenProtocolCodec codec,
                                    @NotNull ChargingApplicationListener listener) {
    myStation = station;
    myCodec = codec;
    myListener = listener;
  }

  public void setMessagePublisher(@NotNull MessagePublisher publisher) {
    myPublisher = publisher;
  }

  public void handleIncomingPayload(@NotNull String payload) {
    try {
      RaidenMessage message = myCodec.parse(payload);
      int cdz = message.getCdz();
      String msgId = message.getMsgId();
      String data = message.getData();

      myListener.onApplicationLog("收到消息 cdz=" + cdz + " msg_id=" + msgId + " data=" + data);

      if (cdz == RaidenProtocolCodec.CDZ_START_CHARGING) {
        handleStartCharging(data, msgId);
      }
      else if (cdz == RaidenProtocolCodec.CDZ_END_BILLING) {
        handleEndBilling(data, msgId);
      }
    }
    catch (Exception e) {
      myListener.onApplicationLog("处理消息失败：" + e.getMessage());
    }
  }

  public void manualClose(int portNumber) {
    ChargingPort port = myStation.findPort(portNumber);
    if (port == null) {
      myListener.onApplicationLog("端口 " + portNumber + " 当前不是充电中状态");
      return;
    }

    ChargingPortSnapshot snapshot = port.snapshot();
    if (snapshot.getState() != ChargingPortState.CHARGING) {
      myListener.onApplicationLog("端口 " + portNumber + " 当前不是充电中状态");
      return;
    }

    if (!myPublisher.publish(myCodec.buildManualCloseJson(snapshot, nextMsgId()))) {
      myListener.onApplicationLog("端口 " + portNumber + " 手动结束订单发送失败");
      return;
    }

    port.beginClosing();
    myListener.onApplicationLog("端口 " + portNumber + " 已发送手动结束订单");
    myListener.onPortsChanged();
  }

  public void publishPeriodicReports() {
    int sentCount = 0;
    for (ChargingPortSnapshot snapshot : myStation.getPortSnapshots()) {
      if (snapshot.getState() != ChargingPortState.IDLE && myPublisher.publish(myCodec.buildReportJson(snapshot, nextMsgId()))) {
        sentCount++;
      }
    }
    if (sentCount > 0) {
      myListener.onApplicationLog("已发送周期状态上报：" + sentCount + " 个端口");
    }
  }

  private void handleStartCharging(@NotNull String data, @NotNull String msgId) {
    RaidenProtocolCodec.StartChargingParams params = myCodec.parseStartChargingData(data);

    ChargingPort port = myStation.findPort(params.portNum);
    if (port == null) {
      myListener.onApplicationLog("未找到端口 " + params.portNum);
      return;
    }

    ChargingPortSnapshot snapshot = port.snapshot();
    if (snapshot.getState() != ChargingPortState.IDLE) {
      myListener.onApplicationLog("端口 " + params.portNum + " 当前不是空闲状态，当前状态：" + snapshot.getState().name());
      return;
    }

    port.startCharging(params.orderType, params.duration, params.kwhFee, params.unit, params.balance);
    myPublisher.publish(myCodec.buildAckJson(port.snapshot(), msgId));

    myListener.onApplicationLog("端口 " + params.portNum + " 开始充电，余额=" + params.balance);
    myListener.onPortsChanged();
  }

  private void handleEndBilling(@NotNull String data, @NotNull String msgId) {
    int portNum = Integer.parseInt(data.trim());
    ChargingPort port = myStation.findPort(portNum);
    if (port == null) {
      myListener.onApplicationLog("未找到端口 " + portNum);
      return;
    }

    ChargingPortSnapshot snapshot = port.snapshot();
    if (snapshot.getState() == ChargingPortState.CHARGING || snapshot.getState() == ChargingPortState.CLOSING) {
      waitBeforeEndBilling();

      myPublisher.publish(myCodec.buildEndBillingJson(port.snapshot(), msgId));
      port.reset();
      myListener.onApplicationLog("端口 " + portNum + " 计费已结束");
      myListener.onPortsChanged();
    }
    else {
      myPublisher.publish(myCodec.buildIdleEndBillingJson(snapshot, msgId));
      myListener.onApplicationLog("端口 " + portNum + " 为空闲状态，已发送空闲响应");
    }
  }

  private void waitBeforeEndBilling() {
    try {
      Thread.sleep(200);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      myListener.onApplicationLog("计费结束等待被中断");
    }
  }

  private long nextMsgId() {
    return myMsgId.getAndIncrement();
  }
}
