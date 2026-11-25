package io.github.teamcreating.mtrnav.client.ui;

import io.github.teamcreating.mtrnav.client.UiBridge;
import io.github.teamcreating.mtrnav.client.WalkingClient;
import io.github.teamcreating.mtrnav.client.ui.drawer.ConfirmDrawer;
import io.github.teamcreating.mtrnav.client.ui.drawer.ReminderDrawer;
import io.github.teamcreating.mtrnav.client.ui.hud.CapsuleReminderHud;
import io.github.teamcreating.mtrnav.client.ui.hud.LcdNavigationHud;
import io.github.teamcreating.mtrnav.client.ui.workspace.JourneyPlanView;
import io.github.teamcreating.mtrnav.client.ui.workspace.NewUiFixtures;
import io.github.teamcreating.mtrnav.client.ui.workspace.WorkspaceHost;
import io.github.teamcreating.mtrnav.client.ui.workspace.WorkspaceScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import static io.github.teamcreating.mtrnav.client.NavigationClient.*;

/**
 * Public client-side UI entry facade.
 * Also provides clean deep-link entry points for future bidirectional MTR-Map-Overlay integration.
 */
public final class NavigationUi {
    private static boolean fixtureAutoOpened;

    private NavigationUi() {}

    public static void openWorkspace() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UiBridge.notifyNativeOpened();
        NewUiFixtures.ensureLoaded();
        if (reminder != null && tracking == null) {
            var screen = new WorkspaceScreen(WorkspaceHost.Tab.BOARD);
            mc.setScreen(screen);
            screen.openStationBoard(reminder.stationId());
            screen.openDrawer(new ReminderDrawer(reminder.stationId(), reminder.event(), reminder.minutes()));
        } else {
            mc.setScreen(new WorkspaceScreen(tracking != null ? WorkspaceHost.Tab.ACTIVE : WorkspaceHost.Tab.PLAN));
        }
    }

    public static void openStationBoard(String stationId) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UiBridge.notifyNativeOpened();
        var screen = new WorkspaceScreen(WorkspaceHost.Tab.BOARD);
        mc.setScreen(screen);
        if (stationId != null && !stationId.isEmpty()) screen.openStationBoard(stationId);
    }

    public static void openPlanWithStation(String stationId, boolean asOrigin) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UiBridge.notifyNativeOpened();
        var screen = new WorkspaceScreen(WorkspaceHost.Tab.PLAN);
        mc.setScreen(screen);
        if (stationId != null && !stationId.isEmpty()) screen.openPlanWithStation(stationId, asOrigin);
    }

    /**
     * Deep-link invoked by MTR Map Overlay when the user clicks "Navigate" on a station in Xaero/JourneyMap.
     * Pre-fills destination, auto-locates nearby origin, and triggers search immediately when unambiguous.
     */
    public static void openPlanFromOverlay(String navDimension, String stationId) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || stationId == null || stationId.isEmpty()) return;
        UiBridge.notifyNativeOpened();
        pendingExternalDimension = navDimension == null ? "" : navDimension;
        pendingExternalDestination = stationId;
        var screen = new WorkspaceScreen(WorkspaceHost.Tab.PLAN);
        mc.setScreen(screen);
        screen.planView().applyExternalDestinationIfReady();
    }

    public static void confirmWalkingConsent(Runnable next) {
        var mc = Minecraft.getInstance();
        WorkspaceScreen ws;
        if (mc.screen instanceof WorkspaceScreen existing) {
            ws = existing;
        } else {
            if (mc.player == null) return;
            ws = new WorkspaceScreen(WorkspaceHost.Tab.PLAN);
            mc.setScreen(ws);
        }
        ws.openDrawer(new ConfirmDrawer(
                tr("mtrnav.walk_consent_title"),
                tr("mtrnav.walk_consent_explanation"),
                tr("mtrnav.walk_consent_agree"),
                tr("mtrnav.walk_consent_decline"),
                () -> {
                    WalkingClient.consent(true);
                    if (WalkingClient.consent() && next != null) next.run();
                },
                () -> {
                    WalkingClient.consent(false);
                    if (next != null) next.run();
                }));
    }

    public static void tickFixtures() {
        if (!NewUiFixtures.enabled()) return;
        var mc = Minecraft.getInstance();
        NewUiFixtures.ensureLoaded();
        if (!fixtureAutoOpened && mc.player != null && mc.level != null && mc.screen == null) {
            fixtureAutoOpened = true;
            openWorkspace();
        }
    }

    public static void reset() {
        fixtureAutoOpened = false;
        JourneyPlanView.resetSessionView();
        CapsuleReminderHud.reset();
    }

    public static void onReminderArmed() { CapsuleReminderHud.armed(); }
    public static void onReminderTriggered() { CapsuleReminderHud.triggered(); }
    public static void onReminderCleared() { CapsuleReminderHud.reset(); }

    public static void renderHud(GuiGraphics g) {
        LcdNavigationHud.render(g);
        CapsuleReminderHud.render(g);
    }
}
