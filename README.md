# Multi-Agent Control of a Manufacturing Cell

A sophisticated **multi-agent system** built with **JADE (Java Agent Development Framework)** that simulates and controls an autonomous manufacturing cell with intelligent agents coordinating material flow, processing tasks, and transport logistics.

## 📋 Table of Contents

- [Overview](#overview)
- [System Architecture](#system-architecture)
- [Key Features](#key-features)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Installation & Setup](#installation--setup)
- [Running the System](#running-the-system)
- [Agent Descriptions](#agent-descriptions)
- [Communication Protocol](#communication-protocol)
- [API Endpoints](#api-endpoints)
- [Configuration](#configuration)
- [Integration with AI Planning](#integration-with-ai-planning)

---

## Overview

This project simulates a manufacturing cell with multiple autonomous agents that collaborate to process parts through a production pipeline. The system uses **multi-agent negotiation protocols** (FIPA Contract Net) to coordinate actions, **Modbus TCP** for hardware communication, and **HTTP APIs** for external system integration.

The manufacturing cell consists of:
- **2 Source Stations** - Where parts enter the system
- **2 Processing Stations** - Where parts undergo manufacturing operations
- **1 Crane System** - Autonomous material handling unit
- **1 Sink Station** - Final destination where processed parts exit

Each component is represented by an intelligent agent that autonomously negotiates and executes its responsibilities.

---

## System Architecture

### High-Level Workflow

```
Part Spawned → Crane Transport → Processing Station → Crane Transport → Next Step/Sink
    ↓              ↓                    ↓                    ↓              ↓
Part Agent   Crane Agent         Process Agent         Crane Agent    Sink Agent
             (Contract Net)       (Modbus TCP)          (Modbus TCP)
```

### Configuration-Driven Architecture

The system now uses **externalized configuration** via `factory_layout.json`:
- All station coordinates and Modbus registers are **centralized in JSON**
- Agents dynamically fetch configuration at runtime via **CONFIG_REQUEST protocol**
- **Decoupled design** - CraneAgent queries each source/destination for coordinates
- **Configuration-driven discovery** - No hardcoded coordinates or registers

```
factory_layout.json (Single Source of Truth)
    ↓
[Agents read on startup]
    ↓
[CONFIG_REQUEST protocol for dynamic lookups]
    ↓
[Crane/Agents fetch coordinates as needed]
```

### Agent Communication Pattern (FIPA Contract Net + CONFIG_REQUEST)

```
1. Part Agent (Initiator)
   └─→ CFP (Call For Proposal) 
       └─→ Crane/Process Agent (Responder)
           └─→ PROPOSE
               └─→ Part Agent
                   └─→ ACCEPT_PROPOSAL
                       └─→ [CONFIG_REQUEST exchanges for dynamic coordinates]
                           └─→ [Execute Physical Action with fetched config]
                               └─→ INFORM (Completion)
                                   └─→ Part Agent
```

---

## Key Features

### 🤖 Intelligent Agent Coordination
- **Autonomous decision-making** - Each agent manages its own state and responsibilities
- **FIPA-compliant negotiation** - Uses industry-standard Contract Net Protocol for service negotiation
- **Dynamic routing** - Parts determine their own routes based on product type
- **Error handling** - Agents validate physical sensor data before executing actions

### 🔧 Hardware Integration
- **Modbus TCP connectivity** - Direct communication with industrial simulation/hardware
- **Closed-loop position tracking** - Crane validates actual position before planning movements
- **Sensor validation** - Confirms part presence before initiating transport
- **Asynchronous execution** - Non-blocking hardware operations with completion callbacks

### 🌐 External System Integration
- **HTTP API endpoints** - SourceAgents expose REST APIs for part spawning
- **Configurable parameters** - Agents accept dynamic configuration at runtime
- **LLM-ready architecture** - Designed to integrate with AI planning systems for intelligent work order generation

### 📊 Scalability & Modularity
- **Agent-based design** - Easy to add new stations or capabilities
- **Service discovery** - Uses Directory Facilitator (Yellow Pages) for dynamic service lookup
- **Behavioral composition** - Complex behaviors built from composable JADE behaviors

---

## Technology Stack

| Component | Technology |
|-----------|-----------|
| **Framework** | JADE 4.x (Java Agent Development Framework) |
| **Language** | Java 17+ |
| **Build Tool** | Maven |
| **Hardware Communication** | j2mod Modbus TCP Client (v3.1.1) |
| **Data Processing** | Jackson JSON (v2.15.2) |
| **Server** | Java Built-in HttpServer |
| **Protocol** | FIPA Agent Communication Language (ACL) |

---

## Project Structure

```
Multi-Agent_Control_of_a_Manufacturing_Cell/
├── pom.xml                          # Maven project configuration
├── factory_layout.json              # ⭐ NEW: Centralized configuration (coordinates & Modbus registers)
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── Main.java           # System entry point & JSON-based agent initialization
│   │   │   └── agents/
│   │   │       ├── CraneAgent.java          # Transport coordinator (dynamic config fetching)
│   │   │       ├── SourceAgent.java         # Part spawning (config listener pattern)
│   │   │       ├── ProcessAgent.java        # Machine controller (hardware failure detection)
│   │   │       ├── SinkAgent.java           # Terminal delivery point
│   │   │       ├── PartAgent.java           # Individual part entity & router
│   │   │       └── PartNegotiator.java      # Contract Net protocol handler
│   │   └── resources/               # Configuration resources
│   └── test/
│       └── java/                    # Test suites (if any)
└── target/                          # Compiled classes & artifacts
```

---

## Prerequisites

### System Requirements
- **Java Development Kit (JDK) 17 or higher**
- **Maven 3.6+** (for building the project)
- **4GB RAM minimum** (recommended 8GB for smooth operation)
- **Linux/macOS/Windows** with network connectivity

### External Dependencies
- **JADE Platform** (included via Maven)
- **j2mod Modbus Library** (included via Maven)
- **Modbus TCP Simulator** (optional - for hardware simulation)
  - The system expects a Modbus server running on `localhost:502` by default

### Network Configuration
- **Localhost networking** - Default configuration uses loopback interface
- **Ports Required:**
  - `1099` - JADE DF/AMS default
  - `502` - Modbus TCP (optional, for hardware)
  - `8001`, `8002` - Source Agent HTTP listeners
  - `8080+` - GUI (if JADE RMA enabled)

---

## Installation & Setup

### Step 1: Clone the Repository
```bash
cd /home/clauds/SpringbootProjects/
git clone <repository-url>
cd Multi-Agent_Control_of_a_Manufacturing_Cell
```

### Step 2: Install Dependencies
```bash
mvn clean install
```
This command:
- Cleans previous builds
- Downloads JADE, j2mod, and Jackson dependencies
- Compiles Java source code
- Packages the application

### Step 3: Build the Project
```bash
mvn compile
```

### Step 4: Configure factory_layout.json
Before running, verify `factory_layout.json` contains your manufacturing cell layout:
```json
{
  "source_station_1_x": "55",
  "source_station_1_sensor": "17",
  "source_station_2_x": "158",
  "source_station_2_sensor": "18",
  "processing_station_1_x": "450",
  "processing_station_1_reg": "4",
  "processing_station_2_x": "650",
  "processing_station_2_reg": "5",
  "sink_station_x": "945",
  "sink_station_reg": "0"
}
```
- **Coordinates** (X values) - Crane movement targets in units
- **Sensor registers** - Modbus registers for part presence detection
- **Control registers** - Modbus registers for machine/station operations

### Step 5: (Optional) Set Up Modbus Simulator
If you have a Modbus TCP simulator:
```bash
# Example with OpenPLC (if available)
# Configure it to listen on 127.0.0.1:502
# Set up the following registers as defined in factory_layout.json:
# Register 4: Process Station 1 control
# Register 5: Process Station 2 control
# Register 15: Crane actual X position (feedback)
# Register 17: Source Station 1 sensor (part present)
# Register 18: Source Station 2 sensor (part present)
# Register 23: Hardware breakdown button (fault detection)
```

---

## Running the System

### Basic Execution
```bash
# From the project root directory
mvn exec:java -Dexec.mainClass="Main"
```

### With JADE GUI Console (Recommended for Development)
The system launches with the JADE RMA (Remote Management Agent) console by default. This provides:
- Real-time agent monitoring
- Message inspection
- Agent lifecycle control
- Performance metrics

### Expected Console Output
```
Crane Agent Crane is starting up...
Crane Agent connected to Modbus simulation.
Source Agent Source1 is ready.
Source Agent Source1 is listening for AI commands on http://localhost:8001/spawn
Source Agent Source2 is ready.
Source Agent Source2 is listening for AI commands on http://localhost:8002/spawn
Process Agent Process1 is ready.
Process Agent Process2 is ready.
Sink Agent Sink is ready.
JADE Platform and all static agents started successfully!
```

### Interactive Testing
Once running, you can trigger the manufacturing process via HTTP:
```bash
# Spawn a type1 part from Source1
curl -X POST http://localhost:8001/spawn -d "type1"

# Spawn a type2 part from Source2
curl -X POST http://localhost:8002/spawn -d "type2"
```

---

## Agent Descriptions

### 🚀 **Main.java** - System Bootstrap
**Responsibility:** Initialize the JADE platform and deploy all static agents using external configuration

**Key Functions:**
- Reads `factory_layout.json` for all station coordinates and Modbus registers
- Creates JADE runtime and main container
- Deploys 6 core agents with **dynamic configuration from JSON**:
  - Crane (no args - fetches config from other agents on demand)
  - 2 Source agents (serviceType, port, X-coord, sensor register)
  - 2 Process agents (serviceType, X-coord, control register)
  - 1 Sink agent (serviceType, X-coord, control register)
- Enables JADE RMA GUI for monitoring

**Configuration Pattern:**
```java
// Reads JSON once at startup
Map<String, String> layout = /* parsed from factory_layout.json */;

// Deploy agents with externalized config
AgentController source1 = mainContainer.createNewAgent("Source1", "agents.SourceAgent",
    new Object[]{"source_station_1", "8001", layout.get("source_station_1_x"), layout.get("source_station_1_sensor")});
```

---

### 🦾 **CraneAgent.java** - Material Handling System (Dynamically Configured)
**Responsibility:** Autonomously transport parts between stations using runtime-fetched configuration

**Key Capabilities:**
- **Service Registration:** Registers as `transport_service` in the Directory Facilitator
- **Contract Net Responder:** Listens for part transportation requests (CFPs)
- **Dynamic Configuration Fetching:** Sends CONFIG_REQUEST to each station to get coordinates
- **Modbus Integration:** Commands physical crane via Modbus TCP
- **Sensor Validation:** Reads source station sensors before pickup
- **Closed-loop Position Tracking:** Validates actual physical position from Modbus register 15

**Workflow:**
1. Receives CFP: `{pickup_location},{dropoff_location}`
2. **Fetches coordinates dynamically** via CONFIG_REQUEST protocol:
   - Queries source station for pickup X-coordinate
   - Queries destination station for dropoff X-coordinate
3. Validates physical sensor (if at source)
4. Executes Modbus commands with fetched coordinates:
   - Raise gripper to safe height (Z=200)
   - Move to pickup location (X coordinate)
   - Lower and grip (Z=82, grip=1)
   - Move to dropoff location
   - Release and retract
5. Sends INFORM completion message

**Dynamic Coordinate Fetching (CranePickAndPlaceBehaviour):**
```
[Crane receives: "source_station_1,processing_station_1"]
    ↓
[Sends CONFIG_REQUEST to "source_station_1"]
    ↓
[Receives "55,17" (X coord, sensor register)]
    ↓
[Sends CONFIG_REQUEST to "processing_station_1"]
    ↓
[Receives "450,4" (X coord, control register)]
    ↓
[Executes move with actual coordinates]
```

**Modbus Registers (Read from factory_layout.json):**
| Register | Function |
|----------|----------|
| 1 | Crane X position command |
| 2 | Crane Z (height) position |
| 3 | Gripper state (0=open, 1=grip) |
| 15 | Crane actual X position (feedback) |
| 17+ | Source/Station sensors (from config) |

---

### 📦 **SourceAgent.java** - Part Entry Point (Configuration Listener)
**Responsibility:** Serve as spawn point with dynamic configuration capabilities

**Key Capabilities:**
- **HTTP Server:** Exposes REST API on configurable port (8001, 8002)
- **Service Registration:** Registers with name from `factory_layout.json`
- **CONFIG_REQUEST Listener:** Responds to CraneAgent coordinate queries with (X, SensorReg)
- **Dynamic Part Creation:** Spawns PartAgent instances on HTTP request

**Configuration Pattern (from Main.java):**
```java
new Object[]{"source_station_1", "8001", "55", "17"}
// Meaning: This station responds to CONFIG_REQUEST with "55,17"
```

**Behavior:** When Crane sends CONFIG_REQUEST:
```
Crane: "Give me your config for source_station_1"
SourceAgent: "INFORM: 55,17" (X-coordinate, sensor register)
```

---

### ⚙️ **ProcessAgent.java** - Manufacturing Station (Config Listener + Fault Detection)
**Responsibility:** Execute processing tasks with dynamic configuration and hardware failure monitoring

**Key Capabilities:**
- **Service Registration:** Registers with name from `factory_layout.json`
- **CONTRACT Net Responder:** Negotiates machine availability
- **CONFIG_REQUEST Listener:** Responds to CraneAgent queries with (X, ControlReg)
- **Modbus Control:** Triggers physical processing via assigned Modbus register
- **Hardware Failure Detection:** Monitors Modbus register 23 for breakdown signals
- **Async Processing:** Non-blocking execution with delayed completion notification

**Workflow:**
1. Receives CFP from PartAgent
2. Immediately sends PROPOSE
3. Upon ACCEPT_PROPOSAL:
   - Writes `1` to assigned Modbus register to START processing
   - **Actively polls register 23 every 50ms for hardware failures**
   - If breakdown detected → sends FAILURE message
   - Otherwise waits 4 seconds, writes `0` to STOP
   - Sends INFORM completion message

**Configuration Pattern (from Main.java):**
```java
new Object[]{"processing_station_1", "450", "4"}
// Meaning: This station responds to CONFIG_REQUEST with "450,4" (X-coord, control register)
```

**Fault Tolerance:**
```
[Process running...] → [Register 23 polled every 50ms]
    ↓
[If breaker == 1] → [Machine failure detected!]
    ↓
[Send FAILURE message to Part]
    ↓
[Part can re-route or alert system]
```

---

### 🎯 **PartAgent.java** - Individual Part Entity
**Responsibility:** Navigate the manufacturing system using agent-discovered services

**Key Capabilities:**
- **Dynamic Route Planning:** Determines route based on product type at initialization
- **Service Discovery:** Queries DF for available transport and processing services
- **Contract Net Initiator:** Negotiates with service providers (automatically gets location from CONFIG_REQUEST)
- **State Machine Routing:** FSM-based path execution
- **Error Recovery:** Handles FAILURE messages and service unavailability

**Route Templates:**
```
Type1 Parts:
  source_station_1 ──→ processing_station_1 ──→ sink_station

Type2 Parts:
  source_station_2 ──→ processing_station_2 ──→ processing_station_1 ──→ sink_station
```

---

### 🏁 **SinkAgent.java** - System Exit (Configuration Listener)
**Responsibility:** Accept completed parts and respond to configuration requests

**Key Capabilities:**
- **Service Registration:** Registers as `sink_station` (name from factory_layout.json)
- **CONFIG_REQUEST Listener:** Responds with (X, ControlReg) for Crane coordination
- **Contract Net Responder:** Negotiates part acceptance
- **Completion Notification:** Acknowledges part completion

**Configuration Pattern:**
```java
new Object[]{"sink_station", "945", "0"}
// Responds with "945,0" when Crane requests config
```

---

## Communication Protocol

### FIPA ACL Message Types Used

| Message Type | Sender | Receiver | Content | Purpose |
|-------------|--------|----------|---------|---------|
| **CFP** | PartAgent | Crane/Process | Pickup,Dropoff or ServiceName | Request service quote |
| **PROPOSE** | Crane/Process | PartAgent | "10" (cost value) | Offer to provide service |
| **ACCEPT_PROPOSAL** | PartAgent | Crane/Process | Confirmed parameters | Accept the proposal |
| **INFORM** | Crane/Process | PartAgent | "Completed" | Task finished successfully |
| **FAILURE** | Crane/Process | PartAgent | "Reason" | Unable to complete task |

### DirectoryFacilitator (Yellow Pages) Services

| Service Type | Agent | Description |
|-------------|-------|-------------|
| `transport_service` | CraneAgent | Material transport |
| `source_station_1` | SourceAgent | Part entry point 1 |
| `source_station_2` | SourceAgent | Part entry point 2 |
| `processing_station_1` | ProcessAgent | Manufacturing station 1 |
| `processing_station_2` | ProcessAgent | Manufacturing station 2 |
| `sink_station` | SinkAgent | Part exit point |

---

## API Endpoints

### Source Station HTTP Endpoints

#### Spawn Part (POST)
```
POST http://localhost:8001/spawn
POST http://localhost:8002/spawn

Request Body: plain text
  type1   - Spawn a Type1 part
  type2   - Spawn a Type2 part

Response (200 OK):
  SUCCESS: Spawned Part_1234567890 of type type1 from source_station_1

Response (500 ERROR):
  ERROR: Failed to spawn part - <exception_message>
```

#### Example Requests

**Using curl:**
```bash
# Spawn type1 part from source 1
curl -X POST http://localhost:8001/spawn -d "type1"

# Spawn type2 part from source 2
curl -X POST http://localhost:8002/spawn -d "type2"

# Multiple parts
for i in {1..5}; do
  curl -X POST http://localhost:8001/spawn -d "type1"
  sleep 2
done
```

**Using Python:**
```python
import requests

response = requests.post('http://localhost:8001/spawn', data='type1')
print(response.status_code, response.text)
```

**Using JavaScript/Node.js:**
```javascript
fetch('http://localhost:8001/spawn', {
  method: 'POST',
  body: 'type1'
}).then(r => r.text()).then(console.log)
```

---

## Configuration

### Centralized Configuration: factory_layout.json

All manufacturing cell hardware parameters are defined in one place:

```json
{
  "source_station_1_x": "55",
  "source_station_1_sensor": "17",
  "source_station_2_x": "158",
  "source_station_2_sensor": "18",
  "processing_station_1_x": "450",
  "processing_station_1_reg": "4",
  "processing_station_2_x": "650",
  "processing_station_2_reg": "5",
  "sink_station_x": "945",
  "sink_station_reg": "0"
}
```

**Key Parameters:**
- `{station}_x` - Crane movement X coordinate
- `{station}_sensor` (sources only) - Modbus register for part presence detection
- `{station}_reg` (processes/sink) - Modbus register for machine control

**How It Works:**
1. Main.java reads factory_layout.json at startup
2. Agents receive their configuration as arguments upon creation
3. Crane fetches coordinates dynamically via CONFIG_REQUEST protocol
4. No hardcoding required - change JSON to reconfigure the entire system

### Runtime Configuration Modifications

#### Edit factory_layout.json to Change Hardware Mapping
```json
// Change station coordinates (system automatically picks up on restart)
"source_station_1_x": "55"  →  "source_station_1_x": "60"
```

#### JADE Platform Configuration
**File:** `Main.java` (lines 17-19)
```java
Profile profile = new ProfileImpl();
profile.setParameter(Profile.MAIN_HOST, "localhost");  // Change host if needed
profile.setParameter(Profile.GUI, "true");              // Set to "false" for headless mode
```

#### Modbus TCP Connection
**Files:** CraneAgent.java, ProcessAgent.java (lines ~44)
```java
InetAddress address = InetAddress.getByName("127.0.0.1");
modbusConnection.setPort(502);  // Change port here if needed
```

#### Source Agent HTTP Ports
**Automatically managed from Main.java and factory_layout.json**
```java
// HTTP ports are hardcoded in Main.java:
// Source1: port 8001
// Source2: port 8002
// (To change, modify Main.java deployment section)
```

### Build Configuration

**File:** `pom.xml`
```xml
<properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>
```

---

## Troubleshooting

### Issue: Modbus Connection Failed
**Symptom:** `CraneAgent failed to connect to Modbus`

**Solution:**
1. Ensure Modbus simulator is running on `localhost:502`
2. Check firewall rules (port 502 may require sudo)
3. Verify the simulator is actually listening: `netstat -tuln | grep 502`
4. If no simulator available, consider disabling Modbus in CraneAgent for testing

### Issue: Source Agent HTTP Port Already in Use
**Symptom:** `Address already in use` on port 8001/8002

**Solution:**
1. Kill existing Java processes: `pkill -f java`
2. Change port numbers in `Main.java` to unused ports
3. Verify ports are available: `netstat -tuln | grep 8001`

### Issue: Parts Not Spawning from API
**Symptom:** HTTP 500 error when calling `/spawn`

**Solution:**
1. Verify SourceAgent started: Check console output for "listening for AI commands"
2. Check firewall/network: `curl http://localhost:8001/spawn`
3. Inspect JADE RMA console for agent errors
4. Ensure correct request format (POST, plain text body)

### Issue: Parts Stuck in Limbo
**Symptom:** Parts appear in logs but don't move

**Solution:**
1. Check Directory Facilitator for service registrations (JADE RMA → Agents → Monitor)
2. Verify all agents are in RUNNING state
3. Check for FAILURE messages in console output
4. Restart entire system: `pkill -f java && mvn exec:java -Dexec.mainClass="Main"`

---

## Performance Monitoring

### JADE RMA Console Features
1. **Agents Tab** - View all active agents and their state
2. **Services Tab** - Monitor registered services in Directory Facilitator
3. **Conversations Tab** - Track FIPA ACL message exchanges in real-time
4. **Logging Tab** - View platform-level events and warnings

### Key Metrics to Monitor
- **Cycle Time:** Time from part spawn to completion (target: <30 seconds for type1)
- **Utilization:** Crane busy time / total time
- **Queue Depth:** Number of waiting parts at each station
- **Error Rate:** Failed negotiation attempts / total attempts

### Console Output Interpretation

**Healthy State:**
```
[Part_xxxxxx] Needs to go to: processing_station_1. Hailing Crane...
[Crane] Moving from actual X:55 to pickup X:450
[Process1] Physical work finished. Notifying Part_xxxxxx
[Part_xxxxxx] Service complete.
```

**Problematic State:**
```
[Part_xxxxxx] No provider found. Waiting...       ← Service not registered
[Crane] ERROR! No parts generated at source_station_1!  ← Sensor issue
[Part_xxxxxx] FATAL ERROR: Received failure notice  ← Negotiation failed
```

---

## Integration with AI Planning

This system is designed to work seamlessly with AI/LLM-based planning systems that generate work orders and part spawn commands.

### How External AI Systems Integrate

1. **Part Type Command Generation:**
   - LLM analyzes production goals
   - Generates spawn commands: `type1` or `type2`

2. **HTTP API Calls:**
   - AI system makes HTTP POST requests to source agents
   - Sends optimal sequencing of part types
   - Adjusts timing based on manufacturing results

3. **Real-time Feedback:**
   - Monitors console output via log aggregation
   - Tracks part completion status
   - Adjusts production plan based on actual cycle times

4. **Optimization Loop:**
   ```
   [AI Planner] ──→ [Spawn API] ──→ [Manufacturing System]
        ↑                                    ↓
        └──────────────────────────────────┘
         (feedback & monitor)
   ```

### Example AI Integration Script

```python
import requests
import time

def spawn_production_plan(parts):
    """Execute a production plan by spawning parts sequentially"""
    for part_type, source in parts:
        url = f'http://localhost:{8001 if source == 1 else 8002}/spawn'
        response = requests.post(url, data=part_type)
        print(f"Spawned {part_type} from source {source}: {response.text}")
        
        # Wait for part to complete
        time.sleep(15)

# Run production plan: 3x type1, then 2x type2
production_plan = [
    ("type1", 1), ("type1", 1), ("type1", 1),
    ("type2", 2), ("type2", 2),
]

spawn_production_plan(production_plan)
```

### Extending for AI Integration

To add custom planning logic:

1. **Create an AI Agent** - Extend `PartAgent` or create new `PlannerAgent`
2. **Implement Logic** - Add decision-making algorithms
3. **Spawn Programmatically** - Use `getContainerController().createNewAgent()`
4. **Monitor via Logs** - Aggregate JADE output for real-time feedback

---

## Advanced Topics

### Adding New Manufacturing Stations

1. **Create New Agent Class** - Extend `ProcessAgent` pattern
2. **Register Service** - Add unique `serviceType` in setup()
3. **Deploy in Main.java** - Create agent in main container
4. **Update PartAgent Routes** - Add station to `processPlan` routes
5. **Map Modbus Registers** - Assign hardware control registers

### Scalability Considerations

- **Crane Bottleneck** - Multiple cranes can be added for parallel transport
- **DF Performance** - Directory Facilitator can handle 100s of agents efficiently
- **Network Latency** - Modbus TCP can introduce delays in real hardware
- **Message Queue** - JADE queues messages, but very high load may cause delays

### Error Handling Strategy

The system implements defensive error handling:
- Closed-loop position verification before movements
- Sensor validation before transporting
- Graceful degradation on service failures
- Automatic cleanup of failed part agents

---

## References & Related Work

### JADE Documentation
- [JADE Official Website](http://jade.tilab.com/)
- [JADE Programmer's Guide](http://jade.tilab.com/doc/api/)
- [FIPA Specifications](http://www.fipa.org/)

### Modbus Protocol
- [j2mod Library GitHub](https://github.com/ghgande/j2mod)
- [Modbus TCP Specification](https://www.modbustools.com/modbus.html)

### Manufacturing System Concepts
- [Smart Manufacturing Systems](https://www.nist.gov/el/intelligent-systems-division-73400/smart-manufacturing-systems)
- [Multi-Agent Systems in Manufacturing](https://ieeexplore.ieee.org/)

### AI Integration
- **See the linked LLM planner system below** for complete AI-based planning integration

---

## Related Projects

### 🤖 LLM-Based Manufacturing Planner

This project integrates with an advanced **Large Language Model (LLM) planner** that uses AI to generate optimal production sequences. The AI system analyzes manufacturing goals, predicts optimal part type sequencing, and dynamically adjusts the production plan in real-time.

**Repository:** [Multi-Agent Control of a Manufacturing Cell - LLM Planner](https://github.com/MaryWagura/Multi-Agent_Control_of_a_Manufacturing_Cell_LLM_Planner.git)

**Integration:**
- The LLM planner analyzes production requirements
- Generates optimal spawn sequences via HTTP API
- Monitors real-time system performance
- Dynamically adjusts scheduling based on actual execution
- Provides intelligent decision-making for work order generation

**Key Features:**
- ✨ GPT-4/Claude integration for natural language planning
- 📊 Production optimization algorithms
- 📈 Real-time monitoring and feedback loops
- 🔄 Adaptive scheduling
- 🎯 Goal-oriented task generation

---

## License

This project is provided as-is for educational and research purposes.

---

## Contributing

Contributions, improvements, and bug reports are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Submit a pull request with detailed descriptions

---

## Acknowledgments

- **JADE Framework** - For the foundational agent framework
- **j2mod** - For Modbus TCP client implementation
- **FIPA** - For the standardized agent communication protocol
- Manufacturing systems research community for best practices

---

## Contact & Support

For questions, issues, or suggestions:
- Open an issue in the repository
- Contact the project maintainers
- Refer to the linked LLM planner repository for AI integration support

---

**Last Updated:** June 7, 2026

**Status:** ✅ Active Development | 🟢 Production Ready | 🔗 LLM Integration Available

