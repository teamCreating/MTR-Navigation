package io.github.teamcreating.mtrnav.client.ui.workspace;

import io.github.teamcreating.mtrnav.client.ui.framework.SlideDrawer;

/**
 * Navigation and drawer host interface implemented by WorkspaceScreen.
 */
public interface WorkspaceHost {
    enum Tab { PLAN, BOARD, ACTIVE, WALKING, HUD }

    void switchTab(Tab tab);
    void openDrawer(SlideDrawer drawer);
    void closeDrawer();
    void openStationBoard(String stationId);
    void openPlanWithStation(String stationId, boolean asOrigin);
    void enterHudPreviewMode();
    void closeWorkspace();
}
