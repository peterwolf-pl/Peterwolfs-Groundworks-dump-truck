package com.piotrek.groundworksdumptruck.item;

import com.piotrek.groundworksdumptruck.GroundworksDumpTruckMod;
import com.piotrek.groundworksdumptruck.entity.GroundworksDumpTruckEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DumpTruckItem extends Item {

    public DumpTruckItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());
        Vec3 spawnVec = new Vec3(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D
        );

        AABB bounds = GroundworksDumpTruckMod.DUMP_TRUCK.getDimensions().makeBoundingBox(spawnVec);
        if (!level.noCollision(bounds)) {
            Vec3 raised = spawnVec.add(0.0D, 0.5D, 0.0D);
            if (!level.noCollision(GroundworksDumpTruckMod.DUMP_TRUCK.getDimensions().makeBoundingBox(raised))) {
                return InteractionResult.FAIL;
            }
            spawnVec = raised;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        GroundworksDumpTruckEntity truck = GroundworksDumpTruckMod.DUMP_TRUCK.create(
                serverLevel,
                EntitySpawnReason.SPAWN_ITEM_USE
        );

        if (truck == null) {
            return InteractionResult.FAIL;
        }

        Player player = context.getPlayer();
        float yaw = player != null ? player.getYRot() : 0.0F;
        truck.snapTo(spawnVec.x, spawnVec.y, spawnVec.z, yaw, 0.0F);
        truck.setYHeadRot(yaw);
        truck.setYBodyRot(yaw);
        serverLevel.addFreshEntity(truck);

        ItemStack stack = context.getItemInHand();
        if (player != null && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResult.CONSUME;
    }
}
