package io.github.teamcreating.mtrnav.api;

import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Client-thread API. Register via listen; close the returned handle to unsubscribe. No Overlay dependency. */
public final class NavigationApi {
    private NavigationApi() {}
    public enum Change { SELECTED, UPDATED, CANCELLED }
    @FunctionalInterface public interface Listener { void changed(Change change, Optional<JourneySelection> selection); }
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static volatile JourneySelection current;
    public static Optional<JourneySelection> current() { return Optional.ofNullable(current); }
    public static AutoCloseable listen(Listener listener) { listeners.add(listener); return () -> listeners.remove(listener); }
    public static void select(JourneySelection selection) {
        var change = current == null ? Change.SELECTED : Change.UPDATED;
        current = selection; dispatch(change);
    }
    public static void cancel() { if (current != null) { current = null; dispatch(Change.CANCELLED); } }
    private static void dispatch(Change change) {
        for (var listener : listeners) {
            try { listener.changed(change, current()); }
            catch (RuntimeException e) { System.getLogger("MTR Navigation API").log(System.Logger.Level.WARNING, "Map listener failed", e); }
        }
    }
}
