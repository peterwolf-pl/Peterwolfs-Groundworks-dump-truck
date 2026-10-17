package com.piotrek.groundworksdumptruck.entity;

import com.piotrek.groundworks.api.GroundworksApi;
import com.piotrek.groundworks.api.container.GranularContainerTransferApi;
import com.piotrek.groundworks.api.container.IMobileWorldGranularContainer;
import com.piotrek.groundworks.api.container.IWorldGranularContainer;
import com.piotrek.groundworks.api.deposit.DepositResult;
import com.piotrek.groundworks.api.material.GranularComposition;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworksdumptruck.GroundworksDumpTruckMod;
import com.piotrek.groundworksdumptruck.route.DumpTruckAutopilot;
import com.piotrek.groundworksdumptruck.vehicle.DumpTruckMovementController;
import com.piotrek.groundworksdumptruck.vehicle.ExhaustPuffs;
import com.piotrek.groundworksdumptruck.vehicle.TruckLights;
import com.piotrek.groundworksdumptruck.vehicle.TruckTrafficGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Server-authoritative three-axle 6x4 Groundworks dump truck.
 *
 * <p>The body is a real Groundworks granular container. Capacity is exactly
 * fifteen full Groundworks blocks: 15 x 512 = 7680 integer units. The visual
 * load mesh reaches the physical bed-wall height at this same threshold; only
 * material above that brim level is allowed to spill over the sides.</p>
 */
public class GroundworksDumpTruckEntity extends Entity implements IMobileWorldGranularContainer {
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);

    public static final int BED_CAPACITY_BLOCKS = 15;
    public static final int BED_CAPACITY = BED_CAPACITY_BLOCKS * 512;

    // Shared visual/physics brim definition. The loose-load renderer starts at
    // -3 px and reaches the inside top edge of the bed wall at -22 px when full.
    /** Cab and stacks move forward so the pipes clear the dump body. */
    public static final float CAB_SHIFT_Z_PX = 4.0F;

    /** Rear pivot of the dump body. More negative is farther behind the cab. */
    public static final float BED_PIVOT_Z_PX = -57.0F;

    public static final float BED_LOAD_BASE_Y_PX = -3.0F;
    public static final float BED_BRIM_Y_PX = -22.0F;
    public static final float BED_FULL_PILE_RISE_PX =
            BED_LOAD_BASE_Y_PX - BED_BRIM_Y_PX;

    public static final float MAX_BED_ANGLE = 50.0F;
    public static final float DUMP_THRESHOLD_ANGLE = 18.0F;

    private static final float BED_SPEED = 0.85F;
    private static final int MAX_DUMP_FLOW_PER_TICK = 160;
    private static final int DUMP_SURFACE_SEARCH_DEPTH = 16;

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

    private static final EntityDataAccessor<Integer> DIRT_UNITS =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SAND_UNITS =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GRAVEL_UNITS =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COBBLESTONE_UNITS =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Boolean> ENGINE_RUNNING =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> ENGINE_LOAD =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_DUMPING =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BRAKING =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> AUTOPILOT_STATE =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ACTIVE_ROUTE =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.STRING);

    private final DumpTruckMovementController movementController =
            new DumpTruckMovementController();
    private final DumpTruckAutopilot autopilot =
            new DumpTruckAutopilot(this);
    private final java.util.ArrayList<BlockPos> headLightBlocks = new java.util.ArrayList<>();
    private final java.util.ArrayList<BlockPos> tailLightBlocks = new java.util.ArrayList<>();
    private final GranularComposition loadComposition =
            new GranularComposition();

    private float inputThrottle;
    private float trafficDesiredSpeed;
    private boolean trafficYielding;
    private float inputSteer;
    private float inputBedLift;
    private int inputFreshTicks;
    private float bedAngle;
    private float engineLoad;
    private boolean overflowSideToggle;
    private double remoteAdvanceRemaining;

    public GroundworksDumpTruckEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return interpolation;
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
        builder.define(DIRT_UNITS, 0);
        builder.define(SAND_UNITS, 0);
        builder.define(GRAVEL_UNITS, 0);
        builder.define(COBBLESTONE_UNITS, 0);

        builder.define(ENGINE_RUNNING, false);
        builder.define(ENGINE_LOAD, 0.0F);
        builder.define(IS_DUMPING, false);
        builder.define(BRAKING, false);
        builder.define(AUTOPILOT_STATE, "IDLE");
        builder.define(ACTIVE_ROUTE, "");
    }

    public void setControlInputs(float throttle, float steer, float bedLift) {
        inputThrottle = Mth.clamp(throttle, -1.0F, 1.0F);
        inputSteer = Mth.clamp(steer, -1.0F, 1.0F);
        inputBedLift = Mth.clamp(bedLift, -1.0F, 1.0F);
        inputFreshTicks = 5;
    }

    public float getInputThrottle() {
        return inputThrottle;
    }

    public float getTrafficDesiredSpeed() {
        return trafficDesiredSpeed;
    }

    public boolean isTrafficYielding() {
        return trafficYielding;
    }

    @Override
    public boolean requestAdvance(double blocks) {
        if (level().isClientSide()
                || blocks <= 0.0D
                || getControllingPassenger() != null
                || remoteAdvanceRemaining > 0.01D) {
            return false;
        }

        remoteAdvanceRemaining = Math.clamp(blocks, 0.1D, 4.0D);
        movementController.stopMotion();
        inputFreshTicks = 0;
        autopilot.updateStationShift();
        return true;
    }

    @Override
    public boolean isAdvanceInProgress() {
        return remoteAdvanceRemaining > 0.01D;
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide()) {
            spawnExhaustSmoke();
            return;
        }

        ServerLevel serverLevel = (ServerLevel) level();
        autopilot.tick(serverLevel);
        Entity driver = getControllingPassenger();

        if (driver instanceof ServerPlayer player) {
            var vanillaInput = player.getLastClientInput();

            if (inputFreshTicks > 0) {
                inputFreshTicks--;
            } else {
                inputThrottle = vanillaInput.forward() ? 1.0F
                        : vanillaInput.backward() ? -1.0F : 0.0F;
                inputSteer = vanillaInput.left() ? -1.0F
                        : vanillaInput.right() ? 1.0F : 0.0F;
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
        } else if (remoteAdvanceRemaining > 0.01D) {
            inputThrottle = 0.65F;
            inputSteer = 0.0F;
            inputBedLift = 0.0F;
            inputFreshTicks = 0;
        } else if (autopilot.isActive()) {
            if (inputFreshTicks > 0) {
                inputFreshTicks--;
            }
        } else {
            inputThrottle = 0.0F;
            inputSteer = 0.0F;
            inputBedLift = 0.0F;
            inputFreshTicks = 0;
        }

        boolean engineRunning = driver != null || remoteAdvanceRemaining > 0.01D || autopilot.isActive();
        entityData.set(ENGINE_RUNNING, engineRunning);

        float speedBeforeStep = movementController.forwardSpeed();
        // Retain intended motion while yielding so the priority holder does not
        // misclassify the waiting vehicle as an unrelated stationary obstacle.
        trafficDesiredSpeed = Math.abs(speedBeforeStep) > 0.02F ? speedBeforeStep
                : Math.abs(inputThrottle) > 0.05F ? Math.copySign(0.15F, inputThrottle) : 0.0F;
        trafficYielding = TruckTrafficGuard.mustStop(serverLevel, this);
        if (trafficYielding) {
            movementController.stopMotion();
            inputThrottle = 0.0F;
            inputSteer = 0.0F;
        }
        boolean braking = engineRunning && (
                inputThrottle * speedBeforeStep < -0.0005F
                        || (inputThrottle < -0.05F && speedBeforeStep <= 0.02F)
        );
        entityData.set(BRAKING, braking || trafficYielding);

        float fillRatio = (float) storedUnits() / (float) BED_CAPACITY;
        float driveDemand = Math.abs(inputThrottle) * (0.68F + 0.22F * fillRatio);
        float steerDemand = Math.abs(inputSteer) * 0.30F;
        float hydraulicDemand = Math.abs(inputBedLift) * 0.88F;
        float remoteDemand = remoteAdvanceRemaining > 0.01D ? 0.58F + 0.20F * fillRatio : 0.0F;
        float targetEngineLoad = engineRunning
                ? Mth.clamp(0.14F + Math.max(
                        Math.max(driveDemand, steerDemand),
                        Math.max(hydraulicDemand, remoteDemand)
                ), 0.0F, 1.0F)
                : 0.0F;
        engineLoad += (targetEngineLoad - engineLoad) * 0.18F;
        entityData.set(ENGINE_LOAD, engineLoad);

        bedAngle = Mth.clamp(
                bedAngle + inputBedLift * BED_SPEED,
                0.0F,
                MAX_BED_ANGLE
        );

        DumpTruckMovementController.StepResult moveResult =
                movementController.step(inputThrottle, inputSteer);

        if (Math.abs(moveResult.deltaYaw()) > 0.001F) {
            setYRot(Mth.wrapDegrees(getYRot() + moveResult.deltaYaw()));
            setYHeadRot(getYRot());
            setYBodyRot(getYRot());
        }

        movementController.updateTerrainOrientation(
                serverLevel,
                position(),
                getYRot()
        );

        double targetGroundY =
                movementController.getAverageGroundY(
                        serverLevel,
                        position(),
                        getYRot()
                );

        float speed = moveResult.forwardSpeed();
        if (remoteAdvanceRemaining > 0.01D) {
            speed = (float) Math.min(speed, remoteAdvanceRemaining);
        }

        float yawRad = (float) Math.toRadians(getYRot());
        double dx = -Math.sin(yawRad) * speed;
        double dz = Math.cos(yawRad) * speed;

        double heightDiff = targetGroundY - getY();
        double dy;

        if (heightDiff > 0.03D) {
            dy = Math.min(
                    heightDiff,
                    Math.max(0.07D, Math.abs(speed) * 0.62D)
            );
        } else if (heightDiff < -0.05D) {
            dy = Math.max(
                    heightDiff,
                    onGround() ? -0.22D : -0.08D
            );
        } else {
            dy = onGround() ? 0.0D : -0.08D;
        }

        Vec3 beforeMove = position();
        move(MoverType.SELF, new Vec3(dx, dy, dz));

        if (driver != null || autopilot.isActive() || remoteAdvanceRemaining > 0.01D || Math.abs(speed) > 0.001F) {
            this.needsSync = true;
            }

        if (remoteAdvanceRemaining > 0.01D) {
            Vec3 moved = position().subtract(beforeMove);
            double horizontal = Math.sqrt(moved.x * moved.x + moved.z * moved.z);
            remoteAdvanceRemaining = Math.max(0.0D, remoteAdvanceRemaining - horizontal);

            if (remoteAdvanceRemaining <= 0.01D) {
                remoteAdvanceRemaining = 0.0D;
                movementController.stopMotion();
                inputThrottle = 0.0F;
                autopilot.updateStationShift();
            } else if (horizontal < 0.001D) {
                // A blocked truck must not report the remote move as complete.
                // Keep the remaining distance pending so the excavator waits.
                movementController.stopMotion();
                inputThrottle = 0.0F;
            }
        }

        boolean dumping = tickDumping(serverLevel);

        entityData.set(BED_ANGLE, bedAngle);
        entityData.set(STEER_ANGLE, moveResult.steerAngle());
        entityData.set(FORWARD_SPEED, speed);
        entityData.set(WHEEL_ROTATION, moveResult.wheelRotation());
        entityData.set(VEHICLE_PITCH, movementController.vehiclePitch());
        entityData.set(VEHICLE_ROLL, movementController.vehicleRoll());
        entityData.set(IS_DUMPING, dumping);
        entityData.set(AUTOPILOT_STATE, autopilot.getState().name());
        entityData.set(ACTIVE_ROUTE, autopilot.getRouteName());

        syncLoadData();
        updateTruckLights();
    }

    @Override
    public int capacity() {
        return BED_CAPACITY;
    }

    @Override
    public int storedUnits() {
        return loadComposition.totalUnits();
    }

    @Override
    public GranularMaterial storedMaterial() {
        int id = loadComposition.dominantMaterialId();
        return id > 0
                ? GranularMaterialRegistry.byId(id)
                : GranularMaterial.EMPTY;
    }

    @Override
    public GranularComposition storedComposition() {
        return loadComposition.copy();
    }

    @Override
    public int acceptMaterial(
            GranularMaterial material,
            int units
    ) {
        if (units <= 0
                || material == null
                || material == GranularMaterial.EMPTY) {
            return 0;
        }

        int room = BED_CAPACITY - loadComposition.totalUnits();
        int accepted = Math.min(units, Math.max(0, room));

        if (accepted > 0) {
            loadComposition.add(material, accepted);
            syncLoadData();
            if (isBedFull()) {
                autopilot.onBedFull();
            }
        }

        return accepted;
    }

    @Override
    public int acceptComposition(GranularComposition composition) {
        if (composition == null
                || composition.isEmpty()
                || !hasRoom()) {
            return 0;
        }

        int room = BED_CAPACITY - loadComposition.totalUnits();
        GranularComposition accepted = composition.copy();

        if (accepted.totalUnits() > room) {
            accepted = accepted.extractProportional(room);
        }

        int added = loadComposition.addAll(accepted);
        syncLoadData();
        if (isBedFull()) {
            autopilot.onBedFull();
        }
        return added;
    }

    @Override
    public int extractMaterial(int maxUnits) {
        if (maxUnits <= 0 || loadComposition.isEmpty()) {
            return 0;
        }

        int selected = loadComposition.dominantMaterialId();
        int removed = loadComposition.remove(selected, maxUnits);

        if (removed > 0) {
            syncLoadData();
        }

        return removed;
    }

    @Override
    public GranularComposition extractComposition(int maxUnits) {
        GranularComposition extracted =
                loadComposition.extractProportional(maxUnits);

        if (!extracted.isEmpty()) {
            syncLoadData();
        }

        return extracted;
    }

    @Override
    public boolean canReceiveAt(Vec3 worldPoint) {
        if (bedAngle > 12.0F) {
            return false;
        }

        Vec3 delta = worldPoint.subtract(position());
        double yawRad = Math.toRadians(getYRot());

        Vec3 forward = new Vec3(
                -Math.sin(yawRad),
                0.0D,
                Math.cos(yawRad)
        );
        Vec3 right = new Vec3(
                Math.cos(yawRad),
                0.0D,
                Math.sin(yawRad)
        );

        double localForward = delta.dot(forward);
        double localRight = delta.dot(right);

        // The body is open from above. A bucket or conveyor does not need to put its
        // release point physically inside the bed volume: it may pour from several
        // blocks above as long as the release point projects over the body opening.
        // Horizontal limits follow the full dump-bed catchment area.
        return Math.abs(localRight) <= 1.95D
                && localForward >= -4.25D
                && localForward <= 1.10D
                && delta.y >= 0.50D
                && delta.y <= 14.00D;
    }

    /**
     * Fill the body until the generated loose-material pile reaches the physical
     * bed brim. BED_CAPACITY is calibrated to that same renderer threshold.
     * Only material above the brim is allowed to fall over the left/right side
     * and become normal Groundworks terrain.
     */
    @Override
    public int receiveMaterialAt(
            ServerLevel level,
            Vec3 worldPoint,
            GranularMaterial material,
            int units
    ) {
        if (units <= 0
                || material == null
                || material == GranularMaterial.EMPTY) {
            return 0;
        }

        int accepted = acceptMaterial(material, units);
        int overflow = units - accepted;

        if (overflow <= 0) {
            return accepted;
        }

        int spilled = spillOverflowToSides(
                level,
                material,
                overflow
        );

        if (spilled > 0) {
            spawnOverflowParticles(
                    level,
                    material,
                    spilled
            );
        }

        if (isBedFull()) {
            autopilot.onBedFull();
        }

        return accepted + spilled;
    }

    private int spillOverflowToSides(
            ServerLevel level,
            GranularMaterial material,
            int units
    ) {
        int remaining = units;
        int depositedTotal = 0;

        Vec3 forward = forwardVector();
        Vec3 right = rightVector();

        // Alternate first side each call so a continuous overfill grows both
        // piles instead of always favoring one side.
        boolean startRight = overflowSideToggle;
        overflowSideToggle = !overflowSideToggle;

        for (int pass = 0; pass < 6 && remaining > 0; pass++) {
            boolean rightSide = ((pass & 1) == 0)
                    ? startRight
                    : !startRight;

            double side = rightSide ? 2.15D : -2.15D;
            double longitudinal =
                    -1.375D - ((pass / 2) * 0.75D);

            Vec3 spillPoint = position()
                    .add(forward.scale(longitudinal))
                    .add(right.scale(side))
                    .add(0.0D, 1.15D, 0.0D);

            BlockPos target =
                    findDepositSurface(level, spillPoint);

            if (target == null) {
                continue;
            }

            int request = Math.min(remaining, 256);
            DepositResult result =
                    GroundworksApi.depositWithOverflow(
                            level,
                            target,
                            material,
                            request
                    );

            int deposited = result.unitsDeposited();
            depositedTotal += deposited;
            remaining -= deposited;

            for (BlockPos affected : result.affectedCells()) {
                GroundworksApi.markForSimulation(level, affected);
            }
        }

        return depositedTotal;
    }

    public boolean tickDumping(ServerLevel level) {
        if (bedAngle <= DUMP_THRESHOLD_ANGLE
                || loadComposition.isEmpty()) {
            return false;
        }

        float progress = Mth.clamp(
                (bedAngle - DUMP_THRESHOLD_ANGLE)
                        / (MAX_BED_ANGLE - DUMP_THRESHOLD_ANGLE),
                0.0F,
                1.0F
        );

        int flowRate = Math.round(
                Mth.lerp(
                        progress,
                        24.0F,
                        (float) MAX_DUMP_FLOW_PER_TICK
                )
        );

        int toDump = Math.min(
                loadComposition.totalUnits(),
                flowRate
        );

        if (toDump <= 0) {
            return false;
        }

        Vec3 lip = getDumpLipWorldPosition();

        IWorldGranularContainer receiver =
                GranularContainerTransferApi.findReceiver(
                        level,
                        lip,
                        this,
                        4.5D
                );

        if (receiver != null && receiver.hasRoom()) {
            GranularComposition outgoing =
                    loadComposition.extractProportional(toDump);
            int consumed = receiver.receiveCompositionAt(level, lip, outgoing);
            if (consumed > 0) {
                int leftover = outgoing.totalUnits() - consumed;
                if (leftover > 0) {
                    GranularComposition back = outgoing.extractProportional(leftover);
                    loadComposition.addAll(back);
                }
                syncLoadData();
                int dominantId = outgoing.dominantMaterialId();
                GranularMaterial mat = dominantId > 0
                        ? GranularMaterialRegistry.byId(dominantId)
                        : GranularMaterial.EMPTY;
                spawnDumpParticles(level, lip, mat, consumed);
                if (tickCount % 5 == 0) {
                    level.playSound(
                            null,
                            lip.x,
                            lip.y,
                            lip.z,
                            SoundEvents.GRAVEL_PLACE,
                            SoundSource.BLOCKS,
                            0.85F,
                            0.90F + level.getRandom().nextFloat() * 0.15F
                    );
                }
                return true;
            } else {
                loadComposition.addAll(outgoing);
            }
        }

        BlockPos target = findDepositSurface(level, lip);

        if (target == null) {
            return false;
        }

        GranularComposition outgoing =
                loadComposition.extractProportional(toDump);

        int[] counts = outgoing.toArray();
        int depositedTotal = 0;
        GranularMaterial particleMaterial =
                GranularMaterial.EMPTY;

        for (int id = 1; id < counts.length; id++) {
            int requested = counts[id];
            if (requested <= 0) {
                continue;
            }

            GranularMaterial material =
                    GranularMaterialRegistry.byId(id);

            if (material == GranularMaterial.EMPTY) {
                loadComposition.add(id, requested);
                continue;
            }

            DepositResult result =
                    GroundworksApi.depositWithOverflow(
                            level,
                            target,
                            material,
                            requested
                    );

            int deposited = result.unitsDeposited();
            depositedTotal += deposited;

            if (particleMaterial == GranularMaterial.EMPTY
                    && deposited > 0) {
                particleMaterial = material;
            }

            if (deposited < requested) {
                loadComposition.add(
                        id,
                        requested - deposited
                );
            }

            for (BlockPos affected : result.affectedCells()) {
                GroundworksApi.markForSimulation(
                        level,
                        affected
                );
            }
        }

        syncLoadData();

        if (depositedTotal <= 0) {
            return false;
        }

        spawnDumpParticles(
                level,
                lip,
                particleMaterial,
                depositedTotal
        );

        if (tickCount % 5 == 0) {
            level.playSound(
                    null,
                    lip.x,
                    lip.y,
                    lip.z,
                    SoundEvents.GRAVEL_PLACE,
                    SoundSource.BLOCKS,
                    0.85F,
                    0.90F
                            + level.getRandom().nextFloat() * 0.15F
            );
        }

        return true;
    }

    public Vec3 getDumpLipWorldPosition() {
        Vec3 forward = forwardVector();

        double angleRad = Math.toRadians(bedAngle);
        double rearOffset =
                3.675D + Math.cos(angleRad) * 0.20D;
        double lift =
                0.95D + Math.sin(angleRad) * 0.80D;

        return position()
                .add(forward.scale(-rearOffset))
                .add(0.0D, lift, 0.0D);
    }

    @Nullable
    private static BlockPos findDepositSurface(
            ServerLevel level,
            Vec3 point
    ) {
        BlockPos start =
                BlockPos.containing(point.x, point.y, point.z);

        int minY = Math.max(
                level.getMinY(),
                start.getY() - DUMP_SURFACE_SEARCH_DEPTH
        );

        for (int y = start.getY(); y >= minY; y--) {
            BlockPos check =
                    new BlockPos(start.getX(), y, start.getZ());

            GranularMaterial terrainMaterial =
                    GroundworksApi.getMaterial(level, check);

            if (terrainMaterial != null
                    && terrainMaterial != GranularMaterial.EMPTY) {
                return check;
            }

            BlockState state =
                    level.getBlockState(check);

            if (!state.isAir()) {
                return check.above();
            }
        }

        return null;
    }

    private void spawnOverflowParticles(
            ServerLevel level,
            GranularMaterial material,
            int units
    ) {
        // Emit overflow from the actual upper side-rail region, not from the
        // middle of the truck body. This makes the visible stream start where a
        // real heaped load crosses the wall height.
        Vec3 left = position()
                .add(rightVector().scale(-1.28D))
                .add(forwardVector().scale(-1.775D))
                .add(0.0D, 2.72D, 0.0D);

        Vec3 right = position()
                .add(rightVector().scale(1.28D))
                .add(forwardVector().scale(-1.775D))
                .add(0.0D, 2.72D, 0.0D);

        spawnDumpParticles(
                level,
                left,
                material,
                Math.max(1, units / 2)
        );

        spawnDumpParticles(
                level,
                right,
                material,
                Math.max(1, units / 2)
        );
    }

    private static void spawnDumpParticles(
            ServerLevel level,
            Vec3 origin,
            GranularMaterial material,
            int units
    ) {
        var block = material != null
                && material.sourceBlock() != null
                ? material.sourceBlock()
                : Blocks.DIRT;

        BlockParticleOption particle =
                new BlockParticleOption(
                        ParticleTypes.BLOCK,
                        block.defaultBlockState()
                );

        int count = Mth.clamp(units / 8, 4, 24);

        level.sendParticles(
                particle,
                origin.x,
                origin.y,
                origin.z,
                count,
                0.32D,
                0.22D,
                0.32D,
                0.07D
        );
    }

    /**
     * Mouth centers in cab-local pixels. The cab part offset is applied
     * by the same matrix as the renderer, so these stay on the lip boxes.
     */
    public static final float EXHAUST_LEFT_X_PX = -15.5F;
    public static final float EXHAUST_RIGHT_X_PX = 15.5F;
    public static final float EXHAUST_TIP_Y_PX = -43.4F;
    public static final float EXHAUST_TIP_Z_PX = 15.9F;

    private void spawnExhaustSmoke() {
        if (!isEngineRunning()) {
            return;
        }

        float load = getEngineLoad();
        spawnLightExhaustPuffs(load, exhaustTip(EXHAUST_LEFT_X_PX));
        spawnLightExhaustPuffs(load, exhaustTip(EXHAUST_RIGHT_X_PX));

        if (load <= 0.30F) {
            return;
        }

        emitLoadedSmoke(exhaustTip(EXHAUST_LEFT_X_PX), load);
        emitLoadedSmoke(exhaustTip(EXHAUST_RIGHT_X_PX), load);
    }

    private void spawnLightExhaustPuffs(float load, Vec3 exhaustPos) {
        int count = ExhaustPuffs.whitePuffCount(load, tickCount);
        if (count == 0) {
            return;
        }

        float blend = (load - ExhaustPuffs.MIN_LOAD)
                / (ExhaustPuffs.MAX_LOAD - ExhaustPuffs.MIN_LOAD);
        double spread = 0.004D + 0.010D * blend;
        double rise = 0.010D + 0.012D * blend;
        for (int i = 0; i < count; i++) {
            level().addParticle(
                    ParticleTypes.WHITE_SMOKE,
                    exhaustPos.x,
                    exhaustPos.y + 0.08D,
                    exhaustPos.z,
                    (Math.random() - 0.5D) * spread,
                    rise + Math.random() * 0.008D,
                    (Math.random() - 0.5D) * spread
            );
        }
    }

    private void emitLoadedSmoke(Vec3 exhaustPos, float load) {
        if (load >= 0.95F) {
            level().addParticle(
                    ParticleTypes.LARGE_SMOKE,
                    exhaustPos.x,
                    exhaustPos.y + 0.05D,
                    exhaustPos.z,
                    (Math.random() - 0.5D) * 0.03D,
                    0.08D + Math.random() * 0.04D,
                    (Math.random() - 0.5D) * 0.03D
            );
            level().addParticle(
                    ParticleTypes.SMOKE,
                    exhaustPos.x,
                    exhaustPos.y + 0.05D,
                    exhaustPos.z,
                    (Math.random() - 0.5D) * 0.02D,
                    0.06D,
                    (Math.random() - 0.5D) * 0.02D
            );
            return;
        }

        if (load > 0.60F) {
            if (tickCount % 2 == 0) {
                level().addParticle(
                        ParticleTypes.SMOKE,
                        exhaustPos.x,
                        exhaustPos.y + 0.05D,
                        exhaustPos.z,
                        (Math.random() - 0.5D) * 0.02D,
                        0.05D + Math.random() * 0.02D,
                        (Math.random() - 0.5D) * 0.02D
                );
            }
            return;
        }

        if (tickCount % 4 == 0) {
            level().addParticle(
                    ParticleTypes.WHITE_SMOKE,
                    exhaustPos.x,
                    exhaustPos.y + 0.05D,
                    exhaustPos.z,
                    (Math.random() - 0.5D) * 0.01D,
                    0.04D,
                    (Math.random() - 0.5D) * 0.01D
            );
        }
    }

    /** Stack mouth in world space. Same transform chain as DumpTruckRenderer. */
    public Vec3 exhaustTip(float modelXPx) {
        return modelToWorld(modelXPx, EXHAUST_TIP_Y_PX, EXHAUST_TIP_Z_PX, true);
    }

    /**
     * Model pixel to world. Cab parts need the cab shift. Chassis and bed
     * points do not.
     */
    public Vec3 modelToWorld(float xPx, float yPx, float zPx, boolean cab) {
        Matrix4f mat = new Matrix4f();
        Vec3 base = position();
        mat.translate((float) base.x, (float) base.y, (float) base.z);
        mat.rotate((float) Math.toRadians(-getYRot()), 0.0F, 1.0F, 0.0F);

        float pitch = getVehiclePitch();
        float roll = getVehicleRoll();
        if (Math.abs(pitch) > 0.01F) {
            mat.rotate((float) Math.toRadians(pitch), 1.0F, 0.0F, 0.0F);
        }
        if (Math.abs(roll) > 0.01F) {
            mat.rotate((float) Math.toRadians(roll), 0.0F, 0.0F, 1.0F);
        }

        mat.scale(-1.0F, -1.0F, 1.0F);
        mat.translate(0.0F, -1.5F, 0.0F);
        if (cab) {
            mat.translate(0.0F, 0.0F, CAB_SHIFT_Z_PX / 16.0F);
        }

        Vector4f point = new Vector4f(xPx / 16.0F, yPx / 16.0F, zPx / 16.0F, 1.0F);
        mat.transform(point);
        return new Vec3(point.x, point.y, point.z);
    }

    /**
     * Invisible light blocks on the ground, the same trick as the excavator
     * floodlights. They follow the truck and go out when the engine stops.
     */
    private void updateTruckLights() {
        if (!isEngineRunning()) {
            clearHeldLights(headLightBlocks);
            clearHeldLights(tailLightBlocks);
            return;
        }

        Vec3 facing = forwardVector();
        Vec3 feet = position();
        syncLightBlocks(
                headLightBlocks,
                TruckLights.beams(
                        modelToWorld(TruckLights.HEAD_LEFT_X, TruckLights.HEAD_Y, TruckLights.HEAD_Z, true),
                        modelToWorld(TruckLights.HEAD_RIGHT_X, TruckLights.HEAD_Y, TruckLights.HEAD_Z, true),
                        feet,
                        facing,
                        false
                ),
                TruckLights.HEAD_LEVEL
        );
        syncLightBlocks(
                tailLightBlocks,
                TruckLights.beams(
                        modelToWorld(TruckLights.TAIL_LEFT_X, TruckLights.TAIL_Y, TruckLights.TAIL_Z, false),
                        modelToWorld(TruckLights.TAIL_RIGHT_X, TruckLights.TAIL_Y, TruckLights.TAIL_Z, false),
                        feet,
                        facing,
                        true
                ),
                isBraking() ? TruckLights.BRAKE_LEVEL : TruckLights.TAIL_LEVEL
        );
    }

    private void syncLightBlocks(java.util.List<BlockPos> held, java.util.List<BlockPos> next, int level) {
        for (int i = held.size() - 1; i >= 0; i--) {
            BlockPos old = held.get(i);
            if (next.contains(old)) {
                continue;
            }
            held.remove(i);
            releaseLight(old);
        }

        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level);
        for (BlockPos pos : next) {
            if (!level().isLoaded(pos)) {
                continue;
            }
            BlockState state = level().getBlockState(pos);
            if (state.isAir()) {
                level().setBlock(pos, light, 3);
                held.add(pos);
            } else if (state.is(Blocks.LIGHT)) {
                if (state.getValue(LightBlock.LEVEL) != level) {
                    level().setBlock(pos, light, 3);
                }
                if (!held.contains(pos)) {
                    held.add(pos);
                }
            }
        }
    }

    private void clearHeldLights(java.util.List<BlockPos> held) {
        for (int i = held.size() - 1; i >= 0; i--) {
            releaseLight(held.remove(i));
        }
    }

    private void releaseLight(BlockPos pos) {
        if (headLightBlocks.contains(pos) || tailLightBlocks.contains(pos)) {
            return;
        }
        if (level().isLoaded(pos) && level().getBlockState(pos).is(Blocks.LIGHT)) {
            level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void readHeldLights(ValueInput input, java.util.List<BlockPos> held, String key) {
        held.clear();
        int count = input.getIntOr(key + "Count", 0);
        for (int i = 0; i < count && i < 24; i++) {
            held.add(new BlockPos(
                    input.getIntOr(key + "X" + i, 0),
                    input.getIntOr(key + "Y" + i, 0),
                    input.getIntOr(key + "Z" + i, 0)
            ));
        }
    }

    private static void writeHeldLights(ValueOutput output, java.util.List<BlockPos> held, String key) {
        output.putInt(key + "Count", held.size());
        for (int i = 0; i < held.size(); i++) {
            BlockPos pos = held.get(i);
            output.putInt(key + "X" + i, pos.getX());
            output.putInt(key + "Y" + i, pos.getY());
            output.putInt(key + "Z" + i, pos.getZ());
        }
    }

    public Vec3 forwardVector() {
        double yawRad = Math.toRadians(getYRot());
        return new Vec3(
                -Math.sin(yawRad),
                0.0D,
                Math.cos(yawRad)
        );
    }

    public Vec3 rightVector() {
        double yawRad = Math.toRadians(getYRot());
        return new Vec3(
                Math.cos(yawRad),
                0.0D,
                Math.sin(yawRad)
        );
    }

    private void syncLoadData() {
        int total = loadComposition.totalUnits();
        int dominantId =
                loadComposition.dominantMaterialId();

        entityData.set(
                CARRIED_UNITS,
                Mth.clamp(total, 0, BED_CAPACITY)
        );
        entityData.set(
                CARRIED_MATERIAL_ID,
                Math.max(0, dominantId)
        );

        entityData.set(
                DIRT_UNITS,
                loadComposition.unitsOf(
                        GranularMaterialRegistry.DIRT
                )
        );
        entityData.set(
                SAND_UNITS,
                loadComposition.unitsOf(
                        GranularMaterialRegistry.SAND
                )
        );
        entityData.set(
                GRAVEL_UNITS,
                loadComposition.unitsOf(
                        GranularMaterialRegistry.GRAVEL
                )
        );
        entityData.set(
                COBBLESTONE_UNITS,
                loadComposition.unitsOf(
                        GranularMaterialRegistry.COBBLESTONE
                )
        );
    }

    @Override
    public InteractionResult interact(
            Player player,
            InteractionHand hand
    ) {
        if (player.isSecondaryUseActive()
                && player.getItemInHand(hand).isEmpty()) {

            // A loaded truck cannot be converted back into an item because that
            // would destroy the physical cargo.
            if (!isEmpty()) {
                return InteractionResult.FAIL;
            }

            if (!level().isClientSide()
                    && getPassengers().isEmpty()) {

                if (!player.getAbilities().instabuild) {
                    player.getInventory().add(
                            new ItemStack(
                                    GroundworksDumpTruckMod.DUMP_TRUCK_ITEM
                            )
                    );
                }

                level().playSound(
                        null,
                        getX(),
                        getY(),
                        getZ(),
                        SoundEvents.ITEM_PICKUP,
                        SoundSource.PLAYERS,
                        1.0F,
                        1.0F
                );

                discard();
            }

            return InteractionResult.SUCCESS;
        }

        if (!player.isSecondaryUseActive()
                && !level().isClientSide()
                && getPassengers().isEmpty()) {
            player.startRiding(this);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return position().add(
                getPassengerAttachmentPoint(
                        passenger,
                        getDimensions(getPose()),
                        1.0F
                )
        );
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(
            Entity passenger,
            EntityDimensions dimensions,
            float scale
    ) {
        Vec3 forward = forwardVector();
        Vec3 right = rightVector();

        // Seat model X is -6.5 px. The renderer mirrors X, so that cushion
        // appears on the driver's left. rightVector() points to that same side
        // (east when the truck faces south), so the rider offset is positive.
        return forward.scale(2.21875D)
                .add(right.scale(0.40625D))
                .add(0.0D, 1.88D, 0.0D);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(
            LivingEntity passenger
    ) {
        Vec3 left = rightVector().scale(-1.0D);

        return position()
                .add(left.scale(2.25D))
                .add(0.0D, 0.20D, 0.0D);
    }

    @Override
    public boolean hurtClient(DamageSource source) {
        return true;
    }

    @Override
    public boolean hurtServer(
            ServerLevel level,
            DamageSource source,
            float amount
    ) {
        if (isInvulnerableToBase(source)) {
            return false;
        }

        level.playSound(
                null,
                getX(),
                getY(),
                getZ(),
                SoundEvents.ANVIL_HIT,
                SoundSource.PLAYERS,
                0.8F,
                1.0F
        );

        if (source.getEntity() instanceof Player player) {
            // Do not allow breaking a loaded truck and silently deleting cargo.
            if (!isEmpty()) {
                return false;
            }

            if (!player.getAbilities().instabuild) {
                spawnAtLocation(
                        level,
                        GroundworksDumpTruckMod.DUMP_TRUCK_ITEM
                );
            }

            discard();
            return true;
        }

        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        DumpTruckState state =
                DumpTruckState.load(input);

        bedAngle = state.bedAngle();

        movementController.setSteerAngle(
                state.steerAngle()
        );
        movementController.setForwardSpeed(
                state.forwardSpeed()
        );
        movementController.setWheelRotation(
                state.wheelRotation()
        );
        movementController.setOrientation(
                state.vehiclePitch(),
                state.vehicleRoll()
        );

        loadComposition.replaceWith(
                state.materialUnits()
        );

        if (loadComposition.totalUnits() > BED_CAPACITY) {
            GranularComposition trimmed =
                    loadComposition.extractProportional(
                            BED_CAPACITY
                    );
            loadComposition.replaceWith(trimmed.toArray());
        }

        entityData.set(
                BED_ANGLE,
                state.bedAngle()
        );
        entityData.set(
                STEER_ANGLE,
                state.steerAngle()
        );
        entityData.set(
                FORWARD_SPEED,
                state.forwardSpeed()
        );
        entityData.set(
                WHEEL_ROTATION,
                state.wheelRotation()
        );
        entityData.set(
                VEHICLE_PITCH,
                state.vehiclePitch()
        );
        entityData.set(
                VEHICLE_ROLL,
                state.vehicleRoll()
        );

        syncLoadData();
        readHeldLights(input, headLightBlocks, "HeadLight");
        readHeldLights(input, tailLightBlocks, "TailLight");
        autopilot.load(input, level() != null ? level().getServer() : null);
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
                loadComposition.toArray()
        ).save(output);
        writeHeldLights(output, headLightBlocks, "HeadLight");
        writeHeldLights(output, tailLightBlocks, "TailLight");
        autopilot.save(output);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            clearHeldLights(headLightBlocks);
            clearHeldLights(tailLightBlocks);
        }
        super.remove(reason);
    }

    public float getBedAngle() {
        return entityData.get(BED_ANGLE);
    }

    public boolean isBedFull() {
        return storedUnits() >= BED_CAPACITY;
    }

    public double calculateBumperGap(GroundworksDumpTruckEntity other) {
        Vec3 forward = forwardVector();
        Vec3 toOther = other.position().subtract(position());
        double fwdDist = toOther.dot(forward);
        return Math.max(0.0D, fwdDist - 6.4D);
    }

    public void onExcavatorHornSignal(Vec3 hornOrigin) {
        if (!level().isClientSide()) {
            autopilot.onExcavatorHorn(hornOrigin);
        }
    }

    public DumpTruckAutopilot getAutopilot() {
        return autopilot;
    }

    public DumpTruckMovementController getMovementController() {
        return movementController;
    }

    public String getAutopilotState() {
        return entityData.get(AUTOPILOT_STATE);
    }

    public String getActiveRoute() {
        return entityData.get(ACTIVE_ROUTE);
    }

    public float getSteerAngle() {
        return entityData.get(STEER_ANGLE);
    }

    public float getForwardSpeed() {
        return entityData.get(FORWARD_SPEED);
    }

    public float getWheelRotation() {
        return entityData.get(WHEEL_ROTATION);
    }

    public float getVehiclePitch() {
        return entityData.get(VEHICLE_PITCH);
    }

    public float getVehicleRoll() {
        return entityData.get(VEHICLE_ROLL);
    }

    public int getCarriedUnits() {
        return entityData.get(CARRIED_UNITS);
    }

    public int getCarriedMaterialId() {
        return entityData.get(CARRIED_MATERIAL_ID);
    }

    public int getDirtUnits() {
        return entityData.get(DIRT_UNITS);
    }

    public int getSandUnits() {
        return entityData.get(SAND_UNITS);
    }

    public int getGravelUnits() {
        return entityData.get(GRAVEL_UNITS);
    }

    public int getCobblestoneUnits() {
        return entityData.get(COBBLESTONE_UNITS);
    }

    public boolean isEngineRunning() {
        return entityData.get(ENGINE_RUNNING);
    }

    public boolean isBraking() {
        return entityData.get(BRAKING);
    }

    public float getEngineLoad() {
        return entityData.get(ENGINE_LOAD);
    }

    public float getFillRatio() {
        return (float) getCarriedUnits() / (float) BED_CAPACITY;
    }

    public boolean isDumping() {
        return entityData.get(IS_DUMPING);
    }

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
        return passenger instanceof LivingEntity
                && getPassengers().isEmpty();
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        Entity first = getFirstPassenger();
        return first instanceof LivingEntity living
                ? living
                : null;
    }

    public boolean isDriver(Entity entity) {
        return entity != null
                && entity == getControllingPassenger();
    }
}
