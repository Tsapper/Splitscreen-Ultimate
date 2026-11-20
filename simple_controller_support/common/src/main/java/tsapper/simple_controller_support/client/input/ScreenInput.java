package tsapper.simple_controller_support.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import tsapper.simple_controller_support.client.config.ControllerConfig;
import tsapper.simple_controller_support.client.controller.ControllerAxis;
import tsapper.simple_controller_support.client.controller.ControllerState;
import tsapper.simple_controller_support.client.cursor.VirtualCursor;
import tsapper.simple_controller_support.client.snap.CursorSnapper;

/**
 * Drives the open screen with the virtual cursor: movement (left stick), D-pad snapping, scrolling (right stick),
 * mouse clicks/drags, and forwarding keyboard keys for key mappings bound to controller buttons.
 * Clicks, scrolls and keys go through {@code MouseHandler}/{@code KeyboardHandler} so loader screen events fire; moves and drags go
 * straight to the screen's handlers. Nothing depends on window focus.
 */
public final class ScreenInput {
    /** Scroll notches per second at full right-stick deflection. */
    private static final double SCROLL_SPEED = 12.0;
    /** Exponent for cursor speed vs stick deflection; higher = finer control near the centre. */
    private static final double CURSOR_CURVE = 1.6;
    private static final double SNAP_INITIAL_REPEAT_DELAY = 0.35;
    private static final double SNAP_REPEAT_INTERVAL = 0.1;

    private final VirtualCursor cursor;
    private int heldMouseButton = -1;
    private boolean shiftHeld;
    private double scrollAccumulatorX;
    private double scrollAccumulatorY;
    @Nullable
    private ScreenDirection heldSnap;
    private double snapRepeatTimer;

    ScreenInput(VirtualCursor cursor) {
        this.cursor = cursor;
    }

    /** Continuous input, called every frame while a screen is open. */
    void frame(Screen screen, ControllerState state, float deltaSeconds) {
        ControllerConfig config = ControllerConfig.get();
        cursor.clamp();

        float[] move = state.stick(ControllerAxis.LEFT_X, ControllerAxis.LEFT_Y, config.stickDeadzone);
        double magnitude = Math.sqrt(move[0] * move[0] + move[1] * move[1]);
        if (magnitude > 0) {
            double speed = Math.pow(magnitude, CURSOR_CURVE) * config.cursorSpeed * deltaSeconds / magnitude;
            moveCursorTo(screen, cursor.x() + move[0] * speed, cursor.y() + move[1] * speed);
        }

        if (heldSnap != null) {
            snapRepeatTimer -= deltaSeconds;
            if (snapRepeatTimer <= 0) {
                snapRepeatTimer = SNAP_REPEAT_INTERVAL;
                snap(screen, heldSnap);
            }
        }

        float[] scroll = state.stick(ControllerAxis.RIGHT_X, ControllerAxis.RIGHT_Y, config.stickDeadzone);
        scrollAccumulatorX = accumulate(scrollAccumulatorX, scroll[0] * SCROLL_SPEED * deltaSeconds);
        scrollAccumulatorY = accumulate(scrollAccumulatorY, -scroll[1] * SCROLL_SPEED * deltaSeconds);
        int notchesX = (int) scrollAccumulatorX;
        int notchesY = (int) scrollAccumulatorY;
        if (notchesX != 0 || notchesY != 0) {
            scrollAccumulatorX -= notchesX;
            scrollAccumulatorY -= notchesY;
            mouse().scs$sendScroll(notchesX, notchesY);
        }
    }

    /** Moves the cursor and tells the screen, dragging if a mouse button is held. */
    private void moveCursorTo(Screen screen, double x, double y) {
        double oldX = cursor.x();
        double oldY = cursor.y();
        cursor.setPosition(x, y);
        double dx = cursor.x() - oldX;
        double dy = cursor.y() - oldY;
        if (dx != 0 || dy != 0) {
            Screen.wrapScreenError(() -> screen.mouseMoved(cursor.x(), cursor.y()),
                    "mouseMoved event handler", screen.getClass().getCanonicalName());
            if (heldMouseButton != -1) {
                int button = heldMouseButton;
                Screen.wrapScreenError(() -> screen.mouseDragged(cursor.x(), cursor.y(), button, dx, dy),
                        "mouseDragged event handler", screen.getClass().getCanonicalName());
            }
            screen.afterMouseMove();
        }
    }

    /** Snaps the cursor to the nearest clickable thing in {@code direction}, repeating while held. */
    public void snapPressed(ScreenDirection direction) {
        Screen screen = screen();
        if (screen == null) {
            return;
        }
        heldSnap = direction;
        snapRepeatTimer = SNAP_INITIAL_REPEAT_DELAY;
        snap(screen, direction);
    }

    public void snapReleased(ScreenDirection direction) {
        if (heldSnap == direction) {
            heldSnap = null;
        }
    }

    private void snap(Screen screen, ScreenDirection direction) {
        cursor.clamp();
        ScreenPosition target = CursorSnapper.snap(screen, cursor.x(), cursor.y(), direction);
        if (target != null) {
            moveCursorTo(screen, target.x(), target.y());
        }
    }

    /** Adds to a scroll accumulator, resetting it when the direction changes or the stick is released. */
    private static double accumulate(double accumulator, double amount) {
        if (amount == 0 || Math.signum(amount) != Math.signum(accumulator) && accumulator != 0) {
            return amount;
        }
        return accumulator + amount;
    }

    public void mouseDown(int button) {
        Screen screen = screen();
        if (screen == null) {
            return;
        }
        heldMouseButton = button;
        mouse().scs$sendButton(button, GLFW.GLFW_PRESS, modifiers());
    }

    public void mouseUp(int button) {
        if (heldMouseButton == button) {
            heldMouseButton = -1;
        }
        Screen screen = screen();
        if (screen == null) {
            return;
        }
        mouse().scs$sendButton(button, GLFW.GLFW_RELEASE, modifiers());
    }

    // Clicks and scrolls go through MouseHandler so loader screen events fire (other mods rely on them).
    private static SyntheticMouse mouse() {
        return (SyntheticMouse) Minecraft.getInstance().mouseHandler;
    }

    private int modifiers() {
        return shiftHeld ? GLFW.GLFW_MOD_SHIFT : 0;
    }

    /** Same as pressing Escape: closes the screen if it allows it. */
    public void back() {
        tapKey(GLFW.GLFW_KEY_ESCAPE);
    }

    /** Sends a keyboard key press to the screen. Mouse-type keys are ignored. */
    public void pressKey(InputConstants.Key key) {
        Screen screen = screen();
        if (screen == null || key.getType() == InputConstants.Type.MOUSE) {
            return;
        }
        int[] codes = keyCodes(key);
        keyboard().scs$sendKey(codes[0], codes[1], GLFW.GLFW_PRESS, modifiers());
    }

    public void releaseKey(InputConstants.Key key) {
        Screen screen = screen();
        if (screen == null || key.getType() == InputConstants.Type.MOUSE) {
            return;
        }
        int[] codes = keyCodes(key);
        keyboard().scs$sendKey(codes[0], codes[1], GLFW.GLFW_RELEASE, modifiers());
    }

    /** Presses and releases a GLFW key, e.g. backspace or enter. */
    public void tapKey(int glfwKey) {
        InputConstants.Key key = InputConstants.getKey(glfwKey, 0);
        pressKey(key);
        releaseKey(key);
    }

    /** Types one character into whatever has focus in the screen. */
    public void typeChar(int codePoint) {
        if (screen() != null) {
            keyboard().scs$sendChar(codePoint, 0);
        }
    }

    private static SyntheticKeyboard keyboard() {
        return (SyntheticKeyboard) Minecraft.getInstance().keyboardHandler;
    }

    /** {keyCode, scanCode} as GLFW would report them. */
    private static int[] keyCodes(InputConstants.Key key) {
        if (key.getType() == InputConstants.Type.SCANCODE) {
            return new int[]{InputConstants.UNKNOWN.getValue(), key.getValue()};
        }
        return new int[]{key.getValue(), GLFW.glfwGetKeyScancode(key.getValue())};
    }

    /** While true, shift reads as held so clicks in inventories become quick-moves. */
    public void setShiftHeld(boolean shiftHeld) {
        this.shiftHeld = shiftHeld;
    }

    public boolean isShiftHeld() {
        return shiftHeld;
    }

    /** Forgets held buttons without sending events, used when the screen changes. */
    void reset() {
        heldMouseButton = -1;
        shiftHeld = false;
        scrollAccumulatorX = 0;
        scrollAccumulatorY = 0;
        heldSnap = null;
    }

    @Nullable
    private static Screen screen() {
        return Minecraft.getInstance().screen;
    }
}
