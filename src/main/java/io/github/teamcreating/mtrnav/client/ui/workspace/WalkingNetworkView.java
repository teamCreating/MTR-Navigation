package io.github.teamcreating.mtrnav.client.ui.workspace;

import io.github.teamcreating.mtrnav.client.WalkingClient;
import io.github.teamcreating.mtrnav.client.ui.drawer.ConfirmDrawer;
import io.github.teamcreating.mtrnav.client.ui.drawer.TextPromptDrawer;
import io.github.teamcreating.mtrnav.client.ui.drawer.WalkingEditorDrawer;
import io.github.teamcreating.mtrnav.client.ui.framework.*;
import io.github.teamcreating.mtrnav.client.ui.framework.UiToggle;
import io.github.teamcreating.mtrnav.client.ui.theme.NavFormat;
import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiIcons;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.core.*;
import io.github.teamcreating.mtrnav.core.ui.ScrollState;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;

import static io.github.teamcreating.mtrnav.client.NavigationClient.*;

/**
 * Unified Walking Network & Query Settings view replacing WalkingManagementScreen,
 * WalkingListScreen, WalkingSettingsScreen, and WalkingTextScreen.
 */
public final class WalkingNetworkView implements UiView {
    private static final int ROW_H = 42;
    private record RowAction(UiRect rect, Runnable action, String label, int style) {} // 0=sec, 1=primary, 2=danger

    private final WorkspaceHost host;
    private final ScrollState scroll = new ScrollState();
    private final UiTextInput searchInput;
    private final List<UiButton> subTabButtons = new ArrayList<>();
    private final UiButton newLinkBtn;
    private final UiButton refreshBtn;
    private final UiButton exportBtn;
    private final UiButton prevPageBtn;
    private final UiButton nextPageBtn;

    // Settings tab controls
    private final UiToggle consentToggle;
    private final List<UiButton> windowButtons = new ArrayList<>();
    private static final int[] WINDOWS = {15, 30, 60, -1};

    private int subTab; // 0=shared, 1=personal, 2=my proposals, 3=moderation, 4=settings
    private UiRect bounds = UiRect.EMPTY;
    private UiRect listRect = UiRect.EMPTY;
    private UiRect settingsRect = UiRect.EMPTY;
    private final List<RowAction> rowActions = new ArrayList<>();

    public WalkingNetworkView(WorkspaceHost host) {
        this.host = host;
        this.subTab = WalkingClient.supportsShared() ? 0 : 1;
        this.searchInput = new UiTextInput(tr("mtrnav.directory_search"))
                .icon(UiIcons.SEARCH)
                .placeholder(tr("mtrnav.walk_layout.search"))
                .responder(ignored -> scroll.reset());

        for (int i = 0; i < 5; i++) {
            final int idx = i;
            String lbl = i < 4 ? tr("mtrnav.walk_tab." + i) : tr("mtrnav.walk_layout.settings");
            subTabButtons.add(new UiButton(lbl, UiButton.Style.CHIP, () -> switchSubTab(idx)));
        }

        this.newLinkBtn = new UiButton("+ " + tr("mtrnav.walk_new"), UiButton.Style.PRIMARY,
                () -> host.openDrawer(new WalkingEditorDrawer(host, null, null)));
        this.refreshBtn = new UiButton(tr("mtrnav.walk_refresh"), UiButton.Style.SECONDARY, WalkingClient::refresh);
        this.exportBtn = new UiButton(tr("mtrnav.walk_export"), UiButton.Style.SECONDARY, WalkingClient::exportLocal);
        this.prevPageBtn = new UiButton("←", UiButton.Style.SECONDARY,
                () -> WalkingClient.page = Math.max(0, WalkingClient.page - 1));
        this.nextPageBtn = new UiButton("→", UiButton.Style.SECONDARY,
                () -> WalkingClient.page = Math.min(WalkingClient.proposalPages() - 1, WalkingClient.page + 1));

        this.consentToggle = new UiToggle(tr("mtrnav.walk_consent_title"), () -> {
            if (WalkingClient.consent()) {
                WalkingClient.consent(false);
            } else {
                host.openDrawer(new ConfirmDrawer(
                        tr("mtrnav.walk_consent_title"),
                        tr("mtrnav.walk_consent_explanation"),
                        tr("mtrnav.walk_consent_agree"),
                        tr("mtrnav.walk_consent_decline"),
                        () -> WalkingClient.consent(true),
                        () -> WalkingClient.consent(false)));
            }
        });
        for (int w : WINDOWS) {
            String lbl = w < 0 ? tr("mtrnav.walk_unlimited") : tr("mtrnav.walk_layout.minutes", w);
            windowButtons.add(new UiButton(lbl, UiButton.Style.CHIP, () -> WalkingClient.setWindow(w)));
        }
    }

    private void switchSubTab(int next) {
        subTab = next;
        WalkingClient.reviewMode = subTab == 3;
        WalkingClient.page = 0;
        scroll.reset();
        if (subTab < 4 && WalkingClient.supportsShared()) WalkingClient.refresh();
    }

    @Override
    public void onActivate() {
        if (!WalkingClient.supportsShared() && subTab == 0) subTab = 1;
        if (WalkingClient.supportsShared()) WalkingClient.refresh();
    }

    @Override
    public void layout(UiRect bounds) {
        this.bounds = bounds;
        UiRect inner = bounds.inset(12, 10, 12, 10);

        int tx = inner.x();
        int ty = inner.y();
        for (int i = 0; i < subTabButtons.size(); i++) {
            if (i == 3 && !WalkingClient.admin) {
                subTabButtons.get(i).bounds(UiRect.EMPTY);
                continue;
            }
            String lbl = i < 4 ? tr("mtrnav.walk_tab." + i) : tr("mtrnav.walk_layout.settings");
            int bw = Math.min(96, UiDraw.font().width(lbl) + 16);
            subTabButtons.get(i).bounds(new UiRect(tx, ty, bw, 19));
            tx += bw + 4;
        }

        int toolY = inner.y() + 26;
        int rightBtnsW = subTab == 1 ? 156 : 76;
        searchInput.bounds(new UiRect(inner.x(), toolY, Math.max(80, inner.width() - rightBtnsW - 8), 20));
        if (subTab == 1) {
            exportBtn.bounds(new UiRect(inner.right() - 156, toolY, 74, 20));
            newLinkBtn.bounds(new UiRect(inner.right() - 78, toolY, 78, 20));
            refreshBtn.bounds(UiRect.EMPTY);
        } else {
            refreshBtn.bounds(new UiRect(inner.right() - 76, toolY, 76, 20));
            exportBtn.bounds(UiRect.EMPTY);
            newLinkBtn.bounds(UiRect.EMPTY);
        }

        this.listRect = new UiRect(inner.x(), toolY + 28, inner.width(), Math.max(0, inner.bottom() - (toolY + 28) - 22));
        this.settingsRect = new UiRect(inner.x(), inner.y() + 28, inner.width(), Math.max(0, inner.bottom() - (inner.y() + 28) - 22));

        prevPageBtn.bounds(new UiRect(inner.right() - 90, inner.bottom() - 18, 24, 18));
        nextPageBtn.bounds(new UiRect(inner.right() - 62, inner.bottom() - 18, 62, 18));
    }

    @Override
    public void render(GuiGraphics g, UiContext ctx) {
        if (subTab == 3 && !WalkingClient.admin) subTab = 2;
        layout(bounds);

        for (int i = 0; i < subTabButtons.size(); i++) {
            if (i == 3 && !WalkingClient.admin) continue;
            subTabButtons.get(i).selected(subTab == i).render(g, ctx);
        }

        if (subTab == 4) {
            renderSettings(g, ctx);
        } else {
            renderList(g, ctx);
        }

        // Bottom status strip
        String statusKey = "mtrnav.walk_message." + (WalkingClient.message.isEmpty() ? "ready" : WalkingClient.message);
        UiDraw.text(g, tr(statusKey), bounds.x() + 12, bounds.bottom() - 22, bounds.width() - 120, UiTokens.TEXT_MUTED);
        if ((subTab == 2 || subTab == 3) && WalkingClient.supportsShared()) {
            prevPageBtn.render(g, ctx);
            nextPageBtn.label((WalkingClient.page + 1) + "/" + WalkingClient.proposalPages()).render(g, ctx);
        }
    }

    private void renderSettings(GuiGraphics g, UiContext ctx) {
        UiDraw.card(g, settingsRect, UiTokens.RADIUS_MD, UiTokens.INSET, UiTokens.BORDER_SUBTLE);
        UiRect inner = settingsRect.inset(12, 10, 12, 10);
        int x = inner.x(), w = inner.width(), y = inner.y();

        // Privacy card
        UiRect card1 = new UiRect(x, y, w, 92);
        UiDraw.card(g, card1, UiTokens.RADIUS_MD, UiTokens.SURFACE, UiTokens.BORDER_SUBTLE);
        UiDraw.bold(g, tr("mtrnav.walk_layout.privacy_title"), x + 10, y + 8, w - 20, UiTokens.ACCENT_SKY);
        int py = UiDraw.wrapped(g, tr("mtrnav.walk_layout.privacy"), x + 10, y + 22, w - 20, 11, UiTokens.TEXT_SECONDARY);
        consentToggle.bounds(new UiRect(x + 8, Math.min(card1.bottom() - 28, py + 6), w - 16, 22))
                .checked(WalkingClient.consent())
                .label(tr("mtrnav.walk_consent", tr(WalkingClient.consent() ? "mtrnav.on" : "mtrnav.off")))
                .render(g, ctx);

        // Late-arrival window card
        int y2 = card1.bottom() + 10;
        UiRect card2 = new UiRect(x, y2, w, Math.min(116, Math.max(82, inner.bottom() - y2)));
        UiDraw.card(g, card2, UiTokens.RADIUS_MD, UiTokens.SURFACE, UiTokens.BORDER_SUBTLE);
        UiDraw.bold(g, tr("mtrnav.walk_layout.window_title"), x + 10, y2 + 8, w - 20, UiTokens.ACCENT_SKY);
        int wy = UiDraw.wrapped(g, tr("mtrnav.walk_layout.window_hint"), x + 10, y2 + 22, w - 20, 11, UiTokens.TEXT_SECONDARY) + 6;
        int bw = (w - 32) / 4;
        int curExtra = WalkingClient.options().extraMinutes();
        for (int i = 0; i < windowButtons.size(); i++) {
            windowButtons.get(i).bounds(new UiRect(x + 10 + i * (bw + 4), wy, bw, 20))
                    .selected(curExtra == WINDOWS[i]).render(g, ctx);
        }
        UiDraw.wrapped(g, tr("mtrnav.walk_layout.window_changed"), x + 10, wy + 26, w - 20, 11, UiTokens.TEXT_MUTED);
    }

    private void renderList(GuiGraphics g, UiContext ctx) {
        rowActions.clear();
        searchInput.render(g, ctx);
        if (subTab == 1) {
            exportBtn.render(g, ctx);
            newLinkBtn.render(g, ctx);
        } else {
            refreshBtn.render(g, ctx);
        }

        UiDraw.card(g, listRect, UiTokens.RADIUS_MD, UiTokens.INSET, UiTokens.BORDER_SUBTLE);
        String needle = searchInput.value().toLowerCase(Locale.ROOT);

        if (subTab == 1) {
            var rows = WalkingClient.local().stream()
                    .filter(c -> matchesSearch(c, needle)).toList();
            renderConnections(g, ctx, rows, null);
        } else if (!WalkingClient.supportsShared()) {
            UiDraw.wrapped(g, tr("mtrnav.walk_message.server_unavailable") + "\n" + tr("mtrnav.walk_personal_available"),
                    listRect.x() + 12, listRect.y() + 14, listRect.width() - 24, 13, UiTokens.TEXT_MUTED);
        } else if (subTab == 0) {
            var rows = WalkingClient.shared.stream()
                    .filter(e -> matchesSearch(e.connection(), needle)).toList();
            renderConnections(g, ctx, rows.stream().map(WalkingLibrary.Entry::connection).toList(), rows);
        } else {
            var player = Minecraft.getInstance().player;
            var rows = WalkingClient.proposals().stream()
                    .filter(p -> subTab == 3 || (player != null && p.author().equals(player.getUUID())))
                    .filter(p -> matchesSearch(p.value(), needle))
                    .toList();
            renderProposals(g, ctx, rows);
        }
    }

    private void renderConnections(GuiGraphics g, UiContext ctx, List<WalkingConnection> rows, List<WalkingLibrary.Entry> sharedEntries) {
        if (rows.isEmpty()) {
            UiDraw.text(g, tr("mtrnav.walk_layout.empty." + subTab), listRect.x() + 12, listRect.y() + 14, listRect.width() - 24, UiTokens.TEXT_MUTED);
            return;
        }
        scroll.update(listRect.y() + 4, listRect.height() - 8, rows.size() * ROW_H, 14);
        UiDraw.enableScissor(g, listRect.inset(1));
        int rw = listRect.width() - (scroll.scrollable() ? 14 : 8);

        for (int i = 0; i < rows.size(); i++) {
            int ry = listRect.y() + 4 + i * ROW_H - scroll.offset();
            if (ry + ROW_H < listRect.y() || ry > listRect.bottom()) continue;
            var c = rows.get(i);
            var pub = sharedEntries == null ? null : sharedEntries.get(i);
            UiRect r = new UiRect(listRect.x() + 4, ry, rw, ROW_H - 4);
            UiDraw.card(g, r, UiTokens.RADIUS_SM, UiTokens.SURFACE, UiTokens.BORDER_SUBTLE);
            UiDraw.roundedRect(g, r.x() + 3, r.y() + 5, 3, r.height() - 10, 1.5f,
                    c.enabled() ? UiTokens.PRIMARY : UiTokens.TEXT_MUTED);

            int actionsWidth = pub == null ? 156 : (WalkingClient.admin ? 104 : 52);
            int textW = Math.max(40, r.width() - actionsWidth - 18);
            UiDraw.bold(g, linkTitle(c), r.x() + 12, r.y() + 6, textW, UiTokens.TEXT_PRIMARY);
            UiDraw.text(g, linkDetail(c) + (pub == null ? "" : " · r" + pub.revision()), r.x() + 12, r.y() + 21, textW, UiTokens.TEXT_SECONDARY);

            int ax = r.right() - 6;
            int ay = r.centerY() - 9;
            if (pub == null) {
                ax = addInlineAction(g, ctx, ax, ay, 42, tr("mtrnav.close"), 2, () -> host.openDrawer(new ConfirmDrawer(
                        tr("mtrnav.walk_delete_local"), linkDetail(c), tr("mtrnav.walk_confirm"), tr("mtrnav.back"),
                        () -> WalkingClient.deleteLocal(c.id()), null)));
                ax = addInlineAction(g, ctx, ax, ay, 48, tr("mtrnav.walk_edit").substring(0, Math.min(2, tr("mtrnav.walk_edit").length())), 0,
                        () -> host.openDrawer(new WalkingEditorDrawer(host, c, null)));
                addInlineAction(g, ctx, ax, ay, 52, tr(c.enabled() ? "mtrnav.on" : "mtrnav.off"), c.enabled() ? 1 : 0, () -> {
                    try { if (!c.enabled()) c.validate(catalog); WalkingClient.putLocal(List.of(c.enabled(!c.enabled()))); }
                    catch (RuntimeException e) { WalkingClient.message = "invalid_connection"; }
                });
            } else {
                if (WalkingClient.admin) {
                    ax = addInlineAction(g, ctx, ax, ay, 48, tr("mtrnav.close"), 2, () -> host.openDrawer(new ConfirmDrawer(
                            tr("mtrnav.walk_remove_shared"), linkDetail(c), tr("mtrnav.walk_confirm"), tr("mtrnav.back"),
                            () -> WalkingClient.change(new WalkingLibrary.Command("remove", c.id(), pub.revision(), null, "")), null)));
                }
                addInlineAction(g, ctx, ax, ay, 48, tr("mtrnav.walk_edit").substring(0, Math.min(2, tr("mtrnav.walk_edit").length())), 0,
                        () -> host.openDrawer(new WalkingEditorDrawer(host, c, null)));
            }
        }
        g.disableScissor();
        UiDraw.scrollbar(g, scroll, listRect.right() - 7, 4);
    }

    private void renderProposals(GuiGraphics g, UiContext ctx, List<WalkingLibrary.Proposal> rows) {
        if (rows.isEmpty()) {
            UiDraw.text(g, tr("mtrnav.walk_layout.empty." + subTab), listRect.x() + 12, listRect.y() + 14, listRect.width() - 24, UiTokens.TEXT_MUTED);
            return;
        }
        scroll.update(listRect.y() + 4, listRect.height() - 8, rows.size() * ROW_H, 14);
        UiDraw.enableScissor(g, listRect.inset(1));
        int rw = listRect.width() - (scroll.scrollable() ? 14 : 8);

        for (int i = 0; i < rows.size(); i++) {
            int ry = listRect.y() + 4 + i * ROW_H - scroll.offset();
            if (ry + ROW_H < listRect.y() || ry > listRect.bottom()) continue;
            var p = rows.get(i);
            UiRect r = new UiRect(listRect.x() + 4, ry, rw, ROW_H - 4);
            UiDraw.card(g, r, UiTokens.RADIUS_SM, UiTokens.SURFACE, UiTokens.BORDER_SUBTLE);

            int textW = Math.max(40, r.width() - 165);
            String statusTxt = tr("mtrnav.walk_status." + p.status().name().toLowerCase(Locale.ROOT));
            UiDraw.bold(g, statusTxt + " · " + linkTitle(p.value()), r.x() + 10, r.y() + 6, textW, UiTokens.TEXT_PRIMARY);
            UiDraw.text(g, linkDetail(p.value()) + (p.reason().isEmpty() ? "" : " · " + p.reason()), r.x() + 10, r.y() + 21, textW, UiTokens.TEXT_SECONDARY);

            if (p.status() == WalkingLibrary.Status.PENDING) {
                int ax = r.right() - 6;
                int ay = r.centerY() - 9;
                if (WalkingClient.admin) {
                    ax = addInlineAction(g, ctx, ax, ay, 46, tr("mtrnav.walk_reject").substring(0, Math.min(2, tr("mtrnav.walk_reject").length())), 2,
                            () -> host.openDrawer(new TextPromptDrawer(tr("mtrnav.walk_reject"), tr("mtrnav.walk_reason"), 256,
                                    reason -> WalkingClient.change(new WalkingLibrary.Command("reject", p.id(), p.revision(), null, reason)))));
                    ax = addInlineAction(g, ctx, ax, ay, 48, tr("mtrnav.walk_approve").substring(0, Math.min(2, tr("mtrnav.walk_approve").length())), 1,
                            () -> host.openDrawer(new ConfirmDrawer(tr("mtrnav.walk_approve"), linkDetail(p.value()), tr("mtrnav.walk_confirm"), tr("mtrnav.back"),
                                    () -> WalkingClient.change(new WalkingLibrary.Command("approve", p.id(), p.revision(), null, "")), null)));
                    addInlineAction(g, ctx, ax, ay, 44, tr("mtrnav.walk_edit").substring(0, Math.min(2, tr("mtrnav.walk_edit").length())), 0,
                            () -> host.openDrawer(new WalkingEditorDrawer(host, p.value(), p)));
                } else if (Minecraft.getInstance().player != null && p.author().equals(Minecraft.getInstance().player.getUUID())) {
                    addInlineAction(g, ctx, ax, ay, 56, tr("mtrnav.walk_withdraw").substring(0, Math.min(2, tr("mtrnav.walk_withdraw").length())), 2,
                            () -> host.openDrawer(new ConfirmDrawer(tr("mtrnav.walk_withdraw"), linkTitle(p.value()), tr("mtrnav.walk_confirm"), tr("mtrnav.back"),
                                    () -> WalkingClient.change(new WalkingLibrary.Command("withdraw", p.id(), p.revision(), null, "")), null)));
                }
            }
        }
        g.disableScissor();
        UiDraw.scrollbar(g, scroll, listRect.right() - 7, 4);
    }

    private int addInlineAction(GuiGraphics g, UiContext ctx, int rightX, int y, int width, String label, int style, Runnable action) {
        UiRect r = new UiRect(rightX - width, y, width, 18);
        boolean hov = r.contains(ctx.mouseX, ctx.mouseY) && listRect.contains(ctx.mouseX, ctx.mouseY);
        int bg = style == 1 ? (hov ? UiTokens.PRIMARY_HOVER : UiTokens.PRIMARY)
                : style == 2 ? (hov ? 0xFFEF4444 : UiTokens.withAlpha(0xFFDC2626, 170))
                : (hov ? UiTokens.SURFACE_ELEVATED : UiTokens.INSET);
        int ink = style == 1 ? UiTokens.PRIMARY_INK : UiTokens.TEXT_PRIMARY;
        UiDraw.card(g, r, 4f, bg, style == 0 ? UiTokens.BORDER_DEFAULT : 0);
        int tw = Math.min(r.width() - 6, UiDraw.font().width(label));
        UiDraw.text(g, label, r.centerX() - tw / 2, r.y() + 5, r.width() - 4, ink);
        UiRect clipped = r.intersect(listRect);
        if (!clipped.isEmpty()) rowActions.add(new RowAction(clipped, action, label, style));
        return r.x() - 4;
    }

    private static boolean matchesSearch(WalkingConnection c, String needle) {
        if (needle.isEmpty()) return true;
        return (linkTitle(c) + " " + linkDetail(c) + " " + c.description()).toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String linkTitle(WalkingConnection c) {
        return c.name().isBlank() ? NavFormat.station(c.fromStation()) + " → " + NavFormat.station(c.toStation()) : c.name();
    }

    private static String linkDetail(WalkingConnection c) {
        return NavFormat.station(c.fromStation())
                + (c.fromPlatform().isEmpty() ? "" : " / " + NavFormat.platform(c.fromPlatform()))
                + " → " + NavFormat.station(c.toStation())
                + (c.toPlatform().isEmpty() ? "" : " / " + NavFormat.platform(c.toPlatform()))
                + " · " + tr("mtrnav.walk_seconds", c.seconds())
                + (c.enabled() ? "" : " · " + tr("mtrnav.walk_disabled"));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        for (int i = 0; i < subTabButtons.size(); i++) {
            if (i == 3 && !WalkingClient.admin) continue;
            if (subTabButtons.get(i).mouseClicked(x, y, button)) return true;
        }
        if (subTab == 4) {
            if (consentToggle.mouseClicked(x, y, button)) return true;
            for (var b : windowButtons) if (b.mouseClicked(x, y, button)) return true;
            return false;
        }
        if (searchInput.mouseClicked(x, y, button)) return true;
        if (subTab == 1 && (exportBtn.mouseClicked(x, y, button) || newLinkBtn.mouseClicked(x, y, button))) return true;
        if (subTab != 1 && refreshBtn.mouseClicked(x, y, button)) return true;
        if ((subTab == 2 || subTab == 3) && WalkingClient.supportsShared()
                && (prevPageBtn.mouseClicked(x, y, button) || nextPageBtn.mouseClicked(x, y, button))) return true;
        if (button == 0 && scroll.beginDrag(x, y, listRect.right() - 7, 4)) return true;
        if (button == 0) {
            for (var act : rowActions) {
                if (act.rect().contains(x, y)) { act.action().run(); return true; }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return button == 0 && scroll.drag(y);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        boolean handled = newLinkBtn.mouseReleased(x, y, button)
                | refreshBtn.mouseReleased(x, y, button)
                | exportBtn.mouseReleased(x, y, button)
                | prevPageBtn.mouseReleased(x, y, button)
                | nextPageBtn.mouseReleased(x, y, button)
                | scroll.endDrag();
        for (var b : subTabButtons) handled |= b.mouseReleased(x, y, button);
        for (var b : windowButtons) handled |= b.mouseReleased(x, y, button);
        return handled;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return subTab != 4 && listRect.contains(x, y) && scroll.scrollBy(vertical, ROW_H);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        return subTab != 4 && searchInput.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        return subTab != 4 && searchInput.charTyped(ch, modifiers);
    }

    @Override
    public boolean isTyping() {
        return subTab != 4 && searchInput.focused();
    }
}
