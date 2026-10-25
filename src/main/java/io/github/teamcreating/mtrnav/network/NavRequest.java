package io.github.teamcreating.mtrnav.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NavRequest(String json) implements CustomPacketPayload {
    public static final Type<NavRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("mtrnav", "request_v1"));
    public static final StreamCodec<FriendlyByteBuf, NavRequest> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeUtf(value.json, 8192), buf -> new NavRequest(buf.readUtf(8192)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
