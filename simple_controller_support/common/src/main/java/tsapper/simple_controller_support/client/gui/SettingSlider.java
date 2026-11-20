package tsapper.simple_controller_support.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/** A slider for a numeric setting, mapping the 0..1 slider position onto [min, max] in fixed steps. */
public class SettingSlider extends AbstractSliderButton {
    private final Component label;
    private final double min;
    private final double max;
    private final double step;
    private final DoubleConsumer setter;
    private final DoubleFunction<Component> formatter;

    public SettingSlider(Component label, double min, double max, double step,
                         DoubleSupplier getter, DoubleConsumer setter, DoubleFunction<Component> formatter) {
        super(0, 0, 150, 20, Component.empty(), (Mth.clamp(getter.getAsDouble(), min, max) - min) / (max - min));
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.setter = setter;
        this.formatter = formatter;
        updateMessage();
    }

    /** Formatter for 0..1 values shown as a percentage. */
    public static Component percent(double value) {
        return Component.literal(Math.round(value * 100) + "%");
    }

    private double actualValue() {
        double raw = min + value * (max - min);
        return Mth.clamp(Math.round(raw / step) * step, min, max);
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.translatable("options.generic_value", label, formatter.apply(actualValue())));
    }

    @Override
    protected void applyValue() {
        setter.accept(actualValue());
    }
}
