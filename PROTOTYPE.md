# Four-player splitscreen prototype

This is a Windows launcher with one Minecraft 1.21.1 process per local player. The launcher
currently installs Fabric. Both Minecraft mods also build for NeoForge.

## Build and run

Use a JDK supported by Gradle (the mods compile to Java 21) and the .NET 10 SDK.
Run each command in its named component directory:

```powershell
# simple_controller_support/
.\gradlew.bat build publishToMavenLocal

# splitscreen_ultimate/
.\gradlew.bat build

# Splitscreen Ultimate/
dotnet build "Splitscreen Ultimate.csproj"
dotnet run --project "Splitscreen Ultimate.csproj"
```

Local Maven coordinates are `tsapper.simple_controller_support:simple_controller_support-common`,
`simple_controller_support-fabric`, and `simple_controller_support-neoforge`, version `1.1.0`.
Republish before rebuilding the consumer after controller API changes.

The launcher must run from this workspace (or its own build output). It stages both built
Fabric mods, Architectury 13.0.11, and Fabric API 0.116.17+1.21.1 from the Gradle cache.
Missing artifacts produce an error before launch. Minecraft/Fabric/runtime files are downloaded
on the first run. Internet access is required for initial installation.

The primary game directory remains `Splitscreen Ultimate/player1`; guests use `player2` through
`player4`. Shared downloads live in `Splitscreen Ultimate/minecraft`. Usernames are stable offline
names `player1` through `player4`. Other installed mods are retained; managed copies of these four
mod dependencies are replaced. Existing worlds are not migrated or overwritten.

## Playing

- Start the launcher: only player one launches and fills the game area.
- Player one initially uses keyboard/mouse. The first controller to press A claims player one;
  release controls to neutral before using it. Other unassigned controllers pressing A launch guests.
- Guests load invisibly. When player one enters a singleplayer world, they connect automatically
  through its private local TCP listener. Only connected guests appear and accept controller input.
- Two players use top/bottom halves. With three, player one takes the top half and guests take
  the bottom quarters. Four use a 2×2 grid.
- Leaving a world hides/disables/disconnects guests but keeps their processes ready. Entering
  another world reconnects them automatically. A failed join stays hidden; use **Retry guests**
  on the main menu to retry it.
- A disconnected controller leaves its player in place with released input. An unassigned
  controller pressing A reclaims the lowest disconnected player slot before launching another.
- The pause menu's **Open to LAN** opens a second, network-facing listener and advertises that
  chosen port. The private local listener and its existing connections stay intact. Before this
  action the automatic listener is bound only to loopback. Standard LAN behavior applies to
  other devices; the four-player cap applies to local windows.
- Closing the launcher shuts down its clients, allowing eight seconds for graceful exit before
  terminating remaining owned processes. A main-instance failure ends the session.

The title panel distinguishes launched processes, IPC-ready instances, and players in the world.
Guests do not accept physical keyboard/mouse events, even when visible. Simple Controller Support
alone retains normal standalone selection; Splitscreen Ultimate without session properties does
not automate anything.

## Integration contract

The launcher supplies JVM properties `splitscreen.session`, `instance`, `slot` (1–4), `role`
(`main` or `guest`), `port`, `token`, and `controller`. Session/instance IDs and tokens are
per launch. `controller` is a session routing ID, not a physical-device or GLFW identifier.

The launcher binds IPv4 loopback on an ephemeral port. Protocol version 1 uses UTF-8 JSON
objects separated by newlines, with a 64 KiB line limit. Each client first sends `hello` with
version/session/instance/slot/token and its Win32 `hwnd` as an unsigned decimal string. The
launcher validates credentials and window ownership before `registered` and embedding.

The splitscreen mod also guards GLFW's process-local `GetActiveWindow` import. Cross-process
window parenting shares input queues; bundled GLFW otherwise reads the active window's `GLFW`
property as though it were an address in its own JVM. The guard rejects foreign-process HWNDs
without changing other modules or standalone controller support. See the
[upstream GLFW event-polling implementation](https://github.com/glfw/glfw/blob/3.3.3/src/win32_window.c).

The main mod owns reservations/controller routing. `launch`/`launchResult` use request IDs;
`guestExited` frees reservations. `world`, `connect`, `disconnect`, `worldReady`, `guestLeft`,
`joinFailed`, `activate`, and `input` are guarded by world generations where applicable.
`input` includes slot, route, sequence, generation, and a `sample` containing enum-named axes
and held buttons. The launcher forwards input only to the assigned, world-ready guest.
`controllerStatus`, `status`, `retry`, and `shutdown` handle presentation and lifecycle.

Input IO is bounded and off the game thread. External samples expire after 500 ms, releasing
held input. Neutral input is required on handover/recovery. Short button transitions are queued;
redundant analog updates coalesce. GLFW slots and observed-connection UUIDs remain local to
the main process; guests receive samples through generic virtual controllers.

Private offline guest login is permitted only for expected player names on the loopback-only
listener's port. It does not globally disable server authentication. Opening LAN uses a distinct
port and does not apply that private guest exemption to the network-facing listener.

## Checks and runtime fixture

```powershell
# Controller tests (also run during build)
.\gradlew.bat :common:test

# Launcher tests, in Splitscreen Ultimate/
dotnet run --project Tests/Prototype.Tests.csproj

# Opt-in end-to-end fixture; launches four real clients using simulated controller samples
dotnet run --project "Splitscreen Ultimate.csproj" -- --prototype-smoke-test
```

The fixture creates two uniquely named `splitscreen-smoke-*` worlds in player one's saves,
joins four clients to each, and stops the session. These test saves are retained for inspection.
It uses flat worlds and reduces rendering distance/frame rate in the managed player profiles to
keep the four-client check lightweight. It verifies received controller input in every guest and
checks both private and optional public listener bindings; the public test listener is closed
when the temporary world closes.
It never opens an existing world. Look for `SPLITSCREEN_SMOKE PASS` in
`player1/launcher-console.log`. Session lifecycle diagnostics are in `prototype-session.log`;
each player has its own `launcher-console.log` and normal Minecraft logs.

Automated tests cover selection, immutable samples, input transitions/timeouts, reservations,
capacity, reconnect priority, launch correlation, layouts, authentication, world generations,
and IPC framing/disconnect. Hardware validation must additionally exercise real gamepads,
held attack/menu-click release, DPI resizing, unplug/reclaim, and access from a second LAN
device. A successful build alone does not validate mixin targets or native embedding.
Recorded checks and remaining manual acceptance scenarios are in `validation/RESULTS.md`.

For an auto-closing NeoForge mixin startup check after the launcher has installed its Java 21 runtime:

```powershell
# In splitscreen_ultimate/
.\gradlew.bat :neoforge:runClient --offline -I ../validation/neoforge-runtime.init.gradle
```

Look for `SPLITSCREEN_LOADER_CHECK PASS` in `neoforge/run/logs/latest.log`. This validates target
loading/title startup on NeoForge, not a four-player NeoForge session. Common Mixin annotations
are compiled against the older NeoForge-compatible annotation API; Fabric supports that form too.
