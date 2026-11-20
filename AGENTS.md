# Splitscreen Minecraft

## Goal and component boundaries

Bring local splitscreen multiplayer to Minecraft Java Edition by running separate Minecraft instances and embedding their windows in one desktop window. This is a multi-process design: each local player has their own Minecraft instance. Do not assume multiple local players will be rendered inside one Minecraft client.

The project has three components:

1. **Simple Controller Support** (`simple_controller_support/`): the basic client-side controller support mod. It handles gamepad discovery/polling, gameplay input, bindings, virtual cursor, menus, and on-screen keyboard. It works standalone and exposes `tsapper.simple_controller_support.api.ControllerSupport` for explicit per-instance controller selection and immutable input snapshots.
2. **Splitscreen Ultimate** (`Splitscreen Ultimate/`): the .NET desktop launcher and window host. It launches Minecraft processes and nests their windows inside one app window. It owns process launching, native window embedding, resizing, and layout. It must communicate bidirectionally with the splitscreen mod.
3. **Splitscreen Ultimate mod** (`splitscreen_ultimate/`): an Architectury/Fabric/NeoForge client mod depending on Simple Controller Support through local Maven. In launcher sessions the main instance coordinates up to four local players, reserves controllers, requests guest processes, forwards their input, hosts singleplayer worlds privately, and coordinates LAN joins and visibility with the app.

## Required communication

The intended integration is:

```text
.NET launcher/window host <--> Splitscreen mod <--> Simple Controller Support
     separate process          inside Minecraft     inside Minecraft
```

- App <-> splitscreen mod: bidirectional inter-process communication for session coordination and instance lifecycle, including requests to launch a new instance when a player is added and the corresponding status/results.
- Splitscreen mod <-> controller mod: an in-process mod integration for controller assignment and the controller information/state needed to coordinate it.
- The splitscreen mod decides controller ownership; the controller mod applies the assignment to input for its instance; the app performs requested process/window operations.
- Keep these responsibilities separate. Preserve standalone controller behavior when the splitscreen mod is absent.
- In coordinated splitscreen, one physical controller should control only its assigned instance. Automatic fallback to another player's controller must not defeat an explicit assignment.
- Keep player, instance, and controller identities distinct. Account for startup, reassignment, disconnection, and release of held input when designing the integration.

## Prototype integration contract

- The launcher starts exactly one main instance. JVM properties under `splitscreen.` identify session, instance, slot, main/guest role, IPC port/token, and initial controller routing ID. The main role is explicit, never inferred from launch order. See `PROTOTYPE.md` for exact properties and protocol.
- The main mod instance owns player/controller reservations. The first controller A claims player one; other A presses launch/reclaim guests, up to four. Guests receive controller samples through main -> launcher -> guest routing; session routing IDs are passed at launch, while hardware UUIDs/GLFW slots remain process-local.
- Simple Controller Support 1.1.0 supplies generic external gamepads and physical-input gating, with unchanged standalone defaults. It owns no session/IPC code. The splitscreen mod consumes its common/platform artifacts using group-filtered `mavenLocal()` and required loader metadata.
- Version 1 IPC is bounded UTF-8 newline-delimited JSON over authenticated IPv4 loopback TCP. The launcher validates per-instance tokens and reported HWND process ownership. Launch requests have correlation IDs; world operations/input have generation guards.
- Guests start hidden and disabled, join the main instance's private loopback TCP listener when a world is available, and become visible/input-active only after joining. Leaving a world hides/disables/disconnects guests and reuses their processes for the next world. Optional Open to LAN adds a network-facing listener without replacing the private listener.

These contracts are implemented as a prototype, not a persistent hardware-identity mapping. See `simple_controller_support/API.md` and `PROTOTYPE.md` for contracts, limitations, build order, and runtime testing. Never use GLFW slots or connection UUIDs as shared hardware identifiers.

## Current implementation and gaps

- Controller mod: Architectury, Fabric and NeoForge, Minecraft `1.21.1`, Java 21. One primary `ControllerContext` per process. Standalone preference/fallback is the default; the public API also supports strict connection assignment and disabled controller input. Assignment changes release held input and require neutral input before handover. Missing assignments never fall back or automatically bind a replacement connection. Session-wide ownership is not enforced by this local API.
- Desktop app: Avalonia on .NET `10.0`, Win32 embedding, CmlLib/Fabric launching, authenticated session IPC, one initial instance, dynamic four-player layouts, isolated player directories, and graceful shutdown. Native runtime remains Windows-only.
- All three components target Minecraft `1.21.1`.
- Splitscreen Ultimate mod: Java 21, Architectury, Fabric and NeoForge. Implements main-instance coordination, join reservations, guest input, private-world TCP hosting, LAN extension, and a main-menu status/retry panel. Without launcher session properties it performs no automation. The launcher currently installs Fabric; NeoForge also requires loader-specific runtime checks.

## Working in this workspace

- Read this file alongside the relevant component's `AGENTS.md`, including when a component directory is opened on its own.
- `simple_controller_support/AGENTS.md` contains controller architecture, conventions, build commands, and runtime mixin validation guidance.
- `Splitscreen Ultimate/AGENTS.md` contains desktop architecture, commands, and current limitations.
- `splitscreen_ultimate/AGENTS.md` contains splitscreen mod architecture, build commands, and integration boundaries. Build both loaders from that directory with `./gradlew build`.
- Run build commands from their component directory. The workspace root is one Git repository containing three separate build projects; component directories must not contain nested Git repositories.
- Keep player-specific input state scoped to its player/client context. One context per Minecraft process is consistent with this design; multiple players do not require multiple contexts inside a single client.
- When implementing integration, update the relevant component instructions and this map with the agreed contracts.
- Update these instructions when architecture or integration contracts change. Clearly distinguish working behavior from plans.
