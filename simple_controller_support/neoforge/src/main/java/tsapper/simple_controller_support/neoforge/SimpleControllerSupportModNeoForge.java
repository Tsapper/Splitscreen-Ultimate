package tsapper.simple_controller_support.neoforge;

import net.neoforged.fml.common.Mod;

import tsapper.simple_controller_support.SimpleControllerSupportMod;

@Mod(SimpleControllerSupportMod.MOD_ID)
public final class SimpleControllerSupportModNeoForge {
    public SimpleControllerSupportModNeoForge() {
        // Run our common setup.
        SimpleControllerSupportMod.init();
    }
}
