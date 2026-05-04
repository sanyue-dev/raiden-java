# Raiden Java

Raiden Java is a desktop charging station simulator for backend development and protocol testing. It exists to make MQTT charging workflows observable and repeatable during service-side debugging.

## Language

**Charging Station Simulator**:
A desktop test tool that simulates one charging station and its ports for backend MQTT integration testing.
_Avoid_: Production client, operations console, charging station management system

**Protocol Interaction State**:
The observable charging workflow state needed to test MQTT command handling and reporting.
_Avoid_: Hardware physics, metering simulation, billing engine

**Charging Port**:
One numbered connector position in the simulated station, matching the protocol `portNum`.
_Avoid_: Gun, socket, channel

**Charging Session**:
The active protocol interaction on one **Charging Port** from accepted start-charging command until billing completion.
_Avoid_: Transaction

**Session Balance**:
The balance value supplied by the server when starting a **Charging Session** and echoed by the simulator during reporting and stopping.
_Avoid_: Account balance, order amount, calculated fee

**Order**:
A server-owned business record for a charging purchase, created and updated outside the simulator.
_Avoid_: Local session, simulator order

**Start-Charging Response**:
The simulator response that confirms a server start-charging command has started a local **Charging Session** on an idle **Charging Port**.
_Avoid_: MQTT ACK, order-created confirmation, transport delivery confirmation

**Message Correlation ID**:
The server-provided `msg_id` that the simulator must echo in responses so the server can match protocol commands and responses.
_Avoid_: Transport delivery confirmation, order ID

**Manual Stop**:
A simulator-side action that stops charging locally and notifies the server that the active **Charging Session** has stopped.
_Avoid_: Stop request, order close request

**Billing Completion**:
The server confirmation that finishes settlement for a stopped or active **Charging Session** and releases the **Charging Port**.
_Avoid_: Local stop

**Final Balance**:
The simulator-provided balance value returned during **Billing Completion** to test server settlement behavior.
_Avoid_: Calculated charge, billing engine result

**Stopped**:
The **Charging Port** state after **Manual Stop** while waiting for **Billing Completion**.
_Avoid_: Stopping, closing order, billing, closed

**Protocol Trace**:
The chronological record of raw MQTT messages and related simulator state changes used for backend debugging.
_Avoid_: User activity log, audit log

**Connection Session**:
One simulator connection to the MQTT broker using a fixed client ID and fixed **Charging Port** count.
_Avoid_: Mutable station configuration

**Local Message ID**:
A temporary simulator-generated `msg_id` for locally initiated MQTT messages during one application run.
_Avoid_: Order ID, persistent sequence

## Relationships

- A **Charging Station Simulator** represents exactly one simulated charging station session at a time.
- A **Charging Station Simulator** is used by backend developers and testers, not by end users operating real charging hardware.
- A **Charging Station Simulator** models **Protocol Interaction State**, not real electrical behavior or billing calculations.
- A **Charging Station Simulator** has one or more **Charging Ports**.
- A **Charging Port** is identified by exactly one `portNum`.
- A **Charging Port** has at most one active **Charging Session** at a time.
- A **Charging Session** has one **Session Balance** supplied by the server start command.
- A **Charging Session** usually corresponds to one server-side **Order**, but the simulator does not create, store, or settle **Orders**.
- The server creates or updates an **Order** when it receives successful protocol responses from the simulator.
- A **Start-Charging Response** is a business response, not an MQTT QoS acknowledgement.
- A **Start-Charging Response** is sent after the simulator starts the local **Charging Session**, but does not prove the server has created an **Order**.
- A successful **Start-Charging Response** uses `data=portNum,1`; failed **Start-Charging Response** data is temporarily treated as `data=portNum,0` until the server protocol is confirmed.
- A **Message Correlation ID** links a server command to the simulator response; with MQTT QoS 0, publishing a response does not prove the server received or processed it.
- A **Manual Stop** does not immediately release the **Charging Port**; it leaves the **Charging Session** waiting for **Billing Completion**.
- If a **Manual Stop** notification cannot be handed to MQTT for publishing, the simulator rolls the **Charging Port** back to charging.
- A **Charging Port** is **Stopped** after **Manual Stop** and before **Billing Completion**.
- A server-side stop is represented by **Billing Completion**; there is no separate server stop command in the supported protocol.
- A **Billing Completion** releases the **Charging Port** back to idle, whether it follows **Manual Stop** or arrives while the port is charging.
- A **Billing Completion** that references an unknown **Charging Port** should receive a protocol-level not-found response, but that response format is not yet confirmed.
- A **Final Balance** is a controllable simulator value for settlement testing, not an automatically calculated real billing result.
- The current code echoes the session snapshot balance as **Final Balance**; the desired default and future manual configuration are still being clarified.
- A **Protocol Trace** should expose raw MQTT payloads as evidence for debugging protocol behavior.
- A **Protocol Trace** should keep the exception type, exception message, and raw payload together when protocol handling fails.
- The available **Charging Ports** can only be reinitialized by changing the port count while disconnected.
- A failed connection attempt does not reset **Charging Port** state.
- Disconnecting a **Connection Session** does not reset **Charging Port** state; the last simulator state remains visible for debugging.
- Reconnecting does not reset **Charging Port** state; it continues from the last visible simulator state.
- A **Local Message ID** continues across connection failure, disconnect, and reconnect during the same application run, but can reset after the simulator restarts.

## Example dialogue

> **Dev:** "Should the simulator silently repair a malformed start-charging payload so testing can continue?"
> **Domain expert:** "No — because this is a **Charging Station Simulator** for backend debugging, malformed protocol data should be visible and easy to diagnose."

> **Dev:** "Should we estimate consumed energy and deduct balance over time?"
> **Domain expert:** "No — unless the protocol test explicitly needs it, the simulator should stay focused on **Protocol Interaction State**."

> **Dev:** "Can one **Charging Port** run two **Charging Sessions** at the same time?"
> **Domain expert:** "No — a **Charging Port** maps to one `portNum` and has at most one active **Charging Session**."

> **Dev:** "Is `balance` the user's real account balance?"
> **Domain expert:** "No — it is the **Session Balance** supplied by the server for this simulator session."

> **Dev:** "When the simulator sends a **Start-Charging Response**, does it create an **Order**?"
> **Domain expert:** "No — the server owns the **Order**; the simulator only starts a **Charging Session**."

> **Dev:** "Does a **Start-Charging Response** mean the **Order** was created successfully?"
> **Domain expert:** "No — it only means the simulator accepted the start-charging command for that **Charging Port**."

> **Dev:** "If the **Charging Port** is already charging, should the simulator ignore the start command?"
> **Domain expert:** "No — it should send a failed **Start-Charging Response**; `data=portNum,0` is the temporary format until the server protocol is confirmed."

> **Dev:** "Does successful MQTT publish mean the server processed the response?"
> **Domain expert:** "No — with QoS 0, the server uses the echoed **Message Correlation ID** if it receives the response."

> **Dev:** "When a user clicks manual stop, is the **Charging Port** immediately idle?"
> **Domain expert:** "No — **Manual Stop** means local charging has stopped, but the **Charging Port** waits for **Billing Completion** before becoming idle."

> **Dev:** "If the **Manual Stop** notification fails to publish, should the simulator keep the port stopped?"
> **Domain expert:** "No — because the server did not receive the notification, the simulator rolls the **Charging Port** back to charging."

> **Dev:** "Does the server send a separate stop-charging command before **Billing Completion**?"
> **Domain expert:** "No — in the supported protocol, server-side stopping is represented by **Billing Completion**."

> **Dev:** "If **Billing Completion** references an unknown **Charging Port**, should the simulator send an idle response?"
> **Domain expert:** "No — it should send a not-found response, but the response format must be confirmed from the server protocol first."

> **Dev:** "Should the simulator remove final balance because it is not a real billing engine?"
> **Domain expert:** "No — **Final Balance** is useful as a test input for server settlement, even though it is not real metering."

> **Dev:** "Should testers configure **Final Balance** per session now?"
> **Domain expert:** "No — keep the current default for now; manual configuration can come later after the desired default is confirmed."

> **Dev:** "Should the UI say the port is closing an order after **Manual Stop**?"
> **Domain expert:** "No — the port is **Stopped** and waiting for **Billing Completion**; order settlement belongs to the server."

> **Dev:** "When a payload cannot be parsed, should the simulator hide the raw message and only show a friendly error?"
> **Domain expert:** "No — the **Protocol Trace** should preserve the raw payload so backend developers can diagnose the protocol mismatch."

> **Dev:** "Should a protocol failure log only say that processing failed?"
> **Domain expert:** "No — it should include the exception type, exception message, and raw payload in the **Protocol Trace**."

> **Dev:** "Can testers change the port count while connected?"
> **Domain expert:** "No — the simulator only reinitializes **Charging Ports** when the port count changes while disconnected."

> **Dev:** "Should disconnecting clear all **Charging Ports** back to idle?"
> **Domain expert:** "No — the simulator keeps the last visible state so testers can inspect what happened before disconnecting."

> **Dev:** "Should a failed connection attempt clear all **Charging Ports** back to idle?"
> **Domain expert:** "No — connection failure is a transport lifecycle result and should not erase protocol interaction state."

> **Dev:** "Should reconnecting start from fresh idle **Charging Ports**?"
> **Domain expert:** "No — reconnecting keeps the current simulator state; change the port count while disconnected if a fresh port set is needed."

> **Dev:** "Is a **Local Message ID** a durable server-side identifier?"
> **Domain expert:** "No — it is only a temporary simulator message number for one application run."

> **Dev:** "Should a failed connection attempt reset the **Local Message ID**?"
> **Domain expert:** "No — connection failure does not end the application run, so the local message sequence should continue."

## Flagged ambiguities

- "Client" can mean a production charging client or this testing tool — resolved: this project is a **Charging Station Simulator**.
- "Simulation" can mean realistic hardware behavior or backend protocol behavior — resolved: this project simulates **Protocol Interaction State**.
- "Gun", "socket", and "channel" may refer to the same numbered connector — resolved: use **Charging Port**.
- "Order" and **Charging Session** are related but not interchangeable — resolved: the server owns **Orders**, while the simulator owns **Charging Sessions**.
- "balance" can imply a real account or wallet — resolved: use **Session Balance** for the simulator-held value.
- "Stop request" implies the simulator asks the server for permission — resolved: use **Manual Stop** for simulator-side stopping and server notification.
- "Closing" and "Stopping" can imply an in-progress operation — resolved: use **Stopped** for the simulator state between **Manual Stop** and **Billing Completion**.
- Start-charging failure response format is not fully confirmed — provisional implementation uses `data=portNum,0`.
- `cdz=203` data currently uses `port,0,0,0,0,0,balance,1`, but the meaning of each placeholder and the final `1` is not yet confirmed from the server protocol.
- `cdz=104` unknown-port response is required by the protocol, but its data format is not yet confirmed; do not substitute an idle response.
- **Final Balance** default policy is not fully settled; current code echoes the session snapshot balance.
