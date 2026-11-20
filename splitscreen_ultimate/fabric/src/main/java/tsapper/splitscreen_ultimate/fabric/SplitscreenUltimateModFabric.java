package tsapper.splitscreen_ultimate.fabric;

import net.fabricmc.api.ModInitializer;

import tsapper.splitscreen_ultimate.SplitscreenUltimateMod;

public final class SplitscreenUltimateModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SplitscreenUltimateMod.init();
    }
}
