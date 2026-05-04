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

    ChargingPortSnapshot snapshot = port.tryBeginClosingFromCharging();
    if (snapshot == null) {
      myListener.onApplicationLog("端口 " + portNumber + " 当前不是充电中状态");
      return;
    }

    if (!myPublisher.publish(myCodec.buildManualCloseJson(snapshot, nextMsgId()))) {
      myListener.onApplicationLog("端口 " + portNumber + " 手动结束订单发送失败");
      if (port.restoreChargingIfStillClosing(snapshot)) {
        myListener.onApplicationLog("端口 " + portNumber + " 已恢复为充电中状态");
        myListener.onPortsChanged();
      }
      else {
        myListener.onApplicationLog("端口 " + portNumber + " 状态已变化，未执行恢复");
      }
      return;
    }

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

    if (!myPublisher.publish(myCodec.buildAckJson(snapshot, msgId))) {
      myListener.onApplicationLog("端口 " + params.portNum + " 启动充电 ACK 发送失败，未进入充电状态");
      return;
    }

    ChargingPortSnapshot startedSnapshot = port.tryStartChargingFromIdle(
        params.orderType,
        params.duration,
        params.kwhFee,
        params.unit,
        params.balance
    );
    if (startedSnapshot == null) {
      myListener.onApplicationLog("端口 " + params.portNum + " ACK 已发送，但端口状态已变化，未进入充电状态");
      return;
    }

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

    ChargingPortSnapshot currentSnapshot = port.snapshot();
    if (currentSnapshot.getState() == ChargingPortState.CHARGING || currentSnapshot.getState() == ChargingPortState.CLOSING) {
      waitBeforeEndBilling();

      ChargingPortSnapshot billingSnapshot = port.finishBillingIfActive();
      if (billingSnapshot == null) {
        ChargingPortSnapshot idleSnapshot = port.snapshot();
        if (myPublisher.publish(myCodec.buildIdleEndBillingJson(idleSnapshot, msgId))) {
          myListener.onApplicationLog("端口 " + portNum + " 状态已变化为空闲，已发送空闲响应");
        }
        else {
          myListener.onApplicationLog("端口 " + portNum + " 空闲计费响应发送失败");
        }
        return;
      }

      if (!myPublisher.publish(myCodec.buildEndBillingJson(billingSnapshot, msgId))) {
        myListener.onApplicationLog("端口 " + portNum + " 计费结束响应发送失败");
      }
      myListener.onApplicationLog("端口 " + portNum + " 计费已结束");
      myListener.onPortsChanged();
    }
    else {
      if (myPublisher.publish(myCodec.buildIdleEndBillingJson(currentSnapshot, msgId))) {
        myListener.onApplicationLog("端口 " + portNum + " 为空闲状态，已发送空闲响应");
      }
      else {
        myListener.onApplicationLog("端口 " + portNum + " 空闲计费响应发送失败");
      }
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
