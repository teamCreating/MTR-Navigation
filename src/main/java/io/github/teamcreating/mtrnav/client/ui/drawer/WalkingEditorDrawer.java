package io.github.teamcreating.mtrnav.client.ui.drawer;

import io.github.teamcreating.mtrnav.client.UiBridge;
import io.github.teamcreating.mtrnav.client.WalkingClient;
import io.github.teamcreating.mtrnav.client.ui.framework.*;
import io.github.teamcreating.mtrnav.client.ui.framework.UiToggle;
import io.github.teamcreating.mtrnav.client.ui.theme.NavFormat;
import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiIcons;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.client.ui.workspace.WorkspaceHost;
import io.github.teamcreating.mtrnav.core.*;
import io.github.teamcreating.mtrnav.core.ui.ScrollState;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;

import static io.github.teamcreating.mtrnav.client.NavigationClient.*;

/**
 * Slide-over walking link editor replacing WalkingEditorScreen.
 * Supports inline station/platform selection without losing form context.
 */
public final class WalkingEditorDrawer extends SlideDrawer {
    private enum PickerMode { NONE, FROM_STATION, TO_STATION, FROM_PLATFORM, TO_PLATFORM }

    private final WorkspaceHost host;
    private final WalkingLibrary.Proposal proposal;
    private final String id, group, dimension;
    private String from = "", to = "", fromPlatform = "", toPlatform = "";
    private boolean twoWay, enabled = true;
    private String error = "";

    private final UiButton fromBtn;
    private final UiButton fromPlatBtn;
    private final UiButton toBtn;
    private final UiButton toPlatBtn;
    private final UiTextInput minInput;
    private final UiTextInput secInput;
    private final UiToggle twoWayToggle;
    private final UiToggle enabledToggle;
    private final UiTextInput nameInput;
    private final UiTextInput descInput;
    private final UiButton cancelBtn;
    private final UiButton shareBtn;
    private final UiButton saveBtn;

    // Inline sub-picker state
    private PickerMode pickerMode = PickerMode.NONE;
    private final UiTextInput pickSearch = new UiTextInput(tr("mtrnav.directory_search")).icon(UiIcons.SEARCH);
    private final UiButton pickBackBtn = new UiButton(tr("mtrnav.back"), UiButton.Style.SECONDARY, () -> pickerMode = PickerMode.NONE);
    private final ScrollState formScroll = new ScrollState();
    private final ScrollState pickScroll = new ScrollState();
    private record PickOption(String id, String title, String sub) {}
    private List<PickOption> pickOptions = List.of();
    private UiRect bodyRect = UiRect.EMPTY;
    private UiRect pickListRect = UiRect.EMPTY;

    public WalkingEditorDrawer(WorkspaceHost host, WalkingConnection value, WalkingLibrary.Proposal proposal) {
        super(tr("mtrnav.walk_edit"), 320);
        this.host = host;
        this.proposal = proposal;
        if (value == null) {
            this.id = UUID.randomUUID().toString();
            this.group = UUID.randomUUID().toString();
            var pos = UiBridge.playerPosition();
            this.dimension = catalog != null ? catalog.dimension() : pos == null ? "" : pos.dimension();
            this.twoWay = true;
        } else {
            this.id = value.id();
            this.group = value.groupId();
            this.dimension = value.dimension();
            this.from = value.fromStation();
            this.to = value.toStation();
            this.fromPlatform = value.fromPlatform();
            this.toPlatform = value.toPlatform();
            this.enabled = value.enabled();
        }

        this.fromBtn = new UiButton("", UiButton.Style.SECONDARY, () -> openPicker(PickerMode.FROM_STATION));
        this.fromPlatBtn = new UiButton("", UiButton.Style.SECONDARY, () -> openPicker(PickerMode.FROM_PLATFORM));
        this.toBtn = new UiButton("", UiButton.Style.SECONDARY, () -> openPicker(PickerMode.TO_STATION));
        this.toPlatBtn = new UiButton("", UiButton.Style.SECONDARY, () -> openPicker(PickerMode.TO_PLATFORM));

        this.minInput = new UiTextInput(tr("mtrnav.walk_form.minutes")).maxLength(2).placeholder("0")
                .filter(s -> s.matches("[0-9]*")).showClear(false).responder(ignored -> error = "");
        this.secInput = new UiTextInput(tr("mtrnav.walk_form.seconds")).maxLength(2).placeholder("0")
                .filter(s -> s.matches("[0-9]*")).showClear(false).responder(ignored -> error = "");
        if (value != null) {
            minInput.setValue(Integer.toString(value.seconds() / 60));
            secInput.setValue(Integer.toString(value.seconds() % 60));
        }

        this.twoWayToggle = new UiToggle(tr("mtrnav.walk_form.both"), () -> twoWay = !twoWay);
        this.enabledToggle = new UiToggle(tr("mtrnav.walk_form.enabled"), () -> enabled = !enabled);

        this.nameInput = new UiTextInput(tr("mtrnav.walk_form.name")).maxLength(48).placeholder(tr("mtrnav.walk_form.name"))
                .responder(ignored -> error = "");
        this.descInput = new UiTextInput(tr("mtrnav.walk_form.description")).maxLength(256).placeholder(tr("mtrnav.walk_form.description"))
                .responder(ignored -> error = "");
        if (value != null) {
            nameInput.setValue(value.name());
            descInput.setValue(value.description());
        }

        this.cancelBtn = new UiButton(tr("mtrnav.back"), UiButton.Style.SECONDARY, this::close);
        this.shareBtn = new UiButton(tr("mtrnav.walk_submit"), UiButton.Style.SECONDARY, this::submitShare);
        this.saveBtn = new UiButton(tr(proposal == null ? "mtrnav.walk_form.save" : "mtrnav.walk_amend"), UiButton.Style.PRIMARY, this::save)
                .icon(UiIcons.CHECK);
        this.pickSearch.responder(ignored -> refreshPickOptions());
    }

    private void openPicker(PickerMode mode) {
        this.pickerMode = mode;
        this.pickSearch.setValue("");
        this.pickSearch.setFocused(true);
        this.pickScroll.reset();
        refreshPickOptions();
    }

    private void refreshPickOptions() {
        var list = new ArrayList<PickOption>();
        if (catalog == null) { pickOptions = list; return; }
        String q = pickSearch.value().toLowerCase(Locale.ROOT);
        if (pickerMode == PickerMode.FROM_STATION || pickerMode == PickerMode.TO_STATION) {
            for (var st : StationSearch.matches(catalog.stations(), pickSearch.value())) {
                list.add(new PickOption(st.id(), NavFormat.primary(st.name()), st.x() + ", " + st.y() + ", " + st.z()));
            }
        } else if (pickerMode == PickerMode.FROM_PLATFORM || pickerMode == PickerMode.TO_PLATFORM) {
            String stId = pickerMode == PickerMode.FROM_PLATFORM ? from : to;
            list.add(new PickOption("", tr("mtrnav.walk_form.any_platform"), tr("mtrnav.walk_station_wide")));
            catalog.platforms().stream()
                    .filter(p -> p.stationId().equals(stId) && p.id().matches("[0-9a-f]{1,16}"))
                    .filter(p -> q.isEmpty() || p.name().toLowerCase(Locale.ROOT).contains(q) || p.id().contains(q))
                    .sorted(Comparator.comparing(Model.Platform::name).thenComparing(Model.Platform::id))
                    .forEach(p -> list.add(new PickOption(p.id(), Model.displayName(p.name()), p.id())));
        }
        pickOptions = List.copyOf(list);
    }

    private void choosePick(PickOption opt) {
        switch (pickerMode) {
            case FROM_STATION -> { if (!from.equals(opt.id())) fromPlatform = ""; from = opt.id(); }
            case TO_STATION -> { if (!to.equals(opt.id())) toPlatform = ""; to = opt.id(); }
            case FROM_PLATFORM -> fromPlatform = opt.id();
            case TO_PLATFORM -> toPlatform = opt.id();
            default -> {}
        }
        error = "";
        pickerMode = PickerMode.NONE;
    }

    private int parsedDuration() {
        if (minInput.value().isBlank() && secInput.value().isBlank()) throw new IllegalArgumentException();
        int m = minInput.value().isBlank() ? 0 : Integer.parseInt(minInput.value());
        int s = secInput.value().isBlank() ? 0 : Integer.parseInt(secInput.value());
        int total = m * 60 + s;
        if (s > 59 || total < 10 || total > 3600) throw new IllegalArgumentException();
        return total;
    }

    private WalkingConnection buildValue() {
        var v = new WalkingConnection(id, group, dimension, from, fromPlatform, to, toPlatform,
                parsedDuration(), nameInput.value(), descInput.value(), enabled);
        v.validate(catalog);
        return v;
    }

    private String problem() {
        if (catalog == null || !dimension.equals(catalog.dimension())) return "catalog";
        if (from.isEmpty()) return "from";
        if (to.isEmpty()) return "to";
        if (from.equals(to) && fromPlatform.equals(toPlatform)) return "same";
        try { parsedDuration(); } catch (RuntimeException e) { return "time"; }
        try { buildValue(); } catch (RuntimeException e) { return "invalid"; }
        return "";
    }

    private void save() {
        error = problem();
        if (!error.isEmpty()) return;
        if (proposal != null) {
            WalkingClient.change(new WalkingLibrary.Command("amend", proposal.id(), proposal.revision(), buildValue(), ""));
            close();
            return;
        }
        try {
            var v = buildValue();
            var links = new ArrayList<WalkingConnection>();
            links.add(v);
            if (twoWay) {
                var rev = WalkingClient.local().stream()
                        .filter(c -> c.groupId().equals(group) && c.fromStation().equals(to) && c.toStation().equals(from)
                                && c.fromPlatform().equals(toPlatform) && c.toPlatform().equals(fromPlatform))
                        .findFirst().orElse(null);
                links.add(v.reverse(rev == null ? UUID.randomUUID().toString() : rev.id()));
            }
            WalkingClient.putLocal(links);
            if (WalkingClient.message.equals("saved")) close();
            else error = "save";
        } catch (RuntimeException e) {
            error = "invalid";
        }
    }

    private void submitShare() {
        error = problem();
        if (!error.isEmpty()) return;
        var val = buildValue();
        long targetRev = WalkingClient.entryRevision(id);
        host.openDrawer(new ConfirmDrawer(
                tr("mtrnav.walk_submit"),
                NavFormat.station(val.fromStation()) + " → " + NavFormat.station(val.toStation())
                        + " · " + tr("mtrnav.walk_seconds", val.seconds())
                        + (val.description().isEmpty() ? "" : "\n" + val.description()),
                tr("mtrnav.walk_confirm"),
                tr("mtrnav.back"),
                () -> WalkingClient.change(new WalkingLibrary.Command("submit", "", targetRev, val, "")),
                null));
    }

    @Override
    protected int footerHeight() { return 46; }

    @Override
    protected void layoutContent(UiRect body, UiRect footer) {
        this.bodyRect = body;
        UiRect inner = body.inset(12, 8, 16, 8);
        int x = inner.x(), w = inner.width();
        int y = inner.y() - formScroll.offset();

        fromBtn.bounds(new UiRect(x, y + 14, w, 20));
        fromPlatBtn.bounds(new UiRect(x, y + 38, w, 19));
        toBtn.bounds(new UiRect(x, y + 74, w, 20));
        toPlatBtn.bounds(new UiRect(x, y + 98, w, 19));
        minInput.bounds(new UiRect(x, y + 138, 52, 20));
        secInput.bounds(new UiRect(x + 82, y + 138, 52, 20));
        twoWayToggle.bounds(new UiRect(x, y + 178, w, 22));
        enabledToggle.bounds(new UiRect(x, y + 204, w, 22));
        nameInput.bounds(new UiRect(x, y + 244, w, 20));
        descInput.bounds(new UiRect(x, y + 280, w, 20));

        // Sub-picker layout
        UiRect pIn = body.inset(10, 8, 10, 8);
        pickBackBtn.bounds(new UiRect(pIn.x(), pIn.y(), 58, 20));
        pickSearch.bounds(new UiRect(pIn.x() + 64, pIn.y(), Math.max(40, pIn.width() - 64), 20));
        this.pickListRect = pIn.trimTop(28);

        // Footer buttons
        UiRect fIn = footer.inset(10, 18, 10, 6);
        if (proposal == null) {
            int bw = (fIn.width() - 8) / 3;
            cancelBtn.bounds(new UiRect(fIn.x(), fIn.y(), bw, 20));
            shareBtn.bounds(new UiRect(fIn.x() + bw + 4, fIn.y(), bw, 20));
            saveBtn.bounds(new UiRect(fIn.x() + (bw + 4) * 2, fIn.y(), fIn.width() - (bw + 4) * 2, 20));
        } else {
            int bw = (fIn.width() - 6) / 2;
            cancelBtn.bounds(new UiRect(fIn.x(), fIn.y(), bw, 20));
            saveBtn.bounds(new UiRect(fIn.x() + bw + 6, fIn.y(), fIn.width() - bw - 6, 20));
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, UiContext ctx, UiRect body, UiRect footer) {
        if (pickerMode != PickerMode.NONE) {
            renderPicker(g, ctx);
            return;
        }
        UiRect inner = body.inset(12, 8, 16, 8);
        formScroll.update(inner.y(), inner.height(), 316, 16);
        layoutContent(body, footer);

        boolean validCatalog = catalog != null && dimension.equals(catalog.dimension());
        fromBtn.label(from.isEmpty() ? tr("mtrnav.walk_form.choose_from") : NavFormat.station(from) + " · " + tr("mtrnav.walk_form.change"))
                .enabled(validCatalog);
        toBtn.label(to.isEmpty() ? tr("mtrnav.walk_form.choose_to") : NavFormat.station(to) + " · " + tr("mtrnav.walk_form.change"))
                .enabled(validCatalog);
        fromPlatBtn.label(tr("mtrnav.walk_form.platform", fromPlatform.isEmpty() ? tr("mtrnav.walk_form.any_platform") : NavFormat.platform(fromPlatform)))
                .enabled(validCatalog && !from.isEmpty());
        toPlatBtn.label(tr("mtrnav.walk_form.platform", toPlatform.isEmpty() ? tr("mtrnav.walk_form.any_platform") : NavFormat.platform(toPlatform)))
                .enabled(validCatalog && !to.isEmpty());
        twoWayToggle.checked(twoWay).enabled(proposal == null)
                .label(tr(proposal != null ? "mtrnav.walk_form.review_direction" : twoWay ? "mtrnav.walk_form.both" : "mtrnav.walk_form.one_way"));
        enabledToggle.checked(enabled).label(tr(enabled ? "mtrnav.walk_form.enabled" : "mtrnav.walk_form.disabled"));

        UiDraw.enableScissor(g, body);
        int x = inner.x(), w = inner.width(), y = inner.y() - formScroll.offset();
        UiDraw.text(g, tr("mtrnav.walk_form.from"), x, y + 2, w, UiTokens.ACCENT_SKY);
        fromBtn.render(g, ctx);
        fromPlatBtn.render(g, ctx);
        UiDraw.text(g, tr("mtrnav.walk_form.to"), x, y + 62, w, UiTokens.ACCENT_SKY);
        toBtn.render(g, ctx);
        toPlatBtn.render(g, ctx);

        UiDraw.text(g, tr("mtrnav.walk_form.duration"), x, y + 124, w, UiTokens.ACCENT_SKY);
        minInput.render(g, ctx);
        UiDraw.text(g, tr("mtrnav.walk_form.minutes"), x + 56, y + 144, 24, UiTokens.TEXT_SECONDARY);
        secInput.render(g, ctx);
        UiDraw.text(g, tr("mtrnav.walk_form.seconds"), x + 138, y + 144, 24, UiTokens.TEXT_SECONDARY);
        UiDraw.text(g, tr("mtrnav.walk_layout.time_hint"), x, y + 162, w, UiTokens.TEXT_MUTED);

        twoWayToggle.render(g, ctx);
        enabledToggle.render(g, ctx);

        UiDraw.text(g, tr("mtrnav.walk_form.name"), x, y + 232, w, UiTokens.TEXT_SECONDARY);
        nameInput.render(g, ctx);
        UiDraw.text(g, tr("mtrnav.walk_form.description"), x, y + 268, w, UiTokens.TEXT_SECONDARY);
        descInput.render(g, ctx);
        g.disableScissor();

        UiDraw.scrollbar(g, formScroll, body.right() - 7, 4);

        // Footer validation message + buttons
        String issue = error.isEmpty() ? problem() : error;
        String hint = tr(issue.isEmpty() ? (proposal == null ? "mtrnav.walk_form.save_hint" : "mtrnav.walk_form.review_direction") : "mtrnav.walk_form.error." + issue);
        UiDraw.text(g, hint, footer.x() + 10, footer.y() + 5, footer.width() - 20, error.isEmpty() ? UiTokens.TEXT_MUTED : UiTokens.STATUS_WARN);

        cancelBtn.render(g, ctx);
        if (proposal == null) {
            shareBtn.enabled(issue.isEmpty() && WalkingClient.supportsShared()).render(g, ctx);
        }
        saveBtn.enabled(issue.isEmpty() && (proposal == null || WalkingClient.supportsShared())).render(g, ctx);
    }

    private void renderPicker(GuiGraphics g, UiContext ctx) {
        pickBackBtn.render(g, ctx);
        pickSearch.render(g, ctx);
        UiDraw.card(g, pickListRect, UiTokens.RADIUS_MD, UiTokens.INSET, UiTokens.BORDER_SUBTLE);
        int rowH = 30;
        pickScroll.update(pickListRect.y() + 4, pickListRect.height() - 8, pickOptions.size() * rowH, 14);
        UiDraw.enableScissor(g, pickListRect.inset(1));
        int rw = pickListRect.width() - (pickScroll.scrollable() ? 14 : 8);
        for (int i = 0; i < pickOptions.size(); i++) {
            int ry = pickListRect.y() + 4 + i * rowH - pickScroll.offset();
            if (ry + rowH < pickListRect.y() || ry > pickListRect.bottom()) continue;
            var opt = pickOptions.get(i);
            UiRect r = new UiRect(pickListRect.x() + 4, ry, rw, rowH - 3);
            boolean hov = r.contains(ctx.mouseX, ctx.mouseY) && pickListRect.contains(ctx.mouseX, ctx.mouseY);
            UiDraw.card(g, r, UiTokens.RADIUS_SM, hov ? UiTokens.SURFACE_HOVER : UiTokens.SURFACE, UiTokens.BORDER_SUBTLE);
            UiDraw.bold(g, opt.title(), r.x() + 8, r.y() + 4, r.width() - 16, UiTokens.TEXT_PRIMARY);
            UiDraw.text(g, opt.sub(), r.x() + 8, r.y() + 16, r.width() - 16, UiTokens.TEXT_MUTED);
        }
        g.disableScissor();
        UiDraw.scrollbar(g, pickScroll, pickListRect.right() - 7, 4);
    }

    @Override
    protected boolean onMouseClicked(double x, double y, int button) {
        if (pickerMode != PickerMode.NONE) {
            if (pickBackBtn.mouseClicked(x, y, button) || pickSearch.mouseClicked(x, y, button)) return true;
            if (button == 0 && pickScroll.beginDrag(x, y, pickListRect.right() - 7, 4)) return true;
            if (button == 0 && pickListRect.contains(x, y)) {
                int idx = (int) ((y - (pickListRect.y() + 4) + pickScroll.offset()) / 30);
                if (idx >= 0 && idx < pickOptions.size()) { choosePick(pickOptions.get(idx)); return true; }
            }
            return true;
        }
        if (cancelBtn.mouseClicked(x, y, button) || saveBtn.mouseClicked(x, y, button)) return true;
        if (proposal == null && shareBtn.mouseClicked(x, y, button)) return true;
        if (button == 0 && formScroll.beginDrag(x, y, bodyRect.right() - 7, 4)) return true;
        if (bodyRect.contains(x, y)) {
            if (fromBtn.mouseClicked(x, y, button) || fromPlatBtn.mouseClicked(x, y, button)
                    || toBtn.mouseClicked(x, y, button) || toPlatBtn.mouseClicked(x, y, button)
                    || twoWayToggle.mouseClicked(x, y, button) || enabledToggle.mouseClicked(x, y, button)) return true;
            boolean hitInput = minInput.mouseClicked(x, y, button) | secInput.mouseClicked(x, y, button)
                    | nameInput.mouseClicked(x, y, button) | descInput.mouseClicked(x, y, button);
            if (hitInput) return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button != 0) return false;
        return pickerMode != PickerMode.NONE ? pickScroll.drag(y) : formScroll.drag(y);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (pickerMode != PickerMode.NONE) return pickBackBtn.mouseReleased(x, y, button) | pickScroll.endDrag();
        return fromBtn.mouseReleased(x, y, button)
                | fromPlatBtn.mouseReleased(x, y, button)
                | toBtn.mouseReleased(x, y, button)
                | toPlatBtn.mouseReleased(x, y, button)
                | cancelBtn.mouseReleased(x, y, button)
                | shareBtn.mouseReleased(x, y, button)
                | saveBtn.mouseReleased(x, y, button)
                | formScroll.endDrag();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return pickerMode != PickerMode.NONE
                ? pickScroll.scrollBy(vertical, 30)
                : formScroll.scrollBy(vertical, 26);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (pickerMode != PickerMode.NONE) return pickSearch.keyPressed(key, scan, modifiers);
        return minInput.keyPressed(key, scan, modifiers) || secInput.keyPressed(key, scan, modifiers)
                || nameInput.keyPressed(key, scan, modifiers) || descInput.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (pickerMode != PickerMode.NONE) return pickSearch.charTyped(ch, modifiers);
        return minInput.charTyped(ch, modifiers) || secInput.charTyped(ch, modifiers)
                || nameInput.charTyped(ch, modifiers) || descInput.charTyped(ch, modifiers);
    }

    @Override
    public boolean isTyping() {
        return pickerMode != PickerMode.NONE
                ? pickSearch.focused()
                : minInput.focused() || secInput.focused() || nameInput.focused() || descInput.focused();
    }
}
