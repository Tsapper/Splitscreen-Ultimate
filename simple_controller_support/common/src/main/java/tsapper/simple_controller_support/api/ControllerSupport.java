package tsapper.simple_controller_support.api;

import net.minecraft.client.Minecraft;
import tsapper.simple_controller_support.SimpleControllerSupportMod;
import tsapper.simple_controller_support.client.input.ControllerInputHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Client-only integration API, shared by Fabric and NeoForge. Call on Minecraft's client thread
 * after client setup (marshal IPC work with Minecraft.execute). No splitscreen mod is required:
 * the default is standalone selection. Changes are synchronous, idempotent, and not persisted.
 */
public final class ControllerSupport {
    private static final List<Consumer<ControllerFrame>> LISTENERS = new ArrayList<>();

    private ControllerSupport() {
    }

    /** Generic input integration; does not contain transport or session logic. */
    public static ExternalController registerExternalController(String name) {
        requireClientThread();
        var controller = ControllerInputHandler.manager().registerExternal(Objects.requireNonNull(name));
        return new ExternalController() {
            private boolean closed;
            public UUID connectionId() { return controller.info().connectionId(); }
            public void submit(ControllerSample sample) {
                requireClientThread();
                if (closed) throw new IllegalStateException("External controller is closed");
                controller.externalSamples().submit(Objects.requireNonNull(sample), System.nanoTime());
            }
            public void clear() {
                requireClientThread();
                controller.externalSamples().clear();
                if (ControllerInputHandler.snapshot().selectedController().filter(connectionId()::equals).isPresent()) {
                    ControllerInputHandler.resetExternalInput();
                }
            }
            public void close() {
                requireClientThread();
                if (!closed) { clear(); controller.closeExternal(); closed = true; }
            }
        };
    }

    /** Disables real keyboard/mouse events but keeps controller-generated synthetic events working. */
    public static void setPhysicalInputEnabled(boolean enabled) {
        requireClientThread();
        ControllerInputHandler.setPhysicalInputEnabled(enabled);
    }

    /** Only this observed connection may drive input. Missing/stale ids stay assigned but inactive. */
    public static void assign(UUID connectionId) {
        setSelection(ControllerSelection.assigned(connectionId));
    }

    /** Releases controller input without enabling automatic fallback. Discovery continues. */
    public static void disable() {
        setSelection(ControllerSelection.disabled());
    }

    /** Explicitly restores the user's saved preference and normal standalone fallback. */
    public static void useStandaloneSelection() {
        setSelection(ControllerSelection.standalone());
    }

    public static void setSelection(ControllerSelection selection) {
        requireClientThread();
        ControllerInputHandler.setSelection(Objects.requireNonNull(selection, "selection"));
    }

    /** Does not poll GLFW. Before the first input frame the inventory is empty. */
    public static ControllerFrame snapshot() {
        requireClientThread();
        return ControllerInputHandler.snapshot();
    }

    /**
     * Called once per input frame after polling and input dispatch, on the client thread.
     * Compare connection ids between frames for hotplug events; use pressed/released for join input.
     * No initial callback. Keep callbacks short. Changes made in a callback affect subsequent input;
     * all callbacks receive the same immutable frame. Exceptions are logged and isolated.
     */
    public static Subscription onFrame(Consumer<ControllerFrame> listener) {
        requireClientThread();
        Objects.requireNonNull(listener, "listener");
        // A wrapper gives each registration its own identity, even for duplicate consumers.
        Consumer<ControllerFrame> registration = listener::accept;
        LISTENERS.add(registration);
        return () -> {
            requireClientThread();
            LISTENERS.remove(registration);
        };
    }

    public interface Subscription extends AutoCloseable {
        /** Unsubscribes on the client thread; safe to call repeatedly. */
        @Override
        void close();
    }

    /** Internal input-loop hook. Integrations should subscribe with onFrame instead. */
    public static void publishFrame() {
        if (LISTENERS.isEmpty()) {
            return;
        }
        ControllerFrame frame = snapshot();
        for (Consumer<ControllerFrame> listener : List.copyOf(LISTENERS)) {
            try {
                listener.accept(frame);
            } catch (RuntimeException exception) {
                SimpleControllerSupportMod.LOGGER.error("Controller API frame listener failed", exception);
            }
        }
    }

    private static void requireClientThread() {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("ControllerSupport must be called on the Minecraft client thread");
        }
    }
}
