package com.piotrek.groundworksdumptruck.client.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class DumpTruckRenderState extends EntityRenderState {
    public float baseYaw;
    public float basePitch;
    public float baseRoll;
    public float bedAngle;
    public float steerAngle;
    public float forwardSpeed;
    public float wheelRotation;

    public int carriedMaterialId;
    public int carriedUnits;
    public int dirtUnits;
    public int sandUnits;
    public int gravelUnits;
    public int cobblestoneUnits;
    public float fillRatio;

    public boolean engineRunning;
    public boolean dumping;
    public float beaconSpin;
    public boolean beaconFlash;
}
