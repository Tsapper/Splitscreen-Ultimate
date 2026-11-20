package tsapper.simple_controller_support.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ControllerSelectionTest {
    private final ControllerInfo first = info(0);
    private final ControllerInfo second = info(1);
    private final List<ControllerInfo> connected = List.of(first, second);

    private static ControllerInfo info(int slot) {
        // Identical models must still be independently assignable.
        return new ControllerInfo(UUID.randomUUID(), slot, "Identical gamepad", "shared-model-guid");
    }

    @Test
    void standaloneRetainsPreferenceAndFallback() {
        var standalone = ControllerSelection.standalone();
        assertEquals(Optional.of(second), standalone.resolve(connected, 1));
        assertEquals(Optional.of(first), standalone.resolve(connected, -1));
        assertEquals(Optional.of(first), standalone.resolve(connected, 9));
        assertTrue(standalone.resolve(List.of(), 1).isEmpty());
    }

    @Test
    void explicitAssignmentIgnoresPreferenceAndInventoryOrder() {
        var assigned = ControllerSelection.assigned(second.connectionId());
        assertEquals(Optional.of(second), assigned.resolve(connected, 0));
        assertEquals(Optional.of(second), assigned.resolve(List.of(second, first), 0));
    }

    @Test
    void missingAssignmentNeverFallsBack() {
        var assigned = ControllerSelection.assigned(second.connectionId());
        assertTrue(assigned.resolve(List.of(first), 0).isEmpty());
        assertTrue(assigned.resolve(List.of(), 0).isEmpty());
        assertEquals(Optional.of(second.connectionId()), assigned.connectionId());
    }

    @Test
    void slotReuseAndIdenticalModelCannotStealAssignment() {
        var assigned = ControllerSelection.assigned(second.connectionId());
        ControllerInfo replacement = info(second.glfwSlot());
        assertTrue(assigned.resolve(List.of(first, replacement), 1).isEmpty());
        assertEquals(Optional.of(replacement), ControllerSelection.assigned(replacement.connectionId())
                .resolve(List.of(first, replacement), 0));
    }

    @Test
    void disabledNeverSelectsEvenWithValidPreference() {
        assertTrue(ControllerSelection.disabled().resolve(connected, 1).isEmpty());
    }

    @Test
    void standaloneNeverFallsBackToExternalSources() {
        ControllerInfo external = info(-1);
        assertTrue(ControllerSelection.standalone().resolve(List.of(external), -1).isEmpty());
        assertEquals(Optional.of(first), ControllerSelection.standalone().resolve(List.of(external, first), -1));
        assertEquals(Optional.of(external), ControllerSelection.assigned(external.connectionId()).resolve(List.of(external), -1));
    }

    @Test
    void rejectsAmbiguousPolicies() {
        assertThrows(IllegalArgumentException.class, () -> new ControllerSelection(
                ControllerSelection.Mode.ASSIGNED, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new ControllerSelection(
                ControllerSelection.Mode.STANDALONE, Optional.of(first.connectionId())));
        assertThrows(IllegalArgumentException.class, () -> new ControllerSelection(
                ControllerSelection.Mode.DISABLED, Optional.of(first.connectionId())));
    }
}
