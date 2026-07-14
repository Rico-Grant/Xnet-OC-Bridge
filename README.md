# XNet OC Bridge

XNet OC Bridge adds an `OC` channel to XNet for Minecraft 1.12.2.

It lets XNet connectors bridge OpenComputers networks without running long OC cable lines.

## Modes

- `LINK`: acts like an OpenComputers cable endpoint. Machines connected through LINK connectors on the same XNet channel share one OC network.
- `ADAPTER`: acts like an OpenComputers adapter on the connector side. It exposes the block on that side as an OC component.

## Notes

- The XNet channel ID is `OC`, so it appears as `OC` in the controller channel list.
- The OC channel uses the gray XNet color marker.
- Connector color filters work like other XNet channels.
- ADAPTER mode is directional: only the configured connector side is proxied.

## Build

This project targets Forge `1.12.2-14.23.5.2864` and Java 8.

Place these compile-only dependency jars in `libs/`:

- Minecraft/Forge 1.12.2 development jar
- XNet
- McJtyLib
- OpenComputers

Then build:

```powershell
.\gradlew.bat build
```

The jar is written to `build/libs/`.

You can also pass local jars without copying them:

```powershell
.\gradlew.bat build "-PextraCompileJars=C:\path\to\minecraft-dev.jar;C:\path\to\forge.jar"
```
