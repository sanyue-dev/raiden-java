package com.raiden.ui;

import org.jetbrains.annotations.NotNull;

public enum ConnectionState {
  DISCONNECTED,
  CONNECTING,
  CONNECTED,
  DISCONNECTING;

  @NotNull
  public String getBadgeText() {
    return this == CONNECTED ? "在线" : "离线";
  }
}
