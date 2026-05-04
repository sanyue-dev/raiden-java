package com.raiden.mqtt;

public enum MqttConnectionStatus {
  CONNECTING,
  CONNECTED,
  CONNECTION_FAILED,
  DISCONNECTING,
  DISCONNECTED,
  CONNECTION_LOST
}
