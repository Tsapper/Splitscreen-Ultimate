package tsapper.simple_controller_support.client.gui;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import tsapper.simple_controller_support.client.config.ControllerConfig;
import tsapper.simple_controller_support.client.controller.ControllerAxis;
import tsapper.simple_controller_support.client.controller.ControllerButton;
import tsapper.simple_controller_support.client.controller.ControllerState;
import tsapper.simple_controller_support.client.input.ControllerContext;
import tsapper.simple_controller_support.client.input.ScreenInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Console-style keyboard drawn on top of the open screen (it is not a screen itself, so the screen keeps its
 * focus). A highlighted key is moved with the D-pad / left stick and typed with A. Output is sent as synthetic
 * char and key events through {@link ScreenInput}, so it reaches whatever has focus, including mod widgets.
 *
 * <p>Fixed shortcuts while open: X backspace, Y space, LB/RB move the text cursor, LT shift, START enter, B close.
 */
public final class OnScreenKeyboard {
    /** Drawn above screen contents and tooltips, below the virtual cursor layer. */
    private static final float RENDER_Z = 1500f;
    /** Every row is this many width units wide; a normal key is 2 units. */
    private static final int ROW_UNITS = 20;
    private static final int KEY_HEIGHT = 16;
    private static final int GAP = 2;
    private static final int PADDING = 4;
    private static final int HINT_HEIGHT = 11;
    private static final double INITIAL_REPEAT_DELAY = 0.4;
    private static final double REPEAT_INTERVAL = 0.07;
    /** Stick deflection needed to move the highlight. */
    private static final float STICK_THRESHOLD = 0.5f;

    private static final int PANEL_COLOR = 0xD0101010;
    private static final int KEY_COLOR = 0xFF3A3A3A;
    private static final int SPECIAL_KEY_COLOR = 0xFF2A2A2A;
    private static final int ACTIVE_KEY_COLOR = 0xFF4A6A2A;
    private static final int SELECTED_KEY_COLOR = 0xFFE0E0E0;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int SELECTED_TEXT_COLOR = 0xFF000000;
    private static final int HINT_COLOR = 0xFFA0A0A0;

    private enum Special { SHIFT, PAGE, SPACE, BACKSPACE, ENTER, DONE }

    private enum Shift { OFF, ONCE, CAPS }

    /** A key either types {@code text} (or {@code shifted} with shift on) or performs a {@link Special}. */
    private record Key(String text, String shifted, @Nullable Special special, int width) {
        static Key special(Special special, int width) {
            return new Key("", "", special, width);
        }
    }

    private static final List<List<Key>> LETTERS = List.of(
            chars("1234567890", "1234567890"),
            chars("qwertyuiop", "QWERTYUIOP"),
            chars("asdfghjkl'", "ASDFGHJKL\""),
            withSpecialFirst(Special.SHIFT, chars("zxcvbnm,.", "ZXCVBNM;:")),
            bottomRow());
    private static final List<List<Key>> SYMBOLS = List.of(
            chars("!@#$%^&*()", "!@#$%^&*()"),
            chars("-_=+[]{}\\|", "-_=+[]{}\\|"),
            chars(";:\"'<>?/~`", ";:\"'<>?/~`"),
            chars(",.€£¥°±×÷¢", ",.€£¥°±×÷¢"),
            bottomRow());

    private final ControllerContext context;
    private boolean open;
    @Nullable
    private EditBox target;
    private boolean symbols;
    private Shift shift = Shift.OFF;
    private int row;
    private int col;

    private int heldDirX;
    private int heldDirY;
    private double navigationTimer;
    private boolean backspaceHeld;
    private double backspaceTimer;

    public OnScreenKeyboard(ControllerContext context) {
        this.context = context;
    }

    private static List<Key> chars(String text, String shifted) {
        List<Key> keys = new ArrayList<>();
        int[] lower = text.codePoints().toArray();
        int[] upper = shifted.codePoints().toArray();
        for (int i = 0; i < lower.length; i++) {
            keys.add(new Key(Character.toString(lower[i]), Character.toString(upper[i]), null, 2));
        }
        return keys;
    }

    private static List<Key> withSpecialFirst(Special special, List<Key> keys) {
        List<Key> row = new ArrayList<>();
        row.add(Key.special(special, ROW_UNITS - keys.size() * 2));
        row.addAll(keys);
        return row;
    }

    private static List<Key> bottomRow() {
        return List.of(Key.special(Special.PAGE, 3), Key.special(Special.SPACE, 8), Key.special(Special.BACKSPACE, 3),
                Key.special(Special.ENTER, 3), Key.special(Special.DONE, 3));
    }

    public boolean isOpen() {
        return open;
    }

    /** Opens the keyboard. {@code target} is the text box being typed into, if known; it decides placement and auto-close. */
    public void open(@Nullable EditBox target) {
        this.open = true;
        this.target = target;
        this.symbols = false;
        this.shift = Shift.OFF;
        this.row = 1;
        this.col = 0;
        resetHeld();
    }

    public void close() {
        open = false;
        target = null;
        resetHeld();
    }

    private void resetHeld() {
        heldDirX = 0;
        heldDirY = 0;
        backspaceHeld = false;
    }

    private List<List<Key>> rows() {
        return symbols ? SYMBOLS : LETTERS;
    }

    private Key selected() {
        return rows().get(row).get(col);
    }

    /** Button press edges. Navigation and backspace are handled in {@link #frame} because they repeat while held. */
    public void onButtonPressed(ControllerButton button) {
        ScreenInput input = context.screenInput();
        switch (button) {
            case A -> activate(selected());
            case Y -> typeText(" ");
            case LEFT_BUMPER -> input.tapKey(GLFW.GLFW_KEY_LEFT);
            case RIGHT_BUMPER -> input.tapKey(GLFW.GLFW_KEY_RIGHT);
            case LEFT_TRIGGER -> cycleShift();
            case START -> input.tapKey(GLFW.GLFW_KEY_ENTER);
            case B -> context.closeKeyboard();
            default -> {
            }
        }
    }

    /** Held-button handling: highlight movement and backspace, both with key repeat. */
    public void frame(ControllerState state, float deltaSeconds, Set<ControllerButton> suppressed) {
        if (target != null && !target.canConsumeInput()) {
            context.closeKeyboard();
            return;
        }

        float[] stick = state.stick(ControllerAxis.LEFT_X, ControllerAxis.LEFT_Y, ControllerConfig.get().stickDeadzone);
        int dirX = 0;
        int dirY = 0;
        if (isHeld(state, suppressed, ControllerButton.DPAD_LEFT)) dirX--;
        if (isHeld(state, suppressed, ControllerButton.DPAD_RIGHT)) dirX++;
        if (isHeld(state, suppressed, ControllerButton.DPAD_UP)) dirY--;
        if (isHeld(state, suppressed, ControllerButton.DPAD_DOWN)) dirY++;
        if (dirX == 0 && dirY == 0) {
            if (Math.abs(stick[0]) >= Math.abs(stick[1])) {
                dirX = Math.abs(stick[0]) > STICK_THRESHOLD ? (int) Math.signum(stick[0]) : 0;
            } else {
                dirY = Math.abs(stick[1]) > STICK_THRESHOLD ? (int) Math.signum(stick[1]) : 0;
            }
        }
        if (dirX != heldDirX || dirY != heldDirY) {
            heldDirX = dirX;
            heldDirY = dirY;
            navigationTimer = INITIAL_REPEAT_DELAY;
            move(dirX, dirY);
        } else if (dirX != 0 || dirY != 0) {
            navigationTimer -= deltaSeconds;
            if (navigationTimer <= 0) {
                navigationTimer += REPEAT_INTERVAL;
                move(dirX, dirY);
            }
        }

        boolean backspace = isHeld(state, suppressed, ControllerButton.X);
        if (backspace && !backspaceHeld) {
            backspaceTimer = INITIAL_REPEAT_DELAY;
            context.screenInput().tapKey(GLFW.GLFW_KEY_BACKSPACE);
        } else if (backspace) {
            backspaceTimer -= deltaSeconds;
            if (backspaceTimer <= 0) {
                backspaceTimer += REPEAT_INTERVAL;
                context.screenInput().tapKey(GLFW.GLFW_KEY_BACKSPACE);
            }
        }
        backspaceHeld = backspace;
    }

    private static boolean isHeld(ControllerState state, Set<ControllerButton> suppressed, ControllerButton button) {
        return state.isDown(button) && !suppressed.contains(button);
    }

    private void move(int dirX, int dirY) {
        List<List<Key>> rows = rows();
        if (dirX != 0) {
            int size = rows.get(row).size();
            col = Math.floorMod(col + dirX, size);
        }
        if (dirY != 0) {
            double center = keyCenterUnits(rows.get(row), col);
            row = Math.floorMod(row + dirY, rows.size());
            col = closestKey(rows.get(row), center);
        }
    }

    private static double keyCenterUnits(List<Key> keys, int index) {
        int start = 0;
        for (int i = 0; i < index; i++) {
            start += keys.get(i).width();
        }
        return start + keys.get(index).width() / 2.0;
    }

    private static int closestKey(List<Key> keys, double centerUnits) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < keys.size(); i++) {
            double distance = Math.abs(keyCenterUnits(keys, i) - centerUnits);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private void activate(Key key) {
        ScreenInput input = context.screenInput();
        if (key.special() == null) {
            typeText(shift == Shift.OFF ? key.text() : key.shifted());
            if (shift == Shift.ONCE) {
                shift = Shift.OFF;
            }
            return;
        }
        switch (key.special()) {
            case SHIFT -> cycleShift();
            case PAGE -> symbols = !symbols;
            case SPACE -> typeText(" ");
            case BACKSPACE -> input.tapKey(GLFW.GLFW_KEY_BACKSPACE);
            case ENTER -> input.tapKey(GLFW.GLFW_KEY_ENTER);
            case DONE -> context.closeKeyboard();
        }
    }

    private void typeText(String text) {
        text.codePoints().forEach(context.screenInput()::typeChar);
    }

    private void cycleShift() {
        shift = switch (shift) {
            case OFF -> Shift.ONCE;
            case ONCE -> Shift.CAPS;
            case CAPS -> Shift.OFF;
        };
    }

    public void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        Window window = minecraft.getWindow();
        int screenWidth = window.getGuiScaledWidth();
        int screenHeight = window.getGuiScaledHeight();

        int keysWidth = Math.min(screenWidth - 2 * PADDING - 8, Math.max(220, (int) (screenWidth * 0.6)));
        double unit = (double) keysWidth / ROW_UNITS;
        List<List<Key>> rows = rows();
        int panelWidth = keysWidth + 2 * PADDING;
        int panelHeight = rows.size() * (KEY_HEIGHT + GAP) - GAP + 2 * PADDING + HINT_HEIGHT;
        int panelLeft = (screenWidth - panelWidth) / 2;
        boolean atTop = target != null && target.getY() + target.getHeight() / 2 > screenHeight / 2;
        int panelTop = atTop ? 4 : screenHeight - panelHeight - 4;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, RENDER_Z);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, PANEL_COLOR);

        int left = panelLeft + PADDING;
        for (int r = 0; r < rows.size(); r++) {
            List<Key> keys = rows.get(r);
            int top = panelTop + PADDING + r * (KEY_HEIGHT + GAP);
            int units = 0;
            for (int c = 0; c < keys.size(); c++) {
                Key key = keys.get(c);
                int x0 = left + (int) Math.round(units * unit);
                units += key.width();
                int x1 = left + (int) Math.round(units * unit) - GAP;
                boolean isSelected = r == row && c == col;
                graphics.fill(x0, top, x1, top + KEY_HEIGHT, keyColor(key, isSelected));
                Component label = label(key);
                graphics.drawString(font, label, (x0 + x1 - font.width(label)) / 2, top + (KEY_HEIGHT - 8) / 2,
                        isSelected ? SELECTED_TEXT_COLOR : TEXT_COLOR, !isSelected);
            }
        }

        Component hint = Component.translatable("simple_controller_support.keyboard.hint");
        graphics.drawCenteredString(font, hint, screenWidth / 2, panelTop + panelHeight - PADDING - 8, HINT_COLOR);
        graphics.pose().popPose();
    }

    private int keyColor(Key key, boolean isSelected) {
        if (isSelected) {
            return SELECTED_KEY_COLOR;
        }
        if (key.special() == Special.SHIFT && shift != Shift.OFF) {
            return ACTIVE_KEY_COLOR;
        }
        return key.special() != null ? SPECIAL_KEY_COLOR : KEY_COLOR;
    }

    private Component label(Key key) {
        if (key.special() == null) {
            return Component.literal(shift == Shift.OFF ? key.text() : key.shifted());
        }
        return switch (key.special()) {
            case SHIFT -> text(shift == Shift.CAPS ? "caps" : "shift");
            case PAGE -> text(symbols ? "letters" : "symbols");
            case SPACE -> text("space");
            case BACKSPACE -> text("backspace");
            case ENTER -> text("enter");
            case DONE -> text("done");
        };
    }

    private static Component text(String key) {
        return Component.translatable("simple_controller_support.keyboard." + key);
    }
}
