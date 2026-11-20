package tsapper.simple_controller_support.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable view of this instance. An ASSIGNED policy with no selectedController means its
 * connection is absent. controllerInputActive also depends on activity and keyboard/mouse mode.
 * All mapped gamepads are observed, even when input is disabled or assigned elsewhere.
 */
public record ControllerFrame(ControllerSelection selection, List<ControllerSnapshot> controllers,
                              Optional<UUID> selectedController, boolean controllerInputActive) {
    public ControllerFrame {
        Objects.requireNonNull(selection, "selection");
        controllers = List.copyOf(controllers);
        Objects.requireNonNull(selectedController, "selectedController");
    }
}
