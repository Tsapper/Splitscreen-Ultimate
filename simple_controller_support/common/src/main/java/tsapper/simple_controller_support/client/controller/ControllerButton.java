package tsapper.simple_controller_support.client.controller;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Every bindable input on a gamepad. Triggers are treated as buttons once they pass the trigger threshold.
 * Labels follow the Xbox layout (GLFW uses the same naming).
 */
public enum ControllerButton {
    A(GLFW.GLFW_GAMEPAD_BUTTON_A, "a"),
    B(GLFW.GLFW_GAMEPAD_BUTTON_B, "b"),
    X(GLFW.GLFW_GAMEPAD_BUTTON_X, "x"),
    Y(GLFW.GLFW_GAMEPAD_BUTTON_Y, "y"),
    LEFT_BUMPER(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER, "left_bumper"),
    RIGHT_BUMPER(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER, "right_bumper"),
    BACK(GLFW.GLFW_GAMEPAD_BUTTON_BACK, "back"),
    START(GLFW.GLFW_GAMEPAD_BUTTON_START, "start"),
    GUIDE(GLFW.GLFW_GAMEPAD_BUTTON_GUIDE, "guide"),
    LEFT_STICK(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_THUMB, "left_stick"),
    RIGHT_STICK(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_THUMB, "right_stick"),
    DPAD_UP(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP, "dpad_up"),
    DPAD_RIGHT(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT, "dpad_right"),
    DPAD_DOWN(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN, "dpad_down"),
    DPAD_LEFT(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT, "dpad_left"),
    LEFT_TRIGGER(ControllerAxis.LEFT_TRIGGER, "left_trigger"),
    RIGHT_TRIGGER(ControllerAxis.RIGHT_TRIGGER, "right_trigger");

    /** GLFW button index, or -1 for trigger "buttons". */
    private final int glfwButton;
    /** Axis backing a trigger "button", null for real buttons. */
    @Nullable
    private final ControllerAxis triggerAxis;
    private final String translationKey;

    ControllerButton(int glfwButton, String name) {
        this.glfwButton = glfwButton;
        this.triggerAxis = null;
        this.translationKey = "simple_controller_support.button." + name;
    }

    ControllerButton(ControllerAxis triggerAxis, String name) {
        this.glfwButton = -1;
        this.triggerAxis = triggerAxis;
        this.translationKey = "simple_controller_support.button." + name;
    }

    public int glfwButton() {
        return glfwButton;
    }

    @Nullable
    public ControllerAxis triggerAxis() {
        return triggerAxis;
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    public boolean isDpad() {
        return this == DPAD_UP || this == DPAD_RIGHT || this == DPAD_DOWN || this == DPAD_LEFT;
    }

    /** Arrow glyph for D-pad directions, used for compact combo labels. Empty for other buttons. */
    public String arrow() {
        return switch (this) {
            case DPAD_UP -> "↑";
            case DPAD_RIGHT -> "→";
            case DPAD_DOWN -> "↓";
            case DPAD_LEFT -> "←";
            default -> "";
        };
    }
}
