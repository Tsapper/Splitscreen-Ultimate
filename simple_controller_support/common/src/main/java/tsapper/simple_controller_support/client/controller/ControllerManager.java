package tsapper.simple_controller_support.client.controller;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import tsapper.simple_controller_support.SimpleControllerSupportMod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tracks which gamepads are connected and polls them once per frame.
 * Controllers are found by scanning GLFW joystick slots rather than relying on connection callbacks,
 * so it also works for controllers that were plugged in before the game started.
 */
public final class ControllerManager {
    /** How many frames between rescans of the joystick slots. */
    private static final int RESCAN_INTERVAL = 30;

    private final List<Controller> controllers = new ArrayList<>();

    public Controller registerExternal(String name) {
        Controller controller = new Controller(name);
        controllers.add(controller);
        return controller;
    }
    private int framesUntilRescan = 0;

    /** Rescans for new/removed controllers when due, then polls every connected controller. */
    public void poll(float triggerThreshold) {
        if (--framesUntilRescan <= 0) {
            framesUntilRescan = RESCAN_INTERVAL;
            rescan();
        }
        controllers.removeIf(controller -> {
            boolean connected = controller.poll(triggerThreshold);
            if (!connected) {
                SimpleControllerSupportMod.LOGGER.info("Controller disconnected: {}", controller.name());
            }
            return !connected;
        });
    }

    private void rescan() {
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (GLFW.glfwJoystickPresent(jid) && GLFW.glfwJoystickIsGamepad(jid) && find(jid) == null) {
                Controller controller = new Controller(jid);
                controllers.add(controller);
                SimpleControllerSupportMod.LOGGER.info("Controller connected: {} (slot {})", controller.name(), jid);
            }
        }
    }

    @Nullable
    private Controller find(int joystickId) {
        for (Controller controller : controllers) {
            if (controller.joystickId() == joystickId) {
                return controller;
            }
        }
        return null;
    }

    public List<Controller> controllers() {
        return Collections.unmodifiableList(controllers);
    }

    /**
     * The controller to use for a player: the preferred joystick slot if it is connected,
     * otherwise the first connected controller. Returns null when no controller is connected.
     */
    @Nullable
    public Controller pick(int preferredJoystickId) {
        Controller preferred = preferredJoystickId >= 0 ? find(preferredJoystickId) : null;
        if (preferred != null) {
            return preferred;
        }
        return controllers.stream().filter(c -> c.joystickId() >= 0 && c.available()).findFirst().orElse(null);
    }
}
