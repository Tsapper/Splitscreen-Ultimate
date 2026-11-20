package tsapper.simple_controller_support.api;

import java.util.UUID;

/** Generic externally supplied gamepad. All methods require Minecraft's client thread. */
public interface ExternalController extends AutoCloseable {
    UUID connectionId();
    /** Samples expire after 500 ms. Button transitions are queued; redundant analog samples coalesce. */
    void submit(ControllerSample sample);
    /** Drops queued/held input immediately; a fresh neutral sample is required before reuse. */
    void clear();
    @Override void close();
}
