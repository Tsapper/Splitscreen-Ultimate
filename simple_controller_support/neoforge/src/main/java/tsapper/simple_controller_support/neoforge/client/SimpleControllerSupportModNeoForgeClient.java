package tsapper.simple_controller_support.neoforge.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import tsapper.simple_controller_support.SimpleControllerSupportMod;
import tsapper.simple_controller_support.client.SimpleControllerSupportModClient;

@Mod(value = SimpleControllerSupportMod.MOD_ID, dist = Dist.CLIENT)
public class SimpleControllerSupportModNeoForgeClient {

    public SimpleControllerSupportModNeoForgeClient(IEventBus modBus) {
        modBus.addListener(this::onInitializeClient);
    }

    private void onInitializeClient(FMLClientSetupEvent event) {

        SimpleControllerSupportModClient client = new SimpleControllerSupportModClient();

        // Client setup runs on a worker thread; input state is touched by the render thread, so init there
        event.enqueueWork(client::init);
    }
}
