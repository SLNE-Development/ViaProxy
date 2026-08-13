# ViaProxy Pterodactyl Egg

This directory contains the Pterodactyl egg used for the SLNE ViaProxy distribution.

## Import

Import `egg-viaproxy.json` in the Pterodactyl admin panel and create a server from the imported egg.

The egg uses the Java 25 Pterodactyl yolk and starts ViaProxy with:

```text
java -Xms128M -XX:MaxRAMPercentage=95.0 -DskipUpdateCheck=true -jar ViaProxy.jar config viaproxy.yml
```

Pterodactyl marks the server ready when ViaProxy reaches:

```text
Binding proxy server to 
```

The stop command is `stop`.

## Installation and updates

Install/reinstall resolves the latest published GitHub release from `SLNE-Development/ViaProxy` and downloads the release asset named exactly `ViaProxy.jar`.

The download is written to `ViaProxy.jar.tmp`, checked for a non-empty result, and only then atomically moved to `ViaProxy.jar`. A failed download therefore does not replace the existing runtime JAR with a partial file.

There is no runtime auto-update. Normal restarts only execute the installed JAR and leave `viaproxy.yml` and other ViaProxy data untouched. To install a newer SLNE build, use Pterodactyl's reinstall action after a newer release has been published.

## Release source

SLNE runtime releases are built from the `fork-patches` branch. Published builds use `build-N` Git tags and expose the runtime artifact under the stable asset name `ViaProxy.jar`.

The egg intentionally does not use upstream ViaProxy GitHub Releases, Jenkins, or a Stable/Dev channel selector.
