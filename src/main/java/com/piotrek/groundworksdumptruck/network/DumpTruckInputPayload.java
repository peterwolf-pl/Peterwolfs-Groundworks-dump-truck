package com.piotrek.groundworksdumptruck.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DumpTruckInputPayload(float throttle, float steer, float bedLift)
        implements CustomPacketPayload {

    public static final Type<DumpTruckInputPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(
                    "pw_groundworks_dump_truck", "dump_truck_input"
            ));

    public static final StreamCodec<RegistryFriendlyByteBuf, DumpTruckInputPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public DumpTruckInputPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new DumpTruckInputPayload(
                            buffer.readFloat(),
                            buffer.readFloat(),
                            buffer.readFloat()
                    );
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, DumpTruckInputPayload payload) {
                    buffer.writeFloat(payload.throttle());
                    buffer.writeFloat(payload.steer());
                    buffer.writeFloat(payload.bedLift());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
