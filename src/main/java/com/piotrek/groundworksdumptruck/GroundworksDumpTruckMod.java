package com.piotrek.groundworksdumptruck;

import com.piotrek.groundworksdumptruck.entity.GroundworksDumpTruckEntity;
import com.piotrek.groundworksdumptruck.item.DumpTruckItem;
import com.piotrek.groundworksdumptruck.network.DumpTruckInputPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroundworksDumpTruckMod implements ModInitializer {
    public static final String MOD_ID = "pw_groundworks_dump_truck";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static final ResourceKey<EntityType<?>> DUMP_TRUCK_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, id("dump_truck"));

    public static final EntityType<GroundworksDumpTruckEntity> DUMP_TRUCK = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            DUMP_TRUCK_KEY,
            EntityType.Builder.of(GroundworksDumpTruckEntity::new, MobCategory.MISC)
                    .sized(3.35F, 3.0F)
                    .clientTrackingRange(12)
                    .build(DUMP_TRUCK_KEY)
    );

    public static final SoundEvent ENGINE_LOOP = Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            id("engine_loop"),
            SoundEvent.createFixedRangeEvent(id("engine_loop"), 48.0F)
    );

    public static final ResourceKey<Item> DUMP_TRUCK_ITEM_KEY =
            ResourceKey.create(Registries.ITEM, id("dump_truck"));

    public static final DumpTruckItem DUMP_TRUCK_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            DUMP_TRUCK_ITEM_KEY,
            new DumpTruckItem(new Item.Properties().setId(DUMP_TRUCK_ITEM_KEY).stacksTo(1))
    );

    public static final ResourceKey<CreativeModeTab> TOOLS_AND_UTILITIES_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Identifier.withDefaultNamespace("tools_and_utilities")
    );

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Peterwolf's Groundworks Dump Truck for MC 26.3...");

        PayloadTypeRegistry.serverboundPlay().register(
                DumpTruckInputPayload.TYPE, DumpTruckInputPayload.CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(
                DumpTruckInputPayload.TYPE, (payload, context) -> context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player.getVehicle() instanceof GroundworksDumpTruckEntity truck
                            && truck.isDriver(player)) {
                        truck.setControlInputs(payload.throttle(), payload.steer(), payload.bedLift());
                    }
                })
        );

        CreativeModeTabEvents.modifyOutputEvent(TOOLS_AND_UTILITIES_TAB).register(output ->
                output.accept(DUMP_TRUCK_ITEM)
        );
    }
}
