package tsapper.simple_controller_support.client.controller;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import tsapper.simple_controller_support.api.ControllerInfo;
import tsapper.simple_controller_support.api.ControllerSnapshot;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.UUID;

/**
 * One physical gamepad, identified by its GLFW joystick id. Polling GLFW joysticks does not require
 * window focus, which is what lets the mod work while the game window is in the background.
 */
public final class Controller {
    private final int joystickId;
    private final String name;
    private final ControllerInfo info;
    private final ControllerState state = new ControllerState();
    private final GLFWGamepadState glfwState = GLFWGamepadState.create();
    private ExternalSamples external;
    private boolean closed;
    private boolean available = true;

    public Controller(String name) {
        this.joystickId = -1;
        this.name = name;
        this.info = new ControllerInfo(UUID.randomUUID(), -1, name, "external");
        this.external = new ExternalSamples();
        this.available = false;
    }

    public ExternalSamples externalSamples() { return external; }
    public boolean hasPendingSamples() { return external != null && external.pending(); }
    public boolean available() { return available && !closed; }
    public void closeExternal() { closed = true; external.clear(); }

    Controller(int joystickId) {
        this.joystickId = joystickId;
        String gamepadName = GLFW.glfwGetGamepadName(joystickId);
        this.name = gamepadName != null ? gamepadName : "Controller " + (joystickId + 1);
        String guid = GLFW.glfwGetJoystickGUID(joystickId);
        this.info = new ControllerInfo(UUID.randomUUID(), joystickId, name, guid != null ? guid : "");
    }

    /** Reads the latest input. Returns false if the controller is no longer connected. */
    boolean poll(float triggerThreshold) {
        if (external != null) {
            var sample = external.poll(System.nanoTime());
            available = sample != null;
            if (sample == null) state.clear(); else state.update(sample, triggerThreshold);
            return !closed;
        }
        if (!GLFW.glfwGetGamepadState(joystickId, glfwState)) {
            state.clear();
            return false;
        }
        state.update(glfwState, triggerThreshold);
        return true;
    }

    public void advanceExternal(float threshold) { poll(threshold); }

    public int joystickId() {
        return joystickId;
    }

    public String name() {
        return name;
    }

    public ControllerState state() {
        return state;
    }

    public ControllerInfo info() {
        return info;
    }

    public ControllerSnapshot snapshot() {
        var axes = new EnumMap<ControllerAxis, Float>(ControllerAxis.class);
        for (ControllerAxis axis : ControllerAxis.values()) {
            axes.put(axis, state.axis(axis));
        }
        var down = EnumSet.noneOf(ControllerButton.class);
        var pressed = EnumSet.noneOf(ControllerButton.class);
        var released = EnumSet.noneOf(ControllerButton.class);
        for (ControllerButton button : ControllerButton.values()) {
            if (state.isDown(button)) down.add(button);
            if (state.wasPressed(button)) pressed.add(button);
            if (state.wasReleased(button)) released.add(button);
        }
        return new ControllerSnapshot(info, axes, down, pressed, released);
    }
}
