# Peterwolf's Groundworks Dump Truck

Three-axle 6x4 construction dump truck for Minecraft Java 26.3 / Fabric.

## Features

- Detailed cab-over 6x4 construction truck model.
- Six animated wheels with steerable front axle.
- Tandem rear axles and terrain-aware pitch / roll.
- Server-authoritative WASD driving.
- Animated dump body up to 50 degrees.
- Arrow Up raises the dump body.
- Arrow Down lowers the dump body.
- Gravity-driven rear tailgate.
- Two-stage hydraulic hoist animation.
- Detailed cab, glazing, mirrors, steps, grille, lights, tanks and safety beacon.
- Exact cargo capacity: 15 full Groundworks blocks = 7680 units. The generated loose-material pile reaches the physical bed brim at 100%; only material above that visual/physical threshold spills over the sides.
- Accepts dirt, sand, gravel, cobblestone and mixed Groundworks compositions.
- Generated loose-material mound instead of a rectangular cargo block.
- Mixed cargo uses Groundworks material proportions for visible textures.
- Real dumping back into Groundworks terrain.
- Loader and excavator can dump directly into the truck through the generic Groundworks world-container API.
- When the body becomes full, additional material spills to both sides and forms Groundworks piles.
- No material is silently deleted. Rejected overflow remains in the source bucket if the side piles cannot accept it.
- Loaded trucks cannot be converted back into an item, preventing cargo loss.

## Controls

- W / S: forward / reverse
- A / D: steer
- Arrow Up: raise dump body
- Arrow Down: lower dump body
- Right click: enter
- Shift + right click with empty hand: retrieve an empty truck

## Item and mod assets

- Dedicated dump truck item icon.
- Dedicated mod icon.
- English and Polish translations.
- Item is available in Tools & Utilities.

## Dependencies

Requires Peterwolf's Groundworks.

Loader-to-truck transfer requires the matching Groundworks Loader integration.
Excavator-to-truck transfer requires the matching Groundworks Excavator integration.
