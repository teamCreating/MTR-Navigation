package io.github.teamcreating.mtrnav.platform.neoforge;

import io.github.teamcreating.mtrnav.network.*;
import io.github.teamcreating.mtrnav.server.NavigationServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod("mtrnav")
public final class NeoNavigation {
    public NeoNavigation(IEventBus bus) {
        bus.addListener(NeoNavigation::payloads);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> NavigationServer.start(event.getServer(), FMLPaths.CONFIGDIR.get(), PacketDistributor::sendToPlayer, PacketDistributor::sendToPlayer));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> NavigationServer.stop());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> NavigationServer.tick());
    }
    private static void payloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToServer(NavRequest.TYPE, NavRequest.CODEC, (request, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) NavigationServer.receive(player, request);
        }));
        registrar.playToServer(NavRequestV2.TYPE, NavRequestV2.CODEC, (request, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) NavigationServer.receiveV2(player, request);
        }));
        registrar.playToServer(WalkingRequest.TYPE, WalkingRequest.CODEC, (request, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) io.github.teamcreating.mtrnav.server.WalkingServer.receive(player, request);
        }));
        if (FMLEnvironment.dist == Dist.CLIENT) registrar.playToClient(WalkingChunk.TYPE, WalkingChunk.CODEC, NeoNavigationClient::walking);
        else registrar.playToClient(WalkingChunk.TYPE, WalkingChunk.CODEC, (chunk, context) -> {});
        if (FMLEnvironment.dist == Dist.CLIENT) registrar.playToClient(NavChunk.TYPE, NavChunk.CODEC, NeoNavigationClient::chunk);
        else registrar.playToClient(NavChunk.TYPE, NavChunk.CODEC, (chunk, context) -> {});
    }
}
