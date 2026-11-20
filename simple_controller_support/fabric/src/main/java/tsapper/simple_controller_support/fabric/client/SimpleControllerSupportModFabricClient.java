package tsapper.simple_controller_support.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import tsapper.simple_controller_support.client.SimpleControllerSupportModClient;

public final class SimpleControllerSupportModFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.

        SimpleControllerSupportModClient client = new SimpleControllerSupportModClient();

        client.init();
    }
}
