package io.github.teamcreating.mtrnav.network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.nio.charset.StandardCharsets;
public record NavRequestV3(String json) implements CustomPacketPayload {
    public NavRequestV3 { NetworkLimits.upload(json); }
    public static final Type<NavRequestV3> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("mtrnav", "request_v3"));
    public static final StreamCodec<FriendlyByteBuf, NavRequestV3> CODEC = StreamCodec.of(
        (buf, value) -> buf.writeByteArray(value.json.getBytes(StandardCharsets.UTF_8)), buf -> new NavRequestV3(new String(buf.readByteArray(16 * 1024), StandardCharsets.UTF_8)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
