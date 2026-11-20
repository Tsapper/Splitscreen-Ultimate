package tsapper.simple_controller_support.client.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import tsapper.simple_controller_support.client.config.ControllerConfig;
import tsapper.simple_controller_support.client.controller.ControllerAxis;
import tsapper.simple_controller_support.client.controller.ControllerState;

/** Analog in-game input: camera from the right stick and movement from the left stick. */
public final class GameplayInput {
    /** {@code Entity.turn} multiplies its input by this to get degrees. */
    private static final double DEGREES_PER_TURN_UNIT = 0.15;
    /** Vanilla slows mouse look by this factor while using a spyglass. */
    private static final double SCOPING_MULTIPLIER = 0.125;
    /** sin(22.5°): the share of the stick vector a component needs before it counts as pressed. */
    private static final double DIAGONAL_THRESHOLD = Math.sin(Math.toRadians(22.5));

    private float strafe;
    private float forward;

    /** Called every frame while no screen is open. */
    void frame(ControllerState state, float deltaSeconds) {
        ControllerConfig config = ControllerConfig.get();

        float[] move = state.stick(ControllerAxis.LEFT_X, ControllerAxis.LEFT_Y, config.stickDeadzone);
        strafe = -digital(move[0], move[1]);
        forward = -digital(move[1], move[0]);

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        float[] look = state.stick(ControllerAxis.RIGHT_X, ControllerAxis.RIGHT_Y, config.stickDeadzone);
        double magnitude = Math.sqrt(look[0] * look[0] + look[1] * look[1]);
        if (magnitude == 0) {
            return;
        }
        double curved = Math.pow(magnitude, config.lookCurve) / magnitude;
        double degrees = config.lookSpeed * deltaSeconds * curved;
        if (Minecraft.getInstance().options.getCameraType().isFirstPerson() && player.isScoping()) {
            degrees *= SCOPING_MULTIPLIER;
        }
        double yaw = look[0] * degrees / DEGREES_PER_TURN_UNIT;
        double pitch = look[1] * degrees / DEGREES_PER_TURN_UNIT * (config.invertLookY ? -1 : 1);
        player.turn(yaw, pitch);
    }

    /**
     * Snaps one stick component to -1, 0 or 1 so movement matches WASD: eight directions at full speed.
     * A component counts once it is at least sin(22.5°) of the stick vector, which splits the circle
     * into eight equal 45° sectors.
     */
    private static float digital(float value, float other) {
        double magnitude = Math.sqrt(value * value + other * other);
        if (magnitude == 0 || Math.abs(value) < magnitude * DIAGONAL_THRESHOLD) {
            return 0;
        }
        return Math.signum(value);
    }

    void reset() {
        strafe = 0;
        forward = 0;
    }

    /**
     * Overrides the keyboard movement in {@code KeyboardInput.tick} while the left stick is pushed.
     * Values are always -1, 0 or 1, the same as {@code KeyboardInput.calculateImpulse}, so the server
     * sees movement it can't tell apart from a keyboard.
     */
    public void applyMovement(Input input, boolean isSneaking, float sneakingSpeedMultiplier) {
        if (strafe == 0 && forward == 0) {
            return;
        }
        input.up = forward > 0;
        input.down = forward < 0;
        input.left = strafe > 0;
        input.right = strafe < 0;
        input.forwardImpulse = forward;
        input.leftImpulse = strafe;
        if (isSneaking) {
            input.forwardImpulse *= sneakingSpeedMultiplier;
            input.leftImpulse *= sneakingSpeedMultiplier;
        }
    }
}
