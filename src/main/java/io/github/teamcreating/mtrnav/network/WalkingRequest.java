package io.github.teamcreating.mtrnav.network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.nio.charset.StandardCharsets;
public record WalkingRequest(String json) implements CustomPacketPayload {
    public WalkingRequest { NetworkLimits.upload(json); }
    public static final Type<WalkingRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("mtrnav", "walking_request_v1"));
    public static final StreamCodec<FriendlyByteBuf, WalkingRequest> CODEC = StreamCodec.of(
        (buf, value) -> buf.writeByteArray(value.json.getBytes(StandardCharsets.UTF_8)), buf -> new WalkingRequest(new String(buf.readByteArray(16 * 1024), StandardCharsets.UTF_8)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
