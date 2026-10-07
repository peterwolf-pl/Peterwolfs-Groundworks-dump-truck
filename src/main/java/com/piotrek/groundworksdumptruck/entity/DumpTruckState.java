package com.piotrek.groundworksdumptruck.entity;

import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public record DumpTruckState(
        float bedAngle,
        float steerAngle,
        float forwardSpeed,
        float wheelRotation,
        float vehiclePitch,
        float vehicleRoll,
        int[] materialUnits
) {
    public void save(ValueOutput output) {
        output.putFloat("BedAngle", bedAngle);
        output.putFloat("SteerAngle", steerAngle);
        output.putFloat("ForwardSpeed", forwardSpeed);
        output.putFloat("WheelRotation", wheelRotation);
        output.putFloat("VehiclePitch", vehiclePitch);
        output.putFloat("VehicleRoll", vehicleRoll);

        int[] units = materialUnits != null ? materialUnits : new int[0];
        for (int id = 1; id < units.length; id++) {
            if (units[id] > 0) {
                output.putInt("LoadMaterial_" + id, units[id]);
            }
        }
    }

    public static DumpTruckState load(ValueInput input) {
        int count = Math.max(GranularMaterialRegistry.count(), 1);
        int[] units = new int[count];
        for (int id = 1; id < count; id++) {
            units[id] = Math.max(0, input.getIntOr("LoadMaterial_" + id, 0));
        }

        // Migration from the first visual-foundation build.
        int legacyUnits = input.getIntOr("CarriedUnits", 0);
        int legacyMaterialId = input.getIntOr("CarriedMaterialId", 0);
        if (legacyUnits > 0
                && legacyMaterialId > 0
                && legacyMaterialId < units.length) {
            units[legacyMaterialId] += legacyUnits;
        }

        return new DumpTruckState(
                input.getFloatOr("BedAngle", 0.0F),
                input.getFloatOr("SteerAngle", 0.0F),
                input.getFloatOr("ForwardSpeed", 0.0F),
                input.getFloatOr("WheelRotation", 0.0F),
                input.getFloatOr("VehiclePitch", 0.0F),
                input.getFloatOr("VehicleRoll", 0.0F),
                units
        );
    }
}
