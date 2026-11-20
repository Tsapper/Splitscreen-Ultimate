package tsapper.simple_controller_support.client.binding;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import tsapper.simple_controller_support.client.input.ControllerContext;

/** Binds a controller button to any vanilla or mod {@link KeyMapping}. */
public final class KeyMappingAction implements BindingAction {
    private final KeyMapping keyMapping;

    public KeyMappingAction(KeyMapping keyMapping) {
        this.keyMapping = keyMapping;
    }

    public KeyMapping keyMapping() {
        return keyMapping;
    }

    @Override
    public String id() {
        return keyMapping.getName();
    }

    @Override
    public Component displayName() {
        return Component.translatable(keyMapping.getName());
    }

    @Override
    public String category() {
        return keyMapping.getCategory();
    }

    @Override
    public ActionContext context() {
        return ActionContext.GAMEPLAY;
    }

    @Override
    public void onPress(ControllerContext ctx) {
        ctx.keyMappings().press(keyMapping);
    }

    @Override
    public void onRelease(ControllerContext ctx) {
        ctx.keyMappings().release(keyMapping);
    }
}
