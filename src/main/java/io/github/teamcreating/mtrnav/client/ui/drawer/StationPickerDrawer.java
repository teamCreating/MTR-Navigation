package io.github.teamcreating.mtrnav.client.ui.drawer;

import io.github.teamcreating.mtrnav.client.UiBridge;
import io.github.teamcreating.mtrnav.client.ui.framework.SlideDrawer;
import io.github.teamcreating.mtrnav.client.ui.framework.UiButton;
import io.github.teamcreating.mtrnav.client.ui.framework.UiContext;
import io.github.teamcreating.mtrnav.client.ui.framework.UiTextInput;
import io.github.teamcreating.mtrnav.client.ui.theme.NavFormat;
import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiIcons;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.core.*;
import io.github.teamcreating.mtrnav.core.ui.ScrollState;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.Consumer;

import static io.github.teamcreating.mtrnav.client.NavigationClient.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

/**
 * Right-side searchable station picker drawer replacing StationDirectoryScreen.
 * Supports A-Z / Z-A / Nearest sorting, letter quick-jump, nearby confirmation, and bilingual names.
 */
public final class StationPickerDrawer extends SlideDrawer {
    private static final int ROW_H = 34;

    private final boolean nearbyOnly;
    private final Consumer<Station> onSelect;
    private final UiTextInput searchInput;
    private final UiButton sortBtn;
    private final UiButton relocateBtn;
    private final UiButton confirmBtn;
    private final UiButton cancelBtn;
    private final ScrollState scroll = new ScrollState();

    private StationDirectoryOrder.Mode order;
    private StationLocator.Position location;
    private Map<String, Double> distances = Map.of();
    private List<Station> matches = List.of();
    private Catalog lastCatalog;
    private String selectedId;
    private String lastClickId = "";
    private long lastClickNanos;
    private UiRect listRect = UiRect.EMPTY;

    public StationPickerDrawer(String title, String initialSelectedId, boolean nearbyOnly,
                               StationLocator.Position initialPosition, Consumer<Station> onSelect) {
        super(title, 300);
        this.selectedId = initialSelectedId == null ? "" : initialSelectedId;
        this.nearbyOnly = nearbyOnly;
        this.location = initialPosition;
        this.order = nearbyOnly ? StationDirectoryOrder.Mode.DISTANCE : StationDirectoryOrder.Mode.AZ;
        this.onSelect = onSelect;

        this.searchInput = new UiTextInput(tr("mtrnav.directory_search"))
                .icon(UiIcons.SEARCH)
                .placeholder(tr("mtrnav.station_hint"))
                .responder(ignored -> refilter());
        this.searchInput.setFocused(true);

        this.sortBtn = new UiButton(tr("mtrnav.directory_sort.az"), UiButton.Style.CHIP, () -> {
            var modes = StationDirectoryOrder.Mode.values();
            order = modes[(order.ordinal() + 1) % modes.length];
            if (order == StationDirectoryOrder.Mode.DISTANCE && location == null) {
                location = UiBridge.playerPosition();
            }
            refilter();
        });
        this.relocateBtn = new UiButton(tr("mtrnav.relocate"), UiButton.Style.CHIP, () -> {
            location = UiBridge.playerPosition();
            refilter();
        }).icon(UiIcons.LOCATE);
        this.confirmBtn = new UiButton(tr("mtrnav.walk_pick_station"), UiButton.Style.PRIMARY, this::commitSelected).icon(UiIcons.CHECK);
        this.cancelBtn = new UiButton(tr("mtrnav.back"), UiButton.Style.SECONDARY, this::close);
        refilter();
    }

    private void refilter() {
        lastCatalog = catalog;
        if (catalog == null) {
            matches = List.of();
            return;
        }
        matches = StationSearch.matches(catalog.stations(), searchInput.value());
        if (nearbyOnly || order == StationDirectoryOrder.Mode.DISTANCE) {
            var map = new HashMap<String, Double>();
            if (location != null && catalog.dimension().equals(location.dimension())) {
                for (var c : StationLocator.nearby(catalog, UiBridge.locatedStations(), location)) {
                    map.put(c.station().id(), c.distance());
                }
            }
            distances = Map.copyOf(map);
            if (nearbyOnly) matches = matches.stream().filter(s -> distances.containsKey(s.id())).toList();
        }
        matches = StationDirectoryOrder.sort(matches, order, distances);
        if (matches.stream().noneMatch(s -> s.id().equals(selectedId))) {
            selectedId = matches.size() == 1 ? matches.getFirst().id() : "";
        }
        scroll.reset();
    }

    private Station selectedStation() {
        return matches.stream().filter(s -> s.id().equals(selectedId)).findFirst().orElse(null);
    }

    private void commitSelected() {
        var st = selectedStation();
        if (st != null && onSelect != null) onSelect.accept(st);
    }

    @Override
    public void tick() {
        if (lastCatalog != catalog) refilter();
    }

    @Override
    protected void layoutContent(UiRect body, UiRect footer) {
        UiRect inner = body.inset(10, 8, 10, 8);
        searchInput.bounds(inner.sliceTop(22));
        int barY = inner.y() + 28;
        sortBtn.bounds(new UiRect(inner.x(), barY, 96, 18));
        relocateBtn.bounds(new UiRect(inner.x() + 100, barY, 84, 18));
        this.listRect = inner.trimTop(64);

        UiRect fIn = footer.inset(10, 7, 10, 7);
        int bw = (fIn.width() - 6) / 2;
        cancelBtn.bounds(new UiRect(fIn.x(), fIn.y(), bw, 22));
        confirmBtn.bounds(new UiRect(fIn.x() + bw + 6, fIn.y(), fIn.width() - bw - 6, 22));
    }

    @Override
    protected void renderContent(GuiGraphics g, UiContext ctx, UiRect body, UiRect footer) {
        searchInput.render(g, ctx);
        sortBtn.label(tr("mtrnav.directory_sort." + order.name().toLowerCase(Locale.ROOT))).render(g, ctx);
        if (nearbyOnly || order == StationDirectoryOrder.Mode.DISTANCE) {
            relocateBtn.render(g, ctx);
        }

        String countText = nearbyOnly
                ? tr("mtrnav.nearby_count", matches.size())
                : tr("mtrnav.directory_count", matches.size(), catalog == null ? 0 : catalog.stations().size());
        UiDraw.text(g, countText, listRect.x() + 2, listRect.y() - 13, listRect.width() - 4, UiTokens.TEXT_MUTED);

        UiDraw.card(g, listRect, UiTokens.RADIUS_MD, UiTokens.INSET, UiTokens.BORDER_SUBTLE);
        scroll.update(listRect.y() + 4, listRect.height() - 8, matches.size() * ROW_H, 14);
        UiDraw.enableScissor(g, listRect.inset(1));
        int rw = listRect.width() - (scroll.scrollable() ? 14 : 8);
        boolean showDist = nearbyOnly || order == StationDirectoryOrder.Mode.DISTANCE;

        for (int i = 0; i < matches.size(); i++) {
            int ry = listRect.y() + 4 + i * ROW_H - scroll.offset();
            if (ry + ROW_H < listRect.y() || ry > listRect.bottom()) continue;
            var st = matches.get(i);
            UiRect row = new UiRect(listRect.x() + 4, ry, rw, ROW_H - 3);
            boolean chosen = st.id().equals(selectedId);
            boolean hov = row.contains(ctx.mouseX, ctx.mouseY) && listRect.contains(ctx.mouseX, ctx.mouseY);

            UiDraw.card(g, row, UiTokens.RADIUS_SM,
                    chosen ? UiTokens.SURFACE_SELECTED : hov ? UiTokens.SURFACE_HOVER : UiTokens.SURFACE,
                    chosen ? UiTokens.BORDER_SELECTED : UiTokens.BORDER_SUBTLE);
            if (chosen) UiDraw.roundedRect(g, row.x() + 2, row.y() + 5, 3, row.height() - 10, 1.5f, UiTokens.ACCENT_SKY);

            int rightColW = 74;
            int leftW = Math.max(40, row.width() - rightColW - 16);
            UiDraw.bold(g, NavFormat.primary(st.name()), row.x() + 10, row.y() + 5, leftW, UiTokens.TEXT_PRIMARY);
            String en = NavFormat.secondary(st.name());
            if (!en.isEmpty()) UiDraw.text(g, en, row.x() + 10, row.y() + 17, leftW, UiTokens.TEXT_MUTED);

            int rx = row.right() - rightColW - 4;
            if (showDist) {
                String d = distances.containsKey(st.id())
                        ? tr("mtrnav.location_distance", Math.round(distances.get(st.id())))
                        : tr("mtrnav.directory_unknown_distance");
                UiDraw.text(g, d, rx, row.y() + 5, rightColW, UiTokens.STATUS_LIVE);
                UiDraw.text(g, st.x() + ", " + st.z(), rx, row.y() + 17, rightColW, UiTokens.TEXT_MUTED);
            } else {
                UiDraw.text(g, st.x() + ", " + st.y() + ", " + st.z(), rx, row.y() + 11, rightColW, UiTokens.TEXT_MUTED);
            }
        }
        if (matches.isEmpty()) {
            UiDraw.text(g, tr(catalog == null ? "mtrnav.connecting" : "mtrnav.directory_empty"),
                    listRect.x() + 10, listRect.y() + 12, listRect.width() - 20, UiTokens.TEXT_MUTED);
        }
        g.disableScissor();
        UiDraw.scrollbar(g, scroll, listRect.right() - 7, 4);

        cancelBtn.render(g, ctx);
        confirmBtn.enabled(selectedStation() != null).render(g, ctx);
    }

    @Override
    protected boolean onMouseClicked(double x, double y, int button) {
        if (searchInput.mouseClicked(x, y, button)) return true;
        if (sortBtn.mouseClicked(x, y, button)) return true;
        if ((nearbyOnly || order == StationDirectoryOrder.Mode.DISTANCE) && relocateBtn.mouseClicked(x, y, button)) return true;
        if (cancelBtn.mouseClicked(x, y, button) || confirmBtn.mouseClicked(x, y, button)) return true;
        if (button == 0 && scroll.beginDrag(x, y, listRect.right() - 7, 4)) return true;
        if (button == 0 && listRect.contains(x, y)) {
            int idx = (int) ((y - (listRect.y() + 4) + scroll.offset()) / ROW_H);
            if (idx >= 0 && idx < matches.size()) {
                var st = matches.get(idx);
                selectedId = st.id();
                searchInput.setFocused(false);
                long now = System.nanoTime();
                boolean dbl = selectedId.equals(lastClickId) && now - lastClickNanos < 320_000_000L;
                lastClickId = selectedId;
                lastClickNanos = now;
                if (dbl) commitSelected();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return button == 0 && scroll.drag(y);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return sortBtn.mouseReleased(x, y, button)
                | relocateBtn.mouseReleased(x, y, button)
                | cancelBtn.mouseReleased(x, y, button)
                | confirmBtn.mouseReleased(x, y, button)
                | scroll.endDrag();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return listRect.contains(x, y) && scroll.scrollBy(vertical, ROW_H);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (selectedStation() != null) { commitSelected(); return true; }
        }
        if ((key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) && !matches.isEmpty()) {
            int cur = -1;
            for (int i = 0; i < matches.size(); i++) if (matches.get(i).id().equals(selectedId)) { cur = i; break; }
            int next = Math.clamp(cur < 0 ? (key == GLFW.GLFW_KEY_DOWN ? 0 : matches.size() - 1) : cur + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1),
                    0, matches.size() - 1);
            selectedId = matches.get(next).id();
            scroll.ensureVisible(next * ROW_H, ROW_H);
            return true;
        }
        return searchInput.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        return searchInput.charTyped(ch, modifiers);
    }

    @Override
    public boolean isTyping() {
        return searchInput.focused();
    }
}
