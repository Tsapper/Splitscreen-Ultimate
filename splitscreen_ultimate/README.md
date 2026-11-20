# Splitscreen Ultimate mod

Client-side Architectury mod for Minecraft 1.21.1, using Java 21 and supporting Fabric and NeoForge. The build setup and source conventions follow the sibling Simple Controller Support project.

This project currently provides the mod foundation and shared client initialisation. Session coordination, controller assignment, and communication with the desktop app are planned, not implemented.

The intended integration is:

```text
.NET launcher/window host <--> Splitscreen Ultimate mod <--> Simple Controller Support
```

Each local player runs in a separate Minecraft process. The desktop app manages processes and windows; this mod will coordinate sessions; Simple Controller Support handles input. Integration contracts are still to be designed.

## Development

Install a JDK compatible with the Gradle wrapper and Java 21 compilation, and set `JAVA_HOME`. Run commands from this directory:

```powershell
.\gradlew.bat build
.\gradlew.bat :fabric:runClient
.\gradlew.bat :neoforge:runClient
```

Loader jars are generated in `fabric/build/libs` and `neoforge/build/libs`. Install the jar matching your loader together with Architectury API (and Fabric API on Fabric). No controller mod dependency or integration exists yet.

See [AGENTS.md](AGENTS.md) for architecture and contribution guidance.
