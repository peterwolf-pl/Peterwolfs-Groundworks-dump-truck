# Peterwolf's Groundworks Dump Truck

Three-axle 6x4 construction dump truck for Minecraft Java 26.3 / Fabric.

## Stage 1: visual and vehicle foundation

- Detailed cab-over 6x4 construction truck model.
- Six animated wheels with steerable front axle.
- Tandem rear axles and terrain-aware pitch / roll.
- Animated dump body up to 50 degrees.
- Gravity-driven rear tailgate.
- Two-stage hydraulic hoist animation.
- Detailed cab, glazing, mirrors, steps, grille, lights, tanks and safety beacon.
- Generated loose-material mound in the dump body.
- Groundworks source-block textures are used for carried material.
- Server-authoritative driving.

## Controls

- W / S: forward / reverse
- A / D: steer
- Arrow Up: raise dump body
- Arrow Down: lower dump body
- Right click: enter
- Shift + right click with empty hand: retrieve vehicle

## Dependency

Requires Peterwolf's Groundworks.

Stage 1 prepares synchronized load state and rendering hooks. Physical loading from the wheel loader / excavator and real tipping back into Groundworks terrain will be implemented as the next integration stage.
