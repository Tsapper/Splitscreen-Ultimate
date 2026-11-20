package tsapper.simple_controller_support.client.binding;

import net.minecraft.network.chat.Component;
import tsapper.simple_controller_support.client.input.ControllerContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Registry of controller-only actions, i.e. things that have no {@link net.minecraft.client.KeyMapping}
 * (hotbar scrolling, pausing, menu clicks...). Other mods can register their own here; they show up in
 * the controls menu next to the key mappings.
 */
public final class ControllerActions {
    private static final List<BindingAction> ACTIONS = new ArrayList<>();

    private ControllerActions() {
    }

    public static BindingAction register(String id, String category, BindingAction.ActionContext context,
                                         Consumer<ControllerContext> onPress, Consumer<ControllerContext> onRelease) {
        BindingAction action = new SimpleAction(id, category, context, onPress, onRelease);
        ACTIONS.add(action);
        ActionRegistry.invalidate();
        return action;
    }

    public static BindingAction register(String id, String category, BindingAction.ActionContext context,
                                         Consumer<ControllerContext> onPress) {
        return register(id, category, context, onPress, ctx -> {
        });
    }

    public static List<BindingAction> all() {
        return Collections.unmodifiableList(ACTIONS);
    }

    /** Display name is looked up from the translation key {@code <id with ':' replaced by '.'>}. */
    private record SimpleAction(String id, String category, ActionContext context,
                                Consumer<ControllerContext> pressHandler,
                                Consumer<ControllerContext> releaseHandler) implements BindingAction {
        @Override
        public Component displayName() {
            return Component.translatable(id.replace(':', '.'));
        }

        @Override
        public void onPress(ControllerContext ctx) {
            pressHandler.accept(ctx);
        }

        @Override
        public void onRelease(ControllerContext ctx) {
            releaseHandler.accept(ctx);
        }
    }
}
