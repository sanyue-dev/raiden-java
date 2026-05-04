# Raiden Java

MQTT 充电桩模拟客户端，Java Swing 版。通过 MQTT 协议与充电桩服务端通信，模拟充电桩的连接、充电、计费和上报流程。

## 环境要求

- JDK 25
- Maven

## 快速开始

```bash
# 编译并运行
mvn exec:java

# 打包为可执行 JAR
mvn package
java -jar target/raiden-java-1.0.0.jar
```

## 使用说明

启动后出现 Swing 图形界面，在连接表单中填写 MQTT Broker 地址和客户端 ID，点击连接即可。连接成功后会自动订阅 `cdz/{clientId}` 主题并开始周期性状态上报。

界面中的端口表格实时显示各充电端口状态，支持手动停充并通知服务端。断开连接和重新连接都会保留当前端口状态；只有在断开状态下修改端口数量才会重建端口。事件日志会显示 `MQTT IN`、`MQTT OUT` 和 `APP` 记录，方便对照原始协议消息与本地状态变化；联调前可清空当前日志。

## 通信协议

使用 MQTT 3.1.1，QoS 0。

| 命令码 | 方向 | 说明 |
|--------|------|------|
| `cdz=101` | 服务端 → 客户端 | 启动充电 |
| `cdz=102` | 客户端 → 服务端 | 周期状态上报 |
| `cdz=104` | 服务端 → 客户端 | 计费结束 |
| `cdz=203` | 客户端 → 服务端 | 手动停充通知 |

协议消息使用自定义文本格式（非 JSON），包含 `cdz`、`msg_id`、`data` 三个字段。`msg_id` 在响应中必须原样回传。

`cdz=101` 启动充电响应复用服务端下发的 `msg_id`：本地开启成功时 `data` 为 `portNum,1`；无法开启时当前暂按 `portNum,0` 处理，待服务端协议确认后再调整。

`cdz=203` 手动停充通知当前实现为 `port,0,0,0,0,0,balance,1`，其中占位字段和最后一位 `1` 的含义待服务端协议确认。

## 项目结构

更详细的包职责和依赖方向见 [docs/architecture.md](docs/architecture.md)，端口和连接状态机见 [docs/state-machine.md](docs/state-machine.md)。

```
src/main/java/com/raiden/
├── Main.java                          # 程序入口
├── ui/
│   ├── MainFrame.java                 # Swing 主窗口
│   ├── PortTableModel.java            # 端口表格数据模型
│   └── ChargingPortPresentation.java  # 端口状态展示
├── application/
│   ├── ChargingApplicationService.java  # 业务逻辑入口
│   ├── ChargingApplicationListener.java # UI 回调接口
│   └── MessagePublisher.java            # 消息发布接口
├── model/
│   ├── ChargingStation.java          # 充电桩（端口集合）
│   ├── ChargingPort.java             # 单个充电端口
│   ├── ChargingPortSnapshot.java     # 端口状态快照（不可变）
│   └── ChargingPortState.java        # 端口状态枚举
├── protocol/
│   ├── RaidenProtocolCodec.java      # 协议编解码
│   └── RaidenMessage.java            # 协议消息对象
├── mqtt/
│   ├── MqttConnectionController.java # MQTT 连接生命周期控制
│   ├── MqttService.java              # MQTT 客户端封装
│   ├── ConnectionListener.java       # MQTT 连接回调接口
│   └── MqttConnectionStatus.java     # MQTT 连接状态枚举
└── platform/
    ├── Disposable.java               # 生命周期接口
    └── Disposer.java                 # 树形资源释放工具
```

## 依赖

- [Eclipse Paho MQTT v3](https://www.eclipse.org/paho/) 1.2.5 — MQTT 客户端
- [FlatLaf](https://www.formdev.com/flatlaf/) 3.5.4 — Swing 主题
- [JetBrains Annotations](https://github.com/JetBrains/java-annotations) 26.0.1 — 可空性标注

## 注意事项

如果系统配置了代理（如 `127.0.0.1:7890`），MQTT 连接可能被 reset。需要通过 `JAVA_TOOL_OPTIONS` 或环境变量绕过内网 broker 地址。
