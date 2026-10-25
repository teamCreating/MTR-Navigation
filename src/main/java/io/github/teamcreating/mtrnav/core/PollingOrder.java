package io.github.teamcreating.mtrnav.core;

/** Oldest overdue source first; slow requests cannot monopolize all native polling. */
public final class PollingOrder {
    public enum Job { NONE, DEPARTURES, BOARD, REMINDER }
    private PollingOrder() {}
    public static Job next(boolean departuresDue, long departuresAt, boolean boardDue, long boardAt,
                           boolean reminderDue, long reminderAt, boolean waitingQuery) {
        if (waitingQuery && departuresDue) return Job.DEPARTURES;
        Job job = Job.NONE; long oldest = Long.MAX_VALUE;
        if (departuresDue) { job = Job.DEPARTURES; oldest = departuresAt; }
        if (boardDue && boardAt < oldest) { job = Job.BOARD; oldest = boardAt; }
        if (reminderDue && reminderAt < oldest) job = Job.REMINDER;
        return job;
    }
}
