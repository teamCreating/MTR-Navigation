package io.github.teamcreating.mtrnav.network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.nio.charset.StandardCharsets;
public record NavRequestV2(String json) implements CustomPacketPayload {
    public NavRequestV2 { NetworkLimits.upload(json); }
    public static final Type<NavRequestV2> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("mtrnav", "request_v2"));
    public static final StreamCodec<FriendlyByteBuf, NavRequestV2> CODEC = StreamCodec.of(
        (buf, value) -> buf.writeByteArray(value.json.getBytes(StandardCharsets.UTF_8)), buf -> new NavRequestV2(new String(buf.readByteArray(16 * 1024), StandardCharsets.UTF_8)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
