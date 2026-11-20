package tsapper.simple_controller_support.client.controller;

import java.util.ArrayDeque;
import tsapper.simple_controller_support.api.ControllerSample;

/** Bounded transition-preserving mailbox, independent of transport and session ownership. */
public final class ExternalSamples {
    private final ArrayDeque<ControllerSample> queue = new ArrayDeque<>();
    private ControllerSample current;
    private long lastSample;

    public void submit(ControllerSample sample, long now) {
        if (!queue.isEmpty() && queue.getLast().down().equals(sample.down())
                && java.util.Objects.equals(queue.getLast().axes().get(ControllerAxis.LEFT_TRIGGER), sample.axes().get(ControllerAxis.LEFT_TRIGGER))
                && java.util.Objects.equals(queue.getLast().axes().get(ControllerAxis.RIGHT_TRIGGER), sample.axes().get(ControllerAxis.RIGHT_TRIGGER))) queue.removeLast();
        if (queue.size() >= 128) {
            clear(); // Fail closed rather than replaying an unbounded input backlog.
            return;
        }
        queue.addLast(sample);
        lastSample = now;
    }

    public ControllerSample poll(long now) {
        if (now - lastSample > 500_000_000L) clear();
        if (!queue.isEmpty()) current = queue.removeFirst();
        return current;
    }

    public boolean pending() { return !queue.isEmpty(); }
    public void clear() { queue.clear(); current = null; lastSample = 0; }
}
