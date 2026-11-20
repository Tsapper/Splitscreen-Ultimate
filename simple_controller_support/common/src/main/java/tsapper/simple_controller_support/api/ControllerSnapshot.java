package tsapper.simple_controller_support.api;

import tsapper.simple_controller_support.client.controller.ControllerAxis;
import tsapper.simple_controller_support.client.controller.ControllerButton;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable raw input from the last poll. Triggers are 0..1; sticks are -1..1, before deadzones. */
public record ControllerSnapshot(ControllerInfo info, Map<ControllerAxis, Float> axes,
                                 Set<ControllerButton> down, Set<ControllerButton> pressed,
                                 Set<ControllerButton> released) {
    public ControllerSnapshot {
        Objects.requireNonNull(info, "info");
        axes = Map.copyOf(axes);
        down = Set.copyOf(down);
        pressed = Set.copyOf(pressed);
        released = Set.copyOf(released);
    }
}
