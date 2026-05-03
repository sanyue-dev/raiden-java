package com.raiden.mqtt;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ConnectionListener {
  void onMqttConnected(@NotNull String brokerUrl, @NotNull String clientId);

  void onMqttDisconnected(@Nullable Throwable cause);

  void onMqttLog(@NotNull String message);
}
