package tsapper.simple_controller_support.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import tsapper.simple_controller_support.client.binding.ActionRegistry;
import tsapper.simple_controller_support.client.binding.Binding;
import tsapper.simple_controller_support.client.binding.BindingAction;
import tsapper.simple_controller_support.client.binding.BindingManager;
import tsapper.simple_controller_support.client.config.ControllerConfig;
import tsapper.simple_controller_support.client.controller.Controller;
import tsapper.simple_controller_support.client.controller.ControllerButton;
import tsapper.simple_controller_support.client.input.ControllerInputHandler;

/** Opened by Options -> Controls. Lists every bindable action and lets the player assign controller buttons. */
public class ControllerControlsScreen extends OptionsSubScreen implements ControllerCaptureScreen {
    private static final Component TITLE = Component.translatable("simple_controller_support.controls.title");
    /** Waiting for a button press is cancelled after this long. */
    private static final long CAPTURE_TIMEOUT_MS = 5000;
    private static final int HEADER_HEIGHT = 48;
    private static final int FOOTER_HEIGHT = 56;

    @Nullable
    private BindingAction selectedAction;
    private long selectionTime;
    /** First D-pad press while capturing a gameplay action; a second D-pad press makes it a combo. */
    @Nullable
    private ControllerButton pendingFirst;
    private ControllerBindingList bindingList;
    private StringWidget statusWidget;
    private Button resetAllButton;

    public ControllerControlsScreen(Screen lastScreen) {
        super(lastScreen, Minecraft.getInstance().options, TITLE);
        layout.setHeaderHeight(HEADER_HEIGHT);
        layout.setFooterHeight(FOOTER_HEIGHT);
    }

    @Override
    protected void addTitle() {
        LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(4));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(TITLE, font));
        statusWidget = header.addChild(new StringWidget(300, 9, statusText(), font));
    }

    @Override
    protected void addContents() {
        bindingList = layout.addToContents(new ControllerBindingList(this, minecraft));
    }

    @Override
    protected void addOptions() {
    }

    @Override
    protected void addFooter() {
        LinearLayout footer = layout.addToFooter(LinearLayout.vertical().spacing(4));
        LinearLayout topRow = footer.addChild(LinearLayout.horizontal().spacing(8));
        topRow.addChild(Button.builder(Component.translatable("simple_controller_support.controls.settings"),
                button -> minecraft.setScreen(new ControllerSettingsScreen(this))).build());
        topRow.addChild(Button.builder(Component.translatable("simple_controller_support.controls.keyboard"),
                button -> ControlsScreenRedirect.openVanilla(this)).build());

        LinearLayout bottomRow = footer.addChild(LinearLayout.horizontal().spacing(8));
        resetAllButton = bottomRow.addChild(Button.builder(Component.translatable("controls.resetAll"), button -> {
            BindingManager.resetAll();
            bindingList.refreshEntries();
        }).build());
        bottomRow.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).build());
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        bindingList.updateSize(width, layout);
    }

    @Override
    public void tick() {
        super.tick();
        statusWidget.setMessage(statusText());
        resetAllButton.active = ActionRegistry.all().stream().anyMatch(action -> !BindingManager.isDefault(action));
        if (selectedAction != null && pendingFirst != null
                && Util.getMillis() - selectionTime > ControllerConfig.get().comboTimeout * 1000) {
            // No second press: bind the direction on its own
            bind(Binding.of(pendingFirst));
        } else if (selectedAction != null && Util.getMillis() - selectionTime > CAPTURE_TIMEOUT_MS) {
            cancelSelection();
        }
    }

    private static Component statusText() {
        Controller controller = ControllerInputHandler.primary().controller();
        return controller == null
                ? Component.translatable("simple_controller_support.controls.no_controller").withStyle(ChatFormatting.GRAY)
                : Component.translatable("simple_controller_support.controls.controller", controller.name()).withStyle(ChatFormatting.GREEN);
    }

    // --- Selecting a binding ---

    void select(BindingAction action) {
        selectedAction = action;
        selectionTime = Util.getMillis();
        pendingFirst = null;
        bindingList.refreshEntries();
    }

    @Nullable
    BindingAction selectedAction() {
        return selectedAction;
    }

    @Nullable
    ControllerButton pendingComboFirst() {
        return pendingFirst;
    }

    private void bind(Binding binding) {
        if (selectedAction != null) {
            BindingManager.set(selectedAction, binding);
        }
        cancelSelection();
    }

    private void cancelSelection() {
        selectedAction = null;
        pendingFirst = null;
        bindingList.refreshEntries();
    }

    @Override
    public boolean isCapturingControllerInput() {
        return selectedAction != null;
    }

    @Override
    public void onControllerButton(ControllerButton button) {
        if (selectedAction == null) {
            return;
        }
        if (!button.isDpad()) {
            bind(Binding.of(button));
        } else if (pendingFirst != null) {
            bind(new Binding.Combo(pendingFirst, button));
        } else if (selectedAction.context() == BindingAction.ActionContext.GAMEPLAY) {
            // Might be the start of a combo; wait for a second D-pad press
            pendingFirst = button;
            selectionTime = Util.getMillis();
            bindingList.refreshEntries();
        } else {
            bind(Binding.of(button));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selectedAction != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelSelection();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selectedAction != null) {
            cancelSelection();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
