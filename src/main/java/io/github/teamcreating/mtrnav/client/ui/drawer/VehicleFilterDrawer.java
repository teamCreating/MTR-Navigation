package io.github.teamcreating.mtrnav.client.ui.drawer;

import io.github.teamcreating.mtrnav.client.UiBridge;
import io.github.teamcreating.mtrnav.client.ui.framework.SlideDrawer;
import io.github.teamcreating.mtrnav.client.ui.framework.UiButton;
import io.github.teamcreating.mtrnav.client.ui.framework.UiContext;
import io.github.teamcreating.mtrnav.client.ui.framework.UiTextInput;
import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiIcons;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.client.ui.workspace.NewUiFixtures;
import io.github.teamcreating.mtrnav.core.Model.*;
import io.github.teamcreating.mtrnav.core.ui.ScrollState;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;

import static io.github.teamcreating.mtrnav.client.NavigationClient.*;

/**
 * Right-side slide-over vehicle condition drawer replacing VehicleSelectionScreen.
 */
public final class VehicleFilterDrawer extends SlideDrawer {
    private static final int ROW_H = 32;

    private final Set<String> selected = new LinkedHashSet<>();
    private VehicleMode mode;
    private final List<UiButton> modeButtons = new ArrayList<>();
    private final UiTextInput searchInput;
    private final UiButton clearBtn;
    private final UiButton applyBtn;
    private final ScrollState scroll = new ScrollState();
    private UiRect listRect = UiRect.EMPTY;
    private List<String> filteredIds = List.of();

    public VehicleFilterDrawer() {
        super(tr("mtrnav.vehicle_filter"), 300);
        UiBridge.refreshVehicles();
        selected.addAll(vehicleCondition.models());
        mode = vehicleCondition.mode();

        for (var value : VehicleMode.values()) {
            modeButtons.add(new UiButton(tr("mtrnav.vehicle_mode." + value.name().toLowerCase(Locale.ROOT)),
                    UiButton.Style.CHIP, () -> mode = value));
        }
        this.searchInput = new UiTextInput(tr("mtrnav.search"))
                .icon(UiIcons.SEARCH)
                .placeholder(tr("mtrnav.vehicle_search"))
                .responder(ignored -> refilter());
        this.clearBtn = new UiButton(tr("mtrnav.vehicle_clear"), UiButton.Style.SECONDARY, () -> {
            selected.clear();
            mode = VehicleMode.NONE;
        });
        this.applyBtn = new UiButton(tr("mtrnav.vehicle_apply"), UiButton.Style.PRIMARY, () -> {
            vehicleCondition(new VehicleCondition(mode, List.copyOf(selected)));
            close();
        }).icon(UiIcons.CHECK);
        refilter();
    }

    private boolean vehicleSupported() {
        return NewUiFixtures.enabled() || supportsVehicles();
    }

    private void refilter() {
        var ids = new TreeSet<>(fleet);
        ids.addAll(selected);
        String needle = searchInput.value().toLowerCase(Locale.ROOT);
        filteredIds = ids.stream().filter(id -> {
            var info = UiBridge.vehicleInfo(id);
            return (id + " " + info.name() + " " + info.description() + " " + info.tags()).toLowerCase(Locale.ROOT).contains(needle);
        }).toList();
        scroll.reset();
    }

    @Override
    protected void layoutContent(UiRect body, UiRect footer) {
        UiRect inner = body.inset(10, 8, 10, 8);
        int bw = (inner.width() - 8) / 3;
        for (int i = 0; i < modeButtons.size(); i++) {
            modeButtons.get(i).bounds(new UiRect(inner.x() + i * (bw + 4), inner.y(), bw, 20));
        }
        searchInput.bounds(new UiRect(inner.x(), inner.y() + 26, inner.width(), 22));
        this.listRect = new UiRect(inner.x(), inner.y() + 56, inner.width(), Math.max(0, inner.height() - 72));

        UiRect fIn = footer.inset(10, 7, 10, 7);
        int fw = (fIn.width() - 6) / 2;
        clearBtn.bounds(new UiRect(fIn.x(), fIn.y(), fw, 22));
        applyBtn.bounds(new UiRect(fIn.x() + fw + 6, fIn.y(), fIn.width() - fw - 6, 22));
    }

    @Override
    protected void renderContent(GuiGraphics g, UiContext ctx, UiRect body, UiRect footer) {
        boolean supported = vehicleSupported();
        for (int i = 0; i < modeButtons.size(); i++) {
            modeButtons.get(i).selected(mode.ordinal() == i).enabled(supported || i == 0).render(g, ctx);
        }
        searchInput.render(g, ctx);

        UiDraw.card(g, listRect, UiTokens.RADIUS_MD, UiTokens.INSET, UiTokens.BORDER_SUBTLE);
        scroll.update(listRect.y() + 4, listRect.height() - 8, filteredIds.size() * ROW_H, 14);

        UiDraw.enableScissor(g, listRect.inset(1));
        int rw = listRect.width() - (scroll.scrollable() ? 14 : 8);
        for (int i = 0; i < filteredIds.size(); i++) {
            int ry = listRect.y() + 4 + i * ROW_H - scroll.offset();
            if (ry + ROW_H < listRect.y() || ry > listRect.bottom()) continue;
            String id = filteredIds.get(i);
            boolean checked = selected.contains(id);
            UiRect row = new UiRect(listRect.x() + 4, ry, rw, ROW_H - 3);
            boolean hov = supported && row.contains(ctx.mouseX, ctx.mouseY) && listRect.contains(ctx.mouseX, ctx.mouseY);
            UiDraw.card(g, row, UiTokens.RADIUS_SM,
                    checked ? UiTokens.SURFACE_SELECTED : hov ? UiTokens.SURFACE_HOVER : UiTokens.SURFACE,
                    checked ? UiTokens.BORDER_SELECTED : UiTokens.BORDER_SUBTLE);

            UiRect box = new UiRect(row.x() + 8, row.centerY() - 6, 12, 12);
            UiDraw.card(g, box, 3f, checked ? UiTokens.PRIMARY : UiTokens.INSET,
                    checked ? UiTokens.PRIMARY_HOVER : UiTokens.BORDER_DEFAULT);
            if (checked) UiIcons.CHECK.draw(g, box.x() + 2, box.y() + 2, 8, UiTokens.PRIMARY_INK);

            var info = UiBridge.vehicleInfo(id);
            UiDraw.bold(g, info.name(), row.x() + 26, row.y() + 5, row.width() - 32, UiTokens.TEXT_PRIMARY);
            UiDraw.text(g, id, row.x() + 26, row.y() + 17, row.width() - 32, UiTokens.TEXT_MUTED);
            if (hov) ctx.tooltip(info.name() + "\n" + id + (info.description().isEmpty() ? "" : "\n" + info.description()));
        }
        if (filteredIds.isEmpty()) {
            UiDraw.text(g, tr("mtrnav.vehicle_empty"), listRect.x() + 10, listRect.y() + 12, listRect.width() - 20, UiTokens.TEXT_MUTED);
        }
        g.disableScissor();
        UiDraw.scrollbar(g, scroll, listRect.right() - 7, 4);

        UiDraw.text(g, supported ? tr("mtrnav.vehicle_rule", selected.size()) : tr("mtrnav.vehicle_unsupported"),
                listRect.x() + 2, listRect.bottom() + 4, listRect.width() - 4,
                supported ? UiTokens.TEXT_MUTED : UiTokens.STATUS_WARN);

        clearBtn.render(g, ctx);
        applyBtn.enabled(supported || mode == VehicleMode.NONE).render(g, ctx);
    }

    @Override
    protected boolean onMouseClicked(double x, double y, int button) {
        for (var b : modeButtons) if (b.mouseClicked(x, y, button)) return true;
        if (searchInput.mouseClicked(x, y, button)) return true;
        if (clearBtn.mouseClicked(x, y, button) || applyBtn.mouseClicked(x, y, button)) return true;
        if (button == 0 && scroll.beginDrag(x, y, listRect.right() - 7, 4)) return true;
        if (button == 0 && vehicleSupported() && listRect.contains(x, y)) {
            int idx = (int) ((y - (listRect.y() + 4) + scroll.offset()) / ROW_H);
            if (idx >= 0 && idx < filteredIds.size()) {
                String id = filteredIds.get(idx);
                if (!selected.remove(id) && selected.size() < 16) selected.add(id);
                if (!selected.isEmpty() && mode == VehicleMode.NONE) mode = VehicleMode.PREFER;
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
        boolean handled = clearBtn.mouseReleased(x, y, button) | applyBtn.mouseReleased(x, y, button) | scroll.endDrag();
        for (var b : modeButtons) handled |= b.mouseReleased(x, y, button);
        return handled;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return listRect.contains(x, y) && scroll.scrollBy(vertical, ROW_H);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
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
