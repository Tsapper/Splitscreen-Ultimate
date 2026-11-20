# AGENTS.md

This file provides guidance to Codex when working in simple_controller_support and its subdirectories. Run the commands below from this directory.

## Project

Simple Controller Support: a client-side gamepad mod for **Minecraft 1.21.1**, built with **Architectury** for both Fabric and NeoForge. It uses Mojang mappings plus Parchment, and Java 21.

## Role in the splitscreen workspace

Read `../AGENTS.md` for the full three-component design, including when opening this repository on its own.

- This is the standalone controller/input component. `../Splitscreen Ultimate/` is the .NET app that launches separate Minecraft processes and embeds their windows in a single desktop window.
- The separate Minecraft splitscreen mod is `../splitscreen_ultimate/`. Its main instance coordinates up to four local players, reserves controllers, requests guest instances, and routes controller samples through the .NET app to generic external controllers in guests.
- Keep gameplay input, bindings, cursor, and keyboard behavior here. Keep session coordination and app IPC in the splitscreen mod, and process/window management in the .NET app.
- Preserve standalone use without the splitscreen mod. The public client-only integration boundary is `tsapper.simple_controller_support.api.ControllerSupport`; see `API.md` for usage and contracts.
- Standalone selection uses `ControllerManager.pick(preferredController)` with normal fallback. API ASSIGNED mode selects only an observed connection UUID; DISABLED selects nothing. Neither external mode uses saved preference/fallback. Only an explicit `useStandaloneSelection()` restores it.
- Splitscreen uses one Minecraft process per local player. The current single primary context per process fits that model; keep input state within `ControllerContext` rather than introducing shared player state.
- The launcher identifies the main role and guest controller-routing ID through `splitscreen.*` JVM properties. The splitscreen mod parses them; this mod only exposes generic selection/input APIs. See `../PROTOTYPE.md`; keep launcher/session parsing out of this standalone mod.
- Splitscreen Ultimate now depends on this mod through local Maven (1.1.0). Publish all artifacts with `./gradlew build publishToMavenLocal` before building the consumer. This mod never depends on Splitscreen Ultimate. Connection UUIDs and GLFW slots remain process-local; the splitscreen mod routes input samples instead of sharing physical-device identities.

## Commands

- **Build both loaders:** `./gradlew build`. Jars land in `fabric/build/libs` and `neoforge/build/libs`.
- **Publish integration artifacts:** `./gradlew publishToMavenLocal`; common and both platform artifacts use group `tsapper.simple_controller_support`, version 1.1.0.
- **Run a dev client:** `./gradlew :fabric:runClient` or `./gradlew :neoforge:runClient`. Run directories are `fabric/run` and `neoforge/run`.
- **API tests:** `./gradlew :common:test` (also runs with `build`). JUnit tests cover standalone/strict/disabled resolution, stale connection/slot reuse, and snapshot immutability. Hardware input and handover releases still require runtime checks.
- **Mixin checks:** compiling does not check mixin targets. A wrong target only fails at runtime because `defaultRequire` is 1. After changing a mixin, launch a client and search the log for `mixin` errors.

## Vanilla source reference

Decompiled 1.21.1 sources (Mojang names) are in a jar under `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/*/…-sources.jar`. Read vanilla classes from it with `unzip -p`, and check exact method signatures there before writing mixins.

NeoForge patches some of the hooked methods, for example `Minecraft.setScreen` and `GameRenderer.render`. So inject at HEAD or TAIL rather than at specific vanilla call sites, and test on both loaders.

## Architecture

All logic lives in `common` and is client-only. The platform modules only contain entrypoints, which call `SimpleControllerSupportModClient.init()`. On NeoForge that call goes through `enqueueWork` so it runs on the main thread.

### Per-frame input flow

The flow starts in `mixin/MinecraftMixin`, which injects at the HEAD of `runTick`. That runs before client ticks and rendering. It calls `ControllerInputHandler.onFrame()`, which does three things:
1. `ControllerManager` polls GLFW gamepads. Polling doesn't need window focus.
2. It picks a controller.
3. It calls `ControllerContext.frame()`.

Selection defaults to standalone and is runtime-only. `ControllerSupport` requires Minecraft's client thread. Assignment changes synchronously reset the primary context, releasing old held input. Explicit attachment and API policy transitions require all buttons/triggers/sticks to return to neutral before input resumes. Assigning the same policy is a no-op. A disconnected observed connection gets no automatic replacement, including reuse of its GLFW slot. New UUIDs are allocated when a new connection is observed; an unplug/replug that GLFW never exposes to polling cannot be distinguished.

The API publishes immutable per-frame snapshots after polling/input dispatch, including all mapped gamepads even in disabled mode. Subscriptions are client-thread-only, removable/idempotent, and receive the same frame; callback changes affect subsequent dispatch. Runtime listener exceptions are logged and isolated. Compare inventory UUIDs for hotplug and use button edges for join input. No launcher IPC, launch parsing, cross-process identity, or global controller ownership is implemented here.

Generic external gamepads (`registerExternalController`) accept validated immutable normalized input samples. The manager includes external sources but excludes them from standalone fallback. Queued button/trigger transitions dispatch individually so a slower client cannot lose a short press; redundant analog samples coalesce. Sources expire after 500 ms, release held input, and require neutral input on recovery. Queue overflow fails closed. `clear` and `close` release selection input. All API calls remain client-thread-only.

The generic physical-input gate defaults enabled. When disabled, keyboard/character/mouse mixins cancel real events, physical key polling reports false, and synthetic controller input/quick-move Shift remain available. Keep all session-specific routing, launch arguments, and player/world policy outside this component. Changes to these mixins require runtime validation on both loaders.

`ControllerContext` holds everything for one controller-using player: the controller, a `VirtualCursor`, held-button state, and the input mode. Only one instance exists per client process today. Planned splitscreen runs a separate Minecraft process for each player; keep per-player state in this class and not in statics.

`ControllerContext.frame()` handles buttons like this:
- **Capture screens:** if the open screen implements `ControllerCaptureScreen` (the rebinding UI), raw button presses go to it.
- **On-screen keyboard:** if the keyboard is open, button presses go to it instead of bindings, and cursor movement and scrolling pause (see below).
- **Button edges:** they are dispatched through `BindingManager.actionsFor(button, context)`.
- **No screen open:** GAMEPLAY actions run. Continuous camera and movement come from `GameplayInput`.
- **Screen open:** SCREEN actions (menu click, quick move, back) run, via `ScreenInput`, which drives the virtual cursor. If the button has no SCREEN action, any key-mapping action bound to it is forwarded as `screen.keyPressed` with that mapping's keyboard key.
- **Screen changes:** held state is dropped, and any button still held is suppressed until it is released. This stops one press from acting in two contexts.

### Input mode

`InputMode` is either KEYBOARD_MOUSE or CONTROLLER. Any real mouse or keyboard event switches it to KEYBOARD_MOUSE (see `MouseHandlerMixin` and `KeyboardHandlerMixin`). The mixin overrides below only apply in CONTROLLER mode:
- **Mouse position:** `MouseHandler.xpos()/ypos()` return the virtual cursor position. Hover, tooltips and the carried item all follow it.
- **Mouse grab:** `isMouseGrabbed()` returns true in-game, so held attack still works while the window is unfocused.
- **Clicks, scrolls, keys and typed characters:** `ScreenInput` sends these through `MouseHandler.onPress/onScroll` and `KeyboardHandler.keyPress/charTyped` (via `SyntheticMouse` and `SyntheticKeyboard`, implemented by the mixins), not by calling `screen.mouseClicked` or `screen.keyPressed` directly. Loader screen events fire inside those methods, and other mods depend on them. `onPress` reads the `xpos`/`ypos` fields, so they are swapped to the virtual cursor for the call. Mouse move and drag still go straight to the screen.
- **Auto-pause:** `GameRendererMixin` suppresses the pause-on-lost-focus.
- **Shift:** `InputConstantsMixin` fakes the Shift keys for quick-move. Container screens read GLFW directly.
- **Movement:** `KeyboardInputMixin` writes analog left-stick values into `Input`.

### On-screen keyboard

`gui/OnScreenKeyboard` is an overlay owned by `ControllerContext`. It is not a `Screen`, so the screen underneath keeps its focus.
- **Output:** it types with `ScreenInput.typeChar` (a synthetic `charTyped`) and `tapKey` (backspace, enter, arrows). Output reaches whatever has focus, including sign and book editors and mod widgets.
- **Detection:** `gui/TextInputDetector` finds the focused `EditBox` by walking `getFocused()`, with the creative search box as a fallback via `CreativeModeInventoryScreenAccessor`. `isTextScreen` matches chat, sign and book screens.
- **Opening:** if `keyboardAutoOpen` is on, it opens when a controller press moves focus to a text field, or when a press lands on the focused field. It does not open for fields a screen focuses by itself, like the world list search. It also opens when a text screen opens. The `open_keyboard` SCREEN action (default View) opens it manually.
- **Closing:** B or Done, a screen change, the target field no longer accepting input, or switching to keyboard/mouse.
- **Held buttons:** opening or closing suppresses all held buttons (`ControllerContext.openKeyboard/closeKeyboard`). The press that opened it doesn't type, and the B that closed it doesn't also run Back. Release handlers still run, so a held click gets its mouse-up.
- **Fixed controls:** these are not bindable.
  - D-pad or left stick moves the highlight, with repeat.
  - A types; X is backspace, with repeat; Y is space.
  - LB and RB move the text cursor; LT cycles shift; START is Enter.
- **Rendering:** it draws in place of the virtual cursor, at z=1500. It sits at the top of the screen if the target field is in the bottom half (chat), and at the bottom otherwise.

### Cursor snapping

The `snap_up/down/left/right` SCREEN actions (default D-pad) jump the virtual cursor to the nearest clickable thing in that direction. They are handled in `ScreenInput.snapPressed`, which repeats while the button is held.
- **Targets:** `snap/SnapTargets` collects widgets (walking `children()`), list rows, container slots, creative tabs and the recipe book. It also asks every registered `SnapTargetSource`. While the cursor is in a list, the rows just outside its visible area are targets too, and snapping to one scrolls it into view.
- **Choice:** `snap/CursorSnapper` starts from the target under the cursor, or the cursor point if there is none. It scores the targets ahead by distance along the direction plus a weighted sideways gap.
### Bindings

- **Action kinds:** a `BindingAction` is either a `KeyMappingAction`, which wraps any vanilla or mod `KeyMapping` with id = `KeyMapping.getName()`, or a controller-only action registered in `ControllerActions`. The built-in ones are in `BuiltinActions`; other mods may register their own.
- **Action list:** `ActionRegistry` combines both kinds and leaves out the four movement keys. It rebuilds lazily when the identity of `Options.keyMappings` changes, which is how late mod keybinds get picked up.
- **Key mapping state:** only `KeyMappingDriver` changes it, setting `setDown` and `clickCount` through `KeyMappingAccessor`. Press and release only on edges, never every frame, because `ToggleKeyMapping` toggles on every `setDown(true)`.
- **Default layout:** `DefaultBindings` is the single source of the default Bedrock/Xbox layout.
- **Saved bindings:** the config stores only overrides of the defaults. Values are a serialized `Binding` (a button name like `"DPAD_UP"`, or a combo like `"DPAD_DOWN+DPAD_UP"`) or `"none"`. Overrides for unknown action ids are kept.

### D-pad combos

A GAMEPLAY action can be bound to a `Binding.Combo`: two D-pad presses in a row, like Rocket League quick chat. Combos never apply in screens.
- **Prefix rule:** a direction that no combo starts with stays a normal button. If any combo starts with it (`BindingManager.isComboPrefix`), pressing it starts a pending combo and the single binding on that direction never fires. `conflictsWith` reports this shadowing.
- **Runtime:** `input/ComboInput`, owned by `ControllerContext`, holds the pending first press. `ControllerContext.press` completes the combo on the next D-pad press, and release handlers go under the second button. The pending press is cleared after `comboTimeout`, on a screen change, or when switching to keyboard/mouse.
- **HUD:** while a combo is pending, `ComboInput.render` draws the reachable actions on the left edge, in `ClientGuiEvent.RENDER_HUD`.
- **Capture:** in `ControllerControlsScreen`, a D-pad press for a GAMEPLAY action waits for a second D-pad press. If none comes before the timeout, it binds the single direction.

### Config and GUI

- **Config file:** `ControllerConfig` is a plain Gson object saved at `config/simple_controller_support.json`. To add a setting, add a field with a default, then add a widget in `ControllerSettingsScreen`.
- **Controls screen redirect:** `ControlsScreenRedirect` replaces an exact `ControlsScreen` instance with `ControllerControlsScreen`. It does this in `MinecraftMixin`, which modifies the `setScreen` argument.
- **Opening the vanilla screen:** use `ControlsScreenRedirect.openVanilla()`. It allowlists that instance so returning from its sub-screens doesn't redirect again.
- **Cursor rendering:** the cursor is drawn in Architectury's `ClientGuiEvent.RENDER_POST` at z=2000. Its shape is an ASCII pattern in `VirtualCursor`.

### Conventions

- Mixin handler methods and accessor methods use the `scs$` prefix.
- All mixins go in the `client` section of `simple_controller_support.mixins.json`.
- User-facing strings go in `assets/simple_controller_support/lang/en_us.json`. Controller-only action names use the translation key `simple_controller_support.<path>`, built from the action id `simple_controller_support:<path>`.
