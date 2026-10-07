package com.piotrek.groundworksdumptruck.client.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class DumpTruckRenderState extends EntityRenderState {
    public float baseYaw;
    public float bedAngle;
    public float steerAngle;
    public float wheelRotation;
    public float beaconSpin;

    public int carriedMaterialId;
    public int carriedUnits;
    public float fillRatio;
}
