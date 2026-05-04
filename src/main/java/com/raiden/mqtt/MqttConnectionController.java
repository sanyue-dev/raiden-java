package com.raiden.mqtt;

import com.raiden.domain.ChargingStation;
import com.raiden.platform.Disposable;
import com.raiden.platform.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MqttConnectionController implements Disposable {

  @NotNull
  private final Object myLock = new Object();
  @NotNull
  private final ChargingStation myStation;
  @NotNull
  private final ChargingApplicationListener myApplicationListener;
  @NotNull
  private final ConnectionListener myConnectionListener;
  @Nullable
  private MqttService myService;
  @NotNull
  private Phase myPhase = Phase.DISCONNECTED;
  private long myGeneration;
  private boolean myDisposed;

  public MqttConnectionController(@NotNull ChargingStation station,
                                  @NotNull Disposable parentDisposable,
                                  @NotNull ChargingApplicationListener applicationListener,
                                  @NotNull ConnectionListener connectionListener) {
    myStation = station;
    myApplicationListener = applicationListener;
    myConnectionListener = connectionListener;
    Disposer.register(parentDisposable, this);
  }

  public boolean connect(@NotNull String brokerUrl, @NotNull String clientId, int portCount) {
    long generation;
    synchronized (myLock) {
      if (myDisposed) {
        throw new IllegalStateException("Connection controller has already been disposed");
      }
      if (myPhase != Phase.DISCONNECTED) {
        myConnectionListener.onMqttLog("当前连接状态未结束，请稍后再试");
        return false;
      }
      myPhase = Phase.CONNECTING;
      generation = ++myGeneration;
    }

    myStation.resetPorts(portCount);
    myApplicationListener.onPortsChanged();

    ChargingApplicationService appService = new ChargingApplicationService(myStation, new RaidenProtocolCodec(), myApplicationListener);
    ServiceCallback callback = new ServiceCallback(generation, brokerUrl, clientId);
    MqttService service = new MqttService(brokerUrl, clientId, appService, callback);
    callback.myService = service;
    appService.setMessagePublisher(service);
    Disposer.register(this, service);

    synchronized (myLock) {
      if (myGeneration != generation || myPhase != Phase.CONNECTING) {
        Disposer.dispose(service);
        return false;
      }
      myService = service;
    }

    try {
      service.connect();
      return true;
    }
    catch (Exception ex) {
      handleServiceStatus(generation, service, brokerUrl, clientId, MqttConnectionStatus.CONNECTION_FAILED, ex);
      return true;
    }
  }

  public void cancelConnect() {
    long generation;
    MqttService service;
    synchronized (myLock) {
      if (myPhase != Phase.CONNECTING || myService == null) {
        return;
      }
      myPhase = Phase.CANCELLING;
      generation = myGeneration;
      service = myService;
    }

    myConnectionListener.onMqttLog("正在取消连接...");
    myConnectionListener.onMqttConnectionStatusChanged(MqttConnectionStatus.DISCONNECTING, null);
    Thread t = new Thread(() -> {
      service.closeForcibly();
      finishCancelled(generation, service);
    }, "mqtt-cancel-connect-thread");
    t.setDaemon(true);
    t.start();
  }

  public void disconnect() {
    MqttService service;
    synchronized (myLock) {
      if (myPhase == Phase.DISCONNECTED) {
        myConnectionListener.onMqttConnectionStatusChanged(MqttConnectionStatus.DISCONNECTED, null);
        return;
      }
      if (myPhase != Phase.CONNECTED || myService == null) {
        return;
      }
      myPhase = Phase.DISCONNECTING;
      service = myService;
    }
    service.disconnect();
  }

  public void manualClose(int portNumber) {
    MqttService service;
    synchronized (myLock) {
      service = myPhase == Phase.CONNECTED ? myService : null;
    }
    if (service == null) {
      myConnectionListener.onMqttLog("请先连接服务器");
      return;
    }
    service.manualClose(portNumber);
  }

  @Override
  public void dispose() {
    MqttService service;
    synchronized (myLock) {
      myDisposed = true;
      myGeneration++;
      myPhase = Phase.DISCONNECTED;
      service = myService;
      myService = null;
    }
    if (service != null) {
      service.closeForcibly();
    }
  }

  private void handleServiceStatus(long generation,
                                   @NotNull MqttService service,
                                   @NotNull String brokerUrl,
                                   @NotNull String clientId,
                                   @NotNull MqttConnectionStatus status,
                                   @Nullable Throwable cause) {
    switch (status) {
      case CONNECTING:
        handleConnecting(generation, brokerUrl);
        break;
      case CONNECTED:
        handleConnected(generation, service, brokerUrl, clientId);
        break;
      case CONNECTION_FAILED:
        handleConnectionFailed(generation, service, cause);
        break;
      case DISCONNECTING:
        handleDisconnecting(generation);
        break;
      case DISCONNECTED:
        handleDisconnected(generation, service);
        break;
      case CONNECTION_LOST:
        handleConnectionLost(generation, service, cause);
        break;
    }
  }

  private void handleConnecting(long generation, @NotNull String brokerUrl) {
    synchronized (myLock) {
      if (myGeneration != generation || myPhase != Phase.CONNECTING) {
        return;
      }
    }
    myConnectionListener.onMqttLog("正在连接 " + brokerUrl);
    myConnectionListener.onMqttConnectionStatusChanged(MqttConnectionStatus.CONNECTING, null);
  }

  private void handleConnected(long generation,
                               @NotNull MqttService service,
                               @NotNull String brokerUrl,
                               @NotNull String clientId) {
    boolean stale;
    synchronized (myLock) {
      stale = myGeneration != generation || myService != service || myPhase != Phase.CONNECTING;
      if (!stale) {
        myPhase = Phase.CONNECTED;
      }
    }
    if (stale) {
      service.closeForcibly();
      return;
    }
    myConnectionListener.onMqttLog("已连接到 " + brokerUrl + "，客户端 " + clientId);
    myConnectionListener.onMqttConnectionStatusChanged(MqttConnectionStatus.CONNECTED, null);
  }

  private void handleConnectionFailed(long generation, @NotNull MqttService service, @Nullable Throwable cause) {
    boolean cancelled;
    synchronized (myLock) {
      if (myGeneration != generation || myService != service) {
        return;
      }
      cancelled = myPhase == Phase.CANCELLING;
      myPhase = Phase.DISCONNECTED;
      myService = null;
    }
    Disposer.dispose(service);
    if (cancelled) {
      myConnectionListener.onMqttLog("已取消连接");
      myConnectionListener.onMqttConnectionStatusChanged(MqttConnectionStatus.DISCONNECTED, null);
    }
    else {
      myConnectionListener.onMqttLog("连接失败：" + messageOf(cause));
      myConnectionListener.onMqttConnectionStatusChanged(MqttConnectionStatus.CONNECTION_FAILED, cause);
    }
  }

  private void handleDisconnecting(long generation) {
    synchronized (myLock) {
      if (myGeneration != generation || myPhase != Phase.DISCONNECTING) {
        return;
      }
    }
    myConnectionListener.onMqttLog("正在断开连接...");
    myConnectionListener.onMqttConnectionStatusChanged(MqttConnectionStatus.DISCONNECTING, null);
  }

  private void handleDisconnected(long generation, @NotNull MqttService service) {
    Phase previousPhase;
    synchronized (myLock) {
      if (myGeneration != generation || myService != service) {
        return;
      }
      previousPhase = myPhase;
      myPhase = Phase.DISCONNECTED;
      myService = null;
    }
    Disposer.dispose(service);
    if (previousPhase == Phase.DISCONNECTING) {
      myConnectionListener.onMqttLog("已断开连接");
    }
    myConnectionListener.onMqttConnectionStatusChanged(MqttConnectionStatus.DISCONNECTED, null);
  }

  private void handleConnectionLost(long generation, @NotNull MqttService service, @Nullable Throwable cause) {
    synchronized (myLock) {
      if (myGeneration != generation || myService != service) {
        return;
      }
      myPhase = Phase.DISCONNECTED;
      myService = null;
    }
    Disposer.dispose(service);
    myConnectionListener.onMqttConnectionStatusChanged(MqttConnectionStatus.CONNECTION_LOST, cause);
  }

  private void finishCancelled(long generation, @NotNull MqttService service) {
    synchronized (myLock) {
      if (myGeneration != generation || myService != service || myPhase != Phase.CANCELLING) {
        return;
      }
      myPhase = Phase.DISCONNECTED;
      myService = null;
    }
    Disposer.dispose(service);
    myConnectionListener.onMqttLog("已取消连接");
    myConnectionListener.onMqttConnectionStatusChanged(MqttConnectionStatus.DISCONNECTED, null);
  }

  @NotNull
  private static String messageOf(@Nullable Throwable cause) {
    return cause == null ? "未知错误" : cause.getMessage();
  }

  private enum Phase {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    CANCELLING,
    DISCONNECTING
  }

  private final class ServiceCallback implements ConnectionListener {
    private final long myCallbackGeneration;
    @NotNull
    private final String myBrokerUrl;
    @NotNull
    private final String myClientId;
    @Nullable
    private MqttService myService;

    private ServiceCallback(long generation, @NotNull String brokerUrl, @NotNull String clientId) {
      myCallbackGeneration = generation;
      myBrokerUrl = brokerUrl;
      myClientId = clientId;
    }

    @Override
    public void onMqttConnectionStatusChanged(@NotNull MqttConnectionStatus status, @Nullable Throwable cause) {
      MqttService service = myService;
      if (service != null) {
        handleServiceStatus(myCallbackGeneration, service, myBrokerUrl, myClientId, status, cause);
      }
    }

    @Override
    public void onMqttLog(@NotNull String message) {
      MqttService service = myService;
      synchronized (myLock) {
        if (service == null || myGeneration != myCallbackGeneration || MqttConnectionController.this.myService != service) {
          return;
        }
      }
      myConnectionListener.onMqttLog(message);
    }
  }
}
