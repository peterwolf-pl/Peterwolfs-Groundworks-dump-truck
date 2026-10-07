package com.piotrek.groundworksdumptruck.entity;

import com.piotrek.groundworks.api.GroundworksApi;
import com.piotrek.groundworks.api.container.IMobileWorldGranularContainer;
import com.piotrek.groundworks.api.deposit.DepositResult;
import com.piotrek.groundworks.api.material.GranularComposition;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworksdumptruck.GroundworksDumpTruckMod;
import com.piotrek.groundworksdumptruck.vehicle.DumpTruckMovementController;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative three-axle 6x4 Groundworks dump truck.
 *
 * <p>The body is a real Groundworks granular container. Capacity is exactly
 * ten full Groundworks blocks: 10 x 512 = 5120 integer units.</p>
 */
public class GroundworksDumpTruckEntity extends Entity implements IMobileWorldGranularContainer {

    public static final int BED_CAPACITY = 5120;
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
    private static final EntityDataAccessor<Boolean> IS_DUMPING =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.BOOLEAN);

    private final DumpTruckMovementController movementController =
            new DumpTruckMovementController();
    private final GranularComposition loadComposition =
            new GranularComposition();

    private float inputThrottle;
    private float inputSteer;
    private float inputBedLift;
    private int inputFreshTicks;
    private float bedAngle;
    private boolean overflowSideToggle;
    private double remoteAdvanceRemaining;

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
        builder.define(DIRT_UNITS, 0);
        builder.define(SAND_UNITS, 0);
        builder.define(GRAVEL_UNITS, 0);
        builder.define(COBBLESTONE_UNITS, 0);

        builder.define(ENGINE_RUNNING, false);
        builder.define(IS_DUMPING, false);
    }

    public void setControlInputs(float throttle, float steer, float bedLift) {
        inputThrottle = Mth.clamp(throttle, -1.0F, 1.0F);
        inputSteer = Mth.clamp(steer, -1.0F, 1.0F);
        inputBedLift = Mth.clamp(bedLift, -1.0F, 1.0F);
        inputFreshTicks = 5;
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
        return true;
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
        } else {
            inputThrottle = 0.0F;
            inputSteer = 0.0F;
            inputBedLift = 0.0F;
            inputFreshTicks = 0;
        }

        entityData.set(ENGINE_RUNNING, driver != null || remoteAdvanceRemaining > 0.01D);

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

        if (remoteAdvanceRemaining > 0.01D) {
            Vec3 moved = position().subtract(beforeMove);
            double horizontal = Math.sqrt(moved.x * moved.x + moved.z * moved.z);
            remoteAdvanceRemaining = Math.max(0.0D, remoteAdvanceRemaining - horizontal);

            if (remoteAdvanceRemaining <= 0.01D || horizontal < 0.001D) {
                remoteAdvanceRemaining = 0.0D;
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

        syncLoadData();
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

        return Math.abs(localRight) <= 1.65D
                && localForward >= -3.35D
                && localForward <= 0.95D
                && delta.y >= 0.85D
                && delta.y <= 3.65D;
    }

    /**
     * Fill the body first. Any excess from a bucket hitting a full body falls
     * over the left/right side and becomes normal Groundworks terrain.
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
                    -0.75D - ((pass / 2) * 0.75D);

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

    private boolean tickDumping(ServerLevel level) {
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

    private Vec3 getDumpLipWorldPosition() {
        Vec3 forward = forwardVector();

        double angleRad = Math.toRadians(bedAngle);
        double rearOffset =
                3.05D + Math.cos(angleRad) * 0.20D;
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
        Vec3 left = position()
                .add(rightVector().scale(-1.85D))
                .add(forwardVector().scale(-1.15D))
                .add(0.0D, 1.45D, 0.0D);

        Vec3 right = position()
                .add(rightVector().scale(1.85D))
                .add(forwardVector().scale(-1.15D))
                .add(0.0D, 1.45D, 0.0D);

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

    private Vec3 forwardVector() {
        double yawRad = Math.toRadians(getYRot());
        return new Vec3(
                -Math.sin(yawRad),
                0.0D,
                Math.cos(yawRad)
        );
    }

    private Vec3 rightVector() {
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
            InteractionHand hand,
            Vec3 location
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

        return forward.scale(2.0D)
                .add(right.scale(-0.48D))
                .add(0.0D, 1.48D, 0.0D);
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
    }

    public float getBedAngle() {
        return entityData.get(BED_ANGLE);
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
