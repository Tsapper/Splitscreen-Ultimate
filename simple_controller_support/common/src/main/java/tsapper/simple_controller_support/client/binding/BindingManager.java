package tsapper.simple_controller_support.client.binding;

import org.jetbrains.annotations.Nullable;
import tsapper.simple_controller_support.client.config.ControllerConfig;
import tsapper.simple_controller_support.client.controller.ControllerButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads and writes what each action is bound to. Only differences from {@link DefaultBindings}
 * are stored in the config, so changing a default also updates players who never rebound that action.
 *
 * <p>In gameplay, a D-pad direction that starts any {@link Binding.Combo} becomes a combo prefix: pressing it
 * waits for the second press, so a single binding on that direction never fires (reported as a conflict).
 */
public final class BindingManager {
    private static final String UNBOUND = "none";

    private BindingManager() {
    }

    @Nullable
    public static Binding get(BindingAction action) {
        return get(action.id());
    }

    @Nullable
    public static Binding get(String actionId) {
        String stored = ControllerConfig.get().bindings.get(actionId);
        if (stored == null) {
            return DefaultBindings.get(actionId);
        }
        if (stored.equals(UNBOUND)) {
            return null;
        }
        Binding binding = Binding.parse(stored);
        return binding != null ? binding : DefaultBindings.get(actionId);
    }

    /** Binds an action, or unbinds it when {@code binding} is null. Saves the config. */
    public static void set(BindingAction action, @Nullable Binding binding) {
        if (Objects.equals(binding, DefaultBindings.get(action.id()))) {
            ControllerConfig.get().bindings.remove(action.id());
        } else {
            ControllerConfig.get().bindings.put(action.id(), binding == null ? UNBOUND : binding.serialize());
        }
        ControllerConfig.save();
    }

    public static void reset(BindingAction action) {
        ControllerConfig.get().bindings.remove(action.id());
        ControllerConfig.save();
    }

    /** Resets every currently known action. Overrides for actions from removed mods are left alone. */
    public static void resetAll() {
        for (BindingAction action : ActionRegistry.all()) {
            ControllerConfig.get().bindings.remove(action.id());
        }
        ControllerConfig.save();
    }

    public static boolean isDefault(BindingAction action) {
        return Objects.equals(get(action), DefaultBindings.get(action.id()));
    }

    /** All actions in the given context bound to exactly this binding. */
    public static List<BindingAction> actionsFor(Binding binding, BindingAction.ActionContext context) {
        List<BindingAction> result = new ArrayList<>();
        for (BindingAction action : ActionRegistry.all()) {
            if (action.context() == context && binding.equals(get(action))) {
                result.add(action);
            }
        }
        return result;
    }

    /** All actions in the given context bound to this button on its own. */
    public static List<BindingAction> actionsFor(ControllerButton button, BindingAction.ActionContext context) {
        return actionsFor(Binding.of(button), context);
    }

    /** Gameplay actions bound to the combo {@code first} then {@code second}. */
    public static List<BindingAction> actionsForCombo(ControllerButton first, ControllerButton second) {
        return actionsFor(new Binding.Combo(first, second), BindingAction.ActionContext.GAMEPLAY);
    }

    /** Whether any gameplay combo starts with this button, so pressing it waits for a second press. */
    public static boolean isComboPrefix(ControllerButton button) {
        for (BindingAction action : ActionRegistry.all()) {
            if (action.context() == BindingAction.ActionContext.GAMEPLAY
                    && get(action) instanceof Binding.Combo combo && combo.first() == button) {
                return true;
            }
        }
        return false;
    }

    /**
     * Other actions in the same context sharing this action's binding. In gameplay this also includes
     * combos shadowing a single D-pad binding, and single bindings shadowed by a combo.
     */
    public static List<BindingAction> conflictsWith(BindingAction action) {
        Binding binding = get(action);
        if (binding == null) {
            return List.of();
        }
        List<BindingAction> conflicts = new ArrayList<>();
        for (BindingAction other : ActionRegistry.all()) {
            if (other == action || other.context() != action.context()) {
                continue;
            }
            Binding otherBinding = get(other);
            if (binding.equals(otherBinding) || (action.context() == BindingAction.ActionContext.GAMEPLAY
                    && (shadows(binding, otherBinding) || shadows(otherBinding, binding)))) {
                conflicts.add(other);
            }
        }
        return conflicts;
    }

    /** Whether {@code combo} turns {@code single}'s button into a prefix, so the single binding never fires. */
    private static boolean shadows(@Nullable Binding combo, @Nullable Binding single) {
        return combo instanceof Binding.Combo c && single instanceof Binding.Single s && c.first() == s.button();
    }
}
