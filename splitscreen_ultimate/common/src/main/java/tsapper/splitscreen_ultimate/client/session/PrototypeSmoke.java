package tsapper.splitscreen_ultimate.client.session;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import tsapper.simple_controller_support.api.*;
import tsapper.simple_controller_support.client.controller.ControllerButton;
import tsapper.splitscreen_ultimate.SplitscreenUltimateMod;
import java.util.*;

/** Opt-in end-to-end fixture. Never runs in normal sessions; creates only uniquely named test worlds. */
final class PrototypeSmoke {
    private final List<ExternalController> pads = new ArrayList<>();
    private int tick, phase, worlds;
    private int stable;
    private long started = System.nanoTime();
    void tick(SessionClient session, int ready, int inWorld, int observedInput) {
        var mc = Minecraft.getInstance();
        if (System.nanoTime() - started > 600_000_000_000L) {
            SplitscreenUltimateMod.LOGGER.error("SPLITSCREEN_SMOKE FAIL: timeout, phase {} ready {} world {}", phase, ready, inWorld);
            mc.stop(); return;
        }
        if (pads.isEmpty()) {
            if (!(mc.screen instanceof TitleScreen)) return;
            for (int i = 0; i < 4; i++) pads.add(ControllerSupport.registerExternalController("Smoke test pad " + (i + 1)));
        }
        tick++;
        if (phase == 1 && inWorld == 4) stable++;
        for (int i = 0; i < pads.size(); i++) pads.get(i).submit(new ControllerSample(Map.of(),
                (phase == 0 && tick == 20 + i * 20) || (phase == 1 && stable > 0 && stable % 40 >= 30)
                        ? Set.of(ControllerButton.A) : Set.of()));
        if (phase == 0 && ready == 4 && tick > 100) { phase = 1; createWorld(); }
        else if (phase == 1 && inWorld == 4 && observedInput == 3) {
            SplitscreenUltimateMod.LOGGER.info("SPLITSCREEN_SMOKE: four players connected in world {}", worlds);
            if (worlds == 1) session.smokePublishLan();
            // Force the pause/LAN screen classes to transform during runtime validation.
            mc.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true));
            new net.minecraft.client.gui.screens.ShareToLanScreen(mc.screen);
            phase = 2; tick = stable = 0;
            mc.level.disconnect();
            mc.disconnect(new TitleScreen());
        } else if (phase == 2 && inWorld == 0 && tick > 60 && mc.level == null && mc.getSingleplayerServer() == null) {
            if (worlds == 1) { phase = 1; createWorld(); }
            else {
                SplitscreenUltimateMod.LOGGER.info("SPLITSCREEN_SMOKE PASS: four players joined two worlds; guests reused between worlds");
                mc.stop(); phase = 3;
            }
        }
        if (tick % 200 == 0) SplitscreenUltimateMod.LOGGER.info("SPLITSCREEN_SMOKE progress: phase {} ready {} world {} observed {} stable {}", phase, ready, inWorld, observedInput, stable);
    }
    private void createWorld() {
        worlds++;
        var mc = Minecraft.getInstance();
        String name = "splitscreen-smoke-" + UUID.randomUUID();
        mc.createWorldOpenFlows().createFreshLevel(name,
                new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                new WorldOptions(12345L, false, false), registries -> registries
                        .registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
    }
}
