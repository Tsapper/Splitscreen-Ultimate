package tsapper.simple_controller_support.client.controller;

import org.lwjgl.glfw.GLFW;

/** Analog axes of a gamepad. Stick values are -1..1 (down/right positive); triggers are remapped to 0..1. */
public enum ControllerAxis {
    LEFT_X(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X, false),
    LEFT_Y(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y, false),
    RIGHT_X(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X, false),
    RIGHT_Y(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y, false),
    LEFT_TRIGGER(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER, true),
    RIGHT_TRIGGER(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER, true);

    private final int glfwAxis;
    private final boolean trigger;

    ControllerAxis(int glfwAxis, boolean trigger) {
        this.glfwAxis = glfwAxis;
        this.trigger = trigger;
    }

    public int glfwAxis() {
        return glfwAxis;
    }

    public boolean isTrigger() {
        return trigger;
    }
}
