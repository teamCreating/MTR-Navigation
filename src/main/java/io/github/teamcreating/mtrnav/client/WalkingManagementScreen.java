package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;
import static io.github.teamcreating.mtrnav.client.NavigationClient.*;
import static io.github.teamcreating.mtrnav.client.NavigationTheme.*;

final class WalkingManagementScreen extends WalkingListScreen {
    private int tab;
    WalkingManagementScreen(Screen previous) {
        super(previous, tr("mtrnav.walk_title"), List::of); items = this::rows;
        if (!WalkingClient.supportsShared()) tab = 1;
        tools.add(new Tool(() -> tr("mtrnav.walk_tab." + tab), () -> { tab = (tab + 1) % (WalkingClient.admin ? 4 : 3); WalkingClient.reviewMode = tab == 3; WalkingClient.page = 0; WalkingClient.refresh(); }));
        tools.add(new Tool(tr("mtrnav.walk_new"), () -> Minecraft.getInstance().setScreen(new WalkingEditorScreen(this, null, null))));
        tools.add(new Tool(tr("mtrnav.walk_details"), () -> { var item = selected(); if (item != null) item.action().run(); }));
        tools.add(new Tool(tr("mtrnav.walk_refresh"), WalkingClient::refresh));
        tools.add(new Tool(tr("mtrnav.walk_import"), this::importLinks)); tools.add(new Tool(tr("mtrnav.walk_export"), WalkingClient::exportLocal));
        tools.add(new Tool(() -> tr("mtrnav.walk_consent", WalkingClient.consent() ? tr("mtrnav.on") : tr("mtrnav.off")), () -> {
            if (WalkingClient.consent()) WalkingClient.consent(false); else confirmConsent(this, () -> {});
        }));
        tools.add(new Tool(() -> tr("mtrnav.walk_window", WalkingClient.options().extraMinutes() < 0 ? tr("mtrnav.walk_unlimited") : WalkingClient.options().extraMinutes()), WalkingClient::nextWindow));
        tools.add(new Tool(tr("mtrnav.walk_previous_page"), () -> WalkingClient.page = Math.max(0, WalkingClient.page - 1)));
        tools.add(new Tool(() -> tr("mtrnav.walk_next_page", WalkingClient.page + 1, WalkingClient.proposalPages()), () -> WalkingClient.page = Math.min(WalkingClient.proposalPages() - 1, WalkingClient.page + 1)));
        tools.add(new Tool(tr("mtrnav.walk_backups"), this::backups));
        if (WalkingClient.supportsShared()) WalkingClient.refresh();
    }
    private List<Item> rows() {
        if (tab == 3 && !WalkingClient.admin) { tab = 2; WalkingClient.reviewMode = false; }
        if (tab == 1) return WalkingClient.local().stream().map(c -> new Item(c.id(), label(c), detail(c) + " · " + tr("mtrnav.walk_personal"), () -> connection(c, null))).toList();
        if (!WalkingClient.supportsShared()) return List.of(new Item("unavailable", tr("mtrnav.walk_message.server_unavailable"), tr("mtrnav.walk_personal_available"), () -> tab = 1));
        if (tab == 0) return WalkingClient.shared.stream().map(e -> new Item(e.connection().id(), label(e.connection()), detail(e.connection()) + " · r" + e.revision(), () -> connection(e.connection(), e))).toList();
        return WalkingClient.proposals().stream().filter(p -> tab == 3 || Minecraft.getInstance().player != null && p.author().equals(Minecraft.getInstance().player.getUUID()))
                .map(p -> new Item(p.id(), tr("mtrnav.walk_status." + p.status().name().toLowerCase(Locale.ROOT)) + " · " + label(p.value()), detail(p.value()) + " · " + tr("mtrnav.walk_author", p.author()) + " · r" + p.revision() + " · " + p.reason(), () -> proposal(p))).toList();
    }
    static String label(WalkingConnection c) { return c.name().isBlank() ? station(c.fromStation()) + " → " + station(c.toStation()) : c.name(); }
    static String detail(WalkingConnection c) { return station(c.fromStation()) + (c.fromPlatform().isEmpty() ? "" : " / " + platform(c.fromPlatform())) + " → " + station(c.toStation()) + (c.toPlatform().isEmpty() ? "" : " / " + platform(c.toPlatform())) + " · " + tr("mtrnav.walk_seconds", c.seconds()) + (c.enabled() ? "" : " · " + tr("mtrnav.walk_disabled")); }
    private void connection(WalkingConnection value, WalkingLibrary.Entry published) {
        var actions = new ArrayList<Item>();
        actions.add(new Item("description", detail(value), value.description(), null));
        actions.add(new Item("edit", tr("mtrnav.walk_edit"), published == null ? tr("mtrnav.walk_personal") : tr("mtrnav.walk_submit_hint"), () -> Minecraft.getInstance().setScreen(new WalkingEditorScreen(this, value, null))));
        if (published == null) {
            actions.add(new Item("toggle", tr(value.enabled() ? "mtrnav.walk_disable" : "mtrnav.walk_enable"), "", () -> {
                try { if (!value.enabled()) value.validate(catalog); WalkingClient.putLocal(List.of(value.enabled(!value.enabled()))); Minecraft.getInstance().setScreen(this); }
                catch (RuntimeException e) { WalkingClient.message = "invalid_connection"; }
            }));
            actions.add(new Item("delete", tr("mtrnav.walk_delete_local"), "", () -> confirm(this, tr("mtrnav.walk_delete_local"), detail(value), () -> WalkingClient.deleteLocal(value.id()))));
            actions.add(new Item("submit", tr("mtrnav.walk_submit"), tr("mtrnav.walk_submit_hint"), () -> submitPreview(this, value, WalkingClient.entryRevision(value.id()))));
        } else if (WalkingClient.admin) {
            actions.add(new Item("disable", tr("mtrnav.walk_disable_suggestion"), tr("mtrnav.walk_submit_hint"), () -> submitPreview(this, value.enabled(!value.enabled()), published.revision())));
            actions.add(new Item("remove", tr("mtrnav.walk_remove_shared"), tr("mtrnav.walk_admin_only"), () -> confirm(this, tr("mtrnav.walk_remove_shared"), detail(value), () -> WalkingClient.change(new WalkingLibrary.Command("remove", value.id(), published.revision(), null, "")))));
        }
        choice(this, label(value), actions);
    }
    private void proposal(WalkingLibrary.Proposal p) {
        var actions = new ArrayList<Item>();
        actions.add(new Item("new", tr("mtrnav.walk_proposed") + ": " + label(p.value()), detail(p.value()) + " · " + p.value().description(), null));
        var old = WalkingClient.shared.stream().filter(e -> e.connection().id().equals(p.value().id())).findFirst().orElse(null);
        if (old != null) actions.add(new Item("old", tr("mtrnav.walk_original") + ": " + label(old.connection()), detail(old.connection()) + " · " + old.connection().description(), null));
        if (p.status() == WalkingLibrary.Status.PENDING) {
            if (Minecraft.getInstance().player != null && p.author().equals(Minecraft.getInstance().player.getUUID()))
                actions.add(new Item("withdraw", tr("mtrnav.walk_withdraw"), "", () -> confirm(this, tr("mtrnav.walk_withdraw"), label(p.value()), () -> WalkingClient.change(new WalkingLibrary.Command("withdraw", p.id(), p.revision(), null, "")))));
            if (WalkingClient.admin) {
                actions.add(new Item("amend", tr("mtrnav.walk_amend"), tr("mtrnav.walk_amend_hint"), () -> Minecraft.getInstance().setScreen(new WalkingEditorScreen(this, p.value(), p))));
                actions.add(new Item("approve", tr("mtrnav.walk_approve"), tr("mtrnav.walk_admin_only"), () -> confirm(this, tr("mtrnav.walk_approve"), detail(p.value()), () -> WalkingClient.change(new WalkingLibrary.Command("approve", p.id(), p.revision(), null, "")))));
                actions.add(new Item("reject", tr("mtrnav.walk_reject"), "", () -> Minecraft.getInstance().setScreen(new WalkingTextScreen(this, tr("mtrnav.walk_reject"), List.of(new WalkingTextScreen.Field(tr("mtrnav.walk_reason"), "", 256)), values -> {
                    if (values.getFirst().isBlank()) throw new IllegalArgumentException();
                    WalkingClient.change(new WalkingLibrary.Command("reject", p.id(), p.revision(), null, values.getFirst())); Minecraft.getInstance().setScreen(this);
                }))));
            }
        }
        choice(this, label(p.value()), actions);
    }
    static void submitPreview(Screen previous, WalkingConnection value, long targetRevision) {
        confirm(previous, tr("mtrnav.walk_submit"), detail(value) + " · " + value.description(), () -> WalkingClient.change(new WalkingLibrary.Command("submit", "", targetRevision, value, "")));
    }
    static void confirm(Screen previous, String title, String detail, Runnable action) {
        choice(previous, title, List.of(new Item("confirm", tr("mtrnav.walk_confirm"), detail, () -> { action.run(); Minecraft.getInstance().setScreen(previous); }),
                new Item("cancel", tr("mtrnav.back"), "", () -> Minecraft.getInstance().setScreen(previous))));
    }
    static void confirmConsent(Screen previous, Runnable next) {
        choice(previous, tr("mtrnav.walk_consent_title"), List.of(
                new Item("agree", tr("mtrnav.walk_consent_agree"), tr("mtrnav.walk_consent_explanation"), () -> { WalkingClient.consent(true); Minecraft.getInstance().setScreen(previous); if (WalkingClient.consent()) next.run(); }),
                new Item("decline", tr("mtrnav.walk_consent_decline"), tr("mtrnav.walk_consent_explanation"), () -> { WalkingClient.consent(false); Minecraft.getInstance().setScreen(previous); next.run(); })));
    }
    private void importLinks() {
        choice(this, tr("mtrnav.walk_import"), WalkingClient.imports().stream().map(path -> new Item(path.getFileName().toString(), path.getFileName().toString(), "config/mtrnav/imports", () -> {
            try {
                var links = new ArrayList<>(WalkingClient.preview(path));
                var preview = new WalkingListScreen(this, tr("mtrnav.walk_import_preview"), () -> java.util.stream.IntStream.range(0, links.size()).mapToObj(i -> {
                    var c = links.get(i);
                    return new Item(c.id(), label(c), detail(c), () -> {
                        try { if (!c.enabled()) c.validate(catalog); links.set(i, c.enabled(!c.enabled())); }
                        catch (RuntimeException error) { WalkingClient.message = "invalid_connection"; }
                    });
                }).toList());
                preview.tools.add(new Tool(tr("mtrnav.walk_import_confirm"), () -> { WalkingClient.putLocal(links); Minecraft.getInstance().setScreen(this); }));
                preview.tools.add(new Tool(tr("mtrnav.walk_toggle_import"), () -> { var selected = preview.selected(); if (selected != null) selected.action().run(); }));
                Minecraft.getInstance().setScreen(preview);
            } catch (Exception e) { WalkingClient.message = "import_failed"; Minecraft.getInstance().setScreen(this); }
        })).toList());
    }
    private void backups() {
        if (!WalkingClient.admin) { WalkingClient.message = "permission_denied"; return; }
        choice(this, tr("mtrnav.walk_backups"), WalkingClient.backups.stream().map(rev -> new Item(rev.toString(), "Generation " + rev, tr("mtrnav.walk_rollback_hint"),
                () -> confirm(this, tr("mtrnav.walk_rollback"), rev.toString(), () -> WalkingClient.rollback(rev)))).toList());
    }
}
