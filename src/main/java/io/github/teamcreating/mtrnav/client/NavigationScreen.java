package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.JourneyTracker;
import io.github.teamcreating.mtrnav.core.JourneyChain;
import io.github.teamcreating.mtrnav.core.StationSearch;
import io.github.teamcreating.mtrnav.core.DepartureBoard;
import io.github.teamcreating.mtrnav.core.ScrollBarGeometry;
import io.github.teamcreating.mtrnav.core.ArrivalReminder;
import io.github.teamcreating.mtrnav.core.StationLocator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;
import static io.github.teamcreating.mtrnav.client.NavigationClient.*;
import static io.github.teamcreating.mtrnav.client.NavigationTheme.*;

/** Railway journey-book layout with a separate, independently searchable station board. */
public final class NavigationScreen extends NavigationPanel {
    private enum View { JOURNEYS, DEPARTURES, ACTIVE }
    private record Row(String text,int color,int height,int kind,String stationId,String time) {
        Row(String text,int color,int height,int kind){this(text,color,height,kind,"","");}
        Row(String text,int color,int height,int kind,String stationId){this(text,color,height,kind,stationId,"");}
    }
    private record StationTarget(String id, int x, int y, int w, int h) {
        boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
    private record ScrollTarget(int input, int x, ScrollBarGeometry geometry) {
        boolean contains(double px, double py) { return px >= x - 4 && px <= x + 6 && py >= geometry.top() && py < geometry.top() + geometry.height(); }
    }
    private record BoardTarget(String key, int x, int y, int w, int h) {
        boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
    private record DepartureTarget(StopEvent event, int x, int y, int w, int h) {
        boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
    private EditBox fromBox, toBox, boardBox;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<Action> cards = new ArrayList<>();
    private final List<StationTarget> stationTargets = new ArrayList<>();
    private final List<ScrollTarget> scrollTargets = new ArrayList<>();
    private final List<BoardTarget> boardTargets = new ArrayList<>();
    private final List<DepartureTarget> departureTargets = new ArrayList<>();
    private final List<Action> clearButtons = new ArrayList<>();
    private final Set<String> expandedLines = new HashSet<>();
    private int draggingScroll = -2;
    private double scrollGrab;
    private Action queryButton, sortButton, routesTab, departuresTab, activeTab, startButton, progressButton, backButton, requeryButton, cancelButton, allStationsButton, moreButton, locateButton;
    private String locationMessage = "";
    private Catalog locationCatalog;
    private int cardScroll, cardBottom, cardHeight;
    private Action vehicleButton, walkingButton, listToggle, detailToggle, reminderMinus, reminderLead, reminderPlus, reminderArm, reminderCancel;
    private StopEvent inlineReminder;
    private int inlineMinutes=ArrivalReminder.DEFAULT_MINUTES;
    private boolean showDetails;
    private int selectedJourney, scroll, contentHeight, left, top, panelWidth, panelHeight, bodyTop, bodyBottom, detailX, detailTop, footerY;
    private boolean wide, collapsed;
    private final Set<String> expandedStops=new HashSet<>();
    private final Map<String,Action> stopControls=new LinkedHashMap<>();
    private int fieldY,toolbarY;
    private boolean searchCollapsed = true;
    private View view = tracking == null ? View.JOURNEYS : View.ACTIVE;
    private String fromText, toText, boardText, selectedBoard = "", pendingSelection = "";
    private List<Journey> lastJourneys = journeys;
    private String lastBoardStation = "";
    private Catalog groupedCatalog;
    private List<StopEvent> groupedArrivals;
    private List<DepartureBoard.Group> boardGroups = List.of();

    public NavigationScreen() { super(Component.translatable("mtrnav.title")); }
    void reviewView(boolean board){view=board?View.DEPARTURES:tracking==null?View.JOURNEYS:View.ACTIVE;fromText=toText=boardText=null;selectedBoard=board?"a":"";groupedCatalog=null;boardGroups=List.of();rebuild();}
    List<AbstractWidget> reviewWidgets() { var result=new ArrayList<>(widgets);result.addAll(stopControls.values());return List.copyOf(result); }
    List<Integer> reviewFrame() { return List.of(left,top,panelWidth,panelHeight); }
    List<Integer> reviewBody() { return List.of(left+12,bodyTop,panelWidth-24,bodyBottom-bodyTop); }
    int reviewExpandedStops() { return expandedStops.size(); }
    boolean wantsBoard() { return view == View.DEPARTURES; }
    private Action button(String key, int x, int y, int w, int h, Runnable action) {
        var b = new Action(x, y, w, h, tr(key), action); b.onLight = y >= top + panelHeight - 38;
        addRenderableWidget(b); widgets.add(b); return b;
    }
    private void rebuild() { clearWidgets(); init(); setFocused(null); }
    private void changeView(View next) {
        if (view == next) return;
        if (view == View.DEPARTURES) stopBoard();
        view = next; scroll = 0; draggingScroll = -2;
        if (next == View.DEPARTURES && selectedBoard.isEmpty() && !origin.isEmpty()) selectedBoard = origin;
        if (next == View.DEPARTURES && catalog != null && !selectedBoard.isEmpty()) {
            boardText = catalog.stationName(selectedBoard); board(selectedBoard);
        }
        rebuild();
        if (next == View.DEPARTURES) { setFocused(boardBox); selectStationText(boardBox); }
    }
    private void openBoard(String stationId) {
        if (catalog == null || catalog.station(stationId) == null) return;
        selectedBoard = stationId; boardText = catalog.stationName(stationId);
        if (view != View.DEPARTURES) changeView(View.DEPARTURES);
        else { boardBox.setValue(boardText); board(stationId); scroll = 0; }
    }
    private void search() {
        if (usingNative && status.equals("mtrnav.native_unavailable")) { NativeMtrClient.opened(); return; }
        if (view == View.DEPARTURES) { if (!selectedBoard.isEmpty()) { board(selectedBoard); scroll = 0; } return; }
        if (collapsed) { searchCollapsed = false; rebuild(); setInitialFocus(fromBox); return; }
        selectedJourney = 0; changeView(View.JOURNEYS); scroll = 0; query();
    }
    @Override protected void init() {
        super.init();
        widgets.clear(); cards.clear(); stationTargets.clear();stopControls.clear();
        clearButtons.clear(); scrollTargets.clear(); boardTargets.clear(); departureTargets.clear(); draggingScroll = -2;
        panelWidth=Math.min(width-20,620);panelHeight=Math.min(height-16,480);
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        wide=panelWidth>=480;
        collapsed=view==View.ACTIVE||view==View.JOURNEYS&&searchCollapsed&&panelHeight<330;
        int x=left+12,inner=panelWidth-24;
        fieldY=top+(wide?64:52);toolbarY=fieldY+40;
        int tabWidth=(inner-8)/3,tabsX=x,tabY=top+(wide?34:28),tabHeight=wide?22:18;
        routesTab=button("mtrnav.query_tab",tabsX,tabY,tabWidth,tabHeight,()->changeView(View.JOURNEYS));routesTab.tab=true;
        departuresTab=button("mtrnav.departures",tabsX+tabWidth+4,tabY,tabWidth,tabHeight,()->changeView(View.DEPARTURES));departuresTab.tab=true;
        activeTab=button("mtrnav.active_trip",tabsX+(tabWidth+4)*2,tabY,inner-(tabWidth+4)*2,tabHeight,()->changeView(View.ACTIVE));activeTab.tab=true;
        int headerControlY=top+(wide?4:2),headerControlHeight=wide?20:18;
        var close=button("mtrnav.close",left+panelWidth-28,headerControlY,20,headerControlHeight,this::onClose);close.headerControl=true;
        moreButton=button("mtrnav.more",left+panelWidth-52,headerControlY,20,headerControlHeight,()->minecraft.setScreen(new NavigationMenuScreen(this,()->changeView(View.DEPARTURES))));moreButton.headerControl=true;
        moreButton.setMessage(Component.literal("…"));moreButton.setTooltip(Tooltip.create(Component.translatable("mtrnav.more_hint")));
        var back=button("mtrnav.back",left+8,headerControlY,20,headerControlHeight,this::onClose);back.mark=Mark.BACK;back.headerControl=true;
        int queryWidth=60,fieldWidth=(inner-queryWidth-34)/2,right=x+fieldWidth+26;
        fromBox = new TextField(font, x, fieldY, fieldWidth, 34, Component.translatable("mtrnav.origin"));
        toBox = new TextField(font, right, fieldY, fieldWidth, 34, Component.translatable("mtrnav.destination"));
        boardBox = new TextField(font, x, fieldY, inner - queryWidth - 8, 34, Component.translatable("mtrnav.board_station"));
        ((TextField)fromBox).caption=tr("mtrnav.origin");((TextField)toBox).caption=tr("mtrnav.destination");((TextField)boardBox).caption=tr("mtrnav.board_station");
        for (var box : List.of(fromBox, toBox, boardBox)) {
            box.setMaxLength(128); box.setTextColor(INPUT_TEXT); box.setTextColorUneditable(INPUT_MUTED);
            box.setTooltip(Tooltip.create(Component.translatable("mtrnav.station_replace_hint")));
            box.setHint(Component.translatable("mtrnav.station_hint")); addRenderableWidget(box); widgets.add(box);
        }
        fromBox.setValue(fromText != null ? fromText : catalog == null ? "" : catalog.stationName(origin));
        toBox.setValue(toText != null ? toText : catalog == null ? "" : catalog.stationName(destination));
        boardBox.setValue(boardText != null ? boardText : catalog == null ? "" : catalog.stationName(selectedBoard));
        for(var box:List.of(fromBox,toBox,boardBox)){box.setCursorPosition(0);box.setHighlightPos(0);}
        fromBox.setResponder(value -> { originLocation.edited(); locationMessage = ""; fromText = value; edited(true, value); updateControls(); });
        toBox.setResponder(value -> { toText = value; edited(false, value); updateControls(); });
        boardBox.setResponder(value -> { boardText = value; selectedBoard = resolve(value); updateControls(); });
        fromBox.visible = toBox.visible = !collapsed && view == View.JOURNEYS;
        boardBox.visible = view == View.DEPARTURES;
        for (int i = 0; i < 3; i++) {
            final int input = i; var box = inputBox(i);
            var clear = button("mtrnav.clear_symbol", box.getX() + box.getWidth() - 18, box.getY()+14, 16, 18, () -> clearInput(input));
            clear.setTooltip(Tooltip.create(Component.translatable("mtrnav.clear_input"))); clearButtons.add(clear);
        }
        var swap = button("mtrnav.swap_short", x + fieldWidth + 4, fieldY+12, 18, 22, () -> {
            originLocation.edited(); locationMessage = "";
            String a = origin, b = destination, value = fromBox.getValue();
            fromBox.setValue(toBox.getValue()); toBox.setValue(value); origin = b; destination = a; updateControls();
        }); swap.visible = fromBox.visible; swap.setTooltip(Tooltip.create(Component.translatable("mtrnav.swap")));
        queryButton = button("mtrnav.search", collapsed ? x : x + inner - queryWidth, collapsed?fieldY:fieldY+12, queryWidth, collapsed?20:22, this::search); queryButton.primary = !collapsed; queryButton.mark = Mark.SEARCH;
        queryButton.setTooltip(Tooltip.create(Component.translatable("mtrnav.search")));
        sortButton = button("mtrnav.fastest", x + inner - 92, collapsed?fieldY:toolbarY, 92, 20, () -> {
            sort = sort == Sort.FASTEST ? Sort.FEWEST_TRANSFERS : Sort.FASTEST;
            if (!origin.isEmpty() && !destination.isEmpty()) { selectedJourney = 0; changeView(View.JOURNEYS); scroll = 0; query(); }
        });
        allStationsButton = button("mtrnav.all_stations", x, toolbarY, 18, 18, () -> {
            var target = view == View.DEPARTURES ? StationDirectoryScreen.Target.BOARD
                    : fromBox.isFocused() ? StationDirectoryScreen.Target.ORIGIN
                    : toBox.isFocused() || !origin.isEmpty() ? StationDirectoryScreen.Target.DESTINATION : StationDirectoryScreen.Target.ORIGIN;
            minecraft.setScreen(new StationDirectoryScreen(this, target));
        });
        allStationsButton.mark = Mark.STATIONS;
        allStationsButton.setTooltip(Tooltip.create(Component.translatable("mtrnav.all_stations_hint")));
        locateButton = button("mtrnav.locate", x + 22, toolbarY, 18, 18, this::locateOrigin);
        locateButton.locateIcon = true;
        locateButton.setTooltip(Tooltip.create(Component.translatable("mtrnav.locate_hint")));
        walkingButton = button("mtrnav.walk_short", locateButton.getX() + locateButton.getWidth() + 4, toolbarY, 18, 18, () -> minecraft.setScreen(new WalkingManagementScreen(this)));
        vehicleButton = button("mtrnav.vehicle_filter", walkingButton.getX() + 22, toolbarY, 18, 18, () -> minecraft.setScreen(new VehicleSelectionScreen(this)));
        vehicleButton.mark = Mark.GEAR; vehicleButton.setTooltip(Tooltip.create(Component.translatable("mtrnav.vehicle_filter")));
        walkingButton.mark = Mark.WALK; walkingButton.setTooltip(Tooltip.create(Component.translatable("mtrnav.walk_short")));
        bodyTop=collapsed?top+(wide?94:76):toolbarY+28+(wide||view!=View.JOURNEYS?0:24);
        footerY=top+panelHeight-32;bodyBottom=footerY-5;
        detailX=wide?x+Math.min(216,inner*36/100)+12:x;
        cardHeight=panelHeight<330?34:56;cardBottom=bodyBottom;
        listToggle=button("mtrnav.ui.list",x,bodyTop-23,90,18,()->{showDetails=false;updateControls();});listToggle.tab=true;
        detailToggle=button("mtrnav.ui.details",x+94,bodyTop-23,90,18,()->{showDetails=true;updateControls();});detailToggle.tab=true;
        if(collapsed&&!wide){listToggle.setX(x+64);listToggle.setY(fieldY);listToggle.setWidth(72);detailToggle.setX(x+140);detailToggle.setY(fieldY);detailToggle.setWidth(72);}
        for (int i = 0; i < 5; i++) {
            final int index = i;
            int cw = wide ? detailX - x - 18 : inner - 8;
            var card = new Action(x, bodyTop + i * (cardHeight + 4), cw, cardHeight, "", () -> { selectedJourney = index; scroll = 0; setFocused(null); });
            widgets.add(card); cards.add(card);
        }
        int primaryWidth = Math.min(140, inner / 3 + 12);
        startButton = button("mtrnav.start", x + inner - primaryWidth, footerY + 6, primaryWidth, 20, () -> {
            if (view == View.DEPARTURES) { if (!boardStation.isEmpty()) board(boardStation); }
            else if (!journeys.isEmpty()) { var selected = journeys.get(selectedJourney); pendingSelection = selected.id(); select(selected); }
        }); startButton.primary = true; startButton.mark = Mark.CHECK;
        progressButton = button("mtrnav.boarded", startButton.getX(), footerY + 6, primaryWidth, 20, () -> {
            if (tracking != null) { scroll = 0; correct(tracking.phase() == JourneyTracker.Phase.RIDING || tracking.phase() == JourneyTracker.Phase.WALKING ? "next" : "board"); }
        }); progressButton.primary = true; progressButton.mark = Mark.CHECK;
        int secondaryWidth = (inner - primaryWidth - 15) / 3;
        backButton = button("mtrnav.previous_step", x, footerY + 6, secondaryWidth, 20, () -> { scroll = 0; correct("previous"); });
        requeryButton = button("mtrnav.requery", x + secondaryWidth + 4, footerY + 6, secondaryWidth, 20, () -> {
            if (tracking != null && tracking.walk() != null) {
                originLocation.edited(); origin = tracking.walk().fromStationId(); destination = tracking.journey().destinationStationId();
                fromText = catalog.stationName(origin); toText = catalog.stationName(destination);
            } else if (tracking != null && tracking.legIndex() < tracking.journey().legs().size()) {
                originLocation.edited(); locationMessage = "";
                var leg = tracking.journey().legs().get(tracking.legIndex());
                origin = leg.fromStationId(); destination = tracking.journey().destinationStationId();
                fromText = catalog.stationName(origin); toText = catalog.stationName(destination);
            }
            changeView(View.JOURNEYS); scroll = 0; query();
        });
        cancelButton = button("mtrnav.end_trip", x + (secondaryWidth + 4) * 2, footerY + 6, secondaryWidth, 20, () -> { cancel(); changeView(View.JOURNEYS); });
        if (view == View.DEPARTURES && !selectedBoard.isEmpty() && boardStation.isEmpty()) board(selectedBoard);
        reminderMinus=button("mtrnav.size_minus",x+12,0,18,18,()->inlineMinutes=Math.max(1,inlineMinutes-1));
        reminderLead=button("mtrnav.reminder_lead",x+34,0,78,18,()->{});reminderLead.labelOnly=true;reminderLead.active=false;
        reminderPlus=button("mtrnav.size_plus",x+116,0,18,18,()->inlineMinutes=Math.min(60,inlineMinutes+1));
        reminderArm=button("mtrnav.reminder_arm",x+140,0,90,18,()->{
            if(inlineReminder!=null&&armReminder(boardStation,inlineReminder,inlineMinutes))inlineReminder=null;
        });reminderArm.primary=true;
        reminderCancel=button("mtrnav.back",x+234,0,54,18,()->inlineReminder=null);
        updateControls();
    }
    private EditBox inputBox(int input) { return input == 0 ? fromBox : input == 1 ? toBox : boardBox; }
    private void clearInput(int input) {
        var box = inputBox(input); box.setValue("");
        if (input == 2) { selectedBoard = ""; stopBoard(); expandedLines.clear(); scroll = 0; }
        else clearSearchResults();
        setFocused(box); updateControls();
    }
    private String resolve(String value) { return catalog == null ? "" : StationSearch.exact(catalog.stations(), value); }
    private void edited(boolean from, String value) {
        String id = resolve(value), previous = from ? origin : destination;
        if (from) origin = id; else destination = id;
        if (!previous.equals(id)) clearSearchResults();
        if (Set.of("mtrnav.search_failed", "mtrnav.search_budget", "mtrnav.station_refreshed", "mtrnav.no_route").contains(status)) status = "mtrnav.ready";
    }
    void useStation(Station station, StationDirectoryScreen.Target target) {
        scroll = 0;
        if (target == StationDirectoryScreen.Target.BOARD) {
            selectedBoard = station.id(); boardText = displayName(station.name()); view = View.DEPARTURES;
        } else {
            if (target == StationDirectoryScreen.Target.ORIGIN) { originLocation.edited(); locationMessage = ""; origin = station.id(); fromText = displayName(station.name()); }
            else { destination = station.id(); toText = displayName(station.name()); }
            clearSearchResults(); view = View.JOURNEYS; searchCollapsed = false;
        }
    }
    void useLocatedStation(Station station, StationLocator.Position position) {
        useStation(station, StationDirectoryScreen.Target.ORIGIN);
        originLocation.selected(station.id(), position);
        locationMessage = "";
    }
    private void locateOrigin() {
        originLocation.attempted();
        var position = playerPosition();
        if (position == null) { locationMessage = tr("mtrnav.location_no_player"); return; }
        if (catalog == null || !catalog.dimension().equals(position.dimension()) || catalog.stations().isEmpty()) {
            locationMessage = tr("mtrnav.location_loading"); return;
        }
        var candidates = StationLocator.nearby(catalog, locatedStations(), position);
        if (candidates.isEmpty()) { locationMessage = tr("mtrnav.location_no_coordinates"); return; }
        if (StationLocator.unambiguous(candidates)) {
            useLocatedStation(candidates.getFirst().station(), position); rebuild();
        } else minecraft.setScreen(new StationDirectoryScreen(this, position));
    }
    @Override public void tick() {
        if (view == View.JOURNEYS && !collapsed && tracking == null && originLocation.ready(!origin.isEmpty(), fromBox.getValue().isBlank(),
                playerPosition() != null, catalog != null && !catalog.stations().isEmpty())) {
            locateOrigin(); if (minecraft.screen != this) return;
        }
        if (locationCatalog != catalog) {
            locationCatalog = catalog;
            var position = playerPosition();
            if (position != null && originLocation.positionFor(origin, position.dimension()) != null
                    && (catalog == null || !catalog.dimension().equals(position.dimension()) || catalog.station(origin) == null
                    || locatedStations() != null && !locatedStations().contains(origin))) originLocation.edited();
        }
        if (origin.isEmpty()) edited(true, fromBox.getValue());
        if (destination.isEmpty()) edited(false, toBox.getValue());
        if (selectedBoard.isEmpty()) selectedBoard = resolve(boardBox.getValue());
        if (!pendingSelection.isEmpty() && tracking != null && tracking.journey().id().equals(pendingSelection)) { pendingSelection = ""; changeView(View.ACTIVE); }

        if (lastJourneys != journeys) {
            lastJourneys = journeys; selectedJourney = 0; scroll = cardScroll = 0;
            if (view == View.JOURNEYS && !journeys.isEmpty()) {
                if (!searchCollapsed && panelHeight < 310) searchCollapsed = true;
                rebuild();
            }
        }
        if (view == View.ACTIVE && tracking == null) changeView(View.JOURNEYS);
        updateControls();
    }
    private void updateControls() {
        boolean active = view == View.ACTIVE && tracking != null;
        boolean retry = usingNative && status.equals("mtrnav.native_unavailable");
        moreButton.primary = reminder != null;
        walkingButton.visible = view == View.JOURNEYS && !collapsed;
        vehicleButton.visible = walkingButton.visible; vehicleButton.selected = vehicleCondition.active();
        allStationsButton.visible = !collapsed && !active;
        allStationsButton.active = catalog != null && !catalog.stations().isEmpty();
        locateButton.visible = view == View.JOURNEYS && !collapsed;
        locateButton.active = playerPosition() != null;
        for (int i = 0; i < clearButtons.size(); i++) {
            var box = inputBox(i); clearButtons.get(i).visible = box.visible;
            clearButtons.get(i).active = !box.getValue().isEmpty();
        }
        routesTab.selected = view == View.JOURNEYS; departuresTab.selected = view == View.DEPARTURES;
        activeTab.visible = true; activeTab.active=tracking!=null; activeTab.selected = active;
        queryButton.visible = !active;
        queryButton.active = retry || (view == View.DEPARTURES ? !selectedBoard.isEmpty() : collapsed || catalog != null && !origin.isEmpty() && !destination.isEmpty() && !status.equals("mtrnav.searching"));
        queryButton.setMessage(Component.translatable(retry ? "mtrnav.retry_service" : view == View.DEPARTURES ? "mtrnav.show_board" : collapsed ? "mtrnav.edit_stations" : status.equals("mtrnav.searching") ? "mtrnav.searching_short" : "mtrnav.search"));
        sortButton.visible = view == View.JOURNEYS; sortButton.setMessage(Component.literal(tr(sort == Sort.FASTEST ? "mtrnav.fastest" : "mtrnav.fewest") + " ▾"));
        sortButton.setTooltip(Tooltip.create(Component.translatable("mtrnav.sort_hint")));
        startButton.visible = !active;
        startButton.setMessage(Component.translatable(view == View.DEPARTURES ? "mtrnav.refresh_board" : "mtrnav.start"));
        startButton.active = view == View.DEPARTURES ? !boardStation.isEmpty() && !boardLoading : !journeys.isEmpty() && !CLOCK.stale(System.nanoTime());
        progressButton.visible = backButton.visible = requeryButton.visible = cancelButton.visible = active;
        if (active) {
            var phase = tracking.phase();
            progressButton.setMessage(Component.translatable(phase == JourneyTracker.Phase.COMPLETE ? "mtrnav.complete" : phase == JourneyTracker.Phase.RIDING ? "mtrnav.alighted" : phase == JourneyTracker.Phase.WALKING ? "mtrnav.walk_arrived" : "mtrnav.boarded"));
            progressButton.active = phase != JourneyTracker.Phase.COMPLETE;
            progressButton.setTooltip(Tooltip.create(Component.translatable(phase == JourneyTracker.Phase.RIDING ? "mtrnav.confirm_alighted" : phase == JourneyTracker.Phase.WALKING ? "mtrnav.walk_arrived" : "mtrnav.confirm_boarded")));
            backButton.active = tracking.stepIndex() > 0 || phase == JourneyTracker.Phase.RIDING; requeryButton.active = phase != JourneyTracker.Phase.COMPLETE;
        }
        listToggle.visible=detailToggle.visible=!wide&&view==View.JOURNEYS;
        listToggle.selected=!showDetails;detailToggle.selected=showDetails;
        selectedJourney = Math.max(0, Math.min(selectedJourney, journeys.size() - 1));
        int count = journeys.isEmpty() && referencePlan != null ? 1 : journeys.size();
        cardScroll = Math.clamp(cardScroll, 0, Math.max(0, count * (cardHeight + 4) - Math.max(0, cardBottom - bodyTop)));
        for (int i = 0; i < cards.size(); i++) {
            var b = cards.get(i); b.setY(bodyTop + i * (cardHeight + 4) - cardScroll); b.visible = b.getY() < cardBottom && b.getY() + cardHeight > bodyTop && view == View.JOURNEYS && (wide||!showDetails) && (i < journeys.size() || i == 0 && journeys.isEmpty() && referencePlan != null);
            if (!b.visible) continue;
            if (journeys.isEmpty()) {
                b.selected = true; b.setMessage(Component.literal(tr("mtrnav.reference_route")));
                b.detail = tr("mtrnav.transfers", referencePlan.transfers());
                b.extra = referencePlan.legs().stream().map(l -> routeCode(l.routeId())).reduce((a, c) -> a + " → " + c).orElse(tr("mtrnav.same_station"));
                b.chain=JourneyChain.of(referencePlan);b.durationLabel="—";b.arrivalLabel=tr("mtrnav.reference_only");b.badges=referencePlan.legs().stream().map(l->routeCode(l.routeId())).toList();b.badgeColors=referencePlan.legs().stream().map(l->routeColor(l.routeId())).toList();b.bestLabel=null;
                b.tint = ACCENT; b.setTooltip(Tooltip.create(Component.translatable(referenceReason))); continue;
            }
            var journey = journeys.get(i); b.chain=JourneyChain.of(journey); b.selected = selectedJourney == i;
            b.setMessage(Component.literal(tr("mtrnav.option",i+1)));
            b.durationLabel=Long.toString(Math.max(1,(journey.arrival()-journey.queryTime()+59999)/60000))+tr("mtrnav.ui.minute_unit");
            b.arrivalLabel=clockTime(journey.arrival())+" "+tr("mtrnav.arrival")+" · "+(journey.legs().isEmpty()?"—":clockTime(journey.legs().getFirst().departure()))+" "+tr("mtrnav.departure");
            b.badges=journey.legs().stream().map(l->routeCode(l.routeId())).toList();b.badgeColors=journey.legs().stream().map(l->routeColor(l.routeId())).toList();
            b.bestLabel=fastestArrival>0&&journey.arrival()==fastestArrival?tr("mtrnav.walk_fastest"):i==0&&sort==Sort.FEWEST_TRANSFERS?tr("mtrnav.fewest"):null;
            b.detail = tr("mtrnav.transfers", journey.transfers()) + " · " + (vehicleCondition.active() ? tr(vehicleCondition.matches(journey) ? "mtrnav.vehicle_match" : "mtrnav.vehicle_fallback") : journey.legs().stream().map(l -> VehicleNames.category(catalog.routeType(l.routeId()))).distinct().collect(java.util.stream.Collectors.joining(" / ")));
            b.extra = journey.legs().stream().map(l -> routeCode(l.routeId())).reduce((a, c) -> a + " → " + c).orElse(tr("mtrnav.same_station"));
            if (journey.arrival() > fastestArrival && fastestArrival > 0) b.extra += " · " + tr("mtrnav.walk_slower", (journey.arrival() - fastestArrival + 59999) / 60000);
            b.tint = journey.legs().isEmpty() ? ACCENT : routeColor(journey.legs().getFirst().routeId());
            b.setTooltip(Tooltip.create(Component.literal(b.getMessage().getString() + " · " + b.detail + " · " + b.extra)));
        }
        detailTop = bodyTop;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if(V2Review.enabled()&&key==296){V2Review.cycle(this);return true;}
        if(V2Review.enabled()&&key==295){minecraft.setScreen(new HudSettingsScreen(this));return true;}
        if(key==294){nextSkin();rebuild();return true;}
        if (key == 258) {
            var previous = getFocused(); boolean handled = super.keyPressed(key, scan, modifiers);
            if (getFocused() instanceof EditBox box && getFocused() != previous) selectStationText(box);
            return handled;
        }
        if ((key == 264 || key == 265) && view == View.JOURNEYS && !journeys.isEmpty() && !(getFocused() instanceof EditBox)) {
            selectedJourney = Math.clamp(selectedJourney + (key == 264 ? 1 : -1), 0, journeys.size() - 1); scroll = 0;
            int row = selectedJourney * (cardHeight + 4);
            if (row < cardScroll) cardScroll = row; else if (row + cardHeight > cardScroll + cardBottom - bodyTop) cardScroll = row + cardHeight - (cardBottom - bodyTop);
            return true;
        }
        if ((key == 257 || key == 335) && queryButton.visible && queryButton.active && !collapsed && !(getFocused() instanceof Action)) { search(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        for(var clear:clearButtons)if(clear.visible&&clear.isMouseOver(x,y))return clear.mouseClicked(x,y,button);
        if (button == 0) for (var box : List.of(fromBox, toBox, boardBox))
            if (box.visible && box.isMouseOver(x, y) && !box.isFocused()) {
                boolean handled = super.mouseClicked(x, y, button); selectStationText(box); return handled;
            }
        if (button == 0) for (var target : scrollTargets) if (target.contains(x, y)) {
            var bar = target.geometry(); draggingScroll = target.input();
            scrollGrab = bar.onThumb(y) ? y - bar.thumbTop() : bar.thumbHeight() / 2.0;
            dragScroll(target, y); return true;
        }
        if (button == 0 && view == View.JOURNEYS && y >= bodyTop && y < cardBottom) for (var card : cards)
            if (card.visible && card.isMouseOver(x, y)) return card.mouseClicked(x, y, button);
        // Handle these directly so the container cannot focus the button after
        // the action has returned focus to the newly cleared input field.
        for (var b : clearButtons) if (b.visible && b.isMouseOver(x, y)) return b.mouseClicked(x, y, button);
        if (button == 0) for (var target : boardTargets) if (target.contains(x, y)) {
            if (!expandedLines.add(target.key())) expandedLines.remove(target.key()); return true;
        }
        if(button==0)for(var control:List.of(reminderMinus,reminderPlus,reminderArm,reminderCancel))if(control.visible&&control.isMouseOver(x,y))return control.mouseClicked(x,y,button);
        if (button == 0) for (var target : departureTargets) if (target.contains(x, y)) {
            int lead = reminder != null && reminder.stationId().equals(boardStation) && ArrivalReminder.sameEvent(reminder.event(), target.event()) ? reminder.minutes() : ArrivalReminder.DEFAULT_MINUTES;
            inlineReminder=target.event();inlineMinutes=lead;scroll=Math.max(0,scroll+Math.max(0,target.y()+78-bodyBottom));return true;
        }
        if (button == 0) for (var target : stationTargets) if (target.contains(x, y)) { openBoard(target.id()); return true; }
        int contentX = wide && view == View.JOURNEYS ? detailX : left + 12;

        return super.mouseClicked(x, y, button);
    }
    private static void selectStationText(EditBox box) { box.setCursorPosition(box.getValue().length()); box.setHighlightPos(0); }
    private void dragScroll(ScrollTarget target, double mouseY) {
        int offset = target.geometry().offsetAtThumbTop(mouseY - scrollGrab);
        if (target.input() == -3) cardScroll = offset; else scroll = offset;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (draggingScroll != -2 && button == 0) {
            for (var target : scrollTargets) if (target.input() == draggingScroll) { dragScroll(target, y); break; }
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && draggingScroll != -2) { draggingScroll = -2; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { if (view == View.DEPARTURES) stopBoard(); super.removed(); }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta) {
        mouseX=OreReview.mouseX(mouseX);mouseY=OreReview.mouseY(mouseY);
        renderBackground(g,mouseX,mouseY,delta);updateControls();
        for(var disclosure:stopControls.values())disclosure.visible=false;
        OreUi.frame(g,left,top,panelWidth,panelHeight,wide?28:24);
        int x=left+12,inner=panelWidth-24;
        String brand=tr("mtrnav.ui.brand");
        text(g,brand,left+(panelWidth-font.width(brand))/2,top+(wide?10:8),panelWidth-112,OreUi.INK);
        g.fill(left+1,top+(wide?29:25),left+panelWidth-1,fieldY-4,SUNKEN);
        g.fill(left+1,fieldY-2,left+panelWidth-1,Math.max(fieldY,bodyTop-(wide||view!=View.JOURNEYS||collapsed?6:26)),PANEL);
        if(!collapsed){
            String message=view==View.DEPARTURES?tr("mtrnav.board_search_hint"):journeys.isEmpty()?tr(status):tr("mtrnav.options_found",journeys.size());
            if(!locationMessage.isEmpty()&&view==View.JOURNEYS)message=locationMessage;
            int messageX=vehicleButton.visible?vehicleButton.getX()+vehicleButton.getWidth()+8:allStationsButton.getX()+allStationsButton.getWidth()+8;
            int limit=view==View.JOURNEYS?sortButton.getX()-6:left+panelWidth-12;
            text(g,message,messageX,toolbarY+5,limit-messageX,FAINT);
        }else if(wide||view==View.ACTIVE){
            String label=view==View.ACTIVE&&tracking!=null?station(tracking.journey().originStationId())+" → "+station(tracking.journey().destinationStationId()):station(origin)+" → "+station(destination);
            text(g,label,left+88,fieldY+6,Math.max(0,sortButton.getX()-left-94),TEXT);
        }
        lining(g,left+3,bodyTop-5,panelWidth-6,bodyBottom-bodyTop+6);
        if(wide&&view==View.JOURNEYS){
            lining(g,left+3,bodyTop-5,detailX-left-9,bodyBottom-bodyTop+6);
            detailPanel(g,detailX-2,bodyTop-2,left+panelWidth-10-detailX,bodyBottom-bodyTop+3);
        }
        stationTargets.clear();boardTargets.clear();departureTargets.clear();scrollTargets.clear();
        for(var b:List.of(reminderMinus,reminderLead,reminderPlus,reminderArm,reminderCancel))b.visible=false;
        if(view==View.DEPARTURES)renderBoard(g,mouseX,mouseY);else if(wide||showDetails||journeys.isEmpty()&&referencePlan==null||view==View.ACTIVE)renderJourney(g,mouseX,mouseY);
        if(view==View.JOURNEYS&&(wide||!showDetails)&&(!journeys.isEmpty()||referencePlan!=null)){
            int cw=wide?detailX-left-18:panelWidth-24;
            int content=(journeys.isEmpty()?1:journeys.size())*(cardHeight+4);
            if(content>cardBottom-bodyTop){
                var geometry=ScrollBarGeometry.of(bodyTop,Math.max(1,cardBottom-bodyTop),content,Math.max(1,cardBottom-bodyTop),cardScroll,8);
                var target=new ScrollTarget(-3,left+12+cw-4,geometry);scrollTargets.add(target);drawScrollbar(g,target);
            }
        }
        g.fill(left+3,footerY-1,left+panelWidth-3,top+panelHeight-3,PANEL);railDivider(g,left+3,footerY-1,panelWidth-6);
        if(view!=View.ACTIVE){String summary=view==View.DEPARTURES?tr("mtrnav.board_summary",arrivals.size()):journeys.isEmpty()?tr(referencePlan==null?"mtrnav.search_help":"mtrnav.reference_only"):tr("mtrnav.option_summary",selectedJourney+1,journeys.size());text(g,summary,x,footerY+11,panelWidth-40-startButton.getWidth(),MUTED);}
        for(var widget:widgets)if(widget.visible){
            if(cards.contains(widget)){g.enableScissor(left+12,bodyTop,wide?detailX-8:left+panelWidth-12,Math.max(bodyTop,cardBottom));widget.render(g,mouseX,mouseY,delta);g.disableScissor();}
            else widget.render(g,mouseX,mouseY,delta);
        }
        for(var target:stationTargets)if(target.contains(mouseX,mouseY)){g.renderTooltip(font,Component.translatable("mtrnav.station_board_hint",station(target.id())),mouseX,mouseY);break;}
        if(V2Review.enabled())reviewAudit();
        if(Boolean.getBoolean("mtrnav.v2Review"))text(g,tr("mtrnav.ui.test_data"),left+12,top+panelHeight+3,panelWidth-24,0xFFFFFFFF);
    }
    private String lastAudit="";
    private void reviewAudit(){
        var values=new java.util.LinkedHashMap<String,Object>();values.put("viewport",List.of(width,height));values.put("scale",minecraft.getWindow().getGuiScale());values.put("skin",skin.name());values.put("view",view.name());
        values.put("frame",List.of(left,top,panelWidth,panelHeight));values.put("body",List.of(left+12,bodyTop,panelWidth-24,bodyBottom-bodyTop));
        var controls=new java.util.ArrayList<java.util.Map<String,Object>>();
        for(var widget:widgets)if(widget.visible&&!cards.contains(widget))controls.add(java.util.Map.of("label",widget.getMessage().getString(),"rect",List.of(widget.getX(),widget.getY(),widget.getWidth(),widget.getHeight())));
        values.put("controls",controls);values.put("visibleCards",cards.stream().filter(c->c.visible).count());
        String json=new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(values);
        if(json.equals(lastAudit))return;lastAudit=json;
        try{java.nio.file.Files.writeString(minecraft.gameDirectory.toPath().resolve("mtrnav-v2-audit.json"),json);}catch(java.io.IOException e){throw new IllegalStateException("Cannot save v2 review audit",e);}
    }
    private static String clockTime(long value){
        if(CLOCK.stale(System.nanoTime()))return "—";
        return java.time.Instant.ofEpochMilli(value).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }
    private void renderJourney(GuiGraphics g, int mouseX, int mouseY) {
        int contentX = wide && view == View.JOURNEYS ? detailX : left + 12;
        int contentWidth = left + panelWidth - 12 - contentX;
        if(view==View.JOURNEYS&&journeys.isEmpty()&&referencePlan==null){
            int cx=left+panelWidth/2,cy=bodyTop+Math.max(15,(bodyBottom-bodyTop)/2-30);
            routeSketch(g,cx-51,cy-16);
            String label=tr("mtrnav.ui.empty_title");text(g,label,Math.max(left+20,cx-font.width(label)/2),cy+30,panelWidth-40,TEXT);
            String hint=tr("mtrnav.ui.empty_hint");text(g,hint,Math.max(left+20,cx-font.width(hint)/2),cy+46,panelWidth-40,MUTED);
            return;
        }
        var rows = rows(contentWidth - 26); contentHeight = rows.stream().mapToInt(Row::height).sum();
        int viewport = Math.max(0, bodyBottom - detailTop); clampScroll(viewport);
        if (viewport <= 0) return;
        g.enableScissor(contentX, detailTop, contentX + contentWidth, bodyBottom);
        drawJourneyRails(g,rows,contentX,detailTop-scroll);
        int y=detailTop-scroll,railTint=0;boolean summary=true;
        for(var row:rows){
            if(row.kind==1||row.kind==3)summary=false;
            if(summary)g.fill(contentX+1,y,contentX+contentWidth-5,y+row.height,skin==Skin.VANILLA?0xFFCCCCCC:PANEL);
            if(row.kind==1){
                g.fill(contentX+2,y,contentX+contentWidth-5,y+row.height,skin==Skin.VANILLA?0xFFD2D2D2:PANEL);
                railTint=row.color;int split=row.text.indexOf(" · ");String code=split<0?row.text:row.text.substring(0,split);
                int bw=badge(g,code,contentX+8,y+5,Math.min(contentWidth-16,100),row.color);
                if(split>=0)text(g,row.text.substring(split+3),contentX+bw+15,y+7,contentWidth-bw-25,MUTED);
                g.fill(contentX+8,y,contentX+contentWidth-7,y+1,SOFT);
            }
            if(row.kind==3)railTint=0;

            if(row.kind==3){
                g.fill(contentX+18,y+2,contentX+contentWidth-7,y+row.height-2,skin==Skin.VANILLA?0xFFB5B5B5:RAISED);
                outline(g,contentX+18,y+2,contentWidth-25,row.height-4,skin==Skin.VANILLA?0xFF888888:BORDER);
                mark(g,Mark.WALK,contentX+23,y+8,skin==Skin.VANILLA?0xFF3A6147:0xFF8FBF9A);
            }
            if(row.kind==4){
                int split=row.text.indexOf(" · ");String main=split<0?row.text:row.text.substring(0,split);
                large(g,main,contentX+8,y+5,contentWidth-16,TEXT,1.5f);
                if(split>=0){int tx=contentX+16+(int)(font.width(main)*1.5);text(g,row.text.substring(split+3),tx,y+8,contentX+contentWidth-tx-8,MUTED);}
            }
            if(row.kind==2){g.fill(contentX+6,y+6,contentX+14,y+14,SUNKEN);outline(g,contentX+6,y+6,8,8,row.color);outline(g,contentX+7,y+7,6,6,row.color);}
            if (row.kind!=5 && !row.stationId.isEmpty() && y + row.height > detailTop && y < bodyBottom) {
                var target = new StationTarget(row.stationId, contentX + 16, Math.max(y, detailTop), contentWidth - 20, Math.min(bodyBottom, y + row.height) - Math.max(y, detailTop));
                stationTargets.add(target); if (target.contains(mouseX, mouseY)) g.fill(contentX + 16, y, contentX + contentWidth - 4, y + row.height, RAISED);
            }
            if(row.kind==5){
                var control=stopControls.computeIfAbsent(row.stationId,key->{
                    var b=new Action(0,0,1,22,"",()->{if(!expandedStops.add(key))expandedStops.remove(key);});
                    b.disclosure=true;return addRenderableWidget(b);
                });
                control.setX(contentX+18);control.setY(y);control.setWidth(contentWidth-30);control.setMessage(Component.literal(row.text));
                control.selected=expandedStops.contains(row.stationId);control.visible=y>=detailTop&&y+22<=bodyBottom;
                if(control.visible)control.render(g,mouseX,mouseY,0);
            }
            if(!row.text.isEmpty()&&row.kind!=1&&row.kind!=2&&row.kind!=4&&row.kind!=5)text(g,row.text,contentX+(row.kind==2?20:row.kind==3?38:20),y+(row.kind==2||row.kind==3?5:2),contentWidth-(row.time.isEmpty()?30:85),row.kind==2?TEXT:row.color);
            if(row.kind==2)stationLine(g,row,contentX+20,y+6,contentWidth-30);
            if(!row.time.isEmpty())text(g,row.time,contentX+contentWidth-52,y+7,47,MUTED);
            y += row.height;
        }
        g.disableScissor(); scrollbar(g, contentX + contentWidth - 3, detailTop, viewport);
    }
    private void drawJourneyRails(GuiGraphics g,List<Row> rows,int x,int topY) {
        int y=topY,first=-1,last=-1,tint=0;
        for(var row:rows){
            if(row.kind==1||row.kind==3){
                if(first>=0&&last>first)g.fill(x+9,first,x+11,last,tint);
                first=last=-1;tint=row.kind==1?row.color:0;
            }
            if(row.kind==2&&tint!=0){if(first<0)first=y+10;last=y+10;}
            y+=row.height;
        }
        if(first>=0&&last>first)g.fill(x+9,first,x+11,last,tint);
    }
    private void stationLine(GuiGraphics g,Row row,int x,int y,int w){
        String[] parts=row.text.split(" · ",2);String name=parts[0],tag=parts.length>1?parts[1]:"";
        int timeSpace=row.time.isEmpty()?0:52;
        int tagWidth=tag.isEmpty()?0:Math.min(Math.min(112,w/3),font.width(tag)+10);
        int nameSpace=Math.max(8,w-timeSpace-tagWidth-(tagWidth>0?8:0));
        String shown=name;
        while(!shown.isEmpty()&&font.width(Component.literal(shown+(shown.equals(name)?"":"…")).withStyle(net.minecraft.ChatFormatting.BOLD))>nameSpace)shown=shown.substring(0,shown.offsetByCodePoints(shown.length(),-1));
        if(!shown.equals(name))shown+="…";
        var label=Component.literal(shown).withStyle(net.minecraft.ChatFormatting.BOLD);g.drawString(font,label,x,y,TEXT,false);
        if(tagWidth>0){
            int tx=x+font.width(label)+8;
            g.fill(tx,y-2,tx+tagWidth,y+11,skin==Skin.VANILLA?0xFFC1C1C1:RAISED);outline(g,tx,y-2,tagWidth,13,skin==Skin.VANILLA?0xFF919191:BORDER);
            text(g,tag,tx+5,y,tagWidth-10,MUTED);
        }
    }
    private boolean boardStale() { return !boardFresh(); }
    private String boardTime(long time) { return boardStale() ? "—" : remaining(time); }
    private boolean inlineFor(StopEvent event){return inlineReminder!=null&&ArrivalReminder.sameEvent(inlineReminder,event);}
    private void renderBoard(GuiGraphics g,int mx,int my){
        int x=left+12,w=panelWidth-24,right=x+w,rowTop=bodyTop+2;
        if(catalog==null||boardStation.isEmpty()){wrapped(g,tr(catalog==null?status:"mtrnav.choose_board_station"),x+8,rowTop+10,w-16,MUTED);return;}
        if(!lastBoardStation.equals(boardStation)){lastBoardStation=boardStation;groupedArrivals=null;boardGroups=List.of();expandedLines.clear();inlineReminder=null;scroll=0;}
        if(groupedCatalog!=catalog||groupedArrivals!=arrivals){
            groupedCatalog=catalog;groupedArrivals=arrivals;boolean initial=boardGroups.isEmpty();boardGroups=DepartureBoard.groups(catalog,boardStation,arrivals);
            if(initial)boardGroups.stream().limit(2).forEach(group->expandedLines.add(group.key()));
        }
        int viewport=Math.max(0,bodyBottom-rowTop);
        contentHeight=0;
        for(var group:boardGroups){contentHeight+=28;if(expandedLines.contains(group.key()))for(var e:group.events())contentHeight+=46+(inlineFor(e)?44:0);}
        clampScroll(viewport);
        if(boardGroups.isEmpty()){wrapped(g,tr(boardLoading?"mtrnav.board_loading":"mtrnav.no_departures"),x+6,rowTop+8,w-12,MUTED);return;}
        if(viewport<=0)return;
        g.enableScissor(x,rowTop,right,bodyBottom);int y=rowTop-scroll;
        for(var group:boardGroups){
            int tint=0xFF000000|group.color()&0xFFFFFF;boolean expanded=expandedLines.contains(group.key());
            g.fill(x,y,right-8,y+24,PANEL);outline(g,x,y,w-8,24,SOFT);
            int bw=badge(g,primary(group.name())+" · "+VehicleNames.category(group.routeType()),x+6,y+6,Math.min(135,w/3),tint);
            String next=group.events().isEmpty()?"—":boardTime(group.events().getFirst().departure());
            text(g,tr("mtrnav.line_departures",group.events().size(),next),x+bw+15,y+8,w-bw-40,MUTED);
            text(g,expanded?"−":"+",right-22,y+8,12,MUTED);
            if(y>=rowTop&&y+24<=bodyBottom)boardTargets.add(new BoardTarget(group.key(),x,y,w-8,24));
            y+=28;if(!expanded)continue;
            for(var event:group.events()){
                boolean chosen=inlineFor(event),armed=reminder!=null&&reminder.stationId().equals(boardStation)&&ArrivalReminder.sameEvent(reminder.event(),event);
                if(y+46>rowTop&&y<bodyBottom){
                    if(chosen)g.fill(x+4,y,right-8,y+45,RAISED);else lining(g,x+4,y,w-12,45);g.fill(x+4,y+44,right-8,y+45,SOFT);
                    long leftMillis=event.arrival()-CLOCK.now(System.nanoTime());
                    large(g,boardTime(event.arrival()),x+12,y+10,65,!boardStale()&&leftMillis<=180000?ACCENT:TEXT,1.4f);
                    int px=right-(wide?112:86),stateX=right-42;
                    text(g,tr("mtrnav.direction",primary(event.destination())),x+84,y+6,Math.max(0,px-x-92),TEXT);
                    text(g,station(boardStation)+" · "+clockTime(event.departure()),x+84,y+20,Math.max(0,px-x-92),FAINT);
                    text(g,platform(event.platformId()),px,y+12,stateX-px-5,MUTED);
                    text(g,tr(event.realtime()?"mtrnav.live_short":"mtrnav.schedule_short"),stateX,y+12,28,event.realtime()?skin==Skin.VANILLA?0xFF1F6B38:0xFF7ECF97:FAINT);
                    var target=new DepartureTarget(event,x+4,Math.max(rowTop,y),w-12,Math.min(y+45,bodyBottom)-Math.max(rowTop,y));
                    if(target.h()>0)departureTargets.add(target);
                    if(target.contains(mx,my)||armed)outline(g,x+4,y,w-12,45,ACCENT);
                }
                text(g, VehicleNames.stock(event.consist()), x + 84, y + 32, w - 100, MUTED);
                y+=46;
                if(chosen){
                    g.fill(x+4,y,right-8,y+40,RAISED);outline(g,x+4,y,w-12,40,SOFT);
                    text(g,tr("mtrnav.reminder_title")+" · "+primary(event.destination()),x+12,y+3,w-28,TEXT);
                    if(y>=rowTop&&y+40<=bodyBottom){
                        for(var control:List.of(reminderMinus,reminderLead,reminderPlus,reminderArm,reminderCancel)){control.setY(y+17);control.visible=true;}
                        reminderLead.setMessage(Component.translatable("mtrnav.reminder_lead",inlineMinutes));
                        reminderArm.active=boardFresh()&&event.departure()>=CLOCK.now(System.nanoTime());
                    }
                    y+=44;
                }
            }
        }
        g.disableScissor();scrollbar(g,right-4,rowTop,viewport);
    }
    private void clampScroll(int viewport) { scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - viewport))); }
    private void scrollbar(GuiGraphics g, int x, int y, int viewport) {
        if (contentHeight <= viewport || viewport <= 0) return;
        drawScrollbar(g, new ScrollTarget(-1, x, ScrollBarGeometry.of(y, viewport, contentHeight, viewport, scroll, 8)));
    }
    private void drawScrollbar(GuiGraphics g, ScrollTarget target) {
        scrollTargets.add(target); var geometry = target.geometry(); int x = target.x();
        g.fill(x, geometry.top(), x + 4, geometry.top() + geometry.height(), BORDER);
        g.fill(x, geometry.thumbTop(), x + 4, geometry.thumbTop() + geometry.thumbHeight(), ACCENT);
    }
    private List<Row> rows(int width) {
        var rows = new ArrayList<Row>();
        if (catalog == null) { wrap(rows, tr(status), width, MUTED); return rows; }
        Journey journey;
        if (view == View.ACTIVE && tracking != null) {
            journey = tracking.journey();
            rows.add(new Row(tr("mtrnav.phase." + tracking.phase().name().toLowerCase(Locale.ROOT)), ACCENT, 21, 1));
            if (tracking.phase() == JourneyTracker.Phase.COMPLETE) wrap(rows, tr("mtrnav.arrived_at", station(journey.destinationStationId())), width, ACCENT);
            else if (tracking.walk() != null) {
                var walk = tracking.walk();
                wrap(rows, tr("mtrnav.walk_to", station(walk.toStationId())), width, TEXT);
                if (!walk.toPlatformId().isEmpty()) wrap(rows, tr("mtrnav.platform", platform(walk.toPlatformId())), width, TEXT);
                wrap(rows, tr("mtrnav.walk_estimated", walk.seconds()), width, ACCENT);
                if (tracking.legIndex() < journey.legs().size()) wrap(rows, tr("mtrnav.walk_next_departure", remaining(journey.legs().get(tracking.legIndex()).departure())), width, MUTED);
                wrap(rows, guidance(), width, WARNING);
            } else if (tracking.legIndex() < journey.legs().size()) {
                var leg = journey.legs().get(tracking.legIndex()); boolean riding = tracking.phase() == JourneyTracker.Phase.RIDING;
                wrap(rows, tr(riding ? "mtrnav.next_alight" : "mtrnav.next_board", station(riding ? leg.toStationId() : leg.fromStationId())), width, TEXT);
                wrap(rows, tr("mtrnav.platform", platform(riding ? leg.toPlatformId() : leg.fromPlatformId())) + " · " + tr(riding ? "mtrnav.arrival" : "mtrnav.departure") + " " + remaining(riding ? leg.arrival() : leg.departure()), width, ACCENT);
                if (!guidance().isEmpty()) wrap(rows, guidance(), width, WARNING);
            }
        } else {
            if (journeys.isEmpty()) {
                if (referencePlan != null) return referenceRows(width);
                wrap(rows, tr(status), width, TEXT); wrap(rows, tr("mtrnav.empty_help"), width, MUTED); return rows;
            }
            journey = journeys.get(selectedJourney);
            rows.add(new Row(duration(journey.arrival()-journey.queryTime())+" · "+tr("mtrnav.transfers",journey.transfers()),TEXT,25,4));
            wrap(rows, station(journey.originStationId()) + " → " + station(journey.destinationStationId()) + " · " + tr("mtrnav.arrival") + " " + clockTime(journey.arrival()), width, TEXT);
        }
        if (journey.legs().isEmpty()) wrap(rows, tr("mtrnav.same_station"), width, TEXT);
        if (view != View.ACTIVE) {

            if (partialResults) wrap(rows, tr("mtrnav.partial_results"), width, WARNING);
            if (fastestArrival > 0 && journey.arrival() > fastestArrival) wrap(rows, tr("mtrnav.walk_slower", (journey.arrival() - fastestArrival + 59999) / 60000), width, MUTED);
        }
        for (int i = 0; i <= journey.legs().size(); i++) {
            for (var walk : journey.walks()) if (walk.beforeRideIndex() == i) walkRows(rows, walk, width);
            if (i == journey.legs().size()) break;
            var leg = journey.legs().get(i); int tint = routeColor(leg.routeId());
            if (i > 0 || journey.walks().stream().anyMatch(w -> w.beforeRideIndex() == 0)) {
                final int before = i;
                long walked = journey.walks().stream().filter(w -> w.beforeRideIndex() == before).mapToLong(w -> w.seconds() * 1000L).sum();
                long gap = leg.departure() - (i == 0 ? journey.queryTime() : journey.legs().get(i - 1).arrival()) - walked;
                long buffer = Math.min(Math.max(0, gap), journey.transferBufferSeconds() * 1000L);
                wrap(rows, tr("mtrnav.walk_buffer_wait", buffer / 1000, (Math.max(0, gap - buffer) + 999) / 1000), width, WARNING);
            }
            rows.add(new Row(routeCode(leg.routeId())+" · "+VehicleNames.category(catalog.routeType(leg.routeId()))+" · "+tr("mtrnav.direction",primary(leg.direction())),tint,23,1));

            wrap(rows, VehicleNames.stock(leg.consist()), width, MUTED);
            if (!leg.consist().cars().isEmpty()) {
                String stockKey = journey.id() + "/stock/" + i;
                rows.add(new Row(tr(expandedStops.contains(stockKey) ? "mtrnav.vehicle_details_collapse" : "mtrnav.vehicle_details"), MUTED, 22, 5, stockKey));
                if (expandedStops.contains(stockKey)) wrap(rows, VehicleNames.ordered(leg.consist()), width, FAINT);
            }
            rows.add(new Row(station(leg.fromStationId()) + " · " + tr("mtrnav.platform", platform(leg.fromPlatformId())), tint, 24, 2, leg.fromStationId(),clockTime(leg.departure())));
            wrap(rows, tr("mtrnav.departure") + " " + remaining(leg.departure()) + " · " + tr(leg.realtime() ? "mtrnav.realtime" : "mtrnav.scheduled"), width, MUTED);
            stopRows(rows,leg.viaStations(),journey.id()+"/"+i);
            rows.add(new Row(station(leg.toStationId()) + " · " + tr("mtrnav.platform", platform(leg.toPlatformId())), tint, 24, 2, leg.toStationId(),clockTime(leg.arrival())));
            wrap(rows, tr("mtrnav.arrival") + " " + remaining(leg.arrival()), width, MUTED); rows.add(new Row("", TEXT, 6, 0));
        }
        return rows;
    }
    private List<Row> referenceRows(int width) {
        var rows = new ArrayList<Row>();
        rows.add(new Row(tr("mtrnav.reference_route") + " · " + tr("mtrnav.transfers", referencePlan.transfers()), ACCENT, 21, 1));
        wrap(rows, tr(referenceReason), width, WARNING);
        wrap(rows, tr("mtrnav.reference_only"), width, MUTED);
        for (int i = 0; i <= referencePlan.legs().size(); i++) {
            for (var walk : referencePlan.walks()) if (walk.beforeRideIndex() == i) walkRows(rows, walk, width);
            if (i == referencePlan.legs().size()) break;
            var leg = referencePlan.legs().get(i); int tint = routeColor(leg.routeId());
            if (i > 0) wrap(rows, tr("mtrnav.reference_transfer"), width, WARNING);
            rows.add(new Row(routeCode(leg.routeId())+" · "+VehicleNames.category(catalog.routeType(leg.routeId()))+" · "+tr("mtrnav.direction",primary(leg.direction())),tint,23,1));

            rows.add(new Row(station(leg.fromStationId()) + " · " + tr("mtrnav.platform", platform(leg.fromPlatformId())), tint, 24, 2, leg.fromStationId()));
            stopRows(rows,leg.viaStations(),"reference/"+leg.routeId()+"/"+i);
            rows.add(new Row(station(leg.toStationId()) + " · " + tr("mtrnav.platform", platform(leg.toPlatformId())), tint, 24, 2, leg.toStationId()));
            rows.add(new Row("", TEXT, 6, 0));
        }
        return rows;
    }
    private void stopRows(List<Row> rows,List<String> stations,String key) {
        if(stations.isEmpty())return;
        rows.add(new Row(tr(expandedStops.contains(key)?"mtrnav.stops_collapse":"mtrnav.stops_count",stations.size()),MUTED,22,5,key));
        if(expandedStops.contains(key))for(var via:stations)rows.add(new Row(station(via),MUTED,20,2,via));
    }
    private void walkRows(List<Row> rows, WalkLeg walk, int width) {
        rows.add(new Row(tr("mtrnav.walk_to",station(walk.toStationId()))+" · "+tr("mtrnav.walk_estimated",walk.seconds()),MUTED,27,3));
        wrap(rows, station(walk.fromStationId()) + (walk.fromPlatformId().isEmpty() ? "" : " / " + platform(walk.fromPlatformId())) + " → "
                + station(walk.toStationId()) + (walk.toPlatformId().isEmpty() ? "" : " / " + platform(walk.toPlatformId())), width, TEXT);
        wrap(rows,tr("mtrnav.walk_source."+walk.source()),width,MUTED);
    }
    private void wrap(List<Row> rows, String text, int width, int color) {
        for (var line : font.split(Component.literal(text), Math.max(30, width))) {
            var plain = new StringBuilder(); line.accept((index, style, point) -> { plain.appendCodePoint(point); return true; }); rows.add(new Row(plain.toString(), color, 12, 0));
        }
    }
    private void wrapped(GuiGraphics g, String text, int x, int y, int width, int color) { for (var line : font.split(Component.literal(text), width)) { g.drawString(font, line, x, y, color, false); y += 13; } }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (view == View.JOURNEYS && x >= left + 12 && x < (wide ? detailX - 7 : left + panelWidth - 12) && y >= bodyTop && y < cardBottom) { cardScroll -= (int)(vertical * 38); return true; }
        if (x >= left && x <= left + panelWidth && y >= bodyTop && y <= bodyBottom) { scroll = Math.max(0, scroll - (int)(vertical * 26)); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
}
