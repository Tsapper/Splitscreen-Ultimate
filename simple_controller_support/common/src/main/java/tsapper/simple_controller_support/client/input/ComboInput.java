package tsapper.simple_controller_support.client.input;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;
import tsapper.simple_controller_support.client.binding.BindingAction;
import tsapper.simple_controller_support.client.binding.BindingManager;
import tsapper.simple_controller_support.client.config.ControllerConfig;
import tsapper.simple_controller_support.client.controller.ControllerButton;

import java.util.List;

/**
 * Rocket League quick-chat style D-pad combos: after the first press of a combo prefix this holds the
 * pending direction until the second press, the timeout, or a context change. Also draws the HUD
 * listing the actions reachable from the pending direction.
 */
public final class ComboInput {
    private static final ControllerButton[] DIRECTIONS = {
            ControllerButton.DPAD_UP, ControllerButton.DPAD_RIGHT, ControllerButton.DPAD_DOWN, ControllerButton.DPAD_LEFT};
    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 11;
    private static final int MAX_NAME_WIDTH = 160;

    @Nullable
    private ControllerButton pending;
    private long startTime;

    @Nullable
    public ControllerButton pending() {
        return pending;
    }

    public void start(ControllerButton first) {
        pending = first;
        startTime = Util.getMillis();
    }

    public void clear() {
        pending = null;
    }

    void frame() {
        if (pending != null && remainingFraction() <= 0) {
            pending = null;
        }
    }

    private float remainingFraction() {
        float timeoutMs = ControllerConfig.get().comboTimeout * 1000f;
        return 1f - (Util.getMillis() - startTime) / timeoutMs;
    }

    /** Panel on the left edge: the first direction as title, then one row per second direction. */
    public void render(GuiGraphics graphics) {
        if (pending == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        Component title = Component.literal(pending.arrow() + " ").append(pending.displayName())
                .withStyle(ChatFormatting.YELLOW);
        Component[] rows = new Component[DIRECTIONS.length];
        int width = font.width(title);
        for (int i = 0; i < DIRECTIONS.length; i++) {
            rows[i] = row(font, DIRECTIONS[i]);
            width = Math.max(width, font.width(rows[i]));
        }

        int height = LINE_HEIGHT * (rows.length + 1) + 3;
        int left = PADDING;
        int top = graphics.guiHeight() / 2 - height / 2;
        int right = left + width + PADDING * 2;
        int bottom = top + height + PADDING * 2;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.fill(left, top, right, bottom, 0x90000000);

        int textX = left + PADDING;
        int y = top + PADDING;
        graphics.drawString(font, title, textX, y, 0xFFFFFF);
        y += LINE_HEIGHT;
        for (Component row : rows) {
            graphics.drawString(font, row, textX, y, 0xFFFFFF);
            y += LINE_HEIGHT;
        }

        // Time left for the second press
        int barWidth = (int) ((right - left) * Math.max(0f, remainingFraction()));
        graphics.fill(left, bottom - 2, left + barWidth, bottom, 0xFFFFFF55);
        graphics.pose().popPose();
    }

    private Component row(Font font, ControllerButton second) {
        List<BindingAction> actions = BindingManager.actionsForCombo(pending, second);
        MutableComponent row = Component.literal(second.arrow() + "  ").withStyle(ChatFormatting.YELLOW);
        if (actions.isEmpty()) {
            return row.append(Component.literal("—").withStyle(ChatFormatting.DARK_GRAY));
        }
        MutableComponent names = Component.empty();
        for (int i = 0; i < actions.size(); i++) {
            if (i > 0) {
                names.append(", ");
            }
            names.append(actions.get(i).displayName());
        }
        Component shown = names;
        if (font.width(names) > MAX_NAME_WIDTH) {
            shown = Component.literal(font.plainSubstrByWidth(names.getString(), MAX_NAME_WIDTH - 6) + "...");
        }
        return row.append(shown.copy().withStyle(ChatFormatting.WHITE));
    }
}
