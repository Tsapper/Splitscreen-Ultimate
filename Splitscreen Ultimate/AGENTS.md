# Splitscreen Ultimate

## Role in the workspace

Read `../AGENTS.md` for the full architecture, including when opening this directory on its own.

This is the .NET desktop launcher and window host for local splitscreen Minecraft Java Edition. Each player runs a separate Minecraft process, with the game windows nested inside one desktop window.

The other components are `../simple_controller_support/`, the standalone controller/input mod, and `../splitscreen_ultimate/`, the session coordinator. Its main instance detects unassigned controllers pressing A and requests another Minecraft instance; this app launches and embeds the process. Guests receive routed controller input through the controller mod's generic external-input API.

Keep process launching, native window hosting, resizing, and layout here. The main splitscreen mod owns controller reservations and world/player decisions. This app now implements authenticated loopback JSON IPC, routes guest controller samples, launches up to four local processes on request, and dynamically embeds only world-ready guests. See `../PROTOTYPE.md` for the exact startup/protocol contract, build order, and runtime checks.

## Stack and commands

- C#, .NET `10.0`, Avalonia `12.1.1`, and CmlLib.Core `4.0.6`.
- Native embedding uses `user32.dll`, so the current implementation requires Windows despite Avalonia's broader platform support.
- Run commands from this directory.
- Build: `dotnet build "Splitscreen Ultimate.csproj"`.
- Run: `dotnet run --project "Splitscreen Ultimate.csproj"`. Running currently launches Minecraft automatically and may install/download game files.
- Protocol/layout tests: `dotnet run --project Tests/Prototype.Tests.csproj`. Opt-in four-client fixture: `dotnet run --project "Splitscreen Ultimate.csproj" -- --prototype-smoke-test`. The fixture creates two uniquely named test worlds; see the workspace prototype guide. Physical controller/other-device LAN validation still requires hardware.

## Source map and current behavior

- `Program.cs` and `App.axaml.cs`: Avalonia initialization and main-window creation.
- `MainWindow.axaml`: four persistent native hosts in a canvas, plus a session-status strip.
- `MainWindow.axaml.cs`: launches one main process, authenticates registrations, manages guest lifecycle/world generations, routes input, and lays out visible players with DPI scaling. Hidden hosts are parked offscreen; HWNDs remain hidden/disabled until guests join. Repeated layout updates must never reset controller input.
- `Launcher.cs`: CmlLib shared installation of Fabric 0.19.5/Minecraft 1.21.1, mod staging from local build outputs/Gradle dependency cache, distinct offline player names/directories, session JVM properties, 2048 MB RAM cap, and per-player console logs. Other installed mods and existing saves are preserved.
- `IpcServer.cs` and `SessionModel.cs`: bounded 64 KiB newline JSON protocol, per-process token authentication, request correlation/state, and pure layout/authentication rules. Tokens must not appear in logs.
- `MinecraftHost.cs`: Avalonia `NativeControlHost` wrapping a Win32 `STATIC` child window; creates and destroys the native host handle.
- `WindowEmbedder.cs`: polls for a process's main window handle (15-second timeout), removes window chrome, reparents the Minecraft window, and moves/resizes it using Win32 APIs.

## Known gaps and integration guidance

- Startup uses explicit `splitscreen.*` JVM session/instance/slot/role/port/token/controller properties. Initial controller assignment is a session routing ID; main starts unassigned. Do not infer main role from launch order or exchange GLFW/connection ids between processes. See `../PROTOTYPE.md` for the full protocol.
- The splitscreen mod installs a process-local GLFW GetActiveWindow import guard: Win32 cross-process SetParent shares input queues, and bundled GLFW otherwise dereferences another JVM's GLFW window property. Keep that compatibility code in the splitscreen mod, never in standalone controller support.
- The main directory is `player1`; guests use isolated `player2`–`player4` directories, with shared read-mostly installation files under `minecraft`. Keep player data. The launcher only stages Fabric; both mods also build for NeoForge.
- Layout is full main outside worlds; two horizontal halves; three with main on top and guests in bottom quarters; four quarters. Disconnected controllers retain their player's visible slot. Failed world joins remain hidden, with Retry guests available from the main menu.
- Main-process/IPC failure shuts down guests. Launcher close requests graceful exit, then kills only owned remaining processes after eight seconds. A single-instance mutex prevents simultaneous installations in the same shared runtime.
- Treat `minecraft/`, `player1/`, `bin/`, `obj/`, and `.vs/` as runtime, download, build, or IDE data rather than application source. Avoid scanning their assets, libraries, and logs unless needed for a specific issue; preserve player data.
- When implementing communication, document the actual app/mod contract in the root instructions and relevant component instructions. Distinguish launch requests, process identity, player identity, controller ownership, and launch results.
