package tsapper.simple_controller_support.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import tsapper.simple_controller_support.client.config.ControllerConfig;
import tsapper.simple_controller_support.client.controller.Controller;
import tsapper.simple_controller_support.client.input.ControllerInputHandler;

import java.util.ArrayList;
import java.util.List;

/** Sensitivity, deadzones and other controller settings. Each widget writes straight into {@link ControllerConfig}. */
public class ControllerSettingsScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("simple_controller_support.settings.title");

    public ControllerSettingsScreen(Screen lastScreen) {
        super(lastScreen, Minecraft.getInstance().options, TITLE);
    }

    @Override
    protected void addOptions() {
        ControllerConfig config = ControllerConfig.get();
        List<AbstractWidget> widgets = new ArrayList<>();

        widgets.add(new SettingSlider(text("look_speed"), 60, 720, 10,
                () -> config.lookSpeed, value -> config.lookSpeed = (float) value,
                value -> Component.translatable("simple_controller_support.settings.degrees_per_second", (int) value)));
        widgets.add(new SettingSlider(text("look_curve"), 1.0, 3.0, 0.1,
                () -> config.lookCurve, value -> config.lookCurve = (float) value,
                value -> Component.literal(String.format("%.1f", value))));
        widgets.add(CycleButton.onOffBuilder(config.invertLookY)
                .create(text("invert_look_y"), (button, value) -> config.invertLookY = value));
        widgets.add(new SettingSlider(text("cursor_speed"), 100, 1000, 25,
                () -> config.cursorSpeed, value -> config.cursorSpeed = (float) value,
                value -> Component.literal(String.valueOf((int) value))));
        widgets.add(new SettingSlider(text("stick_deadzone"), 0.05, 0.5, 0.01,
                () -> config.stickDeadzone, value -> config.stickDeadzone = (float) value,
                SettingSlider::percent));
        widgets.add(new SettingSlider(text("trigger_threshold"), 0.05, 0.95, 0.05,
                () -> config.triggerThreshold, value -> config.triggerThreshold = (float) value,
                SettingSlider::percent));
        widgets.add(CycleButton.onOffBuilder(config.keyboardAutoOpen)
                .create(text("keyboard_auto_open"), (button, value) -> config.keyboardAutoOpen = value));
        widgets.add(new SettingSlider(text("combo_timeout"), 0.5, 5.0, 0.25,
                () -> config.comboTimeout, value -> config.comboTimeout = (float) value,
                value -> Component.translatable("simple_controller_support.settings.seconds", String.format("%.2f", value))));
        widgets.add(controllerButton(config));
        widgets.add(Button.builder(Component.translatable("simple_controller_support.settings.reset"), button -> {
            config.resetSettings();
            minecraft.setScreen(new ControllerSettingsScreen(lastScreen));
        }).build());

        list.addSmall(widgets);
    }

    /** Cycles through "Automatic" and each connected controller (by joystick slot). */
    private static CycleButton<Integer> controllerButton(ControllerConfig config) {
        List<Integer> choices = new ArrayList<>();
        choices.add(-1);
        for (Controller controller : ControllerInputHandler.manager().controllers()) {
            choices.add(controller.joystickId());
        }
        if (!choices.contains(config.preferredController)) {
            choices.add(config.preferredController);
        }
        return CycleButton.<Integer>builder(id -> controllerName(id))
                .withValues(choices)
                .withInitialValue(config.preferredController)
                .create(text("controller"), (button, value) -> config.preferredController = value);
    }

    private static Component controllerName(int joystickId) {
        if (joystickId < 0) {
            return Component.translatable("simple_controller_support.settings.controller.auto");
        }
        for (Controller controller : ControllerInputHandler.manager().controllers()) {
            if (controller.joystickId() == joystickId) {
                return Component.literal(controller.name());
            }
        }
        return Component.translatable("simple_controller_support.settings.controller.disconnected", joystickId + 1);
    }

    private static Component text(String key) {
        return Component.translatable("simple_controller_support.settings." + key);
    }

    @Override
    public void removed() {
        super.removed();
        ControllerConfig.save();
    }
}
