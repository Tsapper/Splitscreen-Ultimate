package tsapper.simple_controller_support.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import org.jetbrains.annotations.Nullable;
import tsapper.simple_controller_support.mixin.OptionsSubScreenAccessor;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Replaces the vanilla Controls screen (Options -> Controls...) with {@link ControllerControlsScreen}.
 * The vanilla screen stays reachable through {@link #openVanilla(Screen)}.
 */
public final class ControlsScreenRedirect {
    /** Vanilla screens we opened on purpose; they may be reopened when returning from their sub-screens. */
    private static final Set<Screen> ALLOWED = Collections.newSetFromMap(new WeakHashMap<>());

    private ControlsScreenRedirect() {
    }

    @Nullable
    public static Screen redirect(@Nullable Screen screen) {
        // Exact class check: subclasses from other mods are left alone
        if (screen != null && screen.getClass() == ControlsScreen.class && !ALLOWED.contains(screen)) {
            return new ControllerControlsScreen(((OptionsSubScreenAccessor) screen).scs$getLastScreen());
        }
        return screen;
    }

    public static void openVanilla(Screen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen vanilla = new ControlsScreen(parent, minecraft.options);
        ALLOWED.add(vanilla);
        minecraft.setScreen(vanilla);
    }
}
