package tsapper.splitscreen_ultimate.neoforge.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import tsapper.splitscreen_ultimate.SplitscreenUltimateMod;
import tsapper.splitscreen_ultimate.client.SplitscreenUltimateModClient;

@Mod(value = SplitscreenUltimateMod.MOD_ID, dist = Dist.CLIENT)
public final class SplitscreenUltimateModNeoForgeClient {
    public SplitscreenUltimateModNeoForgeClient(IEventBus modBus) {
        modBus.addListener(this::onInitializeClient);
    }

    private void onInitializeClient(FMLClientSetupEvent event) {
        // Client setup is dispatched from a worker thread; initialise on the main thread.
        event.enqueueWork(() -> new SplitscreenUltimateModClient().init());
    }
}
