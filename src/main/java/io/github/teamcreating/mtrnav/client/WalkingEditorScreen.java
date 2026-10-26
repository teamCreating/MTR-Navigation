package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;
import static io.github.teamcreating.mtrnav.client.NavigationClient.*;
import static io.github.teamcreating.mtrnav.client.NavigationTheme.*;

final class WalkingEditorScreen extends WalkingListScreen {
    private String id, group, dimension, from = "", to = "", fromPlatform = "", toPlatform = "", name = "", description = "", seconds = "";
    private boolean twoWay, enabled = true;
    private final WalkingLibrary.Proposal proposal;
    WalkingEditorScreen(Screen previous, WalkingConnection value, WalkingLibrary.Proposal proposal) {
        super(previous, tr("mtrnav.walk_edit"), List::of); this.proposal = proposal;
        if (value == null) {
            id = UUID.randomUUID().toString(); group = UUID.randomUUID().toString(); var pos = playerPosition(); dimension = pos == null ? "" : pos.dimension(); twoWay = true;
        } else { id = value.id(); group = value.groupId(); dimension = value.dimension(); from = value.fromStation(); to = value.toStation(); fromPlatform = value.fromPlatform(); toPlatform = value.toPlatform(); seconds = Integer.toString(value.seconds()); name = value.name(); description = value.description(); enabled = value.enabled(); }
        items = this::rows;
        tools.add(new Tool(tr("mtrnav.walk_details"), () -> { var item = selected(); if (item != null && item.action() != null) item.action().run(); }));
        if (proposal == null) {
            tools.add(new Tool(tr("mtrnav.walk_save_personal"), this::saveLocal));
            tools.add(new Tool(tr("mtrnav.walk_submit"), () -> { try { WalkingManagementScreen.submitPreview(this, value(), WalkingClient.entryRevision(id)); } catch (RuntimeException e) { WalkingClient.message = "invalid_connection"; } }));
        } else tools.add(new Tool(tr("mtrnav.walk_amend"), () -> {
            try { WalkingClient.change(new WalkingLibrary.Command("amend", proposal.id(), proposal.revision(), value(), "")); Minecraft.getInstance().setScreen(previous); }
            catch (RuntimeException e) { WalkingClient.message = "invalid_connection"; }
        }));
    }
    private WalkingConnection value() {
        var value = new WalkingConnection(id, group, dimension, from, fromPlatform, to, toPlatform, Integer.parseInt(seconds.strip()), name, description, enabled);
        value.validate(catalog); return value;
    }
    private void saveLocal() {
        try {
            var value = value(); var links = new ArrayList<WalkingConnection>(); links.add(value);
            if (twoWay) {
                var reverse = WalkingClient.local().stream().filter(c -> c.groupId().equals(group) && c.fromStation().equals(to) && c.toStation().equals(from)
                        && c.fromPlatform().equals(toPlatform) && c.toPlatform().equals(fromPlatform)).findFirst().orElse(null);
                links.add(value.reverse(reverse == null ? UUID.randomUUID().toString() : reverse.id()));
            }
            WalkingClient.putLocal(links); if (WalkingClient.message.equals("saved")) Minecraft.getInstance().setScreen(previous);
        } catch (RuntimeException e) { WalkingClient.message = "invalid_connection"; }
    }
    private List<Item> rows() {
        return List.of(
                new Item("from", tr("mtrnav.origin") + ": " + station(from), tr("mtrnav.walk_select_station"), () -> stations(true)),
                new Item("fp", tr("mtrnav.walk_from_platform") + ": " + (fromPlatform.isEmpty() ? tr("mtrnav.walk_station_wide") : platform(fromPlatform)), "", () -> platforms(true)),
                new Item("to", tr("mtrnav.destination") + ": " + station(to), tr("mtrnav.walk_select_station"), () -> stations(false)),
                new Item("tp", tr("mtrnav.walk_to_platform") + ": " + (toPlatform.isEmpty() ? tr("mtrnav.walk_station_wide") : platform(toPlatform)), "", () -> platforms(false)),
                new Item("text", tr("mtrnav.walk_seconds", seconds.isBlank() ? "?" : seconds) + " · " + name, description.isEmpty() ? tr("mtrnav.walk_time_explanation") : description, this::text),
                new Item("direction", tr("mtrnav.walk_both_directions", twoWay ? tr("mtrnav.on") : tr("mtrnav.off")), tr("mtrnav.walk_direction_hint"), () -> { if (proposal == null) twoWay = !twoWay; }),
                new Item("enabled", tr("mtrnav.walk_enabled", enabled ? tr("mtrnav.on") : tr("mtrnav.off")), tr("mtrnav.walk_time_explanation"), () -> enabled = !enabled));
    }
    private void stations(boolean source) {
        if (catalog == null) return;
        WalkingListScreen.choice(this, tr("mtrnav.walk_select_station"), catalog.stations().stream().sorted(Comparator.comparing(Model.Station::name).thenComparing(Model.Station::id)).map(s -> new Item(s.id(), Model.displayName(s.name()), s.x() + ", " + s.y() + ", " + s.z(), () -> {
            if (source) { from = s.id(); fromPlatform = ""; } else { to = s.id(); toPlatform = ""; } Minecraft.getInstance().setScreen(this);
        })).toList());
    }
    private void platforms(boolean source) {
        if (catalog == null) return; String stationId = source ? from : to;
        var choices = new ArrayList<Item>();
        choices.add(new Item("any", tr("mtrnav.walk_station_wide"), tr("mtrnav.walk_time_explanation"), () -> { if (source) fromPlatform = ""; else toPlatform = ""; Minecraft.getInstance().setScreen(this); }));
        catalog.platforms().stream().filter(p -> p.stationId().equals(stationId) && p.id().matches("[0-9a-f]{1,16}")).sorted(Comparator.comparing(Model.Platform::name).thenComparing(Model.Platform::id)).forEach(p -> choices.add(new Item(p.id(), Model.displayName(p.name()), p.id(), () -> {
            if (source) fromPlatform = p.id(); else toPlatform = p.id(); Minecraft.getInstance().setScreen(this);
        })));
        WalkingListScreen.choice(this, tr("mtrnav.platform_label"), choices);
    }
    private void text() {
        Minecraft.getInstance().setScreen(new WalkingTextScreen(this, tr("mtrnav.walk_time_explanation"), List.of(
                new WalkingTextScreen.Field(tr("mtrnav.walk_time_field"), seconds, 4),
                new WalkingTextScreen.Field(tr("mtrnav.walk_name"), name, 48), new WalkingTextScreen.Field(tr("mtrnav.walk_description"), description, 256)), fields -> {
            int time = Integer.parseInt(fields.get(0).strip()); if (time < 10 || time > 3600) throw new IllegalArgumentException();
            WalkingConnection.text(fields.get(1), 48); WalkingConnection.text(fields.get(2), 256);
            seconds = fields.get(0); name = fields.get(1); description = fields.get(2); Minecraft.getInstance().setScreen(this);
        }));
    }
}
