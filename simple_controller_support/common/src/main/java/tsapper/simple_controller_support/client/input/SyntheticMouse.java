package tsapper.simple_controller_support.client.input;

/**
 * Implemented by {@code MouseHandler} (via mixin). Lets the controller feed clicks and scrolls through the
 * vanilla mouse handler at the virtual cursor position, so loader screen events (Fabric API
 * {@code ScreenMouseEvents}, NeoForge {@code ScreenEvent}) fire and other mods see them.
 */
public interface SyntheticMouse {
    /** Equivalent of a GLFW mouse button callback. {@code action} is GLFW_PRESS or GLFW_RELEASE. */
    void scs$sendButton(int button, int action, int modifiers);

    /** Equivalent of a GLFW scroll callback. */
    void scs$sendScroll(double xOffset, double yOffset);
}
