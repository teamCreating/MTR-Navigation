package io.github.teamcreating.mtrnav.client.ui.workspace;

import io.github.teamcreating.mtrnav.api.NavigationApi;
import io.github.teamcreating.mtrnav.client.UiBridge;
import io.github.teamcreating.mtrnav.client.WalkingClient;
import io.github.teamcreating.mtrnav.client.ui.framework.SlideDrawer;
import io.github.teamcreating.mtrnav.client.ui.framework.UiButton;
import io.github.teamcreating.mtrnav.client.ui.framework.UiContext;
import io.github.teamcreating.mtrnav.client.ui.framework.UiView;
import io.github.teamcreating.mtrnav.client.ui.hud.CapsuleReminderHud;
import io.github.teamcreating.mtrnav.client.ui.hud.LcdNavigationHud;
import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiIcons;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.compat.MinecraftGui;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import java.util.*;

import static io.github.teamcreating.mtrnav.client.NavigationClient.*;

/**
 * Unified modern Transit Workbench Screen: permanent left navigation rail + single workspace canvas
 * + right-side slide-over drawer overlay + live full-screen HUD drag preview mode.
 */
public final class WorkspaceScreen extends Screen implements WorkspaceHost {
    private static final ResourceLocation BRAND_ICON = ResourceLocation.fromNamespaceAndPath("mtrnav", "icon.png");
    private final Map<Tab, UiView> views = new EnumMap<>(Tab.class);
    private final Map<Tab, UiButton> railButtons = new EnumMap<>(Tab.class);
    private final UiButton closeBtn;
    private final UiButton exitHudPreviewBtn;

    private final JourneyPlanView planView;
    private final PidsBoardView boardView;
    private final ActiveTripView activeView;
    private final WalkingNetworkView walkingView;
    private final HudSettingsView hudView;

    private Tab activeTab;
    private SlideDrawer activeDrawer;
    private boolean hudPreviewMode;
    private boolean draggingHud;
    private boolean draggingReminder;
    private double dragOffsetX, dragOffsetY;

    private double enteredGuiScale = Double.NaN;
    private UiRect shellRect = UiRect.EMPTY;
    private UiRect innerShellRect = UiRect.EMPTY;
    private UiRect railRect = UiRect.EMPTY;
    private UiRect topBarRect = UiRect.EMPTY;
    private UiRect contentRect = UiRect.EMPTY;
    private boolean railExpanded = true;

    public WorkspaceScreen(Tab initialTab) {
        super(Component.translatable("mtrnav.title"));
        this.activeTab = initialTab == null ? (tracking != null ? Tab.ACTIVE : Tab.PLAN) : initialTab;

        this.planView = new JourneyPlanView(this);
        this.boardView = new PidsBoardView(this);
        this.activeView = new ActiveTripView(this);
        this.walkingView = new WalkingNetworkView(this);
        this.hudView = new HudSettingsView(this);

        views.put(Tab.PLAN, planView);
        views.put(Tab.BOARD, boardView);
        views.put(Tab.ACTIVE, activeView);
        views.put(Tab.WALKING, walkingView);
        views.put(Tab.HUD, hudView);

        railButtons.put(Tab.PLAN, new UiButton(tr("mtrnav.query_tab"), UiButton.Style.GHOST, () -> switchTab(Tab.PLAN)).icon(UiIcons.ROUTE));
        railButtons.put(Tab.BOARD, new UiButton(tr("mtrnav.departures"), UiButton.Style.GHOST, () -> switchTab(Tab.BOARD)).icon(UiIcons.BOARD));
        railButtons.put(Tab.ACTIVE, new UiButton(tr("mtrnav.active_trip"), UiButton.Style.GHOST, () -> switchTab(Tab.ACTIVE)).icon(UiIcons.TRAIN));
        railButtons.put(Tab.WALKING, new UiButton(tr("mtrnav.walk_short"), UiButton.Style.GHOST, () -> switchTab(Tab.WALKING)).icon(UiIcons.WALK));
        railButtons.put(Tab.HUD, new UiButton(tr("mtrnav.hud_settings_short"), UiButton.Style.GHOST, () -> switchTab(Tab.HUD)).icon(UiIcons.HUD));

        this.closeBtn = new UiButton("", UiButton.Style.GHOST, this::closeWorkspace).icon(UiIcons.CLOSE).tooltip(tr("mtrnav.close"));
        this.exitHudPreviewBtn = new UiButton(tr("mtrnav.ore.back_settings"), UiButton.Style.PRIMARY, () -> {
            hudPreviewMode = false;
            draggingHud = false;
            UiBridge.savePreferences();
        });
    }

    public PidsBoardView boardView() { return boardView; }
    public JourneyPlanView planView() { return planView; }
    public boolean wantsBoard() { return activeTab == Tab.BOARD; }

    private void applyReadableScale() {
        var window = minecraft.getWindow();
        if (Double.isNaN(enteredGuiScale)) enteredGuiScale = window.getGuiScale();
        int normal = window.calculateScale(minecraft.options.guiScale().get(), minecraft.isEnforceUnicode());
        int limit = window.getHeight() >= 1260 && window.getWidth() >= 1920 ? 3 : 2;
        window.setGuiScale(Math.min(limit, normal));
        this.width = window.getGuiScaledWidth();
        this.height = window.getGuiScaledHeight();
    }

    @Override
    protected void init() {
        applyReadableScale();
        NewUiFixtures.ensureLoaded();
        relayout();
        views.get(activeTab).onActivate();
    }

    @Override
    public void resize(Minecraft mc, int w, int h) {
        applyReadableScale();
        super.resize(mc, this.width, this.height);
    }

    private void relayout() {
        int sw = Math.min(width - 16, 740);
        int sh = Math.min(height - 16, 470);
        this.shellRect = new UiRect((width - sw) / 2, (height - sh) / 2, sw, sh);
        this.innerShellRect = shellRect.inset(4);
        this.railExpanded = sw >= 560;
        int railW = railExpanded ? UiTokens.RAIL_WIDE : UiTokens.RAIL_COMPACT;

        this.railRect = innerShellRect.sliceLeft(railW);
        UiRect rightArea = innerShellRect.trimLeft(railW);
        this.topBarRect = rightArea.sliceTop(30);
        this.contentRect = rightArea.trimTop(30);

        int ry = railRect.y() + 42;
        for (Tab t : Tab.values()) {
            var btn = railButtons.get(t);
            btn.label(railExpanded ? switch (t) {
                case PLAN -> tr("mtrnav.query_tab");
                case BOARD -> tr("mtrnav.departures");
                case ACTIVE -> tr("mtrnav.active_trip");
                case WALKING -> tr("mtrnav.walk_short");
                case HUD -> tr("mtrnav.hud_settings");
            } : "");
            btn.tooltip(railExpanded ? null : switch (t) {
                case PLAN -> tr("mtrnav.query_tab");
                case BOARD -> tr("mtrnav.departures");
                case ACTIVE -> tr("mtrnav.active_trip");
                case WALKING -> tr("mtrnav.walk_short");
                case HUD -> tr("mtrnav.hud_settings");
            });
            btn.bounds(new UiRect(railRect.x() + 6, ry, railRect.width() - 12, 24));
            ry += 28;
        }

        closeBtn.bounds(new UiRect(topBarRect.right() - 26, topBarRect.y() + 6, 20, 18));
        exitHudPreviewBtn.bounds(new UiRect((width - 110) / 2, height - 30, 110, 22));

        for (var view : views.values()) view.layout(contentRect);
        if (activeDrawer != null) activeDrawer.layout(innerShellRect);
    }

    @Override
    public void switchTab(Tab tab) {
        if (tab == null || tab == activeTab) return;
        closeDrawer();
        views.get(activeTab).onDeactivate();
        activeTab = tab;
        views.get(activeTab).onActivate();
        relayout();
    }

    @Override
    public void openDrawer(SlideDrawer drawer) {
        if (drawer == null) return;
        activeDrawer = drawer;
        drawer.setOnClose(() -> {
            if (activeDrawer == drawer) activeDrawer = null;
        });
        drawer.layout(innerShellRect);
    }

    @Override
    public void closeDrawer() {
        if (activeDrawer != null) {
            var d = activeDrawer;
            activeDrawer = null;
            d.close();
        }
    }

    @Override
    public void openStationBoard(String stationId) {
        closeDrawer();
        boardView.showStation(stationId);
        if (activeTab != Tab.BOARD) {
            views.get(activeTab).onDeactivate();
            activeTab = Tab.BOARD;
            views.get(activeTab).onActivate();
        }
    }

    @Override
    public void openPlanWithStation(String stationId, boolean asOrigin) {
        closeDrawer();
        planView.setStation(stationId, asOrigin);
        if (activeTab != Tab.PLAN) {
            views.get(activeTab).onDeactivate();
            activeTab = Tab.PLAN;
            views.get(activeTab).onActivate();
        }
    }

    @Override
    public void enterHudPreviewMode() {
        closeDrawer();
        hudPreviewMode = true;
        draggingHud = false;
    }

    @Override
    public void closeWorkspace() {
        onClose();
    }

    @Override
    public void tick() {
        NewUiFixtures.refreshClock();
        WalkingClient.tick(activeTab == Tab.WALKING || tracking != null);
        views.get(activeTab).tick();
        if (activeDrawer != null) activeDrawer.tick();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        var ctx = new UiContext(mouseX, mouseY, delta, System.nanoTime());

        if (hudPreviewMode) {
            g.fill(0, 0, width, height, 0x60080C12);
            LcdNavigationHud.preview(g);
            CapsuleReminderHud.preview(g);
            var pair = UiBridge.hudLayout(true);
            var sel = hudView.editingReminder() ? pair.reminder() : pair.navigation();
            UiDraw.roundedOutline(g, (float) sel.x() - 2, (float) sel.y() - 2,
                    (float) sel.width() + 4, (float) sel.height() + 4, 6f, 1.5f, UiTokens.ACCENT_SKY);
            String hint = tr("mtrnav.ui.hud_drag_short");
            UiDraw.pill(g, hint, (width - UiDraw.font().width(hint) - 16) / 2, height - 54,
                    width - 20, UiTokens.HUD_BG, UiTokens.TEXT_PRIMARY);
            if (!draggingHud) exitHudPreviewBtn.render(g, ctx);
            ctx.flushTooltip(g, width);
            return;
        }

        // Subtle modern dark scrim
        g.fill(0, 0, width, height, UiTokens.SCRIM);

        // Direction C: Crafted Minecraft pixel-stepped deepslate outer frame + 100% pure modern interior
        renderMcOuterFrame(g, shellRect);

        // Modern interior backdrop + Left NavRail surface
        UiDraw.rect(g, innerShellRect.x(), innerShellRect.y(), innerShellRect.width(), innerShellRect.height(), UiTokens.SHELL_BG);
        UiDraw.rect(g, railRect.x(), railRect.y(), railRect.width(), railRect.height(), UiTokens.RAIL_BG);
        UiDraw.rect(g, railRect.right() - 1, railRect.y(), 1, railRect.height(), UiTokens.BORDER_SUBTLE);
        // Subtle 1px transit accent hairline along the top of the workspace header
        MinecraftGui.quadGradient(g,
                topBarRect.x(), topBarRect.y(), 0x7010B981,
                topBarRect.x(), topBarRect.y() + 1.5f, 0x7010B981,
                topBarRect.right(), topBarRect.y() + 1.5f, 0x7038BDF8,
                topBarRect.right(), topBarRect.y(), 0x7038BDF8);

        // Brand mark in rail top (official mod icon)
        if (railExpanded) {
            int ix = railRect.x() + 9;
            int iy = railRect.y() + 8;
            UiDraw.roundedRect(g, ix - 1, iy - 1, 20, 20, 4f, UiTokens.SURFACE_ELEVATED);
            MinecraftGui.blitFull(g, BRAND_ICON, ix, iy, 18, 18);
            UiDraw.roundedOutline(g, ix - 1, iy - 1, 20, 20, 4f, 1f, UiTokens.withAlpha(UiTokens.BORDER_DEFAULT, 180));
            UiDraw.bold(g, tr("mtrnav.ui.brand"), ix + 24, railRect.y() + 13, railRect.width() - 38, UiTokens.TEXT_PRIMARY);
        } else {
            int ix = railRect.centerX() - 9;
            int iy = railRect.y() + 8;
            UiDraw.roundedRect(g, ix - 1, iy - 1, 20, 20, 4f, UiTokens.SURFACE_ELEVATED);
            MinecraftGui.blitFull(g, BRAND_ICON, ix, iy, 18, 18);
            UiDraw.roundedOutline(g, ix - 1, iy - 1, 20, 20, 4f, 1f, UiTokens.withAlpha(UiTokens.BORDER_DEFAULT, 180));
        }
        UiDraw.rect(g, railRect.x() + 8, railRect.y() + 33, railRect.width() - 16, 1, UiTokens.BORDER_SUBTLE);

        // Render rail buttons + live activity indicators
        UiContext bgCtx = activeDrawer == null ? ctx : new UiContext(-1000, -1000, delta, ctx.nowNanos);
        for (Tab t : Tab.values()) {
            var btn = railButtons.get(t);
            btn.selected(t == activeTab);
            btn.render(g, bgCtx);
            if (t == Tab.ACTIVE && tracking != null) {
                UiDraw.circle(g, btn.bounds().right() - 6, btn.bounds().centerY(), 2.6f, UiTokens.STATUS_LIVE);
            }
            if (t == Tab.BOARD && reminder != null) {
                UiDraw.circle(g, btn.bounds().right() - 6, btn.bounds().centerY(), 2.6f, UiTokens.STATUS_WARN);
            }
        }

        // Rail footer connection badge
        if (railExpanded) {
            UiRect statusBox = new UiRect(railRect.x() + 8, railRect.bottom() - 34, railRect.width() - 16, 26);
            UiDraw.card(g, statusBox, UiTokens.RADIUS_SM, UiTokens.SURFACE, UiTokens.BORDER_SUBTLE);
            boolean ready = catalog != null;
            UiDraw.circle(g, statusBox.x() + 9, statusBox.y() + 9, 2.5f, ready ? UiTokens.STATUS_LIVE : UiTokens.STATUS_WARN);
            UiDraw.text(g, usingNative ? tr("mtrnav.ui.mode_native") : tr("mtrnav.ui.mode_server"),
                    statusBox.x() + 16, statusBox.y() + 5, statusBox.width() - 20, UiTokens.TEXT_PRIMARY);
            String dim = catalog == null ? "—" : catalog.dimension().replace("minecraft/", "");
            UiDraw.text(g, dim, statusBox.x() + 8, statusBox.y() + 15, statusBox.width() - 14, UiTokens.TEXT_MUTED);
        }

        // Top Bar
        UiDraw.rect(g, topBarRect.x(), topBarRect.bottom() - 1, topBarRect.width() - 1, 1, UiTokens.BORDER_SUBTLE);
        String pageTitle = switch (activeTab) {
            case PLAN -> tr("mtrnav.query_tab");
            case BOARD -> tr("mtrnav.departures");
            case ACTIVE -> tr("mtrnav.active_trip");
            case WALKING -> tr("mtrnav.walk_title");
            case HUD -> tr("mtrnav.hud_settings");
        };
        UiDraw.bold(g, pageTitle, topBarRect.x() + 12, topBarRect.y() + 11, Math.max(40, topBarRect.width() - 210), UiTokens.TEXT_PRIMARY);

        // Top-right status chips: Overlay sync + Fixture indicator
        int chipRight = closeBtn.bounds().x() - 6;
        if (UiBridge.overlayAvailable() && NavigationApi.current().isPresent()) {
            String sync = tr("mtrnav.ui.overlay_synced");
            int sw = UiDraw.font().width(sync) + 12;
            if (chipRight - sw > topBarRect.x() + 90) {
                UiDraw.pill(g, sync, chipRight - sw, topBarRect.y() + 8, sw,
                        UiTokens.withAlpha(UiTokens.PRIMARY, 48), UiTokens.STATUS_LIVE);
                chipRight -= sw + 6;
            }
        }
        if (NewUiFixtures.enabled()) {
            String fx = tr("mtrnav.ui.test_data") + " · " + NewUiFixtures.sceneLabel() + " [F7]";
            int fw = Math.min(180, UiDraw.font().width(fx) + 12);
            if (chipRight - fw > topBarRect.x() + 90) {
                UiDraw.pill(g, fx, chipRight - fw, topBarRect.y() + 8, fw,
                        UiTokens.withAlpha(UiTokens.STATUS_WARN, 48), UiTokens.STATUS_WARN);
            }
        }
        closeBtn.render(g, bgCtx);

        // Active View
        views.get(activeTab).render(g, bgCtx);

        // Slide-over Drawer (always on top of active view)
        if (activeDrawer != null) {
            activeDrawer.render(g, ctx);
        }

        ctx.flushTooltip(g, width);
    }

    /**
     * Direction C outer chassis: pixel-stepped Minecraft deepslate bezel matching assets/mtrnav/icon.png,
     * keeping the entire interior 100% clean modern flat UI with zero clashing bevels on small controls.
     */
    private static void renderMcOuterFrame(GuiGraphics g, UiRect r) {
        int x1 = r.x(), y1 = r.y(), x2 = r.right(), y2 = r.bottom();
        // Soft ambient drop shadow
        g.fill(x1 + 2, y2 + 1, x2 - 2, y2 + 3, 0x50000000);
        g.fill(x2 + 1, y1 + 2, x2 + 3, y2 - 2, 0x40000000);

        // Pixel-stepped dark outer contour (1px chamfered Minecraft corners)
        int dark = 0xF0080B10;
        g.fill(x1 + 2, y1 - 1, x2 - 2, y1, dark);
        g.fill(x1 + 2, y2, x2 - 2, y2 + 1, dark);
        g.fill(x1 - 1, y1 + 2, x1, y2 - 2, dark);
        g.fill(x2, y1 + 2, x2 + 1, y2 - 2, dark);
        // Step pixels on 4 corners
        g.fill(x1, y1, x1 + 2, y1 + 2, dark);
        g.fill(x2 - 2, y1, x2, y1 + 2, dark);
        g.fill(x1, y2 - 2, x1 + 2, y2, dark);
        g.fill(x2 - 2, y2 - 2, x2, y2, dark);

        // 3px machined deepslate bezel body
        int slate = 0xFF232B38;
        g.fill(x1 + 1, y1, x2 - 1, y1 + 3, slate);
        g.fill(x1 + 1, y2 - 3, x2 - 1, y2, slate);
        g.fill(x1, y1 + 1, x1 + 3, y2 - 1, slate);
        g.fill(x2 - 3, y1 + 1, x2, y2 - 1, slate);

        // Crisp 1px stone bevel edges (top/left catch-light, bottom/right shadow)
        int hi = 0xFF3E4C62;
        int lo = 0xFF131820;
        g.fill(x1 + 2, y1, x2 - 2, y1 + 1, hi);
        g.fill(x1, y1 + 2, x1 + 1, y2 - 2, hi);
        g.fill(x1 + 2, y2 - 1, x2 - 2, y2, lo);
        g.fill(x2 - 1, y1 + 2, x2, y2 - 2, lo);

        // Understated brushed steel-slate L-corner brackets (7px arms, matching icon.png DNA)
        int clasp = 0xFF5A6D88;
        int claspHi = 0xFF7B90AE;
        int arm = 8;
        // Top-left
        g.fill(x1 + 1, y1, x1 + arm, y1 + 2, clasp);
        g.fill(x1, y1 + 1, x1 + 2, y1 + arm, clasp);
        g.fill(x1 + 1, y1 + 1, x1 + 3, y1 + 2, claspHi);
        // Top-right
        g.fill(x2 - arm, y1, x2 - 1, y1 + 2, clasp);
        g.fill(x2 - 2, y1 + 1, x2, y1 + arm, clasp);
        g.fill(x2 - 3, y1 + 1, x2 - 1, y1 + 2, claspHi);
        // Bottom-left
        g.fill(x1 + 1, y2 - 2, x1 + arm, y2, clasp);
        g.fill(x1, y2 - arm, x1 + 2, y2 - 1, clasp);
        // Bottom-right
        g.fill(x2 - arm, y2 - 2, x2 - 1, y2, clasp);
        g.fill(x2 - 2, y2 - arm, x2, y2 - 1, clasp);

        // Inner 1px recessed groove cleanly separating bezel from modern interior
        int groove = 0xFF0A0D13;
        g.fill(x1 + 3, y1 + 3, x2 - 3, y1 + 4, groove);
        g.fill(x1 + 3, y2 - 4, x2 - 3, y2 - 3, groove);
        g.fill(x1 + 3, y1 + 3, x1 + 4, y2 - 3, groove);
        g.fill(x2 - 4, y1 + 3, x2 - 3, y2 - 3, groove);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (hudPreviewMode) {
            if (!draggingHud && exitHudPreviewBtn.mouseClicked(x, y, button)) return true;
            var pair = UiBridge.hudLayout(true);
            if (button == 0 && (pair.reminder().contains(x, y) || pair.navigation().contains(x, y))) {
                draggingReminder = pair.reminder().contains(x, y);
                hudView.setEditingReminder(draggingReminder);
                var p = draggingReminder ? pair.reminder() : pair.navigation();
                draggingHud = true;
                dragOffsetX = x - p.x();
                dragOffsetY = y - p.y();
                return true;
            }
            return false;
        }
        if (activeDrawer != null) return activeDrawer.mouseClicked(x, y, button);
        if (closeBtn.mouseClicked(x, y, button)) return true;
        for (var btn : railButtons.values()) if (btn.mouseClicked(x, y, button)) return true;
        return views.get(activeTab).mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (hudPreviewMode) {
            if (draggingHud && button == 0) {
                var pair = UiBridge.hudLayout(true);
                var p = draggingReminder ? pair.reminder() : pair.navigation();
                if (draggingReminder) UiBridge.setReminderPosition(p.relativeX(x - dragOffsetX), p.relativeY(y - dragOffsetY), false);
                else UiBridge.setHudPosition(p.relativeX(x - dragOffsetX), p.relativeY(y - dragOffsetY), false);
                return true;
            }
            return false;
        }
        if (activeDrawer != null) return activeDrawer.mouseDragged(x, y, button, dx, dy);
        return views.get(activeTab).mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (hudPreviewMode) {
            if (draggingHud && button == 0) {
                draggingHud = false;
                UiBridge.savePreferences();
                return true;
            }
            return exitHudPreviewBtn.mouseReleased(x, y, button);
        }
        if (activeDrawer != null) return activeDrawer.mouseReleased(x, y, button);
        boolean handled = closeBtn.mouseReleased(x, y, button);
        for (var btn : railButtons.values()) handled |= btn.mouseReleased(x, y, button);
        return views.get(activeTab).mouseReleased(x, y, button) || handled;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (hudPreviewMode) {
            var pair = UiBridge.hudLayout(true);
            if (vertical != 0 && (pair.reminder().contains(x, y) || pair.navigation().contains(x, y))) {
                boolean rem = pair.reminder().contains(x, y);
                hudView.setEditingReminder(rem);
                if (rem) UiBridge.changeReminderSize(vertical > 0 ? 1 : -1);
                else UiBridge.changeHudSize(vertical > 0 ? 1 : -1);
                return true;
            }
            return false;
        }
        if (activeDrawer != null) return activeDrawer.mouseScrolled(x, y, horizontal, vertical);
        return views.get(activeTab).mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (NewUiFixtures.enabled() && key == GLFW.GLFW_KEY_F7) {
            NewUiFixtures.cycleScene();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (hudPreviewMode) {
                hudPreviewMode = false;
                draggingHud = false;
                UiBridge.savePreferences();
                return true;
            }
            if (activeDrawer != null) {
                closeDrawer();
                return true;
            }
            onClose();
            return true;
        }
        boolean typing = activeDrawer != null ? activeDrawer.isTyping() : views.get(activeTab).isTyping();
        if (!typing && isOpenKey.test(key, scan)) {
            if (hudPreviewMode) {
                hudPreviewMode = false;
                draggingHud = false;
                UiBridge.savePreferences();
            } else {
                onClose();
            }
            return true;
        }
        if (activeDrawer != null) return activeDrawer.keyPressed(key, scan, modifiers);
        return views.get(activeTab).keyPressed(key, scan, modifiers) || super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (activeDrawer != null) return activeDrawer.charTyped(ch, modifiers);
        return views.get(activeTab).charTyped(ch, modifiers) || super.charTyped(ch, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void removed() {
        views.get(activeTab).onDeactivate();
        super.removed();
        var window = minecraft.getWindow();
        if (!Double.isNaN(enteredGuiScale)) window.setGuiScale(enteredGuiScale);
    }
}
