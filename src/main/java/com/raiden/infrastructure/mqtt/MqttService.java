package com.raiden.infrastructure.mqtt;

import com.raiden.application.ChargingApplicationListener;
import com.raiden.application.ChargingApplicationService;
import com.raiden.application.MessagePublisher;
import com.raiden.domain.ChargingStation;
import com.raiden.platform.Disposable;
import com.raiden.protocol.RaidenProtocolCodec;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MqttService implements MessagePublisher, Disposable {

  public interface MqttConnectionListener {
    void onMqttConnected(@NotNull String brokerUrl, @NotNull String clientId);

    void onMqttDisconnected(@Nullable Throwable cause);

    void onMqttLog(@NotNull String message);
  }

  @Nullable
  private volatile MqttClient myClient;
  @NotNull
  private final String myBrokerUrl;
  @NotNull
  private final String myClientId;
  @NotNull
  private final ChargingApplicationService myApplicationService;
  @NotNull
  private final MqttConnectionListener myConnectionListener;
  @NotNull
  private final ExecutorService myMessageExecutor;
  @Nullable
  private ScheduledExecutorService myReportTimer;
  @NotNull
  private final String mySubscribeTopic;
  @NotNull
  private final String myPublishTopic;

  public MqttService(@NotNull String brokerUrl,
                     @NotNull String clientId,
                     @NotNull ChargingStation station,
                     @NotNull ChargingApplicationListener applicationListener,
                     @NotNull MqttConnectionListener connectionListener) {
    myBrokerUrl = brokerUrl;
    myClientId = clientId;
    myConnectionListener = connectionListener;
    myApplicationService = new ChargingApplicationService(station, new RaidenProtocolCodec(), this, applicationListener);
    myMessageExecutor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "mqtt-message-thread"));
    mySubscribeTopic = "cdz/" + clientId;
    myPublishTopic = "upload/cdz/" + clientId;
  }

  public void connect() throws MqttException {
    myClient = new MqttClient(myBrokerUrl, myClientId, new MemoryPersistence());
    MqttConnectOptions options = new MqttConnectOptions();
    options.setAutomaticReconnect(false);
    options.setCleanSession(true);
    options.setConnectionTimeout(10);
    options.setKeepAliveInterval(60);
    options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);

    myClient.setCallback(new MqttCallback() {
      @Override
      public void connectionLost(@Nullable Throwable cause) {
        stopReportTimer();
        stopMessageExecutor();
        myClient = null;
        myConnectionListener.onMqttLog("连接已中断：" + cause);
        myConnectionListener.onMqttDisconnected(cause);
      }

      @Override
      public void messageArrived(@Nullable String topic, @Nullable MqttMessage message) {
        if (message == null) return;
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        myMessageExecutor.execute(() -> myApplicationService.handleIncomingPayload(payload));
      }

      @Override
      public void deliveryComplete(@Nullable IMqttDeliveryToken token) {}
    });

    myClient.connect(options);
    myClient.subscribe(mySubscribeTopic, 0);

    myReportTimer = Executors.newSingleThreadScheduledExecutor();
    myReportTimer.scheduleAtFixedRate(myApplicationService::publishPeriodicReports, 60, 60, TimeUnit.SECONDS);

    myConnectionListener.onMqttConnected(myBrokerUrl, myClientId);
  }

  public void disconnect() {
    try {
      stopReportTimer();
      stopMessageExecutor();
      if (myClient != null && myClient.isConnected()) {
        myClient.disconnect();
      }
    }
    catch (MqttException e) {
      myConnectionListener.onMqttLog("断开连接失败：" + e.getMessage());
    }
  }

  public void closeForcibly() {
    MqttClient client = myClient;
    myClient = null;
    stopReportTimer();
    stopMessageExecutor();
    if (client != null) {
      try { client.disconnectForcibly(0, 0); } catch (Exception ignored) {}
      try { client.close(); } catch (Exception ignored) {}
    }
  }

  public void manualClose(int portNumber) {
    myApplicationService.manualClose(portNumber);
  }

  private void stopReportTimer() {
    if (myReportTimer != null) {
      myReportTimer.shutdownNow();
      myReportTimer = null;
    }
  }

  private void stopMessageExecutor() {
    myMessageExecutor.shutdownNow();
  }

  @Override
  public boolean publish(@NotNull String json) {
    if (myClient == null || !myClient.isConnected()) {
      myConnectionListener.onMqttLog("发布消息失败：MQTT 未连接");
      return false;
    }
    try {
      myClient.publish(myPublishTopic, json.getBytes(StandardCharsets.UTF_8), 0, false);
      return true;
    }
    catch (MqttException e) {
      myConnectionListener.onMqttLog("发布消息失败：" + e.getMessage());
      return false;
    }
  }

  @Override
  public void dispose() {
    closeForcibly();
  }
}
