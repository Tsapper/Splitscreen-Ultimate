package tsapper.simple_controller_support.api;

import java.util.Map;
import java.util.Set;
import tsapper.simple_controller_support.client.controller.ControllerAxis;
import tsapper.simple_controller_support.client.controller.ControllerButton;

/** Raw external input: normalized triggers 0..1 and sticks -1..1; missing axes are neutral. */
public record ControllerSample(Map<ControllerAxis, Float> axes, Set<ControllerButton> down) {
    public ControllerSample {
        axes = Map.copyOf(axes);
        down = Set.copyOf(down);
        for (var entry : axes.entrySet()) {
            float value = entry.getValue();
            if (!Float.isFinite(value) || value > 1 || value < (entry.getKey().isTrigger() ? 0 : -1)) {
                throw new IllegalArgumentException("Invalid axis value");
            }
        }
    }
}
