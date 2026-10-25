package io.github.teamcreating.mtrnav.platform.fabric;

import io.github.teamcreating.mtrnav.network.*;
import io.github.teamcreating.mtrnav.server.NavigationServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;

public final class FabricNavigation implements ModInitializer {
    @Override public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(NavRequest.TYPE, NavRequest.CODEC);
        PayloadTypeRegistry.playS2C().register(NavChunk.TYPE, NavChunk.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(NavRequest.TYPE, (request, context) -> context.server().execute(() -> NavigationServer.receive(context.player(), request)));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> NavigationServer.start(server, FabricLoader.getInstance().getConfigDir(), (player, chunk) -> {
            if (ServerPlayNetworking.canSend(player, NavChunk.TYPE)) ServerPlayNetworking.send(player, chunk);
        }));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> NavigationServer.stop());
        ServerTickEvents.END_SERVER_TICK.register(server -> NavigationServer.tick());
    }
}
