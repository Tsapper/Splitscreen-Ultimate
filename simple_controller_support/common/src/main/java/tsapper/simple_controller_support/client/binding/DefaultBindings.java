package tsapper.simple_controller_support.client.binding;

import org.jetbrains.annotations.Nullable;
import tsapper.simple_controller_support.client.controller.ControllerButton;

import java.util.HashMap;
import java.util.Map;

import static tsapper.simple_controller_support.client.controller.ControllerButton.*;

/**
 * Default controller layout, modelled on Minecraft Bedrock Edition with an Xbox controller.
 * This is the single place to change the out-of-the-box layout. Anything not listed starts unbound.
 *
 * <p>Not bindable (always active): left stick = movement, right stick = camera / menu scrolling,
 * left stick = virtual cursor in menus.
 *
 * <p>There are no default D-pad combos; they are left free for the player's own (often modded) actions.
 */
public final class DefaultBindings {
    private static final Map<String, Binding> DEFAULTS = new HashMap<>();

    static {
        // --- Gameplay (vanilla key mappings) ---
        put("key.jump", A);
        put("key.drop", B);
        put("key.inventory", Y);
        put("key.swapOffhand", X);
        put("key.attack", RIGHT_TRIGGER);
        put("key.use", LEFT_TRIGGER);
        put("key.sprint", LEFT_STICK);
        put("key.sneak", RIGHT_STICK);
        put("key.playerlist", BACK);
        put("key.togglePerspective", DPAD_UP);
        put("key.chat", DPAD_RIGHT);
        put("key.pickItem", DPAD_LEFT);

        // --- Gameplay (controller-only actions) ---
        put(BuiltinActions.PAUSE, START);
        put(BuiltinActions.HOTBAR_PREVIOUS, LEFT_BUMPER);
        put(BuiltinActions.HOTBAR_NEXT, RIGHT_BUMPER);

        // --- Menus ---
        put(BuiltinActions.MENU_CLICK, A);
        put(BuiltinActions.MENU_SECONDARY_CLICK, X);
        put(BuiltinActions.MENU_QUICK_MOVE, Y);
        put(BuiltinActions.MENU_BACK, B);
        put(BuiltinActions.OPEN_KEYBOARD, BACK);
        put(BuiltinActions.SNAP_UP, DPAD_UP);
        put(BuiltinActions.SNAP_DOWN, DPAD_DOWN);
        put(BuiltinActions.SNAP_LEFT, DPAD_LEFT);
        put(BuiltinActions.SNAP_RIGHT, DPAD_RIGHT);
    }

    private DefaultBindings() {
    }

    private static void put(String actionId, ControllerButton button) {
        DEFAULTS.put(actionId, Binding.of(button));
    }

    /** Gameplay actions only. Note that a combo turns its first direction into a prefix, shadowing its single binding. */
    @SuppressWarnings("unused")
    private static void putCombo(String actionId, ControllerButton first, ControllerButton second) {
        DEFAULTS.put(actionId, new Binding.Combo(first, second));
    }

    @Nullable
    public static Binding get(String actionId) {
        return DEFAULTS.get(actionId);
    }
}
