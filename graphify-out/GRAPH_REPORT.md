# Graph Report - Peterwolfs-Groundworks-dump-truck  (2026-10-08)

## Corpus Check
- Corpus is ~42,589 words - fits in a single context window. You may not need a graph.

## Summary
- 664 nodes · 1948 edges · 24 communities (12 shown, 12 thin omitted)
- Extraction: 86% EXTRACTED · 14% INFERRED · 0% AMBIGUOUS · INFERRED: 276 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Client Models and Rendering
- Recorded Route Autopilot
- Vehicle and Cargo Simulation
- Independent Shuttle Navigation
- Planner and Traffic Tests
- Replay Tests and Storage
- Sound Signals and Reservations
- Movement and Persistence
- Driver Input Networking
- Entity Container Integration
- Fabric Mod Registration
- Truck Item Interactions
- Autopilot Lifecycle States
- Truck HUD
- Documented Truck Features
- Audio Attribution
- Vehicle Texture Atlas
- Truck Item Artwork
- Mod Icon Artwork

## God Nodes (most connected - your core abstractions)
1. `GroundworksDumpTruckEntity` - 135 edges
2. `DumpTruckAutopilot` - 60 edges
3. `DumpTruckShuttle` - 45 edges
4. `DumpTruckRoute` - 31 edges
5. `DumpTruckMovementController` - 26 edges

## Surprising Connections (you probably didn't know these)
- `DumpTruckRenderer` --references--> `GroundworksDumpTruckEntity`  [EXTRACTED]
  src/client/java/com/piotrek/groundworksdumptruck/client/render/DumpTruckRenderer.java → src/main/java/com/piotrek/groundworksdumptruck/entity/GroundworksDumpTruckEntity.java
- `Crossing` --references--> `GroundworksDumpTruckEntity`  [EXTRACTED]
  src/gametest/java/com/piotrek/groundworksdumptruck/gametest/RouteReplayClientGameTest.java → src/main/java/com/piotrek/groundworksdumptruck/entity/GroundworksDumpTruckEntity.java
- `Convoy` --references--> `GroundworksDumpTruckEntity`  [EXTRACTED]
  src/gametest/java/com/piotrek/groundworksdumptruck/gametest/RouteReplayClientGameTest.java → src/main/java/com/piotrek/groundworksdumptruck/entity/GroundworksDumpTruckEntity.java

## Import Cycles
- None detected.

## Communities (24 total, 12 thin omitted)

### Community 0 - "Client Models and Rendering"
Cohesion: 0.06
Nodes (6): GroundworksDumpTruckClient, DumpTruckModel, DumpTruckBedLoadRenderer, DumpTruckRenderer, DumpTruckRenderState, GranularDumpBedContentsRenderer

### Community 1 - "Recorded Route Autopilot"
Cohesion: 0.09
Nodes (3): DumpTruckAutopilot, DumpTruckRoute, RouteWaypoint

### Community 2 - "Vehicle and Cargo Simulation"
Cohesion: 0.05
Nodes (3): EngineLoop, GroundworksDumpTruckEntity, TruckLights

### Community 3 - "Independent Shuttle Navigation"
Cohesion: 0.09
Nodes (7): DumpReservationRegression, DumpStationReservations, DumpTruckShuttle, Result, ARMED, NO_EXCAVATOR, NO_SCREEN

### Community 4 - "Planner and Traffic Tests"
Cohesion: 0.06
Nodes (13): Path, Point, Segments, ShuttlePathPlanner, Motion, TruckTrafficGuard, DumpTruckConvoyQueueTest, DumpTruckRouteTest (+5 more)

### Community 5 - "Replay Tests and Storage"
Cohesion: 0.06
Nodes (6): Convoy, Crossing, Replay, RouteReplayClientGameTest, DumpTruckCommand, DumpTruckRouteManager

### Community 6 - "Sound Signals and Reservations"
Cohesion: 0.09
Nodes (5): DumpTruckEngineSoundController, EnginePair, ServerLevelPlaySoundMixin, DumpTruckEngineSoundProfile, Mix

### Community 7 - "Movement and Persistence"
Cohesion: 0.12
Nodes (5): DumpTruckState, DumpTruckMovementController, StepResult, WheelHeights, RouteSteeringActuatorTest

### Community 8 - "Driver Input Networking"
Cohesion: 0.10
Nodes (3): DumpTruckInputHandler, DumpTruckKeyBindings, DumpTruckInputPayload

### Community 13 - "Autopilot Lifecycle States"
Cohesion: 0.18
Nodes (11): AutopilotState, DRIVING, DUMPING, IDLE, PARKED, RECORDING, RETURNING, REVERSING (+3 more)

### Community 15 - "Documented Truck Features"
Cohesion: 0.22
Nodes (9): Cargo capacity: 7680 units / 15 blocks, Animated dump body: 50 degrees, Peterwolf’s Groundworks Dump Truck, Groundworks dependency, Loader and excavator world-container transfer, Overflow conservation, Minecraft 26.3 / Fabric, Mixed Groundworks cargo compositions (+1 more)

### Community 18 - "Audio Attribution"
Cohesion: 0.67
Nodes (3): Diesel engine loop attribution, RWM 6.8 diesel_idle.ogg sound bank, RWM-Zero 1.0 public-domain dedication

## Knowledge Gaps
- **26 isolated node(s):** `IDLE`, `RECORDING`, `WAITING_FOR_LOAD`, `DRIVING`, `REVERSING` (+21 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 123 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **12 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `GroundworksDumpTruckEntity` connect `Vehicle and Cargo Simulation` to `Client Models and Rendering`, `Recorded Route Autopilot`, `Independent Shuttle Navigation`, `Planner and Traffic Tests`, `Replay Tests and Storage`, `Sound Signals and Reservations`, `Movement and Persistence`, `Driver Input Networking`, `Entity Container Integration`, `Fabric Mod Registration`, `Truck Item Interactions`, `Command and Route Interfaces`, `Truck HUD`?**
  _High betweenness centrality (0.394) - this node is a cross-community bridge._
- **What connects `IDLE`, `RECORDING`, `WAITING_FOR_LOAD` to the rest of the system?**
  _26 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Client Models and Rendering` be split into smaller, more focused modules?**
  _Cohesion score 0.05690834473324213 - nodes in this community are weakly interconnected._
## Extraction limitations

- Two engine OGG files could not be transcribed: faster-whisper is unavailable.
- External API tokens: 0. Host-agent semantic extraction token usage is not separately metered; zero is not a claim of zero host-session cost.
- Raw extraction health: 196 dangling-endpoint edges, 89 undirected same-endpoint collapsed edges. See GRAPH_HEALTH.txt. The graph builder includes external-reference nodes; repeated relationships may lose detail.
