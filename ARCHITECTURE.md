# Multi-Agent Manufacturing Cell - Architecture Diagram

## 1. System Overview & Agent Interactions

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        JADE PLATFORM (Main Container)                           │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌─────────────────────────────────────┐  ┌──────────────────────────────────┐ │
│  │  📋 Directory Facilitator (DF)      │  │  🗂️  factory_layout.json         │ │
│  │  Yellow Pages Service Registry      │  │  (Single Source of Truth)        │ │
│  ├─────────────────────────────────────┤  ├──────────────────────────────────┤ │
│  │ • transport_service (Crane)         │  │ Coordinates:                     │ │
│  │ • source_station_1 (Source#1)       │  │ • source_station_1_x: 55        │ │
│  │ • source_station_2 (Source#2)       │  │ • source_station_2_x: 158       │ │
│  │ • processing_station_1 (Process#1)  │  │ • processing_station_1_x: 450   │ │
│  │ • processing_station_2 (Process#2)  │  │ • processing_station_2_x: 650   │ │
│  │ • sink_station (Sink)               │  │ • sink_station_x: 945           │ │
│  └─────────────────────────────────────┘  │                                  │ │
│                                            │ Modbus Registers:                │ │
│                                            │ • source_station_1_sensor: 17   │ │
│                                            │ • source_station_2_sensor: 18   │ │
│                                            │ • processing_station_1_reg: 4   │ │
│                                            │ • processing_station_2_reg: 5   │ │
│                                            │ • sink_station_reg: 0           │ │
│                                            └──────────────────────────────────┘ │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Agent Architecture & Communication Flow

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                         PART LIFECYCLE & ROUTING                                  │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                   │
│  HTTP POST /spawn      ┌─────────────────────────────────────────────────────┐   │
│  "type1" or "type2"    │  SOURCE AGENT (Source#1 or Source#2)              │   │
│         │              ├─────────────────────────────────────────────────────┤   │
│         │              │ • HTTP Server (port 8001/8002)                     │   │
│         │              │ • Spawns PartAgent dynamically                     │   │
│         │              │ • Responds to CONFIG_REQUEST:                      │   │
│         ▼              │   └─→ Returns (X-coord, sensor-register)           │   │
│    ┌─────────────┐     └─────────────────────────────────────────────────────┘   │
│    │ Part Agent  │                                       ▲                       │
│    │ (Dynamic)   │                                       │                       │
│    │             │                  (DF Discovery)       │                       │
│    │ Route:      │              ╭───────────────────╮    │                       │
│    │ type1:      │              │                   │    │                       │
│    │ src1→proc1  │              ▼                   │    │                       │
│    │   →sink     │         ┌──────────────────────────┐  │                       │
│    │             │         │ CRANE AGENT              │  │                       │
│    │ type2:      │         ├──────────────────────────┤  │                       │
│    │ src2→proc2  │         │ • Contract Net Responder │  │                       │
│    │   →proc1    │         │ • CONFIG_REQUEST Sender │  │                       │
│    │   →sink     │         │ • Modbus Commands       │  │                       │
│    │             │         │ • Sensor Validation     │  │                       │
│    └─────────────┘         │ • Position Tracking     │  │                       │
│         │                  └──────────────────────────┘  │                       │
│         │ (Contract Net)                ▲                │                       │
│         │ CFP/PROPOSE/ACCEPT            │                │                       │
│         │ INFORM/FAILURE          [CONFIG_REQUEST]       │                       │
│         │                               │                │                       │
│         ├─────────────────────────────────────────┬──────┘                       │
│         │                                         │                              │
│         ▼                                         ▼                              │
│    ┌─────────────────────┐               ┌─────────────────────────────────┐   │
│    │ PROCESS AGENT #1/2  │               │ SINK AGENT                      │   │
│    ├─────────────────────┤               ├─────────────────────────────────┤   │
│    │ • Contract Net      │               │ • Contract Net Responder        │   │
│    │   Responder         │               │ • Part Consumption              │   │
│    │ • Modbus Control    │               │ • Responds to CONFIG_REQUEST:   │   │
│    │ • Hardware Failure  │               │   └─→ Returns (X-coord, reg)    │   │
│    │   Detection         │               │ • End of manufacturing journey  │   │
│    │ • Active Polling    │               └─────────────────────────────────┘   │
│    │   (Register 23)     │                                                     │
│    └─────────────────────┘                                                     │
│         │                                                                       │
│         └─────────────────────►(Normal or Failure)◄──────────────┐             │
│                                                                   │             │
│                                                                   ▼             │
│                              Part completes or is re-routed on failure          │
│                                                                                 │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Communication Protocols

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                   FIPA CONTRACT NET + CONFIG_REQUEST PROTOCOL                     │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                   │
│  PHASE 1: Contract Net Negotiation                                               │
│  ══════════════════════════════════                                              │
│                                                                                   │
│     Part Agent                Crane/Process Agent                                │
│         │                              │                                         │
│         │──── CFP (Pickup, Dropoff)───►│                                         │
│         │                              │                                         │
│         │◄────── PROPOSE (cost:10) ────│                                         │
│         │                              │                                         │
│         │──── ACCEPT_PROPOSAL ────────►│                                         │
│         │                              │                                         │
│                                                                                   │
│  PHASE 2: Dynamic Configuration Fetching (Crane Only)                            │
│  ═════════════════════════════════════════════════════                           │
│                                                                                   │
│     Crane Agent          Source/Process/Sink Agent                               │
│         │                         │                                              │
│         │──── CONFIG_REQUEST ────►│  (ontology: "CONFIG_REQUEST")               │
│         │                         │                                              │
│         │◄─── INFORM (X,Reg) ────│  (content: "450,4")                          │
│         │                         │                                              │
│                                                                                   │
│  PHASE 3: Task Execution (Hardware Control)                                      │
│  ═══════════════════════════════════════════                                     │
│                                                                                   │
│     Agent                  Modbus Connection                                     │
│         │                         │                                              │
│         │─► Write Register 2: 200 │  (Crane Z = safe height)                    │
│         │                         ▼                                              │
│         │─► Write Register 1: 450 │  (Crane X = pickup location)                │
│         │                         ▼                                              │
│         │─► Read Register 15      │  (Verify actual X position)                 │
│         │◄─── Register 15: 450 ──┤                                              │
│         │                         │                                              │
│         │─► Write Register 2: 82  │  (Lower to grip height)                     │
│         │                         ▼                                              │
│         │─► Write Register 3: 1   │  (Engage gripper)                           │
│         │                         ▼                                              │
│         │─► Write Register 2: 200 │  (Raise to safe height)                     │
│         │                         ▼                                              │
│         │─► Write Register 1: 945 │  (Move to dropoff)                          │
│         │                         ▼                                              │
│         │─► Write Register 3: 0   │  (Release gripper)                          │
│         │                         ▼                                              │
│         │                                                                        │
│  PHASE 4: Completion Notification                                               │
│  ════════════════════════════════════                                            │
│                                                                                   │
│     Crane/Process Agent        Part Agent                                        │
│         │                          │                                             │
│         │──── INFORM ────────────►│  [Normal completion]                        │
│         │      OR                  │                                             │
│         │──── FAILURE ────────────►│  [Hardware error / Service unavailable]     │
│         │                          │                                             │
│                                                                                   │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. System Data Flow

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                          CONFIGURATION & DATA FLOW                                │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                   │
│                         ┌─────────────────────────┐                              │
│                         │  factory_layout.json    │                              │
│                         │  (Configuration File)   │                              │
│                         └────────────┬────────────┘                              │
│                                      │                                           │
│                                      │ (On Startup)                              │
│                                      ▼                                           │
│                         ┌─────────────────────────┐                              │
│                         │     Main.java           │                              │
│                         │   (Bootstrap Agent)     │                              │
│                         └────────────┬────────────┘                              │
│                                      │                                           │
│              ┌───────────────────────┼───────────────────────┐                   │
│              │                       │                       │                   │
│              ▼                       ▼                       ▼                    │
│      ┌────────────────┐      ┌────────────────┐      ┌────────────────┐        │
│      │ Source Agents  │      │ Process Agents │      │  Sink Agent    │        │
│      │ (Receive:      │      │ (Receive:      │      │ (Receives:     │        │
│      │  name, port,   │      │  name, X-coord,│      │  name, X-crd,  │        │
│      │  X-coord,      │      │  control-reg)  │      │  ctrl-reg)     │        │
│      │  sensor-reg)   │      │ (Store for     │      │ (Stores for    │        │
│      │ (Store locally)│      │  CONFIG_REPLY) │      │  CONFIG_REPLY) │        │
│      └────┬───────────┘      └────┬───────────┘      └────┬───────────┘        │
│           │                       │                       │                      │
│           │ Register in DF        │ Register in DF        │ Register in DF       │
│           │ (service type)        │ (service type)        │ (service type)       │
│           └───────────┬───────────┴───────────┬───────────┘                     │
│                       │                       │                                 │
│                       ▼                       ▼                                 │
│            ┌────────────────────────────────────────────┐                       │
│            │  Directory Facilitator (DF)                │                       │
│            │  • transport_service: Crane               │                       │
│            │  • source_station_1: Source#1             │                       │
│            │  • source_station_2: Source#2             │                       │
│            │  • processing_station_1: Process#1        │                       │
│            │  • processing_station_2: Process#2        │                       │
│            │  • sink_station: Sink                     │                       │
│            └────────────┬───────────────────────────────┘                       │
│                         │                                                       │
│                         │ (DF Search)                                           │
│                         ▼                                                       │
│            ┌────────────────────────────────────────────┐                       │
│            │    Crane Agent (on demand)                 │                       │
│            │    • Fetches coordinates via CONFIG_REQ    │                       │
│            │    • Executes Modbus commands              │                       │
│            │    • Sends INFORM/FAILURE                 │                       │
│            └────────────┬───────────────────────────────┘                       │
│                         │                                                       │
│                         ▼                                                       │
│            ┌────────────────────────────────────────────┐                       │
│            │    Modbus TCP Connection (Hardware)        │                       │
│            │    • Crane position control (Reg 1-3)      │                       │
│            │    • Position feedback (Reg 15)             │                       │
│            │    • Sensor inputs (Reg 17-18)             │                       │
│            │    • Machine control (Reg 4-5)             │                       │
│            │    • Failure detection (Reg 23)            │                       │
│            └────────────────────────────────────────────┘                       │
│                                                                                   │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Agent State Machine - Part Agent Routing

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                    PART AGENT FSM: RouteExecutionBehaviour                        │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                   │
│                                                                                   │
│                                ┌──────────────┐                                  │
│                                │   START      │                                  │
│                                └──────┬───────┘                                  │
│                                       │                                          │
│                                       ▼                                          │
│                              ┌────────────────┐                                  │
│                              │  STATE 0       │                                  │
│                              │  Query DF for  │                                  │
│                              │  next service  │                                  │
│                              └────────┬───────┘                                  │
│                                       │                                          │
│                  ┌────────────────────┼────────────────────┐                    │
│                  │                    │                    │                    │
│         [No services]     [Service found]        [Plan empty?]                  │
│                  │                    │                    │                    │
│                  ▼                    ▼                    ▼                     │
│           Block(2 seconds)   ┌────────────────┐    ┌──────────────┐            │
│           Retry             │  STATE 1       │    │  STATE 3     │            │
│                             │  Initiate      │    │  TERMINATE   │            │
│                             │  Contract Net  │    │  Agent       │            │
│                             └────────┬───────┘    └──────────────┘            │
│                                      │                                          │
│                    [PartNegotiator takes over]                                 │
│                                      │                                          │
│                                      ▼                                          │
│                              ┌────────────────┐                                │
│                              │  STATE 2       │                                │
│                              │  (Handled by   │                                │
│                              │   Negotiator)  │                                │
│                              └────────┬───────┘                                │
│                                       │                                         │
│                    [Service completes/fails]                                   │
│                                       │                                         │
│                                       ▼                                         │
│                       ┌───────────────────────────────┐                        │
│                       │negotiationFinished() callback │                        │
│                       │  • Update isAtDestination flag│                        │
│                       │  • Advance processPlan queue │                        │
│                       │  • Restart routing behavior  │                        │
│                       └───────────────┬───────────────┘                        │
│                                       │                                         │
│                              ┌────────▼────────┐                               │
│                              │  Back to STATE0 │                               │
│                              │  (cycle repeats)│                               │
│                              └─────────────────┘                               │
│                                                                                 │
│                                                                                 │
│  ╔═══════════════════════════════════════════════════════════════════════════╗ │
│  ║  isAtDestination Flag (State Toggle)                                      ║ │
│  ╠═══════════════════════════════════════════════════════════════════════════╣ │
│  ║  FALSE (Need Transport)           │  TRUE (Need Processing)              ║ │
│  ║  ┌─────────────────────────────┐  │  ┌──────────────────────────────┐   ║ │
│  ║  │ • Search DF for "transport" │  │  │ • Search DF for actual       │   ║ │
│  ║  │ • Find Crane Agent          │  │  │   service (machine name)     │   ║ │
│  ║  │ • Send CFP with pickup/drop │  │  │ • Send CFP with service name │   ║ │
│  ║  │ • Wait for INFORM (arrival) │  │  │ • Wait for INFORM (done)     │   ║ │
│  ║  │ • Toggle flag TRUE           │  │  │ • Advance queue & toggle OFF │   ║ │
│  ║  └─────────────────────────────┘  │  └──────────────────────────────┘   ║ │
│  ╚═══════════════════════════════════════════════════════════════════════════╝ │
│                                                                                 │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Crane Agent Workflow - Dynamic Configuration

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│              CRANE AGENT: CranePickAndPlaceBehaviour Execution                    │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                   │
│  Receives CFP: "source_station_1,processing_station_1"                           │
│         │                                                                         │
│         ▼                                                                         │
│  ┌────────────────────────────────────────────────────────────────┐              │
│  │ Step 1: Fetch Pickup Config (source_station_1)               │              │
│  ├────────────────────────────────────────────────────────────────┤              │
│  │ • Search DF for "source_station_1"                            │              │
│  │ • Send CONFIG_REQUEST message                                 │              │
│  │ • Receive reply: "55,17" (X-coord, sensor-reg)               │              │
│  │ • Parse: pickupX = 55, sensorReg = 17                        │              │
│  └────────────────┬───────────────────────────────────────────────┘              │
│                   │                                                               │
│                   ▼                                                               │
│  ┌────────────────────────────────────────────────────────────────┐              │
│  │ Step 2: Modbus Sensor Validation                              │              │
│  ├────────────────────────────────────────────────────────────────┤              │
│  │ • Read Modbus register 17 (source sensor)                    │              │
│  │ • If sensor == 0: Send FAILURE ("no_part")                   │              │
│  │ • If sensor == 1: Continue                                    │              │
│  └────────────────┬───────────────────────────────────────────────┘              │
│                   │                                                               │
│                   ▼                                                               │
│  ┌────────────────────────────────────────────────────────────────┐              │
│  │ Step 3: Fetch Dropoff Config (processing_station_1)          │              │
│  ├────────────────────────────────────────────────────────────────┤              │
│  │ • Search DF for "processing_station_1"                        │              │
│  │ • Send CONFIG_REQUEST message                                 │              │
│  │ • Receive reply: "450,4" (X-coord, control-reg)              │              │
│  │ • Parse: dropoffX = 450, controlReg = 4                      │              │
│  └────────────────┬───────────────────────────────────────────────┘              │
│                   │                                                               │
│                   ▼                                                               │
│  ┌────────────────────────────────────────────────────────────────┐              │
│  │ Step 4: Execute Kinematics Macro (Modbus Commands)           │              │
│  ├────────────────────────────────────────────────────────────────┤              │
│  │                                                                │              │
│  │ 4.1. Raise to safe height:     Write Reg 2 ← 200            │              │
│  │      Sleep 1500ms              (Z = safe height)             │              │
│  │                                                                │              │
│  │ 4.2. Read actual position:     Read Reg 15 ← 55             │              │
│  │      Calculate distance        dist = |450 - 55| = 395      │              │
│  │      Calculate time            time = (395 * 10) + 1500     │              │
│  │                                                                │              │
│  │ 4.3. Move to pickup:           Write Reg 1 ← 55             │              │
│  │      Sleep(sleepToPickup)      (~5450ms)                    │              │
│  │                                                                │              │
│  │ 4.4. Lower to grip height:     Write Reg 2 ← 82             │              │
│  │      Sleep 1500ms              (Z = grip height)             │              │
│  │                                                                │              │
│  │ 4.5. Engage gripper:           Write Reg 3 ← 1              │              │
│  │      Sleep 500ms               (Grip engaged)                │              │
│  │                                                                │              │
│  │ 4.6. Raise with part:          Write Reg 2 ← 200            │              │
│  │      Sleep 1500ms              (Z = safe height)             │              │
│  │                                                                │              │
│  │ 4.7. Move to dropoff:          Write Reg 1 ← 450            │              │
│  │      Calculate distance        dist = |450 - 55| = 395      │              │
│  │      Calculate time            time = (395 * 10) + 1500     │              │
│  │      Sleep(sleepToDropoff)     (~5450ms)                    │              │
│  │                                                                │              │
│  │ 4.8. Lower to release height:  Write Reg 2 ← 82             │              │
│  │      Sleep 1500ms              (Z = release height)          │              │
│  │                                                                │              │
│  │ 4.9. Release part:             Write Reg 3 ← 0              │              │
│  │      Sleep 500ms               (Gripper open)                │              │
│  │                                                                │              │
│  │ 4.10. Return to safe height:   Write Reg 2 ← 200            │              │
│  │       Sleep 1500ms             (Z = safe height)             │              │
│  │                                                                │              │
│  └────────────────┬───────────────────────────────────────────────┘              │
│                   │                                                               │
│                   ▼                                                               │
│  ┌────────────────────────────────────────────────────────────────┐              │
│  │ Step 5: Send Completion Message                              │              │
│  ├────────────────────────────────────────────────────────────────┤              │
│  │ • Create INFORM message                                       │              │
│  │ • Set content: "Arrived at processing_station_1"             │              │
│  │ • Reply to original CFP sender (PartAgent)                    │              │
│  └────────────────┬───────────────────────────────────────────────┘              │
│                   │                                                               │
│                   ▼                                                               │
│            ┌──────────────┐                                                      │
│            │  Complete    │                                                      │
│            │  Ready for   │                                                      │
│            │  next CFP    │                                                      │
│            └──────────────┘                                                      │
│                                                                                   │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Process Agent Workflow - Hardware Failure Detection

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│         PROCESS AGENT: Machine Work with Active Failure Detection                 │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                   │
│  Receives CFP from PartAgent                                                     │
│         │                                                                         │
│         ▼                                                                         │
│  ┌────────────────────────────────────────────────────────────────┐              │
│  │ Step 1: Send PROPOSE                                          │              │
│  ├────────────────────────────────────────────────────────────────┤              │
│  │ • Reply with cost: "10"                                        │              │
│  │ • Part decides to accept                                      │              │
│  └────────────────┬───────────────────────────────────────────────┘              │
│                   │                                                               │
│                   ▼                                                               │
│  ┌────────────────────────────────────────────────────────────────┐              │
│  │ Step 2: Receive ACCEPT_PROPOSAL                               │              │
│  ├────────────────────────────────────────────────────────────────┤              │
│  │ • Start machine work in new thread (asynchronous)            │              │
│  └────────────────┬───────────────────────────────────────────────┘              │
│                   │                                                               │
│         ┌─────────▼─────────┐                                                    │
│         │                   │                                                    │
│         ▼                   ▼                                                    │
│  ┌──────────────┐    ┌──────────────────────────────────────────┐               │
│  │ Write START  │    │ Active Polling Loop (80 iterations)     │               │
│  │ Reg=1        │    ├──────────────────────────────────────────┤               │
│  │ (Machine on) │    │ for (i=0; i<80; i++) {                 │               │
│  │              │    │   Sleep 50ms                            │               │
│  │              │    │   Read Reg 23 (breakdown button)        │               │
│  │              │    │                                         │               │
│  │              │    │   if (Reg23 == 1) {                    │               │
│  │              │    │     ► HARDWARE FAILURE DETECTED ◄       │               │
│  │              │    │     ► Exit loop                         │               │
│  │              │    │   }                                     │               │
│  │              │    │ }                                       │               │
│  │              │    └──────────────┬───────────────────────────┘               │
│  └──────────────┘                   │                                            │
│         │                           │                                            │
│         ├─────────────┬─────────────┤                                            │
│         │             │             │                                            │
│         │             │        [Failure detected]                               │
│         │             │             │                                            │
│         ▼             │             ▼                                            │
│    [Normal]           │      ┌──────────────────┐                               │
│    Continue           │      │ Write STOP       │                               │
│    (4 sec elapsed)    │      │ Reg=0            │                               │
│         │             │      │ (Machine off)    │                               │
│         ▼             │      └────────┬─────────┘                               │
│    Write STOP         │             │                                           │
│    Reg=0              │             ▼                                           │
│    (Machine off)      │      Send FAILURE message                              │
│         │             │      Content: "hardware_breakdown"                     │
│         ▼             │             │                                           │
│    Send INFORM        │             ▼                                           │
│    (Success)          │      ┌──────────────────┐                              │
│         │             │      │ Part Agent gets  │                              │
│         └─────────────┼─────►│ FAILURE notice   │                              │
│                       │      │ Can re-route or  │                              │
│                       │      │ request support  │                              │
│                       │      └──────────────────┘                              │
│                       │                                                         │
│                       ▼                                                         │
│              [Thread completes]                                                │
│                                                                                 │
│                                                                                 │
│  ╔════════════════════════════════════════════════════════════════════════╗   │
│  ║  Timeline: Normal Case                                               ║   │
│  ║  T=0ms:      Write Reg=1 (START)                                    ║   │
│  ║  T=0-4000ms: Poll Reg23 every 50ms (80 loops)                       ║   │
│  ║  T=4000ms:   Reg23 stays 0 (no failure)                             ║   │
│  ║  T=4000ms:   Write Reg=0 (STOP)                                     ║   │
│  ║  T=4000ms:   Send INFORM                                            ║   │
│  ║                                                                       ║   │
│  ║  Timeline: Failure Case                                             ║   │
│  ║  T=0ms:      Write Reg=1 (START)                                    ║   │
│  ║  T=1500ms:   Poll Reg23 (30th loop), detect Reg23=1                 ║   │
│  ║  T=1500ms:   Break out of loop                                      ║   │
│  ║  T=1500ms:   Write Reg=0 (STOP)                                     ║   │
│  ║  T=1500ms:   Send FAILURE                                           ║   │
│  ╚════════════════════════════════════════════════════════════════════════╝   │
│                                                                                 │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Modbus Register Mapping

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                        MODBUS TCP REGISTER LAYOUT                                 │
├──────────────┬──────────────┬─────────────────────────────────────────────────────┤
│  Register    │  Function    │  Purpose                                            │
├──────────────┼──────────────┼─────────────────────────────────────────────────────┤
│  1           │  Write       │  Crane X Position Command                          │
│  2           │  Write       │  Crane Z Position (Height)                         │
│  3           │  Write       │  Gripper State (0=open, 1=closed)                 │
│  4           │  Write       │  Processing Station 1 Control (1=start, 0=stop)  │
│  5           │  Write       │  Processing Station 2 Control (1=start, 0=stop)  │
│  15          │  Read        │  Crane Actual X Position (Feedback)               │
│  17          │  Read        │  Source Station 1 Sensor (0=empty, 1=part)       │
│  18          │  Read        │  Source Station 2 Sensor (0=empty, 1=part)       │
│  23          │  Read        │  Breakdown Button / Failure Detection             │
│              │              │  (0=running, 1=hardware fault)                    │
└──────────────┴──────────────┴─────────────────────────────────────────────────────┘

Usage in agents:
├─ CraneAgent:    Writes to 1,2,3; Reads from 15,17,18
├─ ProcessAgent1: Writes to 4; Reads from 23
├─ ProcessAgent2: Writes to 5; Reads from 23
├─ SourceAgent1:  Read-only (responds to config requests)
└─ SourceAgent2:  Read-only (responds to config requests)
```

---

## 9. Key Design Patterns

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                          DESIGN PATTERNS USED                                     │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                   │
│  1. DELEGATION PATTERN (Configuration Discovery)                                 │
│     ───────────────────────────────────────────                                 │
│     Instead of Crane knowing all coordinates:                                   │
│     CraneAgent → [CONFIG_REQUEST] → Each Station                                │
│     Each station owns & provides its own configuration                           │
│     Benefit: Fully decoupled, plug-and-play stations                            │
│                                                                                   │
│  2. CONTRACT NET PROTOCOL (Negotiation)                                          │
│     ──────────────────────────────────                                          │
│     FIPA standard for service negotiation:                                       │
│     Initiator (PartAgent) broadcast CFP                                          │
│     Responders (Crane/Machines) bid with PROPOSE                                │
│     Winner executes task, sends INFORM/FAILURE                                   │
│     Benefit: Autonomous, fair distribution of work                              │
│                                                                                   │
│  3. FINITE STATE MACHINE (Part Routing)                                          │
│     ─────────────────────────────────                                           │
│     PartAgent cycles through states:                                            │
│     State 0: Query DF → State 1: Negotiate → State 2: Wait → State 3: Loop    │
│     Benefit: Clear state transitions, easy to debug                             │
│                                                                                   │
│  4. OBSERVER PATTERN (Callback)                                                  │
│     ──────────────────────────                                                  │
│     PartNegotiator observes service completion                                   │
│     Calls PartAgent.negotiationFinished() callback                              │
│     Benefit: Decoupled notification, non-blocking waits                          │
│                                                                                   │
│  5. COMMAND PATTERN (Modbus Macros)                                              │
│     ───────────────────────────────                                             │
│     Complex Crane movements encapsulated in CranePickAndPlaceBehaviour          │
│     Execute multi-step kinematics macro atomically                              │
│     Benefit: Transactional feel, error recovery                                 │
│                                                                                   │
│  6. CONFIGURATION EXTERNALIZATION (factory_layout.json)                          │
│     ───────────────────────────────────────────────                             │
│     All parameters moved to JSON (not hardcoded in Java)                        │
│     Agents read once at startup, fetch dynamically at runtime                   │
│     Benefit: Single source of truth, no recompilation                            │
│                                                                                   │
│  7. ACTIVE MONITORING (Failure Detection)                                        │
│     ────────────────────────────────────                                        │
│     ProcessAgent continuously polls Modbus register 23                          │
│     Immediately detects and reports hardware failures                           │
│     Benefit: Robust, real-time fault tolerance                                  │
│                                                                                   │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Data Persistence & Scalability

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                    SCALABILITY & EXTENSION POINTS                                 │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                   │
│  Extending the System:                                                            │
│  ═════════════════════                                                           │
│                                                                                   │
│  To add a NEW PROCESSING STATION:                                               │
│  ─────────────────────────────────                                              │
│                                                                                   │
│    Step 1: Edit factory_layout.json                                            │
│    ├─ Add: "processing_station_3_x": "800"                                     │
│    └─ Add: "processing_station_3_reg": "6"                                     │
│                                                                                   │
│    Step 2: Edit Main.java                                                       │
│    └─ Add: new ProcessAgent("Process3", serviceType, ...)                       │
│                                                                                   │
│    Step 3: Update PartAgent routing (if new type needed)                        │
│    └─ Add type3 route: src1 → proc3 → sink                                     │
│                                                                                   │
│    Result: System automatically discovers & uses new station via DF             │
│                                                                                   │
│  To add a SECOND CRANE for Parallelization:                                     │
│  ──────────────────────────────────────────                                     │
│                                                                                   │
│    Step 1: Edit Main.java                                                       │
│    └─ Add: new CraneAgent("Crane2")                                            │
│                                                                                   │
│    Step 2: No other code changes needed!                                        │
│    Feature: DF-based service discovery automatically load-balances              │
│                                                                                   │
│  Current Bottlenecks:                                                            │
│  ═════════════════════                                                           │
│    • Single Crane: Transport operation serialized                               │
│      Solution: Add multiple CraneAgents (transparent to Parts)                 │
│                                                                                   │
│    • Network: Modbus TCP latency                                                │
│      Solution: Cache register values, async polling                             │
│                                                                                   │
│    • DF Lookup: For 100+ agents, query latency increases                       │
│      Solution: Local caching of service descriptions                            │
│                                                                                   │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## Summary

This architecture implements a **configuration-driven, multi-agent manufacturing system** with the following key principles:

✅ **Decoupled Design** - Agents communicate via standardized protocols (FIPA, Modbus)  
✅ **Dynamic Configuration** - JSON-based setup, no hardcoded values  
✅ **Service Discovery** - JADE Directory Facilitator enables plug-and-play agents  
✅ **Hardware Integration** - Modbus TCP with closed-loop feedback and fault detection  
✅ **Scalability** - Add stations/cranes by editing JSON or deploying new agents  
✅ **Robustness** - Active monitoring, failure detection, error recovery  


