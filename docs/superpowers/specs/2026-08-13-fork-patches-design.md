# ViaProxy fork-patches design

Date: 2026-08-13
Updated: 2026-08-15
Repository: `SLNE-Development/ViaProxy`
Branch: `fork-patches`

## Goals

Maintain an upstream-friendly `main` branch while keeping SLNE-specific runtime changes on `fork-patches`.

The fork branch provides:

1. Java 25 build and test validation for `fork-patches`.
2. A new GitHub release for every successful publish run, tagged `build-<github.run_number>` and containing the runtime asset `ViaProxy.jar`.
3. A repository-owned Pterodactyl egg that installs the latest published `ViaProxy.jar`.
4. Signed-chat preservation for Java client/backend versions from Minecraft 1.19.3 onward.
5. No additional SLNE Docker publishing path.

## Branch strategy

`main` stays as close as practical to upstream ViaProxy. `fork-patches` is the SLNE distribution branch.

Future upstream updates should first be synchronized into `main`, then merged or rebased into `fork-patches`. Fork-specific behavior and files remain on `fork-patches`.

## Build and release publishing

The fork workflows use the repository Gradle wrapper with Java 25. Tests and the project build must succeed before a release is published.

The release workflow selects the normal runtime JAR from `build/libs`, excluding the `+java8`, sources and javadoc artifacts, then publishes it as:

```text
ViaProxy.jar
```

Each successful publish run creates:

```text
Tag: build-<github.run_number>
Title: ViaProxy Build #<github.run_number>
Asset: ViaProxy.jar
```

Published builds are normal non-draft, non-prerelease releases so GitHub's latest-release endpoint resolves to the newest successful build.

## Pterodactyl egg

Files:

```text
pterodactyl/egg-viaproxy.json
pterodactyl/README.md
```

The egg has no stable/dev selector. Installation and reinstallation fetch the latest release from `SLNE-Development/ViaProxy`, download the asset named exactly `ViaProxy.jar` to a temporary file, and atomically replace the runtime JAR.

Runtime command:

```bash
java -Xms128M -XX:MaxRAMPercentage=95.0 -DskipUpdateCheck=true -jar ViaProxy.jar config viaproxy.yml
```

Startup completion marker:

```text
Binding proxy server to 
```

Stop command:

```text
stop
```

A running server is not updated automatically. Reinstalling fetches the newest published build.

## Signed-chat support

### Supported scope

Fork-specific handling applies when both Java protocol versions are Minecraft 1.19.3 or newer and `chat-signing` is enabled.

Versions below 1.19.3 keep upstream behavior.

### Root cause found during live testing

The original ViaProxy handler could discard the real client `CHAT_SESSION_UPDATE` when the backend connection itself was not encrypted. In the reproduced `26.2 -> 26.2` topology, the frontend and backend UUIDs were identical, but no ViaProxy-owned `ChatSession1_19_3` existed and `p2sEncrypted` was false. Falling back to the upstream handler therefore removed the client's valid signing session and produced:

```text
Chat disabled due to missing profile public key
Failed to update secure chat state
```

The fix is to prefer the real client signing session whenever the identity presented on both sides is the same.

### Mode selection

The fork keeps three modes:

#### PASSTHROUGH

Used when:

- both endpoints are >= 1.19.3,
- chat signing is enabled,
- the original frontend UUID equals the backend profile UUID.

This does not require a ViaProxy-owned `ChatSession1_19_3`.

Behavior:

- forward the client's `CHAT_SESSION_UPDATE`,
- forward the client's signed chat packet into ViaVersion/ViaBackwards,
- do not inject a replacement session,
- do not re-sign the packet in ViaProxy.

This is the live-verified path for `26.2 -> 26.2`. The same rule intentionally also applies across >=1.19.3 protocol versions when the identity remains the same. The cross-version case is covered by mode-selection tests but was not separately live-tested after the final fix at the user's request.

#### RESIGN

Used only as a fallback when both endpoints are >=1.19.3, chat signing is enabled, the identities differ, and ViaProxy has its own usable `ChatSession1_19_3`.

ViaVersion/ViaBackwards performs protocol translation. ViaProxy's backend signing handler then signs the target-format chat packet and can send the matching backend session update.

#### UPSTREAM

Used outside the fork guarantee, when chat signing is disabled, or when an identity-changing connection does not have the ViaProxy-owned signing material required for the RESIGN fallback.

### Identity handling

ViaProxy stores the original frontend profile UUID before a configured account or backend login can replace `gameProfile`. Mode selection compares that preserved frontend UUID with the backend profile UUID.

No private keys, access tokens or certificate material are logged.

## Testing

CI covers:

- mode selection, including same-identity passthrough without a proxy chat session,
- identity preservation,
- target-format backend signing fallback,
- Pterodactyl egg validation,
- full Gradle build,
- runtime JAR selection and release publication.

The reproduced `26.2 -> 26.2` Velocity/Minestom topology was additionally verified live after the final passthrough fix.

## Non-goals

- No fork guarantee for signed chat below 1.19.3.
- No automatic in-place update of running Pterodactyl servers.
- No fork-specific Docker publishing workflow.
- No semantic-version replacement for upstream ViaProxy versioning; `build-N` is only the fork build identifier.
