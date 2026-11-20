package tsapper.simple_controller_support.client.cursor;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A mouse pointer owned by the mod, independent of the OS cursor (which needs window focus to move).
 * Position is stored in GUI-scaled coordinates, the same space screens use for mouseX/mouseY.
 */
public final class VirtualCursor {
    /** Drawn above tooltips (z 400) and dragged items. */
    private static final float RENDER_Z = 2000f;
    private static final int OUTLINE_COLOR = 0xFF000000;
    private static final int FILL_COLOR = 0xFFFFFFFF;
    /**
     * Pointer shape, one character per GUI pixel: '#' = outline, '.' = fill, ' ' = transparent.
     * The hotspot is the top-left pixel.
     */
    private static final String[] SHAPE = {
            "#",
            "##",
            "#.#",
            "#..#",
            "#...#",
            "#....#",
            "#.....#",
            "#......#",
            "#.......#",
            "#....#####",
            "#..#..#",
            "#.# #..#",
            "##  #..#",
            "     ##",
    };

    private double x;
    private double y;

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
        clamp();
    }

    public void move(double dx, double dy) {
        setPosition(x + dx, y + dy);
    }

    public void center() {
        Window window = Minecraft.getInstance().getWindow();
        setPosition(window.getGuiScaledWidth() / 2.0, window.getGuiScaledHeight() / 2.0);
    }

    /** Keeps the cursor on screen, e.g. after the window or GUI scale changes. */
    public void clamp() {
        Window window = Minecraft.getInstance().getWindow();
        x = Math.max(0, Math.min(x, window.getGuiScaledWidth() - 1));
        y = Math.max(0, Math.min(y, window.getGuiScaledHeight() - 1));
    }

    /** Position in window pixels, the coordinate space of {@code MouseHandler.xpos()/ypos()}. */
    public double windowX() {
        Window window = Minecraft.getInstance().getWindow();
        return x * window.getScreenWidth() / window.getGuiScaledWidth();
    }

    public double windowY() {
        Window window = Minecraft.getInstance().getWindow();
        return y * window.getScreenHeight() / window.getGuiScaledHeight();
    }

    /** Sets the position from window pixels. */
    public void setWindowPosition(double windowX, double windowY) {
        Window window = Minecraft.getInstance().getWindow();
        setPosition(windowX * window.getGuiScaledWidth() / window.getScreenWidth(),
                windowY * window.getGuiScaledHeight() / window.getScreenHeight());
    }

    public void render(GuiGraphics graphics) {
        int left = (int) x;
        int top = (int) y;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, RENDER_Z);
        for (int row = 0; row < SHAPE.length; row++) {
            String line = SHAPE[row];
            for (int col = 0; col < line.length(); col++) {
                char c = line.charAt(col);
                if (c == ' ') {
                    continue;
                }
                int color = c == '#' ? OUTLINE_COLOR : FILL_COLOR;
                graphics.fill(left + col, top + row, left + col + 1, top + row + 1, color);
            }
        }
        graphics.pose().popPose();
    }
}
