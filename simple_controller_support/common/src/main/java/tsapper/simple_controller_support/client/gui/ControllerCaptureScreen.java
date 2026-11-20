package tsapper.simple_controller_support.client.gui;

import tsapper.simple_controller_support.client.controller.ControllerButton;

/**
 * A screen that wants raw controller button presses instead of the normal menu actions,
 * e.g. while waiting for the player to press the button to bind.
 */
public interface ControllerCaptureScreen {
    boolean isCapturingControllerInput();

    void onControllerButton(ControllerButton button);
}
