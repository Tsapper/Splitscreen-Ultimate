package tsapper.simple_controller_support.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.architectury.platform.Platform;
import tsapper.simple_controller_support.SimpleControllerSupportMod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All persisted settings. Serialized as-is with Gson to {@code config/simple_controller_support.json},
 * so adding a setting is just adding a field with a default value.
 */
public final class ControllerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = Platform.getConfigFolder().resolve(SimpleControllerSupportMod.MOD_ID + ".json");

    private static ControllerConfig instance = new ControllerConfig();

    // --- Settings (edit defaults here; ranges live in ControllerSettingsScreen) ---

    /** Camera turn speed at full right-stick deflection, in degrees per second. */
    public float lookSpeed = 300f;
    public boolean invertLookY = false;
    /** Exponent applied to stick deflection for the camera; higher = more precision near the centre. */
    public float lookCurve = 2.0f;
    /** Stick values below this magnitude are ignored. */
    public float stickDeadzone = 0.15f;
    /** How far a trigger must be pulled before it counts as pressed. */
    public float triggerThreshold = 0.3f;
    /** Virtual cursor speed at full stick deflection, in GUI pixels per second. */
    public float cursorSpeed = 350f;
    /** GLFW joystick slot to prefer, or -1 to use the first connected controller. */
    public int preferredController = -1;
    /** Open the on-screen keyboard when the controller focuses a text field or a chat/sign/book screen opens. */
    public boolean keyboardAutoOpen = true;
    /** Seconds to wait for the second press of a D-pad combo. */
    public float comboTimeout = 2.0f;

    /**
     * Binding overrides: action id -> serialized {@link tsapper.simple_controller_support.client.binding.Binding}
     * (a button name like "DPAD_UP", or a combo like "DPAD_DOWN+DPAD_UP"), or "none" for unbound. Actions missing from this map use {@link tsapper.simple_controller_support.client.binding.DefaultBindings}.
     * Entries for actions that no longer exist (e.g. a removed mod) are kept untouched.
     */
    public Map<String, String> bindings = new LinkedHashMap<>();

    public static ControllerConfig get() {
        return instance;
    }

    public static void load() {
        if (!Files.exists(PATH)) {
            instance = new ControllerConfig();
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            ControllerConfig loaded = GSON.fromJson(reader, ControllerConfig.class);
            instance = loaded != null ? loaded : new ControllerConfig();
            if (instance.bindings == null) {
                instance.bindings = new LinkedHashMap<>();
            }
        } catch (IOException | JsonParseException e) {
            SimpleControllerSupportMod.LOGGER.error("Failed to read {}, using defaults", PATH, e);
            instance = new ControllerConfig();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            SimpleControllerSupportMod.LOGGER.error("Failed to write {}", PATH, e);
        }
    }

    /** Resets every setting except bindings. */
    public void resetSettings() {
        ControllerConfig defaults = new ControllerConfig();
        lookSpeed = defaults.lookSpeed;
        invertLookY = defaults.invertLookY;
        lookCurve = defaults.lookCurve;
        stickDeadzone = defaults.stickDeadzone;
        triggerThreshold = defaults.triggerThreshold;
        cursorSpeed = defaults.cursorSpeed;
        preferredController = defaults.preferredController;
        keyboardAutoOpen = defaults.keyboardAutoOpen;
        comboTimeout = defaults.comboTimeout;
    }
}
