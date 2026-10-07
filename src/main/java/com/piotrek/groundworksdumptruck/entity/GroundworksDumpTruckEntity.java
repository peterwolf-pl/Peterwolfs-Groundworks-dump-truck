package com.piotrek.groundworksdumptruck.entity;

import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworksdumptruck.GroundworksDumpTruckMod;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class GroundworksDumpTruckEntity extends Entity {

    public static final int UNITS_PER_BLOCK = 8 * 8 * 8;
    public static final int CAPACITY_BLOCKS = 10;
    public static final int CAPACITY_UNITS = CAPACITY_BLOCKS * UNITS_PER_BLOCK;
    public static final float MAX_BED_ANGLE = 50.0F;

    private static final EntityDataAccessor<Float> BED_ANGLE =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> CARRIED_MATERIAL_ID =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CARRIED_UNITS =
            SynchedEntityData.defineId(GroundworksDumpTruckEntity.class, EntityDataSerializers.INT);

    public GroundworksDumpTruckEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BED_ANGLE, 0.0F);
        builder.define(CARRIED_MATERIAL_ID, 0);
        builder.define(CARRIED_UNITS, 0);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        ItemStack held = player.getItemInHand(hand);

        if (!held.isEmpty() && held.getItem() instanceof BlockItem blockItem) {
            GranularMaterial material =
                    GranularMaterialRegistry.forBlockState(blockItem.getBlock().defaultBlockState());

            if (material != null && material.id() > 0) {
                if (!this.level().isClientSide()) {
                    int accepted = tryAddMaterial(material, UNITS_PER_BLOCK);
                    if (accepted == UNITS_PER_BLOCK) {
                        if (!player.getAbilities().instabuild) {
                            held.shrink(1);
                        }
                        this.level().playSound(
                                null,
                                this.getX(),
                                this.getY(),
                                this.getZ(),
                                SoundEvents.GRAVEL_PLACE,
                                SoundSource.BLOCKS,
                                0.8F,
                                0.9F
                        );
                    }
                }
                return InteractionResult.SUCCESS;
            }
        }

        if (player.isSecondaryUseActive() && held.isEmpty()) {
            if (!this.level().isClientSide()) {
                setBedAngle(getBedAngle() < MAX_BED_ANGLE * 0.5F ? MAX_BED_ANGLE : 0.0F);
            }
            return InteractionResult.SUCCESS;
        }

        if (held.isEmpty()) {
            if (!this.level().isClientSide() && this.getPassengers().isEmpty()) {
                player.startRiding(this);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    public int tryAddMaterial(GranularMaterial material, int requestedUnits) {
        if (material == null || material.id() <= 0 || requestedUnits <= 0) {
            return 0;
        }

        int currentUnits = getCarriedUnits();
        int currentMaterial = getCarriedMaterialId();

        if (currentUnits > 0 && currentMaterial != material.id()) {
            return 0;
        }

        int accepted = Math.min(requestedUnits, CAPACITY_UNITS - currentUnits);
        if (accepted <= 0) {
            return 0;
        }

        this.entityData.set(CARRIED_MATERIAL_ID, material.id());
        this.entityData.set(CARRIED_UNITS, currentUnits + accepted);
        return accepted;
    }

    public int removeMaterial(int requestedUnits) {
        if (requestedUnits <= 0) {
            return 0;
        }

        int current = getCarriedUnits();
        int removed = Math.min(requestedUnits, current);
        int remaining = current - removed;

        this.entityData.set(CARRIED_UNITS, remaining);
        if (remaining <= 0) {
            this.entityData.set(CARRIED_MATERIAL_ID, 0);
        }
        return removed;
    }

    public float getBedAngle() {
        return this.entityData.get(BED_ANGLE);
    }

    public void setBedAngle(float angle) {
        this.entityData.set(BED_ANGLE, Mth.clamp(angle, 0.0F, MAX_BED_ANGLE));
    }

    public int getCarriedMaterialId() {
        return this.entityData.get(CARRIED_MATERIAL_ID);
    }

    public GranularMaterial getCarriedMaterial() {
        return GranularMaterialRegistry.byId(getCarriedMaterialId());
    }

    public int getCarriedUnits() {
        return this.entityData.get(CARRIED_UNITS);
    }

    public float getFillRatio() {
        return (float) getCarriedUnits() / (float) CAPACITY_UNITS;
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return this.position().add(
                this.getPassengerAttachmentPoint(passenger, this.getDimensions(this.getPose()), 1.0F)
        );
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        double yawRad = Math.toRadians(this.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        return forward.scale(2.20D).add(0.0D, 1.65D, 0.0D);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        double yawRad = Math.toRadians(this.getYRot());
        Vec3 left = new Vec3(-Math.cos(yawRad), 0.0D, -Math.sin(yawRad));
        return this.position().add(left.scale(2.25D)).add(0.0D, 0.20D, 0.0D);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof LivingEntity && this.getPassengers().isEmpty();
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        Entity first = this.getFirstPassenger();
        return first instanceof LivingEntity living ? living : null;
    }

    @Override
    public boolean hurtClient(DamageSource source) {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (this.isInvulnerableToBase(source)) {
            return false;
        }

        level.playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                SoundEvents.ANVIL_HIT,
                SoundSource.PLAYERS,
                0.8F,
                1.0F
        );

        if (source.getEntity() instanceof Player player) {
            if (!player.getAbilities().instabuild) {
                this.spawnAtLocation(level, GroundworksDumpTruckMod.DUMP_TRUCK_ITEM);
            }
            this.discard();
            return true;
        }
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        setBedAngle(input.getFloatOr("BedAngle", 0.0F));

        int materialId = Math.max(0, input.getIntOr("CarriedMaterialId", 0));
        int units = Math.clamp(input.getIntOr("CarriedUnits", 0), 0, CAPACITY_UNITS);

        this.entityData.set(CARRIED_MATERIAL_ID, units > 0 ? materialId : 0);
        this.entityData.set(CARRIED_UNITS, units);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putFloat("BedAngle", getBedAngle());
        output.putInt("CarriedMaterialId", getCarriedMaterialId());
        output.putInt("CarriedUnits", getCarriedUnits());
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return other != null && !this.hasPassenger(other);
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
}
