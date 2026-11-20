# Controller integration API

Simple Controller Support works by itself. With no API calls, it uses the saved controller
preference and falls back to the first connected mapped gamepad, just as before. It does not
require Splitscreen Ultimate or a launcher. External selection is runtime-only and does not
overwrite the user's config, bindings, or preference.

The client-only entry point is `tsapper.simple_controller_support.api.ControllerSupport`,
available on Fabric and NeoForge. Call it on Minecraft's client thread after client setup.
For work received on an IPC thread, use `Minecraft.getInstance().execute(...)`.

```java
import tsapper.simple_controller_support.api.ControllerSupport;

// Wait for the coordinator without accepting any controller input.
ControllerSupport.disable();

// Choose from this process's observed controllers, after polling has begun.
var frame = ControllerSupport.snapshot();
// The coordinator chooses the entry; list ordering is not physical identity.
var connectionId = frame.controllers().getFirst().info().connectionId();
ControllerSupport.assign(connectionId);

// Release ownership while keeping controller input disabled.
ControllerSupport.disable();

// Explicitly return to normal independent controller-mod behavior.
ControllerSupport.useStandaloneSelection();
```

Check for an empty controller list before choosing an entry. The initial inventory is empty
until the first input poll. Discovery continues in every selection mode; only mapped GLFW
gamepads are included. All descriptors expose the standardized axes/buttons in
`ControllerAxis` and `ControllerButton`; this backend does not expose battery, rumble,
or device-specific capabilities.

| Selection | Resolution |
| --- | --- |
| `STANDALONE` | Saved preference, then first connected gamepad |
| `ASSIGNED` | Exactly the requested observed connection; no fallback |
| `DISABLED` | No gamepad drives this instance; keyboard/mouse still work |

`snapshot().selection()` reports the requested policy, and `selectedController()` reports
its resolved connection. ASSIGNED with an empty selected connection means the assignment
is absent/disconnected. `controllerInputActive()` separately reports whether controller
input mode is active; a selected device may still be waiting for activity or neutral input.
Selection updates are synchronous, while the new device starts driving input on a later
input frame. Setting the same policy is a no-op.

Changing policy releases the old controller's held actions, synthetic screen input, movement,
combos, and keyboard overlay. A controlled handover waits for all buttons, triggers, and sticks
to return to neutral before dispatch resumes. Explicit assignments also wait for neutral
on their first attachment. Ordinary standalone startup retains its existing behavior.

## Observing connections and input

```java
var subscription = ControllerSupport.onFrame(frame -> {
    // Runs after polling/input dispatch, once per input frame on the client thread.
    for (var controller : frame.controllers()) {
        // info(), axes(), down(), pressed(), and released() are immutable snapshots.
        // The coordinator may use pressed() to detect a join request.
    }
});

// Close on the client thread when the integration is finished.
subscription.close();
```

Compare connection UUIDs between frames to detect connection/disconnection. Removed devices
are absent from the next inventory; no final released-button snapshot is promised for them.
Snapshots include all connected mapped gamepads even when only one or none drives input.
Edges reflect the latest poll and are not consumed by reading them. Axis values are before
deadzones; sticks are -1..1 and triggers are normalized to 0..1. Trigger button edges use the
configured trigger threshold.

Subscriptions have no immediate initial callback. All listeners receive the same captured
frame; changes inside a listener affect later input dispatch. Listener additions/removals
take effect on subsequent notifications (a removed listener may still receive the current
notification). Closing is idempotent. Runtime callback exceptions are logged and do not
prevent other callbacks. Keep callbacks short; marshal long-running work elsewhere.

## Identity and dependency boundaries

Each `ControllerInfo.connectionId()` is a UUID for one connection observed by this Minecraft
process. After an observed disconnect/reconnect a new UUID is allocated, so a stale
assignment cannot select a different device that reuses the same GLFW slot. Reconnection
requires an explicit new assignment. An unplug/replug not observed by GLFW/polling cannot
be distinguished. The UUID must not be persisted as a hardware identity or passed from the
launcher expecting another process to recognize it. `glfwSlot()` is diagnostic and
process-local; `mappingGuid()` identifies a model/mapping, not a physical unit.

Cross-process matching, launch arguments, IPC, and session-wide controller ownership remain
outside this API. The current splitscreen prototype avoids physical-device matching by forwarding
samples from its main process into generic external controllers in guest processes.

A consuming mod should compile against this mod's matching Minecraft 1.21.1 artifacts and
declare Simple Controller Support as a required client dependency in both loader metadata
files. Use a compatible version range for the API. Keep this mod separately installed at
runtime; do not shade another copy into the consuming mod. Splitscreen Ultimate now consumes
the 1.1.0 common/platform artifacts using group-filtered `mavenLocal()` resolution.

## External gamepads and physical input (1.1.0)

`ControllerSupport.registerExternalController(name)` returns an `ExternalController` with a
local `connectionId()`, `submit(ControllerSample)`, `clear()`, and idempotent `close()`.
Registering does not select the source: assign its connection explicitly. Standalone fallback
considers hardware gamepads only. Samples contain immutable axis maps and held-button sets;
trigger button edges are derived from trigger axes and the configured threshold.

All operations require the client thread. Input samples time out after 500 ms. Button/trigger
transitions are queued and drained through normal input dispatch, even when the receiving frame
rate is lower. Analog samples with identical held buttons/trigger values coalesce. A bounded
128-transition overflow clears the stream, failing closed rather than replaying an unbounded
backlog. Timeout, clear, disconnect, and selection changes release held actions. Recovery requires
neutral input. A closed source must not be submitted again.

`ControllerSupport.setPhysicalInputEnabled(false)` gates real keyboard, typed-character, mouse,
and physical modifier polling while preserving synthetic controller events. The default is true;
call true explicitly to restore physical input. This is generic input policy, not a session or IPC
feature. No launcher parsing, controller routing, or joins are implemented in this mod.

## Validation

`./gradlew build` compiles/packages both loaders and runs the common JUnit API tests.
Hardware validation should check standalone input with only this mod installed, held attack
and GUI click releases on disable/reassignment, neutral handover, hotplug/slot reuse, and
frame subscriptions on both loaders. No mixins are changed by this API.
