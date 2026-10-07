package com.piotrek.groundworksdumptruck.entity;

import com.piotrek.groundworksdumptruck.GroundworksDumpTruckMod;
import com.piotrek.groundworksdumptruck.vehicle.DumpTruckMovementController;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative three-axle 6x4 construction dump truck.
 *
 * <p>Stage 1 intentionally keeps material transfer hooks small. The synchronized
 * load state already exists so the renderer and the later loader/excavator
 * integration do not need a vehicle model rewrite.</p>
 */
public class GroundworksDumpTruckEntity extends Entity {

    public static final int BED_CAPACITY = 6144;
    public static final float MAX_BED_ANGLE = 50.0F;
    private static final float BED_SPEED = 0.85F;

    private static final EntityDataAccessor<Float> BED_ANGLE =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> STEER_ANGLE =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FORWARD_SPEED =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WHEEL_ROTATION =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> VEHICLE_PITCH =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> VEHICLE_ROLL =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> CARRIED_UNITS =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CARRIED_MATERIAL_ID =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ENGINE_RUNNING =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.BOOLEAN);

    private final DumpTruckMovementController movementController = new DumpTruckMovementController();

    private float inputThrottle;
    private float inputSteer;
    private float inputBedLift;
    private int inputFreshTicks;
    private float bedAngle;

    public GroundworksDumpTruckEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BED_ANGLE, 0.0F);
        builder.define(STEER_ANGLE, 0.0F);
        builder.define(FORWARD_SPEED, 0.0F);
        builder.define(WHEEL_ROTATION, 0.0F);
        builder.define(VEHICLE_PITCH, 0.0F);
        builder.define(VEHICLE_ROLL, 0.0F);
        builder.define(CARRIED_UNITS, 0);
        builder.define(CARRIED_MATERIAL_ID, 0);
        builder.define(ENGINE_RUNNING, false);
    }

    public void setControlInputs(float throttle, float steer, float bedLift) {
        inputThrottle = Mth.clamp(throttle, -1.0F, 1.0F);
        inputSteer = Mth.clamp(steer, -1.0F, 1.0F);
        inputBedLift = Mth.clamp(bedLift, -1.0F, 1.0F);
        inputFreshTicks = 5;
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide()) {
            return;
        }

        ServerLevel serverLevel = (ServerLevel) level();
        Entity driver = getControllingPassenger();

        if (driver instanceof ServerPlayer player) {
            var vanillaInput = player.getLastClientInput();

            if (inputFreshTicks > 0) {
                inputFreshTicks--;
            } else {
                inputThrottle = vanillaInput.forward() ? 1.0F : vanillaInput.backward() ? -1.0F : 0.0F;
                inputSteer = vanillaInput.left() ? -1.0F : vanillaInput.right() ? 1.0F : 0.0F;
                inputBedLift = 0.0F;
            }

            if (Math.abs(inputThrottle) < 0.01F) {
                if (vanillaInput.forward()) inputThrottle = 1.0F;
                else if (vanillaInput.backward()) inputThrottle = -1.0F;
            }

            if (Math.abs(inputSteer) < 0.01F) {
                if (vanillaInput.left()) inputSteer = -1.0F;
                else if (vanillaInput.right()) inputSteer = 1.0F;
            }
        } else {
            inputThrottle = 0.0F;
            inputSteer = 0.0F;
            inputBedLift = 0.0F;
            inputFreshTicks = 0;
        }

        entityData.set(ENGINE_RUNNING, driver != null);

        bedAngle = Mth.clamp(
                bedAngle + inputBedLift * BED_SPEED,
                0.0F,
                MAX_BED_ANGLE
        );

        DumpTruckMovementController.StepResult move =
                movementController.step(inputThrottle, inputSteer);

        if (Math.abs(move.deltaYaw()) > 0.001F) {
            setYRot(Mth.wrapDegrees(getYRot() + move.deltaYaw()));
            setYHeadRot(getYRot());
            setYBodyRot(getYRot());
        }

        movementController.updateTerrainOrientation(serverLevel, position(), getYRot());
        double targetGroundY = movementController.getAverageGroundY(
                serverLevel, position(), getYRot()
        );

        float speed = move.forwardSpeed();
        float yawRad = (float) Math.toRadians(getYRot());
        double dx = -Math.sin(yawRad) * speed;
        double dz = Math.cos(yawRad) * speed;

        double heightDiff = targetGroundY - getY();
        double dy;
        if (heightDiff > 0.03D) {
            dy = Math.min(heightDiff, Math.max(0.07D, Math.abs(speed) * 0.62D));
        } else if (heightDiff < -0.05D) {
            dy = Math.max(heightDiff, onGround() ? -0.22D : -0.08D);
        } else {
            dy = onGround() ? 0.0D : -0.08D;
        }

        move(MoverType.SELF, new Vec3(dx, dy, dz));

        entityData.set(BED_ANGLE, bedAngle);
        entityData.set(STEER_ANGLE, move.steerAngle());
        entityData.set(FORWARD_SPEED, speed);
        entityData.set(WHEEL_ROTATION, move.wheelRotation());
        entityData.set(VEHICLE_PITCH, movementController.vehiclePitch());
        entityData.set(VEHICLE_ROLL, movementController.vehicleRoll());
    }

    /**
     * Visual/material-state hook for the upcoming Groundworks transfer integration.
     */
    public void setBedLoad(int materialId, int units) {
        if (level().isClientSide()) {
            return;
        }
        entityData.set(CARRIED_MATERIAL_ID, Math.max(0, materialId));
        entityData.set(CARRIED_UNITS, Mth.clamp(units, 0, BED_CAPACITY));
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.isSecondaryUseActive() && player.getItemInHand(hand).isEmpty()) {
            if (!level().isClientSide() && getPassengers().isEmpty()) {
                if (!player.getAbilities().instabuild) {
                    player.getInventory().add(new ItemStack(GroundworksDumpTruckMod.DUMP_TRUCK_ITEM));
                }
                level().playSound(
                        null, getX(), getY(), getZ(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F
                );
                discard();
            }
            return InteractionResult.SUCCESS;
        }

        if (!player.isSecondaryUseActive() && !level().isClientSide() && getPassengers().isEmpty()) {
            player.startRiding(this);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return position().add(getPassengerAttachmentPoint(
                passenger, getDimensions(getPose()), 1.0F
        ));
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(
            Entity passenger,
            EntityDimensions dimensions,
            float scale
    ) {
        double yawRad = Math.toRadians(getYRot());
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        Vec3 right = new Vec3(Math.cos(yawRad), 0.0D, Math.sin(yawRad));
        return forward.scale(2.0D)
                .add(right.scale(-0.48D))
                .add(0.0D, 1.48D, 0.0D);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        double yawRad = Math.toRadians(getYRot());
        Vec3 left = new Vec3(-Math.cos(yawRad), 0.0D, -Math.sin(yawRad));
        return position().add(left.scale(2.25D)).add(0.0D, 0.20D, 0.0D);
    }

    @Override
    public boolean hurtClient(DamageSource source) {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isInvulnerableToBase(source)) {
            return false;
        }

        level.playSound(
                null, getX(), getY(), getZ(),
                SoundEvents.ANVIL_HIT, SoundSource.PLAYERS, 0.8F, 1.0F
        );

        if (source.getEntity() instanceof Player player) {
            if (!player.getAbilities().instabuild) {
                spawnAtLocation(level, GroundworksDumpTruckMod.DUMP_TRUCK_ITEM);
            }
            discard();
            return true;
        }

        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        DumpTruckState state = DumpTruckState.load(input);
        bedAngle = state.bedAngle();
        movementController.setSteerAngle(state.steerAngle());
        movementController.setForwardSpeed(state.forwardSpeed());
        movementController.setWheelRotation(state.wheelRotation());
        movementController.setOrientation(state.vehiclePitch(), state.vehicleRoll());

        entityData.set(BED_ANGLE, state.bedAngle());
        entityData.set(STEER_ANGLE, state.steerAngle());
        entityData.set(FORWARD_SPEED, state.forwardSpeed());
        entityData.set(WHEEL_ROTATION, state.wheelRotation());
        entityData.set(VEHICLE_PITCH, state.vehiclePitch());
        entityData.set(VEHICLE_ROLL, state.vehicleRoll());
        entityData.set(CARRIED_UNITS, Mth.clamp(state.carriedUnits(), 0, BED_CAPACITY));
        entityData.set(CARRIED_MATERIAL_ID, Math.max(0, state.carriedMaterialId()));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        new DumpTruckState(
                bedAngle,
                movementController.steerAngle(),
                movementController.forwardSpeed(),
                movementController.wheelRotation(),
                movementController.vehiclePitch(),
                movementController.vehicleRoll(),
                getCarriedUnits(),
                getCarriedMaterialId()
        ).save(output);
    }

    public float getBedAngle() { return entityData.get(BED_ANGLE); }
    public float getSteerAngle() { return entityData.get(STEER_ANGLE); }
    public float getForwardSpeed() { return entityData.get(FORWARD_SPEED); }
    public float getWheelRotation() { return entityData.get(WHEEL_ROTATION); }
    public float getVehiclePitch() { return entityData.get(VEHICLE_PITCH); }
    public float getVehicleRoll() { return entityData.get(VEHICLE_ROLL); }
    public int getCarriedUnits() { return entityData.get(CARRIED_UNITS); }
    public int getCarriedMaterialId() { return entityData.get(CARRIED_MATERIAL_ID); }
    public boolean isEngineRunning() { return entityData.get(ENGINE_RUNNING); }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return other != null && !hasPassenger(other);
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return false;
    }

    @Override
    public boolean isClientAuthoritative() {
        return false;
    }

    @Override
    protected boolean isLocalClientAuthoritative() {
        return false;
    }

    @Override
    public float maxUpStep() {
        return 0.40F;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof LivingEntity && getPassengers().isEmpty();
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        Entity first = getFirstPassenger();
        return first instanceof LivingEntity living ? living : null;
    }

    public boolean isDriver(Entity entity) {
        return entity != null && entity == getControllingPassenger();
    }
}
