package tsapper.simple_controller_support.client.controller;

import org.junit.jupiter.api.Test;
import tsapper.simple_controller_support.api.ControllerSample;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ExternalSamplesTest {
    private ControllerSample sample(float x, boolean a) {
        return new ControllerSample(Map.of(ControllerAxis.LEFT_X, x), a ? Set.of(ControllerButton.A) : Set.of());
    }
    @Test void shortPressSurvivesSlowerConsumer() {
        var queue = new ExternalSamples();
        queue.submit(sample(0, false), 100);
        queue.submit(sample(0, true), 101);
        queue.submit(sample(0, false), 102);
        assertFalse(queue.poll(103).down().contains(ControllerButton.A));
        assertTrue(queue.poll(103).down().contains(ControllerButton.A));
        assertFalse(queue.poll(103).down().contains(ControllerButton.A));
    }
    @Test void analogCoalescesWithoutLosingButtons() {
        var queue = new ExternalSamples();
        queue.submit(sample(.1f, false), 100);
        queue.submit(sample(.5f, false), 101);
        assertEquals(.5f, queue.poll(102).axes().get(ControllerAxis.LEFT_X));
        assertFalse(queue.pending());
    }
    @Test void triggerTransitionsAreNotCoalesced() {
        var queue = new ExternalSamples();
        queue.submit(new ControllerSample(Map.of(ControllerAxis.LEFT_TRIGGER, 1f), Set.of()), 100);
        queue.submit(new ControllerSample(Map.of(ControllerAxis.LEFT_TRIGGER, 0f), Set.of()), 101);
        assertEquals(1f, queue.poll(102).axes().get(ControllerAxis.LEFT_TRIGGER));
        assertEquals(0f, queue.poll(102).axes().get(ControllerAxis.LEFT_TRIGGER));
    }
    @Test void staleAndClearedStreamsReleaseState() {
        var queue = new ExternalSamples(); queue.submit(sample(1, true), 100);
        assertNotNull(queue.poll(101)); assertNull(queue.poll(500_000_101));
        queue.submit(sample(0, false), 600_000_000); assertNotNull(queue.poll(600_000_001));
        queue.clear(); assertNull(queue.poll(600_000_002));
    }
    @Test void invalidAnalogInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> sample(Float.NaN, false));
        assertThrows(IllegalArgumentException.class, () -> sample(2, false));
    }
    @Test void edgesAreDerivedLocallyExactlyOnce() {
        var state = new ControllerState();
        state.update(sample(0, true), .5f); assertTrue(state.wasPressed(ControllerButton.A));
        state.update(sample(0, true), .5f); assertFalse(state.wasPressed(ControllerButton.A));
        state.update(sample(0, false), .5f); assertTrue(state.wasReleased(ControllerButton.A));
        state.update(sample(0, false), .5f); assertFalse(state.wasReleased(ControllerButton.A));
    }
}
