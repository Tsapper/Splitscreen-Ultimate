package tsapper.simple_controller_support.client.snap;

import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;

/**
 * Supplies snap targets that the built-in scan can't see, e.g. a mod's custom widgets.
 * Register with {@link SnapTargets#registerSource}.
 */
@FunctionalInterface
public interface SnapTargetSource {
    void collect(Screen screen, Query query, Consumer<SnapTarget> out);

    /**
     * @param searchArea the part of the screen in the snap direction; sources may skip targets outside it
     */
    record Query(ScreenDirection direction, double cursorX, double cursorY, ScreenRectangle searchArea) {
    }
}
