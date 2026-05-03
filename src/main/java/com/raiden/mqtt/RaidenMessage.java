package com.raiden.mqtt;

import org.jetbrains.annotations.NotNull;

public final class RaidenMessage {

  private final int myCdz;
  @NotNull
  private final String myMsgId;
  @NotNull
  private final String myData;

  public RaidenMessage(int cdz, @NotNull String msgId, @NotNull String data) {
    myCdz = cdz;
    myMsgId = msgId;
    myData = data;
  }

  public int getCdz() {
    return myCdz;
  }

  @NotNull
  public String getMsgId() {
    return myMsgId;
  }

  @NotNull
  public String getData() {
    return myData;
  }
}
