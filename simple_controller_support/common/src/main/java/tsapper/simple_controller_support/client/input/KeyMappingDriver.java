package tsapper.simple_controller_support.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import tsapper.simple_controller_support.mixin.KeyMappingAccessor;

import java.util.HashSet;
import java.util.Set;

/**
 * The only place that changes {@link KeyMapping} state. Pressing sets {@code isDown} and adds a click,
 * which is exactly what a real key press does, so both {@code consumeClick()} and {@code isDown()}
 * users (vanilla and modded) react. Tracks which mappings the controller is holding so a release
 * never cancels a key the keyboard is still holding.
 */
public final class KeyMappingDriver {
    private final Set<KeyMapping> held = new HashSet<>();

    public void press(KeyMapping keyMapping) {
        keyMapping.setDown(true);
        KeyMappingAccessor accessor = (KeyMappingAccessor) keyMapping;
        accessor.scs$setClickCount(accessor.scs$getClickCount() + 1);
        held.add(keyMapping);
    }

    public void release(KeyMapping keyMapping) {
        if (held.remove(keyMapping) && !isPhysicallyHeld(keyMapping)) {
            keyMapping.setDown(false);
        }
    }

    public void releaseAll() {
        for (KeyMapping keyMapping : Set.copyOf(held)) {
            release(keyMapping);
        }
    }

    /** Clears tracking without touching the mappings, for when vanilla already released them (e.g. a screen opened). */
    public void forgetAll() {
        held.clear();
    }

    private static boolean isPhysicallyHeld(KeyMapping keyMapping) {
        InputConstants.Key key = ((KeyMappingAccessor) keyMapping).scs$getKey();
        return key.getType() == InputConstants.Type.KEYSYM
                && key.getValue() != InputConstants.UNKNOWN.getValue()
                && InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key.getValue());
    }
}
