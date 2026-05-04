package com.raiden.mqtt;

import com.raiden.platform.Disposable;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class MqttService implements MessagePublisher, Disposable {

  @Nullable
  private volatile MqttAsyncClient myClient;
  @NotNull
  private final String myBrokerUrl;
  @NotNull
  private final String myClientId;
  @NotNull
  private final ChargingApplicationService myApplicationService;
  @NotNull
  private final ConnectionListener myConnectionListener;
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
                     @NotNull ChargingApplicationService applicationService,
                     @NotNull ConnectionListener connectionListener) {
    myBrokerUrl = brokerUrl;
    myClientId = clientId;
    myConnectionListener = connectionListener;
    myApplicationService = applicationService;
    myMessageExecutor = Executors.newSingleThreadExecutor(daemonThreadFactory("mqtt-message-thread"));
    mySubscribeTopic = "cdz/" + clientId;
    myPublishTopic = "upload/cdz/" + clientId;
  }

  public void connect() throws MqttException {
    MqttAsyncClient client = new MqttAsyncClient(myBrokerUrl, myClientId, new MemoryPersistence());
    myClient = client;
    MqttConnectOptions options = new MqttConnectOptions();
    options.setAutomaticReconnect(false);
    options.setCleanSession(true);
    options.setConnectionTimeout(10);
    options.setKeepAliveInterval(60);
    options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);

    client.setCallback(new MqttCallback() {
      @Override
      public void connectionLost(@Nullable Throwable cause) {
        cleanupClient(client);
        myConnectionListener.onMqttLog("连接已中断：" + cause);
        myConnectionListener.onMqttConnectionStatusChanged(MqttConnectionStatus.CONNECTION_LOST, cause);
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

    myConnectionListener.onMqttConnectionStatusChanged(MqttConnectionStatus.CONNECTING, null);
    client.connect(options, null, new IMqttActionListener() {
      @Override
      public void onSuccess(@Nullable IMqttToken asyncActionToken) {
        subscribeAfterConnect(client);
      }

      @Override
      public void onFailure(@Nullable IMqttToken asyncActionToken, @Nullable Throwable exception) {
        cleanupClient(client);
        myConnectionListener.onMqttConnectionStatusChanged(
            MqttConnectionStatus.CONNECTION_FAILED,
            exception == null ? new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION) : exception
        );
      }
    });
  }

  public void disconnect() {
    myConnectionListener.onMqttConnectionStatusChanged(MqttConnectionStatus.DISCONNECTING, null);
    MqttAsyncClient client = myClient;
    stopReportTimer();
    stopMessageExecutor();
    if (client == null) {
      myConnectionListener.onMqttConnectionStatusChanged(MqttConnectionStatus.DISCONNECTED, null);
      return;
    }
    try {
      if (client.isConnected()) {
        client.disconnect(0, null, new IMqttActionListener() {
          @Override
          public void onSuccess(@Nullable IMqttToken asyncActionToken) {
            cleanupClient(client);
            myConnectionListener.onMqttConnectionStatusChanged(MqttConnectionStatus.DISCONNECTED, null);
          }

          @Override
          public void onFailure(@Nullable IMqttToken asyncActionToken, @Nullable Throwable exception) {
            myConnectionListener.onMqttLog("断开连接失败：" + messageOf(exception) + "，正在强制关闭");
            closeForcibly();
            myConnectionListener.onMqttConnectionStatusChanged(MqttConnectionStatus.DISCONNECTED, exception);
          }
        });
      }
      else {
        cleanupClient(client);
        myConnectionListener.onMqttConnectionStatusChanged(MqttConnectionStatus.DISCONNECTED, null);
      }
    }
    catch (MqttException e) {
      myConnectionListener.onMqttLog("断开连接失败：" + e.getMessage() + "，正在强制关闭");
      closeForcibly();
      myConnectionListener.onMqttConnectionStatusChanged(MqttConnectionStatus.DISCONNECTED, e);
    }
  }

  public void closeForcibly() {
    MqttAsyncClient client = myClient;
    if (client != null) {
      closeClientForcibly(client);
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
    MqttAsyncClient client = myClient;
    if (client == null || !client.isConnected()) {
      myConnectionListener.onMqttLog("发布消息失败：MQTT 未连接");
      return false;
    }
    try {
      client.publish(myPublishTopic, json.getBytes(StandardCharsets.UTF_8), 0, false);
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

  private static ThreadFactory daemonThreadFactory(@NotNull String name) {
    return runnable -> {
      Thread t = new Thread(runnable, name);
      t.setDaemon(true);
      return t;
    };
  }

  private void subscribeAfterConnect(@NotNull MqttAsyncClient client) {
    if (myClient != client) return;
    try {
      client.subscribe(mySubscribeTopic, 0, null, new IMqttActionListener() {
        @Override
        public void onSuccess(@Nullable IMqttToken asyncActionToken) {
          if (myClient != client) return;
          myReportTimer = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory("mqtt-report-timer"));
          myReportTimer.scheduleAtFixedRate(myApplicationService::publishPeriodicReports, 60, 60, TimeUnit.SECONDS);
          myConnectionListener.onMqttConnectionStatusChanged(MqttConnectionStatus.CONNECTED, null);
        }

        @Override
        public void onFailure(@Nullable IMqttToken asyncActionToken, @Nullable Throwable exception) {
          closeClientForcibly(client);
          myConnectionListener.onMqttConnectionStatusChanged(
              MqttConnectionStatus.CONNECTION_FAILED,
              exception == null ? new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION) : exception
          );
        }
      });
    }
    catch (MqttException e) {
      closeClientForcibly(client);
      myConnectionListener.onMqttConnectionStatusChanged(MqttConnectionStatus.CONNECTION_FAILED, e);
    }
  }

  private void cleanupClient(@NotNull MqttAsyncClient client) {
    if (myClient == client) {
      myClient = null;
    }
    stopReportTimer();
    stopMessageExecutor();
    try { client.close(); } catch (Exception ignored) {}
  }

  private void closeClientForcibly(@NotNull MqttAsyncClient client) {
    if (myClient == client) {
      myClient = null;
    }
    stopReportTimer();
    stopMessageExecutor();
    try { client.disconnectForcibly(1000, 1000); } catch (Exception ignored) {}
    try { client.close(); } catch (Exception ignored) {}
  }

  @NotNull
  private static String messageOf(@Nullable Throwable exception) {
    return exception == null ? "未知错误" : exception.getMessage();
  }
}
