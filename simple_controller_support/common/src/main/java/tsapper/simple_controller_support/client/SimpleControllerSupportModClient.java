package tsapper.simple_controller_support.client;

import dev.architectury.event.events.client.ClientGuiEvent;
import net.minecraft.client.Minecraft;
import tsapper.simple_controller_support.SimpleControllerSupportMod;
import tsapper.simple_controller_support.client.binding.BuiltinActions;
import tsapper.simple_controller_support.client.config.ControllerConfig;
import tsapper.simple_controller_support.client.input.ControllerContext;
import tsapper.simple_controller_support.client.input.ControllerInputHandler;

/**
 * Client setup. Input itself is driven per frame from {@code MinecraftMixin}; see
 * {@link ControllerInputHandler} for the flow.
 */
public final class SimpleControllerSupportModClient {

    public void init() {
        ControllerConfig.load();
        BuiltinActions.register();

        // Draw the on-screen keyboard, or otherwise the virtual cursor, on top of every screen while the controller is in use
        ClientGuiEvent.RENDER_POST.register((screen, graphics, mouseX, mouseY, delta) -> {
            if (ControllerInputHandler.isControllerActive()) {
                ControllerContext context = ControllerInputHandler.primary();
                if (context.keyboard().isOpen()) {
                    context.keyboard().render(graphics);
                } else {
                    context.cursor().render(graphics);
                }
            }
        });

        // Quick-chat style list of D-pad combos reachable from the pending first press
        ClientGuiEvent.RENDER_HUD.register((graphics, delta) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (ControllerInputHandler.isControllerActive() && minecraft.screen == null && !minecraft.options.hideGui) {
                ControllerInputHandler.primary().combos().render(graphics);
            }
        });

        SimpleControllerSupportMod.LOGGER.info("Controller support initialised");
    }
}
