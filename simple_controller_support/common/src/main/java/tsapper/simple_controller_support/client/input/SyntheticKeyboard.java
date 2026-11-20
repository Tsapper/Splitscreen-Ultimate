package tsapper.simple_controller_support.client.input;

/**
 * Implemented by {@code KeyboardHandler} (via mixin). Lets the controller feed key presses through the vanilla
 * keyboard handler so loader screen events (Fabric API {@code ScreenKeyboardEvents}, NeoForge
 * {@code ScreenEvent}) fire and other mods see them.
 */
public interface SyntheticKeyboard {
    /** Equivalent of a GLFW key callback. {@code action} is GLFW_PRESS or GLFW_RELEASE. */
    void scs$sendKey(int key, int scanCode, int action, int modifiers);

    /** Equivalent of a GLFW char callback: types one character into whatever has focus. */
    void scs$sendChar(int codePoint, int modifiers);
}
