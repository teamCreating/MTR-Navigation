package io.github.teamcreating.mtrnav.network;

import io.github.teamcreating.mtrnav.core.Model.VehicleCondition;
import java.util.Objects;

/** Additive capability: older servers never receive this envelope. */
public final class QueryV3 {
    private QueryV3() {}
    public record Request(QueryV2.Request query, VehicleCondition vehicles) {
        public Request { Objects.requireNonNull(query); Objects.requireNonNull(vehicles); }
    }
}
