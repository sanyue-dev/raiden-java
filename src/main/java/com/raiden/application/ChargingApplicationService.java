package com.raiden.application;

import com.raiden.domain.ChargingPort;
import com.raiden.domain.ChargingPortSnapshot;
import com.raiden.domain.ChargingPortState;
import com.raiden.domain.ChargingStation;
import com.raiden.protocol.RaidenMessage;
import com.raiden.protocol.RaidenProtocolCodec;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;

public final class ChargingApplicationService {

  @NotNull
  private final ChargingStation myStation;
  @NotNull
  private final RaidenProtocolCodec myCodec;
  @NotNull
  private final MessagePublisher myPublisher;
  @NotNull
  private final ChargingApplicationListener myListener;
  @NotNull
  private final AtomicLong myMsgId = new AtomicLong(0);

  public ChargingApplicationService(@NotNull ChargingStation station,
                                    @NotNull RaidenProtocolCodec codec,
                                    @NotNull MessagePublisher publisher,
                                    @NotNull ChargingApplicationListener listener) {
    myStation = station;
    myCodec = codec;
    myPublisher = publisher;
    myListener = listener;
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
    String[] params = data.split(",");
    int portNum = Integer.parseInt(params[0].trim());
    int orderType = Integer.parseInt(params[1].trim());
    int duration = Integer.parseInt(params[2].trim());
    int kwhFee = Integer.parseInt(params[4].trim());
    int unit = Integer.parseInt(params[5].trim());
    int balance = Integer.parseInt(params[6].trim());

    ChargingPort port = myStation.findPort(portNum);
    if (port == null) {
      myListener.onApplicationLog("未找到端口 " + portNum);
      return;
    }

    ChargingPortSnapshot snapshot = port.snapshot();
    if (snapshot.getState() != ChargingPortState.IDLE) {
      myListener.onApplicationLog("端口 " + portNum + " 当前不是空闲状态，当前状态：" + snapshot.getState().getLabel());
      return;
    }

    port.startCharging(orderType, duration, kwhFee, unit, balance);
    myPublisher.publish(myCodec.buildAckJson(port.snapshot(), msgId));

    myListener.onApplicationLog("端口 " + portNum + " 开始充电，余额=" + balance);
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
