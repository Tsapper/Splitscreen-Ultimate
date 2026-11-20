package tsapper.simple_controller_support.client.controller;

import org.lwjgl.glfw.GLFWGamepadState;
import tsapper.simple_controller_support.api.ControllerSample;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Snapshot of one controller for the current frame, plus the previous frame so callers can detect
 * press/release edges. Raw values only; deadzones are applied by the helpers below.
 */
public final class ControllerState {
    private final float[] axes = new float[ControllerAxis.values().length];
    private final Set<ControllerButton> down = EnumSet.noneOf(ControllerButton.class);
    private final Set<ControllerButton> previouslyDown = EnumSet.noneOf(ControllerButton.class);

    /** Copies a GLFW gamepad state into this snapshot, shifting the current frame into "previous". */
    void update(GLFWGamepadState glfwState, float triggerThreshold) {
        previouslyDown.clear();
        previouslyDown.addAll(down);
        down.clear();

        for (ControllerAxis axis : ControllerAxis.values()) {
            float value = glfwState.axes(axis.glfwAxis());
            // GLFW reports triggers as -1 (released) .. 1 (fully pressed)
            axes[axis.ordinal()] = axis.isTrigger() ? (value + 1f) / 2f : value;
        }

        for (ControllerButton button : ControllerButton.values()) {
            ControllerAxis trigger = button.triggerAxis();
            boolean pressed = trigger != null
                    ? axis(trigger) > triggerThreshold
                    : glfwState.buttons(button.glfwButton()) == 1;
            if (pressed) {
                down.add(button);
            }
        }
    }

    /** Clears all input, e.g. when the controller disconnects. Held buttons will report a release edge. */
    void clear() {
        previouslyDown.clear();
        previouslyDown.addAll(down);
        down.clear();
        Arrays.fill(axes, 0f);
    }

    void update(ControllerSample sample, float triggerThreshold) {
        previouslyDown.clear();
        previouslyDown.addAll(down);
        down.clear();
        for (ControllerAxis axis : ControllerAxis.values()) axes[axis.ordinal()] = sample.axes().getOrDefault(axis, 0f);
        for (ControllerButton button : ControllerButton.values()) {
            if (button.triggerAxis() != null ? axis(button.triggerAxis()) > triggerThreshold : sample.down().contains(button)) {
                down.add(button);
            }
        }
    }

    public float axis(ControllerAxis axis) {
        return axes[axis.ordinal()];
    }

    public boolean isDown(ControllerButton button) {
        return down.contains(button);
    }

    public boolean wasPressed(ControllerButton button) {
        return down.contains(button) && !previouslyDown.contains(button);
    }

    public boolean wasReleased(ControllerButton button) {
        return !down.contains(button) && previouslyDown.contains(button);
    }

    /** Returns stick values with a radial deadzone applied and rescaled so output starts at 0 at the deadzone edge. */
    public float[] stick(ControllerAxis xAxis, ControllerAxis yAxis, float deadzone) {
        float x = axis(xAxis);
        float y = axis(yAxis);
        float magnitude = (float) Math.sqrt(x * x + y * y);
        if (magnitude <= deadzone) {
            return new float[]{0f, 0f};
        }
        float scaled = Math.min(1f, (magnitude - deadzone) / (1f - deadzone));
        return new float[]{x / magnitude * scaled, y / magnitude * scaled};
    }

    /** True if any button is held or any stick/trigger is outside the given deadzone. */
    public boolean hasActivity(float deadzone) {
        if (!down.isEmpty()) {
            return true;
        }
        for (float value : axes) {
            if (Math.abs(value) > deadzone) {
                return true;
            }
        }
        return false;
    }
}
