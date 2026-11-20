package tsapper.simple_controller_support.client.binding;

import net.minecraft.network.chat.Component;
import tsapper.simple_controller_support.client.input.ControllerContext;

/**
 * Something a controller button can be bound to: either a vanilla/mod {@link net.minecraft.client.KeyMapping}
 * ({@link KeyMappingAction}) or a controller-only action registered in {@link ControllerActions}.
 */
public interface BindingAction {
    /** Stable id used in the config file. For key mappings this is the KeyMapping name, e.g. {@code key.jump}. */
    String id();

    Component displayName();

    /** Translation key of the category shown in the controls menu. */
    String category();

    /** Whether this action is used while playing or while a screen is open. */
    ActionContext context();

    void onPress(ControllerContext ctx);

    void onRelease(ControllerContext ctx);

    enum ActionContext {
        /** Active while no screen is open. */
        GAMEPLAY,
        /** Active while a screen (menu, inventory, ...) is open. */
        SCREEN
    }
}
