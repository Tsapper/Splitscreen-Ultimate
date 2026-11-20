# Prototype validation — November 19, 2026

- Simple Controller Support: Fabric/NeoForge builds and local Maven publication passed. All 14
  JUnit tests passed (standalone/strict/disabled selection, external-source exclusion from fallback,
  stale handles, immutable snapshots, input transitions/coalescing/timeouts, and sample validation).
- Splitscreen Ultimate mod: both loader builds passed. All five reservation/capacity/reconnect/
  request-correlation tests passed. Neither platform jar contains shaded controller-mod classes.
- Launcher: .NET 10 build passed with zero warnings/errors. The console regression suite passed
  layout coverage/no-overlap, authentication rejection, stale-world guards, IPC framing/replies,
  and disconnect cleanup.
- Fabric end-to-end: four real Minecraft clients launched using simulated external gamepads,
  joined two uniquely named flat worlds, applied controller input in each guest, reused the same
  guest processes across worlds, and shut down. The launcher verified loopback-only private
  listeners and a distinct wildcard-bound optional LAN listener. The final fixture logs
  `SPLITSCREEN_SMOKE PASS` in `Splitscreen Ultimate/player1/logs/latest.log`.
- Independent Win32 inspection passed: four visible native game windows had equal 640×347 tiles,
  all guest windows were disabled for physical input, and world exit restored the main window
  to 1280×694 with every guest hidden. These are the tested desktop's dimensions, not fixed sizes.
- NeoForge 21.1.248: a Java 21 development client loaded the title screen and all session mixin
  target classes, then stopped automatically with `SPLITSCREEN_LOADER_CHECK PASS` in
  `splitscreen_ultimate/neoforge/run/logs/latest.log`. This is a startup/mixin check, not a
  four-player NeoForge session; the launcher currently installs Fabric.

Real gamepad unplug/reclaim, held attack/menu-click behavior with physical hardware, multi-monitor
DPI changes, and joining from another LAN device remain manual acceptance checks. Socket binding
checks establish listener scope on this PC; they do not prove a separate device's firewall path.
Test saves and runtime downloads are retained in the managed player/shared directories.

To independently inspect actual native windows during the opt-in fixture, run
`./validation/Inspect-PrototypeWindows.ps1` from a second PowerShell. It reads only the current
test-session JVMs' Win32 visibility/enabled state/geometry, and checks settled four-way equal
tiles plus main-only visibility after world exit.
