# Architecture Diagram - Visual Representation

## System Overview (Mermaid Diagram)

```mermaid
graph TB
    subgraph JADE["🤖 JADE Platform (Main Container)"]
        DF["📋 Directory Facilitator<br/>(Service Registry)"]
        
        subgraph Agents["Agent Population"]
            Crane["🦾 CraneAgent<br/>(transport_service)"]
            Source1["📦 SourceAgent #1<br/>(source_station_1)"]
            Source2["📦 SourceAgent #2<br/>(source_station_2)"]
            Proc1["⚙️ ProcessAgent #1<br/>(processing_station_1)"]
            Proc2["⚙️ ProcessAgent #2<br/>(processing_station_2)"]
            Sink["🏁 SinkAgent<br/>(sink_station)"]
            Part["👤 PartAgent<br/>(Dynamic)"]
        end
    end
    
    Config["🗂️ factory_layout.json<br/>(Coordinates & Registers)"]
    
    HTTP["🌐 HTTP API<br/>:8001, :8002"]
    
    Modbus["🔌 Modbus TCP<br/>localhost:502"]
    
    Hardware["🏭 Hardware Simulator<br/>(Crane + Machines)"]
    
    %% Configuration flows
    Config -->|Startup| Source1
    Config -->|Startup| Source2
    Config -->|Startup| Proc1
    Config -->|Startup| Proc2
    Config -->|Startup| Sink
    
    %% Service registration
    Source1 -->|Register| DF
    Source2 -->|Register| DF
    Proc1 -->|Register| DF
    Proc2 -->|Register| DF
    Sink -->|Register| DF
    Crane -->|Register| DF
    
    %% Part spawning
    HTTP -->|POST /spawn| Source1
    HTTP -->|POST /spawn| Source2
    Source1 -->|Spawn| Part
    Source2 -->|Spawn| Part
    
    %% Service discovery & negotiation
    Part -->|DF Search| DF
    Part -->|Contract Net| Crane
    Part -->|Contract Net| Proc1
    Part -->|Contract Net| Proc2
    Part -->|Contract Net| Sink
    
    %% Dynamic config fetching
    Crane -->|CONFIG_REQUEST| Source1
    Crane -->|CONFIG_REQUEST| Source2
    Crane -->|CONFIG_REQUEST| Proc1
    Crane -->|CONFIG_REQUEST| Proc2
    Crane -->|CONFIG_REQUEST| Sink
    
    %% Modbus control
    Crane -->|Read/Write Registers| Modbus
    Proc1 -->|Read/Write Registers| Modbus
    Proc2 -->|Read/Write Registers| Modbus
    
    %% Physical execution
    Modbus -->|Commands| Hardware
    Hardware -->|Feedback| Modbus
    
    style Crane fill:#ff9999
    style Source1 fill:#99ccff
    style Source2 fill:#99ccff
    style Proc1 fill:#99ff99
    style Proc2 fill:#99ff99
    style Sink fill:#ffcc99
    style Part fill:#ff99ff
    style DF fill:#ffff99
    style Config fill:#cccccc
    style HTTP fill:#cccccc
    style Modbus fill:#cccccc
    style Hardware fill:#eeeeee
```

---

## Part Lifecycle (Mermaid Diagram)

```mermaid
sequenceDiagram
    participant HTTP as "External<br/>Client"
    participant SRC as "SourceAgent"
    participant DF as "Directory<br/>Facilitator"
    participant PART as "PartAgent"
    participant CRANE as "CraneAgent"
    participant MB as "Modbus TCP"
    participant PROC as "ProcessAgent"
    
    HTTP->>SRC: POST /spawn<br/>(type1)
    activate SRC
    SRC->>SRC: Create PartAgent<br/>with type1 routing
    SRC->>DF: Register service
    deactivate SRC
    
    Note over PART: Part lifecycle begins
    
    activate PART
    PART->>DF: Query for<br/>transport_service
    DF-->>PART: Found CraneAgent
    
    PART->>CRANE: CFP<br/>(src1, proc1)
    activate CRANE
    CRANE-->>PART: PROPOSE<br/>(cost: 10)
    PART->>CRANE: ACCEPT_PROPOSAL
    
    Note over CRANE: Dynamic Config Phase
    CRANE->>SRC: CONFIG_REQUEST
    SRC-->>CRANE: INFORM<br/>(55, 17)
    
    CRANE->>PROC: CONFIG_REQUEST
    PROC-->>CRANE: INFORM<br/>(450, 4)
    
    Note over CRANE: Execution Phase
    CRANE->>MB: Write Reg2=200<br/>(Raise)
    MB->>MB: Poll feedback
    CRANE->>MB: Read Reg17<br/>(Sensor check)
    CRANE->>MB: Write Reg1=450<br/>(Move to proc1)
    MB->>MB: Wait & move
    CRANE->>MB: Write Reg2=82<br/>(Lower)
    CRANE->>MB: Write Reg3=1<br/>(Grip)
    CRANE->>MB: Write Reg1=945<br/>(Move to proc)
    
    CRANE-->>PART: INFORM<br/>(Transport complete)
    deactivate CRANE
    
    Note over PART: Processing Phase
    PART->>DF: Query for<br/>processing_station_1
    DF-->>PART: Found ProcessAgent
    
    PART->>PROC: CFP<br/>(processing_station_1)
    activate PROC
    PROC-->>PART: PROPOSE<br/>(cost: 10)
    PART->>PROC: ACCEPT_PROPOSAL
    
    PROC->>MB: Write Reg4=1<br/>(Start machine)
    
    Note over PROC: Active Polling<br/>(4 seconds)
    loop Every 50ms
        PROC->>MB: Read Reg23<br/>(Breakdown check)
        MB-->>PROC: 0 (OK)
    end
    
    PROC->>MB: Write Reg4=0<br/>(Stop machine)
    PROC-->>PART: INFORM<br/>(Process complete)
    deactivate PROC
    
    Note over PART: Sink Phase
    PART->>DF: Query for<br/>sink_station
    DF-->>PART: Found SinkAgent
    
    PART->>Sink: CFP<br/>(sink_station)
    Note over Sink: [SinkAgent accepts]
    PART->>Sink: ACCEPT_PROPOSAL
    Sink-->>PART: INFORM
    
    Note over PART: Part lifecycle complete
    deactivate PART
    PART->>PART: doDelete()
```

---

## Configuration-Driven Discovery (Mermaid Diagram)

```mermaid
graph LR
    A["factory_layout.json"] -->|Parse| B["Main.java"]
    
    B -->|Deploy with<br/>args| C["SourceAgent #1<br/>name=src_station_1<br/>port=8001<br/>X=55<br/>sensor_reg=17"]
    B -->|Deploy with<br/>args| D["ProcessAgent #1<br/>name=proc_station_1<br/>X=450<br/>ctrl_reg=4"]
    B -->|Deploy with<br/>args| E["SinkAgent<br/>name=sink_station<br/>X=945<br/>sink_reg=0"]
    B -->|Deploy| F["CraneAgent<br/>no args needed"]
    
    C -->|Register in DF| G["📋 DF"]
    D -->|Register in DF| G
    E -->|Register in DF| G
    F -->|Register in DF| G
    
    H["CraneAgent<br/>at runtime"] -->|DF Discovery| G
    H -->|CONFIG_REQUEST| C
    C -->|X=55, sensor_reg=17| H
    
    H -->|CONFIG_REQUEST| D
    D -->|X=450, ctrl_reg=4| H
    
    I["PartAgent"] -->|DF Discovery| G
    I -->|DF Discovery| G
    I -->|Contract Net| H
    I -->|Contract Net| D
```

---

## Hardware Failure Detection Flow (Mermaid Diagram)

```mermaid
graph TD
    A["ProcessAgent accepts<br/>task from Part"] --> B["Spawns worker thread"]
    B --> C["Write Reg 4/5 = 1<br/>START MACHINE"]
    C --> D["Active Polling Loop<br/>80 iterations x 50ms"]
    
    D --> E{"Read Reg 23<br/>Breakdown?"}
    
    E -->|NO| F["Continue polling<br/>i = i+1"]
    F --> E
    
    E -->|YES| G["🚨 HARDWARE FAILURE<br/>DETECTED"]
    G --> H["Write Reg 4/5 = 0<br/>STOP MACHINE"]
    H --> I["Send FAILURE<br/>to PartAgent"]
    I --> J["Part receives FAILURE<br/>Can re-route"]
    
    E -->|Loop Complete| K["Normal Completion<br/>4 seconds elapsed"]
    K --> L["Write Reg 4/5 = 0<br/>STOP MACHINE"]
    L --> M["Send INFORM<br/>to PartAgent"]
    M --> N["Part receives INFORM<br/>Continue routing"]
    
    style G fill:#ff6666
    style L fill:#66ff66
    style M fill:#66ff66
    style I fill:#ff9999
    style J fill:#ff9999
```

---

## State Machine - RouteExecutionBehaviour (Mermaid Diagram)

```mermaid
stateDiagram-v2
    [*] --> STATE0
    
    STATE0: STATE 0: Query DF<br/>for next service
    STATE0 --> CheckQueue{Process plan<br/>empty?}
    
    CheckQueue -->|YES| STATE3: Continue to<br/>STATE 3
    CheckQueue -->|NO| GetService{Service<br/>found?}
    
    GetService -->|NO| Wait["Block 2 seconds<br/>& retry"]
    Wait --> STATE0
    
    GetService -->|YES| STATE1
    
    STATE1: STATE 1: Initiate<br/>Contract Net
    STATE1 --> Ready["Add PartNegotiator<br/>behavior"]
    Ready --> STATE3
    
    STATE3: STATE 3: TERMINATE
    STATE3 --> Cleanup{Plan<br/>empty?}
    
    Cleanup -->|YES| Callback["negotiationFinished()<br/>callback"]
    Callback --> UpdateState["Update:<br/>isAtDestination flag<br/>processPlan queue"]
    UpdateState --> STATE0
    
    Cleanup -->|NO| Exit["doDelete()<br/>Agent lifecycle end"]
    Exit --> [*]
    
    note right of STATE0
        If not at destination: Search for Crane
        If at destination: Search for Machine
    end note
    
    note right of STATE1
        Send CFP with appropriate content
        Depending on isAtDestination flag
    end note
    
    note right of Callback
        Called by PartNegotiator
        when service completes
    end note
```

---

## Modbus Register Timeline (Mermaid Diagram)

```mermaid
timeline
    title: Crane Movement - Modbus Sequence with Timing
    
    section Raise to Safe Height
    T=0ms: Write Reg2=200 (Z-position)
    T=1500ms: Sleep ends, proceed to move
    
    section Move to Pickup
    T=1500ms: Read Reg15 (actual position) → 55
    T=1500ms: Calculate distance | dropoff = 450, pickup = 55, dist = 395
    T=1500ms: Calculate time | (395*10)+1500 = 5450ms
    T=1500ms: Write Reg1=450 (X-position command)
    T=6950ms: After sleep, proceed to lower
    
    section Grip & Raise
    T=6950ms: Write Reg2=82 (lower to grip height)
    T=8450ms: Write Reg3=1 (engage gripper)
    T=8950ms: Write Reg2=200 (raise to safe height)
    T=10450ms: Proceed to dropoff
    
    section Move to Dropoff
    T=10450ms: Calculate distance | from 450 to 945, dist = 495
    T=10450ms: Calculate time | (495*10)+1500 = 6450ms
    T=10450ms: Write Reg1=945 (X-position command)
    T=16900ms: After sleep, proceed to release
    
    section Release & Retract
    T=16900ms: Write Reg2=82 (lower to release height)
    T=18400ms: Write Reg3=0 (open gripper)
    T=18900ms: Write Reg2=200 (raise to safe height)
    T=20400ms: Movement complete, send INFORM
```

---

## System Deployment View (Mermaid Diagram)

```mermaid
C4Context
    title: Manufacturing Cell - Deployment Context
    
    Person(User, "AI Planner / Operator", "External system")
    
    System_Boundary(MES, "Manufacturing Cell") {
        System(JADE_Platform, "JADE Platform", "Multi-agent system with:<br/>- Directory Facilitator (DF)<br/>- 6 core agents<br/>- Dynamic part spawning")
        
        Ext_System(Config, "factory_layout.json", "Hardware configuration")
        Ext_System(NetworkIface, "Modbus TCP", "Hardware control interface")
    }
    
    Ext_System(PLC, "PLC / Simulator", "Physical/simulated hardware:<br/>- Crane with X/Z control<br/>- 2 Processing stations<br/>- Part sensors<br/>- Breakd down button")
    
    Birel(User, JADE_Platform, "REST API<br/>POST /spawn<br/>(type1 or type2)")
    Birel(Config, JADE_Platform, "Read on startup")
    Birel(JADE_Platform, NetworkIface, "Read/Write Modbus<br/>Registers 1-23")
    Birel(NetworkIface, PLC, "TCP/IP<br/>127.0.0.1:502")
    Birel(User, PLC, "View/Control<br/>(optional)")
    
    UpdateRelStyle(JADE_Platform, Config, $textColor=green, $lineColor=green)
    UpdateRelStyle(JADE_Platform, NetworkIface, $textColor=blue, $lineColor=blue)
```

---

## Service Discovery (Mermaid Diagram)

```mermaid
graph LR
    subgraph DF["📋 Directory Facilitator"]
        S1["transport_service<br/>CraneAgent"]
        S2["source_station_1<br/>SourceAgent#1"]
        S3["source_station_2<br/>SourceAgent#2"]
        S4["processing_station_1<br/>ProcessAgent#1"]
        S5["processing_station_2<br/>ProcessAgent#2"]
        S6["sink_station<br/>SinkAgent"]
    end
    
    PART["PartAgent<br/>(type1 part)"]
    
    PART -->|"1. Query for<br/>transport_service"| S1
    S1 -->|"Found!"| PART
    
    PART -->|"2. Query for<br/>processing_station_1"| S4
    S4 -->|"Found!"| PART
    
    PART -->|"3. Query for<br/>sink_station"| S6
    S6 -->|"Found!"| PART
    
    style S1 fill:#ff9999
    style S2 fill:#99ccff
    style S3 fill:#99ccff
    style S4 fill:#99ff99
    style S5 fill:#99ff99
    style S6 fill:#ffcc99
    style PART fill:#ff99ff
    style DF fill:#ffff99
```


