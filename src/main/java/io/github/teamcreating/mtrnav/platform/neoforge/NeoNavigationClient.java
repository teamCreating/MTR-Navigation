package io.github.teamcreating.mtrnav.platform.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.teamcreating.mtrnav.client.NavigationClient;
import io.github.teamcreating.mtrnav.client.WalkingClient;
import io.github.teamcreating.mtrnav.network.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lwjgl.glfw.GLFW;

@Mod(value = "mtrnav", dist = Dist.CLIENT)
public final class NeoNavigationClient {
    private static final KeyMapping OPEN = new KeyMapping("key.mtrnav.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "category.mtrnav");
    public NeoNavigationClient(IEventBus bus) {
        NavigationClient.openKey = () -> OPEN.getTranslatedKeyMessage().getString();
        bus.addListener((RegisterKeyMappingsEvent event) -> event.register(OPEN));
        NavigationClient.transport(() -> Minecraft.getInstance().getConnection() != null && Minecraft.getInstance().getConnection().hasChannel(NavRequest.TYPE), PacketDistributor::sendToServer);
        WalkingClient.transport(() -> Minecraft.getInstance().getConnection() != null && Minecraft.getInstance().getConnection().hasChannel(WalkingRequest.TYPE), PacketDistributor::sendToServer,
                () -> Minecraft.getInstance().getConnection() != null && Minecraft.getInstance().getConnection().hasChannel(NavRequestV2.TYPE), PacketDistributor::sendToServer);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            NavigationClient.tick();
            while (OPEN.consumeClick()) NavigationClient.open();
        });
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> event.getDispatcher().register(Commands.literal("mtrnav").executes(context -> { NavigationClient.open(); return 1; })));
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> NavigationClient.renderHud(event.getGuiGraphics()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> NavigationClient.reset());
    }
    static void walking(WalkingChunk chunk, IPayloadContext context) { context.enqueueWork(() -> WalkingClient.chunk(chunk)); }
    static void chunk(NavChunk chunk, IPayloadContext context) { context.enqueueWork(() -> NavigationClient.chunk(chunk)); }
}
