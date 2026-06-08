# Visual Step-by-Step: How Coordinates Enable Crane Movement

This document provides visual flowcharts and code examples showing exactly how coordinates flow through the system.

---

## 📍 The Three Coordinate Types

```
┌─────────────────────────────────────────────────────────────────────┐
│                    THREE TYPES OF COORDINATES                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  1. SOURCE COORDINATES (Where parts spawn)                          │
│     └─ Example: source_station_1_x = "55"                          │
│     └ Used by: CraneAgent (pickup location)                        │
│     └ Modbus effect: writeModbus(1, 55)                            │
│                                                                     │
│  2. PROCESS COORDINATES (Where parts are worked on)                 │
│     └─ Example: processing_station_1_x = "450"                     │
│     └ Used by: CraneAgent (dropoff location)                       │
│     └ Modbus effect: writeModbus(1, 450)                           │
│                                                                     │
│  3. SINK COORDINATES (Where parts exit)                             │
│     └─ Example: sink_station_x = "945"                             │
│     └ Used by: CraneAgent (final dropoff)                          │
│     └ Modbus effect: writeModbus(1, 945)                           │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 🔴 PHASE 1: JSON → Main.java → Agent Creation

### The JSON File Contains All Coordinates

```
┌─────────────────────────────────────────────────────┐
│ factory_layout.json (Lines 1-13)                    │
├─────────────────────────────────────────────────────┤
│                                                     │
│ {                                                   │
│   "source_station_1_x": "55",       ← PICKUP #1   │
│   "source_station_1_sensor": "17",                 │
│   "source_station_2_x": "158",      ← PICKUP #2   │
│   "source_station_2_sensor": "18",                 │
│   "processing_station_1_x": "450",  ← DROPOFF #1  │
│   "processing_station_1_reg": "4",                 │
│   "processing_station_2_x": "650",  ← DROPOFF #2  │
│   "processing_station_2_reg": "5",                 │
│   "sink_station_x": "945",          ← FINAL EXIT  │
│   "sink_station_reg": "0"                          │
│ }                                                   │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### Main.java Reads & Parses

```
STEP 1: Read File
┌──────────────────────────────┐
│ Files.readAllBytes(          │
│   Paths.get(                 │
│     "factory_layout.json"    │ ← File location (project root)
│   )                          │
│ )                            │
└──────────────────────────────┘
              ↓
        String: "{"source_station_1_x":"55",...}"

STEP 2: Strip JSON Syntax
┌──────────────────────────────┐
│ replaceAll("[{}\"\\s]", "")  │ ← Remove: {}  "  spaces
└──────────────────────────────┘
              ↓
        String: "source_station_1_x:55,source_station_1_sensor:17,..."

STEP 3: Split into Pairs
┌──────────────────────────────┐
│ split(",")                   │
└──────────────────────────────┘
              ↓
        Array: ["source_station_1_x:55", "source_station_1_sensor:17", ...]

STEP 4: Parse Each Pair
┌──────────────────────────────┐
│ for (String pair : pairs)    │
│   split(":")                 │
│   layout.put(key, value)     │
└──────────────────────────────┘
              ↓
        HashMap: {
          "source_station_1_x" → "55",
          "source_station_1_sensor" → "17",
          "processing_station_1_x" → "450",
          ...
        }
```

### Main.java Deploys Agents with Configuration

```
DEPLOY PHASE: Main.java lines 48-69

┌──────────────────────────────────────────────────────────────────┐
│ SourceAgent #1 Creation                                          │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│ new Object[]{                                                    │
│   "source_station_1",                    ← Arg[0] = serviceType │
│   "8001",                                ← Arg[1] = HTTP port   │
│   layout.get("source_station_1_x"),      ← Arg[2] = "55"       │
│   layout.get("source_station_1_sensor")  ← Arg[3] = "17"       │
│ }                                                                │
│                                                                   │
│ These 4 values are passed to SourceAgent.setup() via            │
│ Object[] args = getArguments()                                   │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│ ProcessAgent #1 Creation                                         │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│ new Object[]{                                                    │
│   "processing_station_1",           ← Arg[0] = serviceType     │
│   layout.get(...1_x"),              ← Arg[1] = "450"           │
│   layout.get(...1_reg")             ← Arg[2] = "4"             │
│ }                                                                │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│ CraneAgent Creation                                              │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│ new Object[0]     ← EMPTY! Crane gets NO config!                 │
│                                                                   │
│ Why? Because Crane fetches coordinates dynamically via           │
│ CONFIG_REQUEST protocol when it needs them.                      │
└──────────────────────────────────────────────────────────────────┘
```

---

## 🟠 PHASE 2: Agent Initialization & DF Registration

### SourceAgent Receives and Stores Coordinates

```
┌────────────────────────────────────────────────────────┐
│ SourceAgent.setup() - Lines 29-37                      │
├────────────────────────────────────────────────────────┤
│                                                        │
│ @Override                                              │
│ protected void setup() {                               │
│     Object[] args = getArguments();                    │
│     // args = ["source_station_1", "8001", "55", "17"]│
│                                                        │
│     serviceType = (String) args[0];                    │
│     // serviceType = "source_station_1"               │
│                                                        │
│     int port = Integer.parseInt((String) args[1]);    │
│     // port = 8001                                    │
│                                                        │
│     String myLocationX = (String) args[2];            │
│     // myLocationX = "55"                             │
│     // ← STORED FOR LATER USE IN CONFIG_RESPONSE!    │
│                                                        │
│     int startRegister = Integer.parseInt(             │
│         (String) args[3]                              │
│     );                                                 │
│     // startRegister = 17                             │
│     // ← STORED FOR LATER USE IN CONFIG_RESPONSE!    │
│                                                        │
│     System.out.println(getLocalName() + " ready at X:"│
│         + myLocationX + " wired to Modbus Reg:" +     │
│         startRegister);                                │
│     // Output: Source1 ready at X:55 wired to         │
│     //         Modbus Reg:17                          │
│ }                                                      │
│                                                        │
└────────────────────────────────────────────────────────┘
```

### All Agents Register with DF

```
┌──────────────────────────────────────────────┐
│ Agent Registration Pattern (All agents)      │
├──────────────────────────────────────────────┤
│                                              │
│ DFAgentDescription dfd =                     │
│   new DFAgentDescription();                  │
│ dfd.setName(getAID());                       │
│                                              │
│ ServiceDescription sd =                      │
│   new ServiceDescription();                  │
│ sd.setType(serviceType);                     │
│ // "source_station_1"                      │
│ // "processing_station_1"                  │
│ // "sink_station"                          │
│                                              │
│ dfd.addServices(sd);                         │
│ DFService.register(this, dfd);               │
│                                              │
│ Result: Agents are now discoverable in DF    │
│                                              │
└──────────────────────────────────────────────┘

DIRECTORY FACILITATOR STATE:
┌────────────────────────────────────────┐
│ DF Registry (Yellow Pages)              │
├────────────────────────────────────────┤
│ source_station_1 → SourceAgent#1       │
│ source_station_2 → SourceAgent#2       │
│ processing_station_1 → ProcessAgent#1  │
│ processing_station_2 → ProcessAgent#2  │
│ sink_station → SinkAgent               │
│ transport_service → CraneAgent         │
└────────────────────────────────────────┘
```

### Agents Set Up CONFIG_REQUEST Listeners

```
┌──────────────────────────────────────────────────┐
│ CONFIG_REQUEST Listener (All agents)            │
│ Lines 61-76 (SourceAgent.java)                  │
├──────────────────────────────────────────────────┤
│                                                  │
│ MessageTemplate reqTemplate =                    │
│   MessageTemplate.MatchOntology(                 │
│     "CONFIG_REQUEST"  ← Listen for this         │
│   );                                             │
│                                                  │
│ addBehaviour(new CyclicBehaviour() {             │
│   @Override                                      │
│   public void action() {                         │
│     ACLMessage msg =                             │
│       myAgent.receive(reqTemplate);              │
│                                                  │
│     if (msg != null) {                           │
│       // Someone asked for our config!           │
│       ACLMessage reply =                         │
│         msg.createReply();                       │
│       reply.setPerformative(INFORM);             │
│       reply.setContent(                          │
│         myLocationX + "," + startRegister        │
│         // "55,17"                              │
│       );                                         │
│       myAgent.send(reply);                       │
│     } else {                                     │
│       block();  // Wait for message              │
│     }                                            │
│   }                                              │
│ });                                              │
│                                                  │
│ Result: Agent is ready to respond with:          │
│   SourceAgent#1: "55,17"                        │
│   ProcessAgent#1: "450,4"                       │
│   SinkAgent: "945,0"                            │
│                                                  │
└──────────────────────────────────────────────────┘
```

---

## 🟡 PHASE 3: Part Spawning & Crane Request

### HTTP API Spawns Part

```
┌──────────────────────────────────┐
│ User/AI sends:                   │
│                                  │
│ curl -X POST                     │
│   http://localhost:8001/spawn    │
│   -d "type1"                    │
│                                  │
└──────────────────────────────────┘
              ↓
        SourceAgent#1 handles request
              ↓
┌──────────────────────────────────┐
│ SourceAgent creates PartAgent    │
│                                  │
│ String agentName =               │
│   "Part_" + System.                │
│   currentTimeMillis()             │
│                                   │
│ AgentController part =            │
│   createNewAgent(                 │
│     agentName,                    │
│     "agents.PartAgent",           │
│     new Object[]{"type1"}         │
│   );                              │
│ part.start();                     │
│                                  │
│ Result: PartAgent created with   │
│         type = "type1"           │
│                                  │
└──────────────────────────────────┘
```

### PartAgent Determines Route

```
┌──────────────────────────────────────────────┐
│ PartAgent.setup() - Creates routing plan    │
├──────────────────────────────────────────────┤
│                                              │
│ if (partType.equals("type1")) {              │
│   currentLocation = "source_station_1";     │
│   processPlan = [                            │
│     "processing_station_1",                 │
│     "sink_station"                          │
│   ];                                         │
│ }                                            │
│                                              │
│ Result:                                      │
│ Part knows it must:                          │
│   1. Go to processing_station_1             │
│   2. Then to sink_station                   │
│                                              │
└──────────────────────────────────────────────┘
```

### PartAgent Requests Crane Transport

```
INTERNAL ROUTING ENGINE:

State 0: Query DF for next service
  └─→ Need to go to processing_station_1
  └─→ But first, need TRANSPORT
  └─→ Search DF for "transport_service"
  └─→ Found: CraneAgent

State 1: Send CFP (Call For Proposal)
  ┌──────────────────────────────────┐
  │ ACLMessage cfp = new ACLMessage(  │
  │   ACLMessage.CFP                 │
  │ );                               │
  │ cfp.addReceiver(CraneAgent);      │
  │ cfp.setContent(                  │
  │   "source_station_1," +          │
  │   "processing_station_1"         │
  │   // PICKUP,DROPOFF locations    │
  │ );                               │
  │ myAgent.addBehaviour(            │
  │   new PartNegotiator(             │
  │     myAgent, cfp                 │
  │   )                              │
  │ );                               │
  └──────────────────────────────────┘
```

---

## 🟢 PHASE 4: Crane Receives Request & Fetches Coordinates

### CraneAgent Contract Net Handler

```
┌────────────────────────────────────────────────────┐
│ CraneAgent.handleCfp() - Lines 64-73              │
├────────────────────────────────────────────────────┤
│                                                    │
│ @Override                                          │
│ protected ACLMessage handleCfp(                     │
│     ACLMessage cfp                                 │
│ ) throws NotUnderstood Exception {                 │
│                                                    │
│   String destination =                             │
│     cfp.getContent();                              │
│   // "source_station_1,processing_station_1"     │
│                                                    │
│   System.out.println(                              │
│     "Received transport request to " +             │
│     destination + " from " +                       │
│     cfp.getSender().getLocalName()                │
│   );                                               │
│                                                    │
│   ACLMessage propose =                             │
│     cfp.createReply();                             │
│   propose.setPerformative(PROPOSE);                │
│   propose.setContent("10");                        │
│   return propose;                                  │
│   // Crane always accepts (simplified)            │
│ }                                                  │
│                                                    │
│ PartAgent receives PROPOSE and sends ACCEPT       │
│                                                    │
└────────────────────────────────────────────────────┘

Output:
  Crane: Received transport request to 
  source_station_1,processing_station_1 from Part_xxxxx
```

### CraneAgent Executes Movement (Fetching Coordinates)

```
┌───────────────────────────────────────────────────────────┐
│ CraneAgent.handleAcceptProposal() - Lines 76-86          │
├───────────────────────────────────────────────────────────┤
│                                                           │
│ String[] route =                                          │
│   cfp.getContent().split(",");                            │
│ // ["source_station_1", "processing_station_1"]          │
│                                                           │
│ String pickup = route[0];                                 │
│ // "source_station_1"                                   │
│                                                           │
│ String dropoff = route[1];                                │
│ // "processing_station_1"                               │
│                                                           │
│ myAgent.addBehaviour(                                     │
│   new CranePickAndPlaceBehaviour(                         │
│     myAgent,                                              │
│     accept,                                               │
│     pickup,    ← Passed to behaviour                      │
│     dropoff    ← Passed to behaviour                      │
│   )                                                       │
│ );                                                        │
│                                                           │
│ return null;  // Async response (handled by behaviour)   │
│                                                           │
└───────────────────────────────────────────────────────────┘
```

---

## 🔵 PHASE 5: CranePickAndPlaceBehaviour Fetches Dynamic Coordinates

### The fetchModuleConfig Method in Detail

```
┌──────────────────────────────────────────────────────────┐
│ Step 1: Search DF for Agent                              │
├──────────────────────────────────────────────────────────┤
│                                                           │
│ fetchModuleConfig("source_station_1")                    │
│       │                                                  │
│       ↓                                                  │
│ DFAgentDescription template =                            │
│   new DFAgentDescription();                              │
│ ServiceDescription sd =                                  │
│   new ServiceDescription();                              │
│ sd.setType("source_station_1");  ← Search for this      │
│ template.addServices(sd);                                │
│                                                           │
│ DFAgentDescription[] result =                            │
│   DFService.search(agentRef, template);                  │
│   // Queries DF                                          │
│   // Returns: [SourceAgent#1 description]               │
│                                                           │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ Step 2: Send CONFIG_REQUEST Message                      │
├──────────────────────────────────────────────────────────┤
│                                                           │
│ ACLMessage req =                                         │
│   new ACLMessage(ACLMessage.REQUEST);                    │
│                                                           │
│ req.addReceiver(                                         │
│   result[0].getName()  ← Send to SourceAgent#1          │
│ );                                                       │
│                                                           │
│ req.setOntology(                                         │
│   "CONFIG_REQUEST"  ← Special marker!                    │
│ );                                                       │
│                                                           │
│ req.setReplyWith(                                        │
│   "req" + System.currentTimeMillis()                     │
│   // Unique ID for matching reply                       │
│ );                                                       │
│                                                           │
│ agentRef.send(req);                                      │
│ // → Message goes to SourceAgent#1                      │
│                                                           │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ Step 3: SourceAgent Receives & Responds                  │
├──────────────────────────────────────────────────────────┤
│                                                           │
│ [Inside SourceAgent.setup() listener - lines 66-71]     │
│                                                           │
│ ACLMessage msg =                                         │
│   myAgent.receive(reqTemplate);                          │
│   // Receives: CONFIG_REQUEST from CraneAgent           │
│                                                           │
│ ACLMessage reply =                                       │
│   msg.createReply();                                     │
│ reply.setPerformative(ACLMessage.INFORM);                │
│ reply.setContent(                                        │
│   myLocationX + "," + startRegister                      │
│   // "55" + "," + "17"                                 │
│   // Sends back: "55,17"                               │
│ );                                                       │
│ myAgent.send(reply);                                     │
│                                                           │
│ → Reply goes back to CraneAgent                          │
│                                                           │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ Step 4: Crane Waits & Receives Reply                     │
├──────────────────────────────────────────────────────────┤
│                                                           │
│ ACLMessage reply =                                       │
│   agentRef.blockingReceive(                              │
│     MessageTemplate.MatchInReplyTo(                      │
│       req.getReplyWith()                                 │
│       // Match the reply ID                             │
│     ),                                                   │
│     5000  ← Timeout after 5 seconds                      │
│   );                                                     │
│                                                           │
│ if (reply != null &&                                     │
│     reply.getPerformative() ==                           │
│     ACLMessage.INFORM) {                                 │
│   return reply.getContent().split(",");                  │
│   // "55,17".split(",") → ["55", "17"]                 │
│   // RETURNS THIS ARRAY TO ACTION METHOD                │
│ }                                                        │
│                                                           │
└──────────────────────────────────────────────────────────┘
```

### CranePickAndPlaceBehaviour.action() Uses Coordinates

```
┌────────────────────────────────────────────────────────┐
│ Lines 167-189: Fetch & Parse Coordinates               │
├────────────────────────────────────────────────────────┤
│                                                        │
│ // 1. FETCH PICKUP COORDINATES                        │
│ String[] pickupConfig =                                │
│   fetchModuleConfig("source_station_1");               │
│   // Returns: ["55", "17"]                            │
│                                                        │
│ int pickupX =                                          │
│   Integer.parseInt(pickupConfig[0]);                   │
│   // 55 ← AS AN INTEGER                               │
│   // Can now use in math and Modbus commands          │
│                                                        │
│ int sensorReg =                                        │
│   Integer.parseInt(pickupConfig[1]);                   │
│   // 17 ← Used to read part sensor                    │
│                                                        │
│ // 2. FETCH DROPOFF COORDINATES                       │
│ String[] dropoffConfig =                               │
│   fetchModuleConfig("processing_station_1");           │
│   // Returns: ["450", "4"]                            │
│                                                        │
│ int dropoffX =                                         │
│   Integer.parseInt(dropoffConfig[0]);                  │
│   // 450 ← COORDINATE FROM JSON!                      │
│                                                        │
└────────────────────────────────────────────────────────┘
```

---

## 🔴 PHASE 6: Modbus Commands Use Coordinates

### Step-by-Step Crane Movement

```
┌──────────────────────────────────────────────────────┐
│ Modbus Sequence with Coordinates                     │
├──────────────────────────────────────────────────────┤
│                                                      │
│ LINE 193: writeModbus(2, 200);                      │
│   Write Z-position = 200 (safe height)             │
│   Hardcoded value (from kinematics)                │
│                                                      │
│ LINE 197: int actualStartX = readModbus(15);       │
│   Read actual X position from PLC                  │
│   Register 15 = feedback position                  │
│   Example: actualStartX = 55                       │
│                                                      │
│ LINE 198-199:                                      │
│   int distToPickup = Math.abs(pickupX - actual);  │
│   // abs(55 - 55) = 0 (already at pickup!)        │
│   long sleepToPickup = (0 * 10) + 1500;           │
│   // (distance * 10ms) + overhead = 1500ms        │
│                                                      │
│ LINE 202: writeModbus(1, pickupX);                │
│   Write X-position = 55                           │
│   Value from CONFIG_REQUEST! (from JSON!)         │
│                                                      │
│ LINE 203: Thread.sleep(sleepToPickup);            │
│   Wait 1500ms for crane movement                  │
│                                                      │
│ [... grip part ...]                                │
│                                                      │
│ LINE 214-218:                                      │
│   int distToDropoff =                              │
│     Math.abs(dropoffX - pickupX);                 │
│   // abs(450 - 55) = 395 units                    │
│   long sleepToDropoff =                            │
│     (395 * 10) + 1500;                            │
│   // (395 * 10) + 1500 = 5450ms                   │
│                                                      │
│ LINE 218: writeModbus(1, dropoffX);               │
│   Write X-position = 450                          │
│   Value from CONFIG_REQUEST! (from JSON!)         │
│   This is the DROPOFF coordinate!                 │
│                                                      │
│ LINE 219: Thread.sleep(sleepToDropoff);           │
│   Wait 5450ms for crane to move 395 units         │
│                                                      │
│ [... release part ...]                             │
│                                                      │
│ Result: Crane moved from 55 → 450 ← Coordinate   │
│          from factory_layout.json!                 │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### Modbus Register Writes

```
┌────────────────────────────────────────────┐
│ Actual Hardware Commands Generated          │
├────────────────────────────────────────────┤
│                                             │
│ writeModbus(1, 55)                          │
│ └─→ WriteSingleRegisterRequest(1, 55)      │
│     └─→ Modbus TCP → Register 1 ← 55       │
│         └─→ Hardware: Move X to 55         │
│                                             │
│ writeModbus(2, 200)                         │
│ └─→ WriteSingleRegisterRequest(2, 200)     │
│     └─→ Modbus TCP → Register 2 ← 200      │
│         └─→ Hardware: Move Z to 200 (safe) │
│                                             │
│ writeModbus(1, 450)                         │
│ └─→ WriteSingleRegisterRequest(1, 450)     │
│     └─→ Modbus TCP → Register 1 ← 450      │
│         └─→ Hardware: Move X to 450 ← JSON!│
│                                             │
│ readModbus(17)                              │
│ └─→ ReadMultipleRegistersRequest(17, 1)    │
│     └─→ Modbus TCP → Read Register 17      │
│         └─→ returns: 1 (part present)      │
│                                             │
└────────────────────────────────────────────┘
```

---

## 📊 Complete Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│ JSON: source_station_1_x = "55"                                    │
└────────────────────┬────────────────────────────────────────────────┘
                     │
        ┌────────────▼────────────┐
        │  Main.java              │
        │  layout.put(            │
        │    "source_station_1_x",│
        │    "55"                 │
        │  )                      │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │ Agent Constructor Args: │
        │ args[2] = "55"         │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │ SourceAgent.setup()     │
        │ myLocationX = "55"      │
        │ (stored locally)        │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │ Register in DF:         │
        │ "source_station_1"      │
        │ SourceAgent#1           │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │ CONFIG_REQUEST Listener │
        │ Ready to respond        │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │ Runtime: Part requests  │
        │ transport to            │
        │ processing_station_1    │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │ CraneAgent:             │
        │ fetchModuleConfig(      │
        │   "source_station_1"    │
        │ )                       │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │ CraneAgent searches DF  │
        │ Finds: SourceAgent#1    │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │ CraneAgent sends        │
        │ CONFIG_REQUEST          │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │ SourceAgent receives    │
        │ CONFIG_REQUEST          │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │ SourceAgent replies:    │
        │ INFORM: "55,17"         │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │ CraneAgent receives     │
        │ reply: ["55", "17"]     │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │ pickupX =               │
        │ Integer.parseInt("55")  │
        │ = 55                    │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │ writeModbus(1, 55)      │
        │                         │
        │ Modbus TCP → Register 1 │
        │ Value ← 55 (from JSON!) │
        └────────────┬────────────┘
                     │
        ┌────────────▼────────────┐
        │ Hardware receives:       │
        │ Move crane X to 55       │
        └────────────┬────────────┘
                     │
                     ▼
                 🎉 CRANE MOVES!
```

---

## 🧪 Live Tracing Example

Here's what actually happens when you run the system:

### Startup (Lines appear in this order)

```
[1] Successfully loaded factory_layout.json: {source_station_1_x=55, 
    source_station_2_x=158, processing_station_1_x=450, ...}

[2] Crane Agent Crane is starting up...
    Crane Agent connected to Modbus simulation.

[3] Source1 ready at X:55 wired to Modbus Reg:17
    Source1 is listening for AI commands on http://localhost:8001/spawn

[4] Process1 ready at X:450 wired to Modbus Reg:4
    Process1 connected to Modbus simulation.

[5] JADE Platform and all static agents started successfully!
```

### Runtime (User sends spawn request)

```
[6] curl -X POST http://localhost:8001/spawn -d "type1"

[7] [Source1] API TRIGGERED! Injecting Part_1718456789 (type1) 
    into the JADE platform.

[8] Part Agent [Part_1718456789] (Type: type1) has entered the system.
    [Part_1718456789] Process plan loaded: [processing_station_1, sink_station]

[9] [Part_1718456789] Needs to go to: processing_station_1. 
    Hailing Crane...

[10] Crane: Received transport request to source_station_1,processing_station_1 
     from Part_1718456789

[11] Crane: Validating route config from source_station_1 to 
     processing_station_1

[12] Crane: Location data acquired. Raising to safe travel height.

[13] Crane: Moving from actual X:55 to pickup X:55 (Waiting 1500ms)
     └─ X:55 came from fetchModuleConfig("source_station_1") → "55,17"

[14] Crane: Part secured. Moving to dropoff X:450 (Waiting 5450ms)
     └─ X:450 came from fetchModuleConfig("processing_station_1") → "450,4"

[15] [Part_1718456789] Successfully transported to destination.
    [Part_1718456789] Arrived! Requesting service from: processing_station_1

[16] Process1: Proposal accepted! Starting physical work...
    Process1: Physical work finished. Notifying Part_1718456789

[17] [Part_1718456789] Service complete. Queue updated. 
     Remaining steps: [sink_station]
```

**Key Lines Show Coordinates From JSON:**
- Line 13: `X:55` ← from `source_station_1_x` in JSON
- Line 14: `X:450` ← from `processing_station_1_x` in JSON

---

## 🎯 The Key Insight

```
Every time you see "X:450" in the crane output,
that value came from factory_layout.json!

JSON: "processing_station_1_x": "450"
  ↓ (stored in ProcessAgent)
CONFIG_REQUEST/REPLY: "450,4"
  ↓ (parsed by CraneAgent)
writeModbus(1, 450)
  ↓ (sent to hardware)
CRANE MOVES TO 450!
```

Changing the JSON and restarting changes where the crane moves—**no code changes needed!**


