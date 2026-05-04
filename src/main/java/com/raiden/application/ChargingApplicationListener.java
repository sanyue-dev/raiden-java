package com.raiden.application;

import org.jetbrains.annotations.NotNull;

public interface ChargingApplicationListener {
  void onApplicationLog(@NotNull String message);

  void onPortsChanged();
}
