package tsapper.simple_controller_support.api;

import java.util.Objects;
import java.util.UUID;

/**
 * A gamepad connection observed by this Minecraft process. connectionId is valid only for this
 * observed connection; it is neither a persistent hardware id nor an id shared with other processes.
 * glfwSlot is diagnostic only. mappingGuid identifies a model/mapping, not a physical unit.
 */
public record ControllerInfo(UUID connectionId, int glfwSlot, String name, String mappingGuid) {
    public ControllerInfo {
        Objects.requireNonNull(connectionId, "connectionId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(mappingGuid, "mappingGuid");
    }
}
