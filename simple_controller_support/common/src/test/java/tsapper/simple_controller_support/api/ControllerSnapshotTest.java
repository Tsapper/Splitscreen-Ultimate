package tsapper.simple_controller_support.api;

import org.junit.jupiter.api.Test;
import tsapper.simple_controller_support.client.controller.ControllerAxis;
import tsapper.simple_controller_support.client.controller.ControllerButton;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ControllerSnapshotTest {
    @Test
    void snapshotsDoNotExposeOrRetainMutableCollections() {
        var axes = new EnumMap<ControllerAxis, Float>(ControllerAxis.class);
        axes.put(ControllerAxis.LEFT_X, 0.5f);
        var down = new HashSet<ControllerButton>();
        down.add(ControllerButton.A);
        var snapshot = new ControllerSnapshot(new ControllerInfo(UUID.randomUUID(), 0, "Pad", "guid"),
                axes, down, down, down);
        var inventory = new ArrayList<ControllerSnapshot>();
        inventory.add(snapshot);
        var frame = new ControllerFrame(ControllerSelection.standalone(), inventory, Optional.empty(), false);

        axes.clear();
        down.clear();
        inventory.clear();
        assertEquals(0.5f, snapshot.axes().get(ControllerAxis.LEFT_X));
        assertTrue(snapshot.down().contains(ControllerButton.A));
        assertTrue(snapshot.pressed().contains(ControllerButton.A));
        assertTrue(snapshot.released().contains(ControllerButton.A));
        assertEquals(1, frame.controllers().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.axes().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.down().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.pressed().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.released().clear());
        assertThrows(UnsupportedOperationException.class, () -> frame.controllers().clear());
    }
}
