package io.github.teamcreating.mtrnav.platform.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.teamcreating.mtrnav.client.NavigationClient;
import io.github.teamcreating.mtrnav.network.*;
import net.minecraft.client.KeyMapping;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.lwjgl.glfw.GLFW;

public final class FabricNavigationClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        var open = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.mtrnav.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "category.mtrnav"));
        NavigationClient.openKey = () -> open.getTranslatedKeyMessage().getString();
        NavigationClient.transport(() -> ClientPlayNetworking.canSend(NavRequest.TYPE), ClientPlayNetworking::send);
        ClientPlayNetworking.registerGlobalReceiver(NavChunk.TYPE, (chunk, context) -> context.client().execute(() -> NavigationClient.chunk(chunk)));
        ClientTickEvents.END_CLIENT_TICK.register(client -> { NavigationClient.tick(); while (open.consumeClick()) NavigationClient.open(); });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(ClientCommandManager.literal("mtrnav").executes(context -> { NavigationClient.open(); return 1; })));
        HudRenderCallback.EVENT.register((graphics, delta) -> NavigationClient.renderHud(graphics));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> NavigationClient.reset());
    }
}
