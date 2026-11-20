package tsapper.simple_controller_support.client.snap;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Something clickable the cursor can snap to, in GUI-scaled coordinates.
 *
 * @param reveal for targets that are scrolled out of view (list rows): scrolls them into view and returns
 *               the new rectangle. Null for targets that are already visible.
 */
public record SnapTarget(ScreenRectangle rect, @Nullable Supplier<ScreenRectangle> reveal) {
    public SnapTarget(ScreenRectangle rect) {
        this(rect, null);
    }

    public SnapTarget(int x, int y, int width, int height) {
        this(new ScreenRectangle(x, y, width, height));
    }

    public double centerX() {
        return rect.left() + rect.width() / 2.0;
    }

    public double centerY() {
        return rect.top() + rect.height() / 2.0;
    }
}
