package io.github.teamcreating.mtrnav.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WalkingChunk(long transfer, int index, int total, byte[] data) implements CustomPacketPayload {
    public static final Type<WalkingChunk> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("mtrnav", "walking_response_v1"));
    public static final StreamCodec<FriendlyByteBuf, WalkingChunk> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeLong(value.transfer); buf.writeVarInt(value.index); buf.writeVarInt(value.total); buf.writeByteArray(value.data); },
            buf -> new WalkingChunk(buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(WalkingWire.CHUNK)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
