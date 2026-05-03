package com.raiden.mqtt;

import org.jetbrains.annotations.NotNull;

public interface ChargingApplicationListener {
  void onApplicationLog(@NotNull String message);

  void onPortsChanged();
}
