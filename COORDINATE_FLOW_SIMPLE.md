# How Coordinates Flow from JSON to Crane Movement

## 📝 Simple Explanation

The coordinate system works in two phases. **Phase 1 (Startup):** At system startup, `Main.java` reads `factory_layout.json` and extracts all coordinates (like X=450). These values are then passed as arguments when creating agents. Each agent (SourceAgent, ProcessAgent, SinkAgent) receives its coordinates and stores them locally. **Phase 2 (Runtime):** When a part needs transport, the CraneAgent doesn't know any coordinates upfront—instead, it dynamically asks each station "What's your coordinate?" using a CONFIG_REQUEST message. The stations reply with their stored coordinates (e.g., "450,4" meaning X-position 450 and Modbus register 4). The Crane then uses those coordinates directly in Modbus commands to move the hardware (e.g., `writeModbus(1, 450)` tells the crane to move to X=450). This design means coordinates are **centralized in JSON** and can be changed without touching any code—just restart the system and the crane uses the new positions.

---

## 🎯 High-Level Diagram

```
┌──────────────────┐
│ factory_layout   │
│     .json        │
│                  │
│ X coordinates    │
│ Modbus registers │
└────────┬─────────┘
         │
         │ Read & Parse
         ▼
    ┌─────────────┐
    │  Main.java  │
    │             │
    │ Create      │
    │ Agents      │
    └────┬────┬───┘
         │    │
    ┌────▼┐  ┌▼────────┐      ┌──────────┐
    │Src  │  │Process  │      │ Crane    │
    │Agent│  │ Agent   │      │ (empty)  │
    └────┬┘  └┬────────┘      └────┬─────┘
         │    │                    │
         │ Store "450,4"           │ Ask: "What's your X?"
         │                         │
         └─────────────┬───────────┘
                       │
              CONFIG_REQUEST
            (Dynamic Discovery)
                       │
         ┌─────────────▼──────────┐
         │ Agent replies:         │
         │ "450,4"                │
         │ (X-coord, register)    │
         └─────────────┬──────────┘
                       │
         ┌─────────────▼──────────┐
         │ Crane executes:        │
         │ writeModbus(1, 450)    │
         │                        │
         │ ← 450 from JSON!       │
         └─────────────┬──────────┘
                       │
                       ▼
                  Crane Moves!
```

---

## 🔑 Key Points

- **JSON is the source of truth** for all coordinates
- **Main.java distributes** JSON values to agents via constructor arguments
- **Agents store locally** and register in Directory Facilitator
- **CraneAgent fetches dynamically** via CONFIG_REQUEST at runtime
- **No hardcoding** - change JSON, restart, new coordinates take effect


