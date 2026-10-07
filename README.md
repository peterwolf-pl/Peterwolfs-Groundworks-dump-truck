# Peterwolf's Groundworks Dump Truck

Visual-first Fabric 26.3 add-on for Peterwolf's Groundworks.

## Stage 1

This first stage establishes the dump truck as a detailed 6x4 construction vehicle and prepares the model for later driving and automatic loading workflows.

- 6x4 rigid construction dump truck
- three axles with heavy-duty wheels
- detailed cab, chassis, mirrors, steps, exhaust, lights and safety beacon
- ribbed steel dump body with rear hinge
- animated hydraulic dump-body pivot
- generated loose-material mound instead of a rectangular cargo block
- cargo texture is inherited from the current Groundworks granular material
- exact capacity: 10 Groundworks blocks = 5,120 microvoxel units
- right-click the truck with dirt, sand, gravel or cobblestone block items to add one full block for visual testing
- sneak + right-click with an empty hand toggles the dump body between transport and raised positions
- normal right-click with an empty hand lets the player sit in the cab

Driving physics, machine-to-machine loading, granular unloading and full Groundworks transfer logic are intentionally reserved for the next stage.

## Requirements

- Minecraft Java 26.3
- Fabric Loader
- Fabric API
- Peterwolf's Groundworks
