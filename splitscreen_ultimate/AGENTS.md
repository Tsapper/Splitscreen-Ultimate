# Splitscreen Ultimate mod

Read `../AGENTS.md` alongside this file, including when opening this component on its own. Run commands from this directory.

## Project

Client-side Minecraft **1.21.1** mod built with **Architectury**, **Fabric**, and **NeoForge**, targeting **Java 21**. Uses Mojang mappings plus Parchment, matching `../simple_controller_support/`.

## Current implementation

This is a four-local-player prototype. In launcher sessions the main instance owns controller reservations, launches/reclaims players on A, forwards guest input through launcher IPC, opens host worlds on loopback, and automatically coordinates guest joins. Guests remain hidden/disabled outside a host world. Main-menu UI shows launch/readiness/world counts, player states, errors, and Retry guests. Without session launch properties the mod remains passive. Read `../PROTOTYPE.md` for setup/contracts and testing limitations.

## Component boundaries

- This mod will coordinate sessions and controller-to-instance assignments and communicate bidirectionally with the desktop app.
- `../simple_controller_support/` owns gamepad polling, input, bindings, virtual cursor, menus, and on-screen keyboard. Keep it usable standalone.
- `../Splitscreen Ultimate/` is the separate .NET launcher/window host. It owns process launching, window embedding, layout, and resizing.
- Splitscreen uses one Minecraft process per local player. Keep player, instance, and controller identities distinct; keep client-specific state within its owning context.
- This mod depends on Simple Controller Support 1.1.0 through group-filtered `mavenLocal()`. Publish the common/Fabric/NeoForge artifacts from that component before building this one. Common integration uses its common artifact; platform builds use matching runtime artifacts and required loader metadata. Do not shade controller-mod classes.
- The main instance owns reservations and controller routing; the launcher owns process/window operations and IPC routing. Local physical connection UUIDs never leave the main instance. Session routing IDs identify guest input streams, which feed generic external controllers through the controller API.
- Assignments never fall back. Disconnection releases input, retains the player slot/process, and lets an unassigned controller A reclaim the lowest disconnected slot. A first controller A claims player one, initially on keyboard/mouse. Four includes the main player and pending launches.

## Startup and session contracts

- `LaunchConfig` reads `splitscreen.session`, `instance`, `slot`, `role`, `port`, `token`, and `controller` JVM properties. Role is explicit; controller is a routing ID, not a GLFW slot/hardware UUID. See `../PROTOTYPE.md` for version 1 JSON protocol.
- Fabric client entrypoints run before Minecraft finishes initializing its thread/window. `MinecraftSessionMixin` bootstraps session state at the first `runTick` HEAD with priority 1100, before Simple Controller Support's first input poll. Do not move controller API calls back into client entrypoints.
- IPC uses bounded background socket IO over authenticated IPv4 loopback; messages marshal onto the client thread. World generations reject stale connects/readiness/input; request IDs correlate launches. Guest input streams expire after 500 ms and neutral is required on recovery.
- The private host uses `publishServer` with a loopback-only bind and suppressed advertisement. The pause menu still offers Open to LAN, adding a separate network-facing listener and advertisement. Expected offline guest names bypass authentication only on the private listener's port; do not globally disable authentication.
- Guest native windows start hidden before `glfwCreateWindow`; the Win32 HWND is reported in the registration message. Guest keyboard/mouse is gated through the controller API, even when visible. Leaving a world disconnects/hides/disables guests but retains their processes for the next world.
- NeoForge replaces the vanilla window-creation call with its early-window provider. The pre-create visibility hook is therefore optional (`require=0`); a required constructor TAIL hook hides reused guest windows. The current launcher installs Fabric, so NeoForge's earlier bootstrap splash is not part of launcher guest startup.
- Common compilation pins Mixin annotations to `0.15.2+mixin.0.8.7` on its compile classpath. New Fabric Mixin serializes `Redirect.at` as an array; NeoForge 1.21.1's bundled MixinExtras 0.5.3 requires the older single-annotation form. Do not remove this compatibility pin without rerunning both loaders.
- `GlfwActiveWindowGuard` guards only the current GLFW DLL's GetActiveWindow import, rejecting HWNDs from another process. Win32 parenting joins input queues; without this guard GLFW can dereference another JVM's window property and crash. It is installed only in launcher sessions, not in passive/standalone use.

## Commands

- Build both loaders: `./gradlew build` (Windows: `.\gradlew.bat build`). Output jars: `fabric/build/libs` and `neoforge/build/libs`.
- Run Fabric: `./gradlew :fabric:runClient`.
- Run NeoForge: `./gradlew :neoforge:runClient`.
- Run directories: `fabric/run` and `neoforge/run`.
- Tests: `./gradlew :common:test` (included in build) covers main reservation/capacity/reconnect/launch-correlation logic. A build alone does not prove runtime mixin compatibility. Launcher smoke fixture and manual hardware checks are described in `../PROTOTYPE.md`.

## Architecture and conventions

- Shared logic belongs in `common/src/main/java/tsapper/splitscreen_ultimate`.
- `SplitscreenUltimateMod` holds `MOD_ID`, `LOGGER`, and loader-neutral setup.
- Loader setup belongs in `client/SplitscreenUltimateModClient`; runtime sessions belong in `client/session`. NeoForge uses `enqueueWork`, but actual session/API initialization is consistently deferred to the first frame on both loaders.
- Platform modules contain thin entrypoints only. Keep client classes out of loader-neutral initialisation.
- Future mixins belong in the `client` section of `splitscreen_ultimate.mixins.json`; use the `su$` handler/accessor prefix. Check exact Minecraft signatures before adding mixins and launch both loaders after changing them (`defaultRequire` is 1).
- Future user-facing strings belong in `assets/splitscreen_ultimate/lang/en_us.json`.
- Update this file and the workspace component map when integration contracts or implemented behavior change.
