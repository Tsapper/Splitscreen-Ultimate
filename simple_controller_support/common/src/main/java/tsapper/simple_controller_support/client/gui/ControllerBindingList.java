package tsapper.simple_controller_support.client.gui;

import com.google.common.collect.ImmutableList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;
import tsapper.simple_controller_support.client.binding.ActionRegistry;
import tsapper.simple_controller_support.client.binding.Binding;
import tsapper.simple_controller_support.client.binding.BindingAction;
import tsapper.simple_controller_support.client.binding.BindingManager;
import tsapper.simple_controller_support.client.controller.ControllerButton;

import java.util.Collections;
import java.util.List;

/** Scrollable list of actions grouped by category, modelled on the vanilla key binds list. */
public class ControllerBindingList extends ContainerObjectSelectionList<ControllerBindingList.Entry> {
    private static final int ITEM_HEIGHT = 20;
    private static final int ROW_WIDTH = 360;
    private static final int GAP = 3;

    private final ControllerControlsScreen screen;

    public ControllerBindingList(ControllerControlsScreen screen, Minecraft minecraft) {
        super(minecraft, screen.width, screen.layout.getContentHeight(), screen.layout.getHeaderHeight(), ITEM_HEIGHT);
        this.screen = screen;

        // Sticks are fixed, but listing them makes the full layout visible in one place
        addEntry(new CategoryEntry(Component.translatable("simple_controller_support.category.sticks")));
        addEntry(new InfoEntry(Component.translatable("simple_controller_support.stick.move"),
                Component.translatable("simple_controller_support.button.left_stick_move")));
        addEntry(new InfoEntry(Component.translatable("simple_controller_support.stick.look"),
                Component.translatable("simple_controller_support.button.right_stick_move")));
        addEntry(new InfoEntry(Component.translatable("simple_controller_support.stick.cursor"),
                Component.translatable("simple_controller_support.button.left_stick_move")));
        addEntry(new InfoEntry(Component.translatable("simple_controller_support.stick.scroll"),
                Component.translatable("simple_controller_support.button.right_stick_move")));

        for (ActionRegistry.Category category : ActionRegistry.categories()) {
            addEntry(new CategoryEntry(Component.translatable(category.translationKey())));
            for (BindingAction action : category.actions()) {
                addEntry(new BindingEntry(action));
            }
        }
    }

    public void refreshEntries() {
        children().forEach(Entry::refresh);
    }

    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }

    public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        abstract void refresh();
    }

    /** Centered category title. */
    public class CategoryEntry extends Entry {
        private final Component name;

        CategoryEntry(Component name) {
            this.name = name;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            int textWidth = minecraft.font.width(name);
            graphics.drawString(minecraft.font, name, ControllerBindingList.this.width / 2 - textWidth / 2,
                    top + height - 9 - 1, 0xFFFFFF, false);
        }

        @Nullable
        @Override
        public ComponentPath nextFocusPath(FocusNavigationEvent event) {
            return null;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.emptyList();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(new NarratableEntry() {
                @Override
                public NarrationPriority narrationPriority() {
                    return NarrationPriority.HOVERED;
                }

                @Override
                public void updateNarration(NarrationElementOutput output) {
                    output.add(NarratedElementType.TITLE, name);
                }
            });
        }

        @Override
        void refresh() {
        }
    }

    /** A read-only row, used for the fixed stick functions. */
    public class InfoEntry extends Entry {
        private final Component name;
        private final Component value;

        InfoEntry(Component name, Component value) {
            this.name = name;
            this.value = value.copy().withStyle(ChatFormatting.GRAY);
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            int textY = top + height / 2 - 9 / 2;
            graphics.drawString(minecraft.font, name, left, textY, 0xFFFFFF);
            int right = getScrollbarPosition() - 10;
            graphics.drawString(minecraft.font, value, right - minecraft.font.width(value), textY, 0xFFFFFF);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.emptyList();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Collections.emptyList();
        }

        @Override
        void refresh() {
        }
    }

    /** [action name]  [button] [x] [Reset] */
    public class BindingEntry extends Entry {
        private final BindingAction action;
        private final Component name;
        private final Button bindButton;
        private final Button clearButton;
        private final Button resetButton;
        private boolean hasConflict;

        BindingEntry(BindingAction action) {
            this.action = action;
            this.name = action.displayName();
            this.bindButton = Button.builder(name, button -> screen.select(action))
                    .bounds(0, 0, 75, 20)
                    .createNarration(message -> Component.translatable("narrator.controls.bound", name, message.get()))
                    .build();
            this.clearButton = Button.builder(Component.literal("✕"), button -> {
                        BindingManager.set(action, null);
                        refreshEntries();
                    })
                    .bounds(0, 0, 20, 20)
                    .tooltip(Tooltip.create(Component.translatable("simple_controller_support.controls.clear")))
                    .build();
            this.resetButton = Button.builder(Component.translatable("controls.reset"), button -> {
                        BindingManager.reset(action);
                        refreshEntries();
                    })
                    .bounds(0, 0, 50, 20)
                    .createNarration(message -> Component.translatable("narrator.controls.reset", name))
                    .build();
            refresh();
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            int buttonY = top - 2;
            int resetX = getScrollbarPosition() - resetButton.getWidth() - 10;
            resetButton.setPosition(resetX, buttonY);
            int clearX = resetX - GAP - clearButton.getWidth();
            clearButton.setPosition(clearX, buttonY);
            int bindX = clearX - GAP - bindButton.getWidth();
            bindButton.setPosition(bindX, buttonY);

            resetButton.render(graphics, mouseX, mouseY, partialTick);
            clearButton.render(graphics, mouseX, mouseY, partialTick);
            bindButton.render(graphics, mouseX, mouseY, partialTick);

            int maxNameWidth = bindX - left - 8;
            Component shownName = name;
            if (minecraft.font.width(name) > maxNameWidth) {
                shownName = Component.literal(minecraft.font.plainSubstrByWidth(name.getString(), maxNameWidth - 6) + "...");
            }
            graphics.drawString(minecraft.font, shownName, left, top + height / 2 - 9 / 2, 0xFFFFFF);
            if (hasConflict) {
                int markerX = bindX - 6;
                graphics.fill(markerX, top - 1, markerX + 3, top + height, 0xFFFF0000);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(bindButton, clearButton, resetButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(bindButton, clearButton, resetButton);
        }

        @Override
        void refresh() {
            Binding bound = BindingManager.get(action);
            Component label = bound == null
                    ? Component.translatable("simple_controller_support.button.none")
                    : bound.displayName();

            List<BindingAction> conflicts = BindingManager.conflictsWith(action);
            hasConflict = !conflicts.isEmpty();
            if (hasConflict) {
                MutableComponent names = Component.empty();
                for (int i = 0; i < conflicts.size(); i++) {
                    if (i > 0) {
                        names.append(", ");
                    }
                    names.append(conflicts.get(i).displayName());
                }
                label = Component.literal("[ ").append(label.copy().withStyle(ChatFormatting.WHITE))
                        .append(" ]").withStyle(ChatFormatting.RED);
                bindButton.setTooltip(Tooltip.create(Component.translatable("simple_controller_support.controls.conflict", names)));
            } else {
                bindButton.setTooltip(null);
            }

            if (screen.selectedAction() == action) {
                ControllerButton comboFirst = screen.pendingComboFirst();
                Component prompt = comboFirst == null
                        ? Component.translatable("simple_controller_support.controls.press_button")
                        : Component.translatable("simple_controller_support.controls.press_second", comboFirst.arrow());
                label = Component.literal("> ")
                        .append(prompt.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE))
                        .append(" <").withStyle(ChatFormatting.YELLOW);
            }
            bindButton.setMessage(label);
            clearButton.active = bound != null;
            resetButton.active = !BindingManager.isDefault(action);
        }
    }
}
