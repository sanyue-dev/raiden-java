package com.raiden.mqtt;

import com.raiden.domain.ChargingPortSnapshot;
import org.jetbrains.annotations.NotNull;

public final class RaidenProtocolCodec {

  public static final int CDZ_START_CHARGING = 101;
  public static final int CDZ_REPORT = 102;
  public static final int CDZ_END_BILLING = 104;
  public static final int CDZ_MANUAL_CLOSE = 203;

  @NotNull
  public RaidenMessage parse(@NotNull String payload) {
    return new RaidenMessage(
        extractIntField(payload, "\"cdz\""),
        extractStringField(payload, "\"msg_id\""),
        extractStringField(payload, "\"data\"")
    );
  }

  public static final class StartChargingParams {
    public final int portNum;
    public final int orderType;
    public final int duration;
    public final int kwhFee;
    public final int unit;
    public final int balance;

    public StartChargingParams(int portNum, int orderType, int duration, int kwhFee, int unit, int balance) {
      this.portNum = portNum;
      this.orderType = orderType;
      this.duration = duration;
      this.kwhFee = kwhFee;
      this.unit = unit;
      this.balance = balance;
    }
  }

  @NotNull
  public StartChargingParams parseStartChargingData(@NotNull String data) {
    String[] params = data.split(",");
    return new StartChargingParams(
        Integer.parseInt(params[0].trim()),
        Integer.parseInt(params[1].trim()),
        Integer.parseInt(params[2].trim()),
        Integer.parseInt(params[4].trim()),
        Integer.parseInt(params[5].trim()),
        Integer.parseInt(params[6].trim())
    );
  }

  @NotNull
  public String buildAckJson(@NotNull ChargingPortSnapshot port, @NotNull String msgId) {
    return "{\"cdz\":" + CDZ_START_CHARGING + ",\"msg_id\":\"" + msgId + "\",\"data\":\"" + port.getPortNumber() + ",1\"}";
  }

  @NotNull
  public String buildReportJson(@NotNull ChargingPortSnapshot port, long msgId) {
    return "{\"cdz\":" + CDZ_REPORT + ",\"msg_id\":\"" + msgId + "\",\"data\":\"" +
           port.getPortNumber() + ",0,0,0,0,0," + port.getBalance() + "\"}";
  }

  @NotNull
  public String buildEndBillingJson(@NotNull ChargingPortSnapshot port, @NotNull String msgId) {
    int finalBalance = port.getBalance() - 1;
    return "{\"cdz\":" + CDZ_END_BILLING + ",\"msg_id\":\"" + msgId + "\",\"data\":\"" +
           port.getPortNumber() + ",0,0,0,0,0," + finalBalance + "\"}";
  }

  @NotNull
  public String buildIdleEndBillingJson(@NotNull ChargingPortSnapshot port, @NotNull String msgId) {
    return "{\"cdz\":" + CDZ_END_BILLING + ",\"msg_id\":\"" + msgId + "\",\"data\":\"" + port.getPortNumber() + "\"}";
  }

  @NotNull
  public String buildManualCloseJson(@NotNull ChargingPortSnapshot port, long msgId) {
    return "{\"cdz\":" + CDZ_MANUAL_CLOSE + ",\"msg_id\":\"" + msgId + "\",\"data\":\"" +
           port.getPortNumber() + ",0,0,0,0,0," + port.getBalance() + ",1\"}";
  }

  private static int extractIntField(@NotNull String json, @NotNull String fieldName) {
    return Integer.parseInt(extractStringField(json, fieldName));
  }

  @NotNull
  private static String extractStringField(@NotNull String json, @NotNull String fieldName) {
    String searchKey = fieldName + ":";
    int start = json.indexOf(searchKey);
    if (start < 0) {
      searchKey = fieldName + " :";
      start = json.indexOf(searchKey);
    }
    if (start < 0) throw new IllegalArgumentException("未找到字段：" + fieldName);

    start += searchKey.length();

    while (start < json.length() && json.charAt(start) == ' ') start++;

    if (start >= json.length()) throw new IllegalArgumentException("字段后缺少内容：" + fieldName);

    if (json.charAt(start) == '"') {
      int end = json.indexOf('"', start + 1);
      if (end < 0) throw new IllegalArgumentException("字段字符串未结束：" + fieldName);
      return json.substring(start + 1, end);
    }
    else {
      int end = start;
      while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ' ') {
        end++;
      }
      return json.substring(start, end);
    }
  }
}
