package tsapper.splitscreen_ultimate.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import tsapper.splitscreen_ultimate.client.SplitscreenUltimateModClient;

public final class SplitscreenUltimateModFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        new SplitscreenUltimateModClient().init();
    }
}
