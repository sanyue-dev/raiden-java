# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Raiden Java

MQTT 充电桩模拟客户端，Java Swing 版。

## 常用命令

- `mvn exec:java` — 编译并直接运行 Swing 客户端，入口为 `com.raiden.Main`。
- `mvn package` — 打包 shaded fat JAR，输出 `target/raiden-java-${version}.jar`。
- `java -jar target/raiden-java-${version}.jar` — 运行已打包的客户端。
- `mvn test` — 运行 Maven 测试阶段；当前仓库没有 `src/test` 测试源码。
- `mvn -Dtest=ClassName test` — 后续添加 Maven 测试后运行单个测试类。
- `mvn clean package -Pnative -Djpackage.type=app-image` — 从 clean 状态验证 native app-image；确认 `target/jpackage-input/*.jar` 包含依赖类。
- `mvn clean package -Pnative -Djpackage.type=dmg` — 在本机 Intel macOS 生成 x64 DMG，输出 `target/native/Raiden-${version}.dmg`。

仓库当前没有单独的 lint 插件或格式化命令。

## 架构概览

代码位于 `src/main/java/com/raiden`，按 UI、应用服务、领域模型、协议编解码和 MQTT 基础设施分层。

- **Disposable 体系**（`platform/`）：`Disposable` + `Disposer` 提供树形资源生命周期管理。`Disposer.register(parent, child)` 建立父子关系，`Disposer.dispose(parent)` 逆序销毁整棵子树。`MainFrame` 持有 `myFrameDisposable` 作为根，`MqttService` 注册为其子节点，窗口关闭时自动清理。

- `Main` 设置 FlatLaf 主题，并通过 `SwingUtilities.invokeLater` 启动 `MainFrame`。
- `ui/MainFrame` 负责 Swing 界面、连接表单、端口表格、日志和用户操作。它同时实现 `ChargingApplicationListener` 和 `MqttService.MqttConnectionListener`，持有 `ChargingStation` 并把端口状态展示交给 `PortTableModel` 和 `ChargingPortPresentation`。
- `infrastructure/mqtt/MqttService` 封装 Eclipse Paho MQTT v3 客户端，使用 MQTT 3.1.1。它在构造时内部创建 `ChargingApplicationService`（而非外部注入）。消息收发均使用 QoS 0。订阅 topic 为 `cdz/{clientId}`，发布 topic 为 `upload/cdz/{clientId}`。每 60 秒通过 `ScheduledExecutorService` 调度一次周期状态上报。
- `application/ChargingApplicationService` 是业务入口，处理收到的协议消息、手动结束订单和周期上报；通过 `MessagePublisher` 接口发布消息（`MqttService` 实现该接口），通过 `ChargingApplicationListener` 回调通知 UI。
- `domain/ChargingStation` 管理端口集合，`ChargingPort` 管理单个端口状态，`ChargingPortSnapshot` 用于跨线程读取稳定快照。端口状态为 `IDLE`、`CHARGING`、`CLOSING`。
- `protocol/RaidenProtocolCodec` 使用手写的字符串解析（非 JSON 库），从 payload 中提取 `cdz`、`msg_id`、`data` 三个字段。`RaidenMessage` 是解析结果的不可变持有对象。

## 协议和线程约束

- 协议命令码：`cdz=101`（启动充电）、`102`（周期上报/本地发起）、`104`（计费结束）、`203`（手动关闭/本地发起）。`101` 和 `104` 由远端下发，`102` 和 `203` 由本地主动发送。
- `cdz=101` 的 `data` 字段为 CSV 格式 `portNum,orderType,duration,skip,kwhFee,unit,balance`，其中 `params[3]` 被跳过不使用。
- `msg_id` 在 ACK 和 EndBilling 响应中必须保持 `String` 原样回传；本地发起的周期上报和手动结束使用 `AtomicLong` 递增的 long 值。
- UI 更新必须回到 Swing Event Dispatch Thread，使用 `SwingUtilities.invokeLater`。
- MQTT 消息回调通过 `MqttService` 内的单线程 `ExecutorService`（`mqtt-message-thread`）串行处理，避免消息回调直接在 Paho MQTT 线程池上执行。周期上报使用单独的 `ScheduledExecutorService`。三个线程（MQTT 回调线程、EDT、定时上报线程）会并发访问端口状态；领域对象使用 `synchronized(myLock)` 私有锁，并通过 `ChargingPortSnapshot` 对外暴露不可变快照。

## 编码规范

- 字段使用 IntelliJ 风格的 `my` 前缀，例如 `myBrokerUrl`、`myStation`。
- 使用 `org.jetbrains.annotations` 的 `@NotNull` / `@Nullable` 标注边界。
- 主界面字体通过 `ui/MainFrameFonts` 加载 `src/main/resources/fonts/Maple-Regular.ttf` 和 `Maple-Bold.ttf`；资源缺失会明确失败，不做系统字体 fallback。
- 不要为了“跑通”加入静默 fallback、mock 成功路径或吞错逻辑；调试时让失败明确暴露。`.claude/rules/debug-first.md` 中的 Debug-First Policy 适用于 Java/Kotlin 文件。

## 依赖和环境

- JDK 25 + Maven。
- Eclipse Paho MQTT v3 `1.2.5`。
- FlatLaf `3.5.4`，当前入口使用 `FlatLightLaf`。
- JetBrains Annotations `26.0.1`。
- GitHub Actions native 包只自动构建 `macos-14` arm64 DMG 和 Windows ZIP；macOS x64 DMG 由本机 Intel macOS 手动构建并上传 release。
- release 资产名由 workflow 统一加版本号，例如 `Raiden-macOS-arm64-1.0.0.dmg` 和 `Raiden-Windows-1.0.0.zip`。
- 发版流程：更新 `pom.xml` 版本并推送 `main`，本机先构建 x64 DMG，再推送 `vX.Y.Z` tag 触发 CI 生成 arm64/Windows，最后用 `gh release upload` 补传 x64 DMG。
- Windows release ZIP 必须先将 artifact 目录重命名为 `Raiden` 再压缩，避免解压后出现 `artifacts/Raiden-Windows/`。
- 重发同一 tag 的 release 资产时，先删除同名旧 asset，再移动 tag 或重新上传，避免 `softprops/action-gh-release` 同名冲突。
- 移动已发布 tag 属于共享状态变更，仅用于补发/修复 release；操作前确认 tag 应指向当前 `main`。
- 系统代理 `127.0.0.1:7890` 可能导致 MQTT 连接 reset；运行时需要通过 `JAVA_TOOL_OPTIONS` 或 shell 环境绕过内网 broker 地址。
