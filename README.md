# Splitscreen Ultimate

Local splitscreen multiplayer for **Minecraft Java Edition 1.21.1**. Each local player runs a separate Minecraft instance, and a Windows desktop launcher embeds the game windows in one window with layouts for up to four players.

This repository contains three independently built components:

| Component | Source | Responsibility |
| --- | --- | --- |
| Simple Controller Support | [simple_controller_support](simple_controller_support/) | Standalone Fabric/NeoForge controller mod: gameplay input, bindings, virtual cursor, menus, and on-screen keyboard. |
| Splitscreen Ultimate launcher | [Splitscreen Ultimate](Splitscreen%20Ultimate/) | Windows desktop app: process launching, window embedding, resizing, and layouts. |
| Splitscreen Ultimate mod | [splitscreen_ultimate](splitscreen_ultimate/) | Fabric/NeoForge client mod: controller ownership, player sessions, guest input routing, and automatic local world joins. |

Simple Controller Support works independently of the launcher and splitscreen mod. Its source and [integration API documentation](simple_controller_support/API.md) live in its own folder.

## Status

The integrated project is a four-player prototype. The launcher currently installs Fabric; both mods also build for NeoForge. Native window embedding requires Windows. See [validation results and remaining manual checks](validation/RESULTS.md).

## Build and run

Install the .NET 10 SDK and a JDK supported by the bundled Gradle wrappers; the mods target Java 21. In PowerShell, run these commands from the repository root:

```powershell
Push-Location simple_controller_support
.\gradlew.bat build publishToMavenLocal
Pop-Location

Push-Location splitscreen_ultimate
.\gradlew.bat build
Pop-Location

Push-Location 'Splitscreen Ultimate'
dotnet build 'Splitscreen Ultimate.csproj'
dotnet run --project 'Splitscreen Ultimate.csproj'
Pop-Location
```

Build the controller mod first: the splitscreen mod resolves its artifacts through local Maven. The launcher stages the built Fabric mods and dependencies from local build outputs and the Gradle cache. Its first run downloads Minecraft, Fabric, and Java runtime files.

Start the launcher, then press A on a controller to claim player one. Additional unassigned controllers pressing A launch guests. Guests automatically join when the main player enters a singleplayer world. Leaving the world hides guests and retains their processes for the next world.

See [PROTOTYPE.md](PROTOTYPE.md) for setup, controls, build order, IPC contracts, tests, and limitations. Game downloads, player directories, saves, logs, and build outputs stay local and are excluded from Git.
