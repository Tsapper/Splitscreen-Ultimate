package tsapper.simple_controller_support.client.snap;

import net.minecraft.client.gui.navigation.ScreenAxis;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import tsapper.simple_controller_support.client.snap.SnapTargetSource.Query;

import java.util.List;
import java.util.function.Supplier;

/** Picks the clickable thing the cursor should jump to for a D-pad direction. */
public final class CursorSnapper {
    /** How much sideways distance counts against a target compared to distance in the snap direction. */
    private static final double SIDEWAYS_WEIGHT = 2.0;
    /** Breaks ties between targets that overlap the origin sideways, preferring the one most in line. */
    private static final double ALIGNMENT_WEIGHT = 0.1;

    private CursorSnapper() {
    }

    /**
     * Finds the nearest target in {@code direction}. If the cursor is over a target, the search starts from that
     * target's edge so it moves on to the next one. A target scrolled out of view is scrolled into view here.
     *
     * @return where to put the cursor, or null if nothing lies in that direction
     */
    @Nullable
    public static ScreenPosition snap(Screen screen, double cursorX, double cursorY, ScreenDirection direction) {
        Query query = new Query(direction, cursorX, cursorY, searchArea(screen, cursorX, cursorY, direction));
        List<SnapTarget> targets = SnapTargets.collect(screen, query);

        SnapTarget origin = null;
        for (SnapTarget target : targets) {
            ScreenRectangle rect = target.rect();
            if (target.reveal() == null && contains(rect, cursorX, cursorY)
                    && (origin == null || area(rect) < area(origin.rect()))) {
                origin = target;
            }
        }

        boolean horizontal = direction.getAxis() == ScreenAxis.HORIZONTAL;
        double cursorAlong = horizontal ? cursorX : cursorY;
        double cursorAcross = horizontal ? cursorY : cursorX;
        double originStart = cursorAlong;
        double originEnd = cursorAlong;
        double originCentreAlong = cursorAlong;
        double originCentreAcross = cursorAcross;
        double originAcrossStart = cursorAcross;
        double originAcrossEnd = cursorAcross;
        if (origin != null) {
            ScreenRectangle rect = origin.rect();
            originStart = horizontal ? rect.left() : rect.top();
            originEnd = horizontal ? rect.right() : rect.bottom();
            originAcrossStart = horizontal ? rect.top() : rect.left();
            originAcrossEnd = horizontal ? rect.bottom() : rect.right();
            originCentreAlong = horizontal ? origin.centerX() : origin.centerY();
            originCentreAcross = horizontal ? origin.centerY() : origin.centerX();
        }
        double leadingEdge = direction.isPositive() ? originEnd : originStart;

        SnapTarget best = null;
        double bestScore = Double.MAX_VALUE;
        for (SnapTarget target : targets) {
            if (target == origin) {
                continue;
            }
            ScreenRectangle rect = target.rect();
            double along = horizontal ? target.centerX() : target.centerY();
            if (direction.isPositive() ? along <= leadingEdge : along >= leadingEdge) {
                continue;
            }
            double acrossStart = horizontal ? rect.top() : rect.left();
            double acrossEnd = horizontal ? rect.bottom() : rect.right();
            double across = horizontal ? target.centerY() : target.centerX();
            double gap = Math.max(0, Math.max(acrossStart - originAcrossEnd, originAcrossStart - acrossEnd));
            double score = Math.abs(along - originCentreAlong) + SIDEWAYS_WEIGHT * gap
                    + ALIGNMENT_WEIGHT * Math.abs(across - originCentreAcross);
            if (score < bestScore) {
                bestScore = score;
                best = target;
            }
        }
        if (best == null) {
            return null;
        }

        Supplier<ScreenRectangle> reveal = best.reveal();
        if (reveal == null) {
            return new ScreenPosition((int) best.centerX(), (int) best.centerY());
        }
        // A row scrolled into view: keep the cursor's column so it lands on the same button of an options row
        ScreenRectangle rect = reveal.get();
        int x = (int) Math.max(rect.left(), Math.min(cursorX, rect.right() - 1));
        return new ScreenPosition(x, rect.top() + rect.height() / 2);
    }

    /** The part of the screen ahead of the cursor in {@code direction}. */
    private static ScreenRectangle searchArea(Screen screen, double cursorX, double cursorY, ScreenDirection direction) {
        int x = (int) cursorX;
        int y = (int) cursorY;
        return switch (direction) {
            case UP -> new ScreenRectangle(0, 0, screen.width, y + 1);
            case DOWN -> new ScreenRectangle(0, y, screen.width, screen.height - y);
            case LEFT -> new ScreenRectangle(0, 0, x + 1, screen.height);
            case RIGHT -> new ScreenRectangle(x, 0, screen.width - x, screen.height);
        };
    }

    private static boolean contains(ScreenRectangle rect, double x, double y) {
        return x >= rect.left() && x < rect.right() && y >= rect.top() && y < rect.bottom();
    }

    private static int area(ScreenRectangle rect) {
        return rect.width() * rect.height();
    }
}
