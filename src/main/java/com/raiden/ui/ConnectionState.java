package com.raiden.ui;

import org.jetbrains.annotations.NotNull;

public enum ConnectionState {
  DISCONNECTED("离线"),
  CONNECTING("连接中"),
  CONNECTED("在线"),
  DISCONNECTING("断开中");

  @NotNull
  private final String myBadgeText;

  ConnectionState(@NotNull String badgeText) {
    myBadgeText = badgeText;
  }

  @NotNull
  public String getBadgeText() {
    return myBadgeText;
  }
}
