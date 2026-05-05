package com.raiden.application;

import com.raiden.application.session.ChargingSessionLifecycle;
import com.raiden.application.session.ChargingSessionLifecycleResult;
import com.raiden.model.ChargingPortSnapshot;
import com.raiden.model.ChargingPortState;
import com.raiden.model.ChargingStation;
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
  private volatile MessagePublisher myPublisher;
  @NotNull
  private final ChargingApplicationListener myListener;
  @NotNull
  private final AtomicLong myMsgId;
  @NotNull
  private final ChargingSessionLifecycle mySessionLifecycle;

  public ChargingApplicationService(@NotNull ChargingStation station,
                                    @NotNull RaidenProtocolCodec codec,
                                    @NotNull ChargingApplicationListener listener) {
    this(station, codec, listener, new AtomicLong(0));
  }

  public ChargingApplicationService(@NotNull ChargingStation station,
                                    @NotNull RaidenProtocolCodec codec,
                                    @NotNull ChargingApplicationListener listener,
                                    @NotNull AtomicLong msgId) {
    myStation = station;
    myCodec = codec;
    myListener = listener;
    myMsgId = msgId;
    mySessionLifecycle = new ChargingSessionLifecycle(station);
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
      myListener.onApplicationLog(
          "处理消息失败 type=" + e.getClass().getSimpleName() +
          " message=" + e.getMessage() +
          " payload=" + payload
      );
    }
  }

  public void manualClose(int portNumber) {
    ChargingSessionLifecycleResult result = mySessionLifecycle.manualStop(
        portNumber,
        snapshot -> myPublisher.publish(myCodec.buildManualCloseJson(snapshot, nextMsgId()))
    );
    logManualStopResult(result);
    notifyPortsChanged(result);
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

    ChargingSessionLifecycleResult result = mySessionLifecycle.startCharging(
        params.portNum,
        params.orderType,
        params.duration,
        params.kwhFee,
        params.unit,
        params.balance,
        (portNumber, success) -> myPublisher.publish(myCodec.buildStartChargingResponseJson(portNumber, msgId, success))
    );
    logStartChargingResult(result, params.balance);
    notifyPortsChanged(result);
  }

  private void handleEndBilling(@NotNull String data, @NotNull String msgId) {
    int portNum = Integer.parseInt(data.trim());
    if (mySessionLifecycle.willCompleteActiveBilling(portNum)) {
      waitBeforeEndBilling();
    }

    ChargingSessionLifecycleResult result = mySessionLifecycle.completeBilling(
        portNum,
        new ChargingSessionLifecycle.BillingResponsePublisher() {
          @Override
          public boolean publishActiveBillingResponse(@NotNull ChargingPortSnapshot snapshot) {
            return myPublisher.publish(myCodec.buildEndBillingJson(snapshot, msgId));
          }

          @Override
          public boolean publishIdleBillingResponse(@NotNull ChargingPortSnapshot snapshot) {
            return myPublisher.publish(myCodec.buildIdleEndBillingJson(snapshot, msgId));
          }
        }
    );
    logBillingResult(result);
    notifyPortsChanged(result);
  }

  private void logStartChargingResult(@NotNull ChargingSessionLifecycleResult result, int balance) {
    int portNum = result.getPortNumber();
    switch (result.getType()) {
      case START_ACCEPTED:
        myListener.onApplicationLog("端口 " + portNum + " 开始充电，余额=" + balance);
        break;
      case START_REJECTED_PORT_NOT_FOUND:
        myListener.onApplicationLog("未找到端口 " + portNum);
        logStartChargingFailureResponse(result);
        break;
      case START_REJECTED_PORT_NOT_IDLE:
        ChargingPortSnapshot currentSnapshot = result.getCurrentSnapshot();
        if (currentSnapshot == null) {
          throw new IllegalStateException("启动充电拒绝结果缺少当前端口快照");
        }
        myListener.onApplicationLog("端口 " + portNum + " 当前不是空闲状态，当前状态：" + currentSnapshot.getState().name());
        logStartChargingFailureResponse(result);
        break;
      case START_RESPONSE_PUBLISH_FAILED_ROLLED_BACK:
        myListener.onApplicationLog("端口 " + portNum + " 启动充电响应发送失败，已回滚为空闲");
        break;
      case START_RESPONSE_PUBLISH_FAILED_NOT_ROLLED_BACK:
        myListener.onApplicationLog("端口 " + portNum + " 启动充电响应发送失败，状态已变化，未执行回滚");
        break;
      default:
        throw new IllegalStateException("未知启动充电结果：" + result.getType());
    }
  }

  private void logStartChargingFailureResponse(@NotNull ChargingSessionLifecycleResult result) {
    int portNum = result.getPortNumber();
    if (result.isResponsePublished()) {
      myListener.onApplicationLog("端口 " + portNum + " 已发送启动充电失败响应");
    }
    else {
      myListener.onApplicationLog("端口 " + portNum + " 启动充电失败响应发送失败");
    }
  }

  private void logManualStopResult(@NotNull ChargingSessionLifecycleResult result) {
    int portNum = result.getPortNumber();
    switch (result.getType()) {
      case MANUAL_STOP_REJECTED_PORT_NOT_FOUND:
      case MANUAL_STOP_REJECTED_NOT_CHARGING:
        myListener.onApplicationLog("端口 " + portNum + " 当前不是充电中状态");
        break;
      case MANUAL_STOPPED:
        myListener.onApplicationLog("端口 " + portNum + " 已发送手动停充通知");
        break;
      case MANUAL_STOP_PUBLISH_FAILED_RESTORED:
        myListener.onApplicationLog("端口 " + portNum + " 手动停充通知发送失败");
        myListener.onApplicationLog("端口 " + portNum + " 已恢复为充电中状态");
        break;
      case MANUAL_STOP_PUBLISH_FAILED_NOT_RESTORED:
        myListener.onApplicationLog("端口 " + portNum + " 手动停充通知发送失败");
        myListener.onApplicationLog("端口 " + portNum + " 状态已变化，未执行恢复");
        break;
      default:
        throw new IllegalStateException("未知手动停充结果：" + result.getType());
    }
  }

  private void logBillingResult(@NotNull ChargingSessionLifecycleResult result) {
    int portNum = result.getPortNumber();
    switch (result.getType()) {
      case BILLING_COMPLETED:
        myListener.onApplicationLog("端口 " + portNum + " 计费已结束");
        break;
      case BILLING_RESPONSE_PUBLISH_FAILED:
        myListener.onApplicationLog("端口 " + portNum + " 计费结束响应发送失败");
        myListener.onApplicationLog("端口 " + portNum + " 计费已结束");
        break;
      case BILLING_IDLE_RESPONSE_SENT:
        if (result.getCurrentSnapshot() == null) {
          myListener.onApplicationLog("端口 " + portNum + " 为空闲状态，已发送空闲响应");
        }
        else {
          myListener.onApplicationLog("端口 " + portNum + " 状态已变化为空闲，已发送空闲响应");
        }
        break;
      case BILLING_IDLE_RESPONSE_PUBLISH_FAILED:
        myListener.onApplicationLog("端口 " + portNum + " 空闲计费响应发送失败");
        break;
      case BILLING_PORT_NOT_FOUND_RESPONSE_PENDING:
        myListener.onApplicationLog("未找到端口 " + portNum);
        myListener.onApplicationLog("端口 " + portNum + " 不存在响应格式待服务端协议确认，未发送计费结束响应");
        break;
      default:
        throw new IllegalStateException("未知计费结束结果：" + result.getType());
    }
  }

  private void notifyPortsChanged(@NotNull ChargingSessionLifecycleResult result) {
    if (result.isPortsChanged()) {
      myListener.onPortsChanged();
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
