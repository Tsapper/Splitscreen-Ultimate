package tsapper.splitscreen_ultimate.client;

import tsapper.splitscreen_ultimate.SplitscreenUltimateMod;

/** Client setup shared by Fabric and NeoForge. */
public final class SplitscreenUltimateModClient {
    public void init() {
        SplitscreenUltimateMod.LOGGER.info("Splitscreen Ultimate client initialised");
        // Fabric invokes client entrypoints before Minecraft finishes constructing its client thread.
        // Session bootstrap runs before the first controller poll, in MinecraftSessionMixin.
        if (Boolean.getBoolean("splitscreen.loaderCheck")) {
            dev.architectury.event.events.client.ClientTickEvent.CLIENT_POST.register(client -> {
                if (client.getOverlay() == null && client.level == null
                        && !(client.screen instanceof net.minecraft.client.gui.screens.TitleScreen))
                    client.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
                if (client.screen instanceof net.minecraft.client.gui.screens.TitleScreen && client.getOverlay() == null) {
                    Class<?>[] targets = { net.minecraft.client.server.IntegratedServer.class,
                            net.minecraft.server.network.ServerLoginPacketListenerImpl.class,
                            net.minecraft.client.gui.screens.PauseScreen.class,
                            net.minecraft.client.gui.screens.ShareToLanScreen.class,
                            net.minecraft.client.player.KeyboardInput.class };
                    for (Class<?> target : targets) target.getDeclaredMethods();
                    SplitscreenUltimateMod.LOGGER.info("SPLITSCREEN_LOADER_CHECK PASS: title screen and session mixin targets loaded");
                    client.stop();
                }
            });
        }
    }
}
