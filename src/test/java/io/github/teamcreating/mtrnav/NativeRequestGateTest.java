package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.NativeRequestGate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRequestGateTest {
    @Test void joiningAndIdleDoNotStartNativeTraffic() {
        var gate = new NativeRequestGate();
        assertFalse(gate.start(0)); assertFalse(gate.start(10_000_000_000L));
        assertTrue(gate.resume(10_000_000_000L)); assertTrue(gate.start(10_000_000_000L));
        assertFalse(gate.start(20_000_000_000L)); // One in-flight request, regardless of elapsed time.
        gate.complete(); assertTrue(gate.start(20_000_000_000L));
        gate.reset(); assertFalse(gate.start(30_000_000_000L));
    }
    @Test void failedServiceDoesNotAutomaticallyRetryEvenAfterCooldown() {
        var gate = new NativeRequestGate(); gate.resume(0); gate.start(0); gate.fail(1_000_000_000L);
        assertFalse(gate.resume(15_000_000_000L));
        assertFalse(gate.start(100_000_000_000L));
        assertTrue(gate.resume(100_000_000_000L)); assertTrue(gate.start(100_000_000_000L));
    }
    @Test void fastResponsesCannotCreateUnboundedRequestBursts() {
        var gate = new NativeRequestGate(); gate.resume(0); assertTrue(gate.start(0)); gate.complete();
        assertFalse(gate.start(499_999_999L)); assertTrue(gate.start(500_000_000L));
    }
}
