package com.raiden.mqtt;

import com.raiden.application.ChargingApplicationService;
import com.raiden.application.MessagePublisher;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class MqttService implements MessagePublisher, Disposable {

  @Nullable
  private MqttAsyncClient myClient;
  @NotNull
  private final Object myLifecycleLock = new Object();
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
  private boolean myAcceptingMessages;
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
    synchronized (myLifecycleLock) {
      myClient = client;
      myAcceptingMessages = true;
    }
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
        String actualTopic = topic == null ? "<unknown>" : topic;
        if (!isAcceptingMessages(client)) {
          myConnectionListener.onMqttLog("拒绝晚到消息：MQTT 服务已关闭或切换");
          return;
        }
        myConnectionListener.onMqttLog(
            "MQTT IN  topic=" + actualTopic + " qos=" + message.getQos() + " payload=" + payload
        );
        try {
          myMessageExecutor.execute(() -> {
            if (!isAcceptingMessages(client)) {
              myConnectionListener.onMqttLog("丢弃已排队消息：MQTT 服务已关闭或切换");
              return;
            }
            myApplicationService.handleIncomingPayload(payload);
          });
        }
        catch (RejectedExecutionException e) {
          myConnectionListener.onMqttLog("拒绝晚到消息：消息执行器已关闭");
        }
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
    MqttAsyncClient client = currentClient();
    rejectIncomingMessages();
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
    MqttAsyncClient client = currentClient();
    if (client != null) {
      closeClientForcibly(client);
    }
  }

  public void manualClose(int portNumber) {
    myApplicationService.manualClose(portNumber);
  }

  private void stopReportTimer() {
    ScheduledExecutorService reportTimer;
    synchronized (myLifecycleLock) {
      reportTimer = myReportTimer;
      myReportTimer = null;
    }
    if (reportTimer != null) {
      reportTimer.shutdownNow();
    }
  }

  private void stopMessageExecutor() {
    myMessageExecutor.shutdownNow();
  }

  @Override
  public boolean publish(@NotNull String json) {
    MqttAsyncClient client = currentClient();
    if (client == null || !client.isConnected()) {
      myConnectionListener.onMqttLog("发布消息失败：MQTT 未连接");
      return false;
    }
    try {
      client.publish(myPublishTopic, json.getBytes(StandardCharsets.UTF_8), 0, false);
      myConnectionListener.onMqttLog("MQTT OUT topic=" + myPublishTopic + " qos=0 payload=" + json);
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
    if (!isCurrentClient(client)) return;
    try {
      client.subscribe(mySubscribeTopic, 0, null, new IMqttActionListener() {
        @Override
        public void onSuccess(@Nullable IMqttToken asyncActionToken) {
          ScheduledExecutorService reportTimer = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory("mqtt-report-timer"));
          reportTimer.scheduleAtFixedRate(myApplicationService::publishPeriodicReports, 60, 60, TimeUnit.SECONDS);
          synchronized (myLifecycleLock) {
            if (myClient != client || !myAcceptingMessages) {
              reportTimer.shutdownNow();
              return;
            }
            if (myReportTimer != null) {
              myReportTimer.shutdownNow();
            }
            myReportTimer = reportTimer;
          }
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
    clearClient(client);
    closeClient(client);
  }

  private void closeClientForcibly(@NotNull MqttAsyncClient client) {
    clearClient(client);
    try {
      client.disconnectForcibly(1000, 1000);
    }
    catch (Exception e) {
      myConnectionListener.onMqttLog("强制断开 MQTT 失败：" + e.getMessage());
    }
    closeClient(client);
  }

  @Nullable
  private MqttAsyncClient currentClient() {
    synchronized (myLifecycleLock) {
      return myClient;
    }
  }

  private boolean isCurrentClient(@NotNull MqttAsyncClient client) {
    synchronized (myLifecycleLock) {
      return myClient == client;
    }
  }

  private boolean isAcceptingMessages(@NotNull MqttAsyncClient client) {
    synchronized (myLifecycleLock) {
      return myClient == client && myAcceptingMessages && !myMessageExecutor.isShutdown();
    }
  }

  private void rejectIncomingMessages() {
    synchronized (myLifecycleLock) {
      myAcceptingMessages = false;
    }
  }

  private void clearClient(@NotNull MqttAsyncClient client) {
    ScheduledExecutorService reportTimer = null;
    boolean clearMessageExecutor = false;
    synchronized (myLifecycleLock) {
      if (myClient == client) {
        myClient = null;
        myAcceptingMessages = false;
        reportTimer = myReportTimer;
        myReportTimer = null;
        clearMessageExecutor = true;
      }
    }
    if (reportTimer != null) {
      reportTimer.shutdownNow();
    }
    if (clearMessageExecutor) {
      stopMessageExecutor();
    }
  }

  private void closeClient(@NotNull MqttAsyncClient client) {
    try {
      client.close();
    }
    catch (Exception e) {
      myConnectionListener.onMqttLog("关闭 MQTT client 失败：" + e.getMessage());
    }
  }

  @NotNull
  private static String messageOf(@Nullable Throwable exception) {
    return exception == null ? "未知错误" : exception.getMessage();
  }
}
