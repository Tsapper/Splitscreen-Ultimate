package tsapper.simple_controller_support.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Runtime-only input policy. External assignment never changes the user's saved preference. */
public record ControllerSelection(Mode mode, Optional<UUID> connectionId) {
    public enum Mode { STANDALONE, ASSIGNED, DISABLED }

    public ControllerSelection {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(connectionId, "connectionId");
        if ((mode == Mode.ASSIGNED) != connectionId.isPresent()) {
            throw new IllegalArgumentException("Only ASSIGNED requires a connection id");
        }
    }

    public static ControllerSelection standalone() {
        return new ControllerSelection(Mode.STANDALONE, Optional.empty());
    }

    public static ControllerSelection assigned(UUID connectionId) {
        return new ControllerSelection(Mode.ASSIGNED, Optional.of(connectionId));
    }

    public static ControllerSelection disabled() {
        return new ControllerSelection(Mode.DISABLED, Optional.empty());
    }

    /** Resolves this policy against an inventory without polling or changing input. */
    public Optional<ControllerInfo> resolve(List<ControllerInfo> controllers, int preferredGlfwSlot) {
        return switch (mode) {
            case DISABLED -> Optional.empty();
            case ASSIGNED -> controllers.stream()
                    .filter(info -> connectionId.orElseThrow().equals(info.connectionId())).findFirst();
            case STANDALONE -> controllers.stream().filter(info -> info.glfwSlot() >= 0 && info.glfwSlot() == preferredGlfwSlot)
                    .findFirst().or(() -> controllers.stream().filter(info -> info.glfwSlot() >= 0).findFirst());
        };
    }
}
