package tsapper.simple_controller_support.client.binding;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import tsapper.simple_controller_support.client.controller.ControllerButton;

/**
 * What an action is bound to: a single button, or (gameplay only) a two-press D-pad combo.
 * Stored in the config as {@code "DPAD_UP"} or {@code "DPAD_DOWN+DPAD_UP"}.
 */
public sealed interface Binding {
    String serialize();

    Component displayName();

    record Single(ControllerButton button) implements Binding {
        @Override
        public String serialize() {
            return button.name();
        }

        @Override
        public Component displayName() {
            return button.displayName();
        }
    }

    /** First press a D-pad direction, then another (possibly the same) one. */
    record Combo(ControllerButton first, ControllerButton second) implements Binding {
        public Combo {
            if (!first.isDpad() || !second.isDpad()) {
                throw new IllegalArgumentException("Combos only use D-pad buttons: " + first + ", " + second);
            }
        }

        @Override
        public String serialize() {
            return first.name() + "+" + second.name();
        }

        @Override
        public Component displayName() {
            return Component.translatable("simple_controller_support.binding.combo", first.arrow(), second.arrow());
        }
    }

    static Binding of(ControllerButton button) {
        return new Single(button);
    }

    /** Parses {@link #serialize()} output, or returns null if it isn't valid. */
    @Nullable
    static Binding parse(String value) {
        try {
            int plus = value.indexOf('+');
            if (plus < 0) {
                return new Single(ControllerButton.valueOf(value));
            }
            return new Combo(ControllerButton.valueOf(value.substring(0, plus)),
                    ControllerButton.valueOf(value.substring(plus + 1)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
