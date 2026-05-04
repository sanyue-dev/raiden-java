package com.raiden.mqtt;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ConnectionListener {
  void onMqttConnectionStatusChanged(@NotNull MqttConnectionStatus status, @Nullable Throwable cause);

  void onMqttLog(@NotNull String message);
}
