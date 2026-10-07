package com.piotrek.groundworksdumptruck.entity;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public record DumpTruckState(
        float bedAngle,
        float steerAngle,
        float forwardSpeed,
        float wheelRotation,
        float vehiclePitch,
        float vehicleRoll,
        int carriedUnits,
        int carriedMaterialId
) {
    public void save(ValueOutput output) {
        output.putFloat("BedAngle", bedAngle);
        output.putFloat("SteerAngle", steerAngle);
        output.putFloat("ForwardSpeed", forwardSpeed);
        output.putFloat("WheelRotation", wheelRotation);
        output.putFloat("VehiclePitch", vehiclePitch);
        output.putFloat("VehicleRoll", vehicleRoll);
        output.putInt("CarriedUnits", carriedUnits);
        output.putInt("CarriedMaterialId", carriedMaterialId);
    }

    public static DumpTruckState load(ValueInput input) {
        return new DumpTruckState(
                input.getFloatOr("BedAngle", 0.0F),
                input.getFloatOr("SteerAngle", 0.0F),
                input.getFloatOr("ForwardSpeed", 0.0F),
                input.getFloatOr("WheelRotation", 0.0F),
                input.getFloatOr("VehiclePitch", 0.0F),
                input.getFloatOr("VehicleRoll", 0.0F),
                input.getIntOr("CarriedUnits", 0),
                input.getIntOr("CarriedMaterialId", 0)
        );
    }
}
