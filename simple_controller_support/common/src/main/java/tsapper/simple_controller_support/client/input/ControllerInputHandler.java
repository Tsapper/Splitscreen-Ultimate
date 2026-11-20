package tsapper.simple_controller_support.client.input;

import net.minecraft.Util;
import tsapper.simple_controller_support.api.ControllerFrame;
import tsapper.simple_controller_support.api.ControllerSelection;
import tsapper.simple_controller_support.api.ControllerSupport;
import tsapper.simple_controller_support.client.controller.Controller;
import tsapper.simple_controller_support.client.config.ControllerConfig;
import tsapper.simple_controller_support.client.controller.ControllerManager;

import java.util.Optional;

/**
 * Entry point called once per frame (from {@code MinecraftMixin}, before ticks and rendering).
 * Polls controllers and hands input to the player's {@link ControllerContext}.
 */
public final class ControllerInputHandler {
    /** Longest frame time used for analog input, so a lag spike doesn't fling the camera. */
    private static final float MAX_DELTA_SECONDS = 0.1f;

    private static final ControllerManager MANAGER = new ControllerManager();
    private static final ControllerContext PRIMARY = new ControllerContext();
    private static long lastFrameNanos = Util.getNanos();
    private static ControllerSelection selection = ControllerSelection.standalone();
    private static boolean physicalInputEnabled = true;

    public static boolean physicalInputEnabled() { return physicalInputEnabled; }
    public static void setPhysicalInputEnabled(boolean enabled) {
        if (physicalInputEnabled != enabled) {
            PRIMARY.resetController();
            net.minecraft.client.KeyMapping.releaseAll();
            physicalInputEnabled = enabled;
        }
    }

    public static void resetExternalInput() { PRIMARY.resetController(); }

    private ControllerInputHandler() {
    }

    public static void onFrame() {
        long now = Util.getNanos();
        float deltaSeconds = Math.min(MAX_DELTA_SECONDS, (now - lastFrameNanos) / 1.0E9f);
        lastFrameNanos = now;

        ControllerConfig config = ControllerConfig.get();
        MANAGER.poll(config.triggerThreshold);
        Controller selected = selectedController();
        if (selected != null && selected.hasPendingSamples()) {
            PRIMARY.frame(selected, 0, true);
            while (selected.hasPendingSamples()) {
                selected.advanceExternal(config.triggerThreshold);
                PRIMARY.frame(selected, selected.hasPendingSamples() ? 0 : deltaSeconds, true);
            }
        } else {
            PRIMARY.frame(selected, deltaSeconds, selection.mode() != ControllerSelection.Mode.STANDALONE);
        }
        ControllerSupport.publishFrame();
    }

    /** Internal API bridge; callers must use ControllerSupport on the client thread. */
    public static void setSelection(ControllerSelection next) {
        if (!selection.equals(next)) {
            selection = next;
            PRIMARY.resetController();
        }
    }

    private static Controller selectedController() {
        if (selection.mode() == ControllerSelection.Mode.STANDALONE) {
            return MANAGER.pick(ControllerConfig.get().preferredController);
        }
        var selected = selection.resolve(MANAGER.controllers().stream().map(Controller::info).toList(), -1);
        return selected.flatMap(info -> MANAGER.controllers().stream()
                .filter(controller -> controller.available() && controller.info().connectionId().equals(info.connectionId())).findFirst())
                .orElse(null);
    }

    /** Internal API bridge. Snapshotting never exposes mutable input state. */
    public static ControllerFrame snapshot() {
        Controller selected = selectedController();
        return new ControllerFrame(selection, MANAGER.controllers().stream().map(Controller::snapshot).toList(),
                Optional.ofNullable(selected).map(controller -> controller.info().connectionId()),
                isControllerActive());
    }

    /** Called by mixins whenever the real keyboard or mouse is used. */
    public static void onKeyboardMouseInput() {
        PRIMARY.switchToKeyboardMouse();
    }

    public static ControllerManager manager() {
        return MANAGER;
    }

    public static ControllerContext primary() {
        return PRIMARY;
    }

    public static boolean isControllerActive() {
        return PRIMARY.mode() == InputMode.CONTROLLER;
    }
}
