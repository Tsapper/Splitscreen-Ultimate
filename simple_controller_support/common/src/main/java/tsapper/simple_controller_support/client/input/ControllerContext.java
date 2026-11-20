package tsapper.simple_controller_support.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import tsapper.simple_controller_support.client.binding.BindingAction;
import tsapper.simple_controller_support.client.binding.BindingAction.ActionContext;
import tsapper.simple_controller_support.client.binding.BindingManager;
import tsapper.simple_controller_support.client.binding.KeyMappingAction;
import tsapper.simple_controller_support.client.config.ControllerConfig;
import tsapper.simple_controller_support.client.controller.Controller;
import tsapper.simple_controller_support.client.controller.ControllerButton;
import tsapper.simple_controller_support.client.controller.ControllerState;
import tsapper.simple_controller_support.client.cursor.VirtualCursor;
import tsapper.simple_controller_support.client.gui.ControllerCaptureScreen;
import tsapper.simple_controller_support.client.gui.OnScreenKeyboard;
import tsapper.simple_controller_support.client.gui.TextInputDetector;
import tsapper.simple_controller_support.mixin.KeyMappingAccessor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything belonging to one controller-using player: the controller, its virtual cursor and the
 * state of held buttons. Splitscreen uses one context per Minecraft process, one process per player.
 */
public final class ControllerContext {
    /** Stick movement needed to switch from keyboard/mouse to controller, so stick drift doesn't steal input. */
    private static final float ACTIVATION_THRESHOLD = 0.4f;

    private final VirtualCursor cursor = new VirtualCursor();
    private final KeyMappingDriver keyMappings = new KeyMappingDriver();
    private final ScreenInput screenInput = new ScreenInput(cursor);
    private final GameplayInput gameplayInput = new GameplayInput();
    private final OnScreenKeyboard keyboard = new OnScreenKeyboard(this);
    private final ComboInput combos = new ComboInput();

    /** Buttons held across a context change; ignored until released so one press never acts twice. */
    private final Set<ControllerButton> suppressed = EnumSet.noneOf(ControllerButton.class);
    /** What to undo when each held button is released. */
    private final Map<ControllerButton, List<Runnable>> releaseHandlers = new EnumMap<>(ControllerButton.class);

    @Nullable
    private Controller controller;
    private InputMode mode = InputMode.KEYBOARD_MOUSE;
    @Nullable
    private Screen lastScreen;
    /** Text field focused at the end of the last frame, so the keyboard only auto-opens when focus moves to a new one. */
    @Nullable
    private EditBox lastFocusedField;
    /** Controlled handovers require neutral input before a new connection can act. */
    private boolean awaitNeutral;
    private boolean suppressNextController;

    void frame(@Nullable Controller newController, float deltaSeconds, boolean explicitAssignment) {
        if (newController != controller) {
            if (mode == InputMode.CONTROLLER) {
                switchToKeyboardMouse();
            }
            controller = newController;
            if (controller != null) {
                awaitNeutral = explicitAssignment || suppressNextController;
                suppressNextController = false;
            }
        }
        if (controller == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ControllerState state = controller.state();
        if (awaitNeutral) {
            // Includes triggers/buttons and both sticks. A held join button must not also act in-game.
            awaitNeutral = state.hasActivity(ControllerConfig.get().stickDeadzone);
            return;
        }
        if (mode != InputMode.CONTROLLER && state.hasActivity(Math.max(ACTIVATION_THRESHOLD, ControllerConfig.get().stickDeadzone))) {
            switchToController();
        }
        if (mode != InputMode.CONTROLLER) {
            return;
        }

        updateOsCursor();
        if (minecraft.getOverlay() != null) {
            return;
        }

        Screen screen = minecraft.screen;
        if (screen != lastScreen) {
            onScreenChanged(lastScreen, screen);
            lastScreen = screen;
        }

        if (screen instanceof ControllerCaptureScreen capture && capture.isCapturingControllerInput()) {
            for (ControllerButton button : ControllerButton.values()) {
                if (state.wasPressed(button) && !suppressed.contains(button)) {
                    capture.onControllerButton(button);
                    suppressed.add(button);
                } else if (state.wasReleased(button)) {
                    // e.g. the click that started the capture still needs its mouse release
                    release(button);
                }
            }
            updateSuppressed(state);
            return;
        }

        if (screen != null && keyboard.isOpen()) {
            for (ControllerButton button : ControllerButton.values()) {
                if (state.wasReleased(button)) {
                    // e.g. the click that focused the text field still needs its mouse release
                    release(button);
                } else if (state.wasPressed(button) && !suppressed.contains(button)) {
                    keyboard.onButtonPressed(button);
                }
            }
            updateSuppressed(state);
            // A key above may have closed the screen (enter in chat) or the keyboard
            if (minecraft.screen == lastScreen && keyboard.isOpen()) {
                keyboard.frame(state, deltaSeconds, suppressed);
            }
            return;
        }

        updateSuppressed(state);
        boolean anyPressed = false;
        for (ControllerButton button : ControllerButton.values()) {
            if (suppressed.contains(button)) {
                continue;
            }
            if (state.wasPressed(button)) {
                anyPressed = true;
                press(button, minecraft.screen != null);
            } else if (state.wasReleased(button)) {
                release(button);
            }
        }

        // Re-read: a button press above may have opened or closed a screen
        if (minecraft.screen != lastScreen) {
            return;
        }
        if (minecraft.screen != null && !keyboard.isOpen()) {
            // Open the keyboard when a controller press focuses a text field (not when a screen focuses one by itself)
            EditBox field = TextInputDetector.focusedTextField(minecraft.screen);
            // (or clicks the already focused one again, e.g. after the keyboard was closed)
            if (anyPressed && field != null && ControllerConfig.get().keyboardAutoOpen
                    && (field != lastFocusedField || field.isMouseOver(cursor.x(), cursor.y()))) {
                openKeyboard(field);
            }
            lastFocusedField = field;
        }
        if (keyboard.isOpen()) {
            return;
        }
        if (minecraft.screen != null) {
            screenInput.frame(minecraft.screen, state, deltaSeconds);
        } else {
            gameplayInput.frame(state, deltaSeconds);
            combos.frame();
        }
    }

    /** Releases old ownership immediately; the next controller must first return to neutral. */
    void resetController() {
        switchToKeyboardMouse();
        controller = null;
        suppressed.clear();
        awaitNeutral = false;
        suppressNextController = true;
    }

    private void press(ControllerButton button, boolean inScreen) {
        List<Runnable> releases = releaseHandlers.computeIfAbsent(button, b -> new ArrayList<>());
        List<BindingAction> screenActions = inScreen ? BindingManager.actionsFor(button, ActionContext.SCREEN) : List.of();
        if (!screenActions.isEmpty()) {
            for (BindingAction action : screenActions) {
                action.onPress(this);
                releases.add(() -> action.onRelease(this));
            }
            return;
        }

        List<BindingAction> gameplayActions;
        ControllerButton comboFirst = combos.pending();
        if (!inScreen && button.isDpad() && comboFirst != null) {
            // Second press of a combo; consumed even when the combo is unbound
            combos.clear();
            gameplayActions = BindingManager.actionsForCombo(comboFirst, button);
        } else if (!inScreen && button.isDpad() && BindingManager.isComboPrefix(button)) {
            combos.start(button);
            return;
        } else {
            gameplayActions = BindingManager.actionsFor(button, ActionContext.GAMEPLAY);
        }

        for (BindingAction action : gameplayActions) {
            if (!inScreen) {
                action.onPress(this);
                releases.add(() -> action.onRelease(this));
            } else if (action instanceof KeyMappingAction keyMappingAction) {
                // In menus, act like the keyboard key bound to the same mapping (e.g. inventory key closes the inventory)
                InputConstants.Key key = ((KeyMappingAccessor) keyMappingAction.keyMapping()).scs$getKey();
                screenInput.pressKey(key);
                releases.add(() -> screenInput.releaseKey(key));
            }
        }
    }

    private void release(ControllerButton button) {
        List<Runnable> releases = releaseHandlers.remove(button);
        if (releases != null) {
            releases.forEach(Runnable::run);
        }
    }

    private void updateSuppressed(ControllerState state) {
        suppressed.removeIf(button -> !state.isDown(button));
    }

    private void onScreenChanged(@Nullable Screen oldScreen, @Nullable Screen newScreen) {
        // Vanilla already released key mappings / the old screen is gone, so drop held state without replaying releases
        releaseHandlers.clear();
        keyMappings.releaseAll();
        screenInput.reset();
        gameplayInput.reset();
        combos.clear();
        suppressHeld();
        if (oldScreen == null && newScreen != null) {
            cursor.center();
        }

        keyboard.close();
        lastFocusedField = newScreen != null ? TextInputDetector.focusedTextField(newScreen) : null;
        if (newScreen != null && TextInputDetector.isTextScreen(newScreen) && ControllerConfig.get().keyboardAutoOpen) {
            keyboard.open(lastFocusedField);
        }
    }

    /** Opens the on-screen keyboard for the open screen. Buttons held now are ignored until released. */
    public void openKeyboard(@Nullable EditBox target) {
        if (Minecraft.getInstance().screen == null) {
            return;
        }
        keyboard.open(target);
        suppressHeld();
    }

    /** Closes the on-screen keyboard. Buttons held now (e.g. the B that closed it) are ignored until released. */
    public void closeKeyboard() {
        keyboard.close();
        suppressHeld();
        Screen screen = Minecraft.getInstance().screen;
        lastFocusedField = screen != null ? TextInputDetector.focusedTextField(screen) : null;
    }

    private void suppressHeld() {
        if (controller != null) {
            for (ControllerButton button : ControllerButton.values()) {
                if (controller.state().isDown(button)) {
                    suppressed.add(button);
                }
            }
        }
    }

    private void switchToController() {
        Minecraft minecraft = Minecraft.getInstance();
        // Still in keyboard/mouse mode here, so these are the real mouse coordinates
        cursor.setWindowPosition(minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos());
        lastScreen = minecraft.screen;
        mode = InputMode.CONTROLLER;
    }

    /** Called when the keyboard or mouse is used, or the controller goes away. */
    public void switchToKeyboardMouse() {
        if (mode == InputMode.KEYBOARD_MOUSE) {
            return;
        }
        List<Runnable> releases = new ArrayList<>();
        releaseHandlers.values().forEach(releases::addAll);
        releaseHandlers.clear();
        releases.forEach(Runnable::run);
        keyMappings.releaseAll();
        screenInput.reset();
        gameplayInput.reset();
        combos.clear();
        keyboard.close();
        suppressed.clear();
        mode = InputMode.KEYBOARD_MOUSE;
        updateOsCursor();
    }

    /** Hides the OS cursor while the virtual one is in use. Never touches a grabbed (disabled) cursor. */
    private void updateOsCursor() {
        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();
        int current = GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR);
        boolean hide = mode == InputMode.CONTROLLER && minecraft.screen != null;
        if (hide && current == GLFW.GLFW_CURSOR_NORMAL) {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
        } else if (!hide && current == GLFW.GLFW_CURSOR_HIDDEN) {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
    }

    @Nullable
    public Controller controller() {
        return controller;
    }

    public InputMode mode() {
        return mode;
    }

    public VirtualCursor cursor() {
        return cursor;
    }

    public KeyMappingDriver keyMappings() {
        return keyMappings;
    }

    public ScreenInput screenInput() {
        return screenInput;
    }

    public GameplayInput gameplayInput() {
        return gameplayInput;
    }

    public OnScreenKeyboard keyboard() {
        return keyboard;
    }

    public ComboInput combos() {
        return combos;
    }
}
