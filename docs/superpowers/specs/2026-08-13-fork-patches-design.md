# ViaProxy fork-patches design

Date: 2026-08-13
Repository: `SLNE-Development/ViaProxy`
Branch: `fork-patches`

## Goals

Maintain an upstream-friendly `main` branch while keeping SLNE-specific changes on `fork-patches`.

The fork branch must provide:

1. Automatic Java 25 builds on pushes to `fork-patches`.
2. A new immutable GitHub release for every successful publish run, tagged `build-<github.run_number>` and containing exactly one runtime asset named `ViaProxy.jar`.
3. A Pterodactyl egg stored in the repository that installs the newest published `ViaProxy.jar` from GitHub Releases.
4. A signed-chat fix for Java clients and Java backend targets from Minecraft 1.19.3 onward, including cross-version connections such as 1.19.3 client -> 26.2 backend.
5. No Docker publishing in the SLNE workflow.

## Branch strategy

`main` stays as close as practical to upstream ViaProxy and is not used for SLNE-specific runtime changes.

`fork-patches` is the SLNE distribution branch and is intended to become the repository default branch after the work is complete.

Future upstream updates should first be synchronized into `main`, then merged or rebased into `fork-patches`. Fork-specific files and behavior live only on `fork-patches` unless an upstream contribution is intentionally prepared later.

## Build and release publishing

### Build trigger

A dedicated GitHub Actions workflow on `fork-patches` runs on:

- pushes to `fork-patches`
- manual `workflow_dispatch`
- pull requests targeting `fork-patches` for validation only

Release creation happens only for successful push or explicitly allowed manual runs on `fork-patches`; pull requests never publish releases.

### Build environment

Use the repository Gradle wrapper and Java 25. The existing project build remains authoritative:

```bash
./gradlew build --stacktrace
```

ViaProxy currently also produces a Java 8 downgraded artifact. The release workflow must explicitly select the normal Java 25 runtime JAR and must not accidentally publish a `+java8` artifact.

Before publishing, copy or rename the selected artifact to the stable asset name:

```text
ViaProxy.jar
```

### Release numbering

Each successful publish run creates a new GitHub release:

```text
Tag: build-<github.run_number>
Title: ViaProxy Build #<github.run_number>
Asset: ViaProxy.jar
```

The release body includes at least:

- full commit SHA
- short commit SHA
- source branch
- link or reference to the GitHub Actions run where practical

`github.run_number` is used only as a monotonically increasing build identifier for this workflow, not as a semantic upstream version.

A workflow rerun keeps the same `github.run_number`; publishing logic must therefore avoid silently replacing a previously published build tag. If the tag already exists, the publish step must fail clearly rather than mutate the immutable release.

### Latest release behavior

Every published `build-N` is a normal non-draft, non-prerelease GitHub release so GitHub's `releases/latest` endpoint resolves to the newest successful build. The Pterodactyl installer depends on this behavior.

## Pterodactyl egg

Files:

```text
pterodactyl/egg-viaproxy.json
pterodactyl/README.md
```

The egg has no stable/dev channel selector. Installation and reinstallation always install the latest published SLNE build.

### Installer

The installer resolves the latest GitHub release for:

```text
SLNE-Development/ViaProxy
```

It locates the release asset named exactly `ViaProxy.jar`, downloads it to a temporary path, validates that the download succeeded and is non-empty, then atomically replaces the runtime file:

```text
ViaProxy.jar.tmp -> ViaProxy.jar
```

The installer must fail with a useful error if:

- GitHub has no published release yet
- `ViaProxy.jar` is missing from the latest release
- download fails
- the resulting file is empty

No Jenkins dependency and no development/stable selector are used.

### Runtime

Use Java 25 and the startup command:

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

A running Pterodactyl server is not auto-updated when a new GitHub build appears. Reinstalling the server fetches the newest published `build-N` release.

The repository README for the egg documents import, installation, required Java image, update behavior, and the fact that releases are generated from `fork-patches`.

## Signed-chat support

### Supported scope

The fork-specific signed-chat bridge applies when both endpoints are Java protocol versions at or above Minecraft 1.19.3 and `chat-signing` is enabled with a usable Microsoft account.

Required supported examples include:

```text
1.19.3 -> 26.2
1.20.x -> 26.2
1.21.x -> 26.2
26.2 -> 26.2
26.2 -> older >=1.19.3 target
```

Versions older than 1.19.3 are outside the new fork guarantee and retain upstream behavior.

### Problem in current behavior

ViaProxy currently creates its own `ChatSession1_19_3`, drops an incoming client `CHAT_SESSION_UPDATE` in the encrypted/backend-authenticated path, rewrites chat messages using its own chat session, and injects a proxy-generated `CHAT_SESSION_UPDATE` after Join Game.

This mechanism failed for the tested 26.2 -> 26.2 path: the downstream server reported a secure-chat state update failure and the player received `Chat disabled due to missing profile public key`. A diagnostic same-version patch that stopped replacing the client session restored working signed chat, proving that the proxy-side session replacement path is involved.

### Design principle

Signed chat is treated as a bridge with two operating modes:

#### Transparent mode

When the client and target protocol are identical and the client's signed-chat session can be passed through safely, ViaProxy should preserve the original client chat session and signatures instead of replacing them.

For this mode:

- do not drop the client's `CHAT_SESSION_UPDATE`
- do not inject a ViaProxy replacement chat session
- do not re-sign the client's already valid chat packet

This includes the already validated 26.2 -> 26.2 case and should work for equal versions >=1.19.3 where the packet model matches.

#### Bridged re-sign mode

When client and target protocol versions differ but both are >=1.19.3, ViaVersion remains responsible for protocol translation while ViaProxy is responsible for establishing a valid backend-side chat session and signing the backend-format message.

Conceptual flow:

```text
client signed-chat packet
  -> ViaVersion translation
  -> backend-format chat packet
  -> ViaProxy backend-side signing bridge
  -> target server
```

The signature must be calculated for the target/backend protocol representation, not by blindly interpreting the original client packet layout as if it were the target layout.

### Identity and credentials

Cross-version re-signing uses the configured Microsoft account and its current Mojang/Minecraft player certificate, private key, profile UUID, and backend authentication token.

The backend-side chat session must correspond to the same profile identity ViaProxy authenticates as toward the target server. The implementation must never combine a chat certificate from profile A with a backend login identity for profile B.

Transparent passthrough is only valid when preserving the client session does not create an identity mismatch with the identity presented to the backend. If identity cannot be proven compatible, ViaProxy must use bridged re-sign mode rather than unsafe passthrough.

No private keys, access tokens, or sensitive certificate material may be logged.

### Implementation boundary

The existing `ChatSignaturePacketHandler` may be refactored or split, but the design should keep three responsibilities explicit:

1. Decide whether this connection uses transparent passthrough or backend re-signing.
2. Manage the backend chat session update only when re-signing is required.
3. Sign the target-format chat message after translation, using the target protocol's packet layout and acknowledgement/checksum requirements.

The solution must avoid hardcoding 26.2-specific packet IDs. Packet IDs and structures should continue to come from ViaVersion/netminecraft protocol abstractions where available.

### Failure behavior

If `chat-signing: true` is requested but ViaProxy cannot obtain a valid certificate/session required for backend re-signing, the failure should be explicit in logs without exposing secrets. It must not silently claim secure chat is active while sending malformed signing state.

Existing behavior for configurations without chat signing should remain unchanged.

## Testing

### Build and publishing tests

Validate locally or in CI that:

- `./gradlew build --stacktrace` succeeds on Java 25
- the selected runtime JAR is the normal non-`+java8` artifact
- the publish workflow does not run release creation on pull requests
- release tags use `build-<run_number>`
- duplicate tag publication fails rather than overwriting an old release

### Egg tests

Validate the egg JSON structure and installer script.

Test installer behavior against:

- latest release with valid `ViaProxy.jar`
- missing asset
- failed GitHub request/download

Confirm the resulting runtime path is exactly `ViaProxy.jar` and startup configuration matches the expected CLI invocation.

### Signed-chat tests

At minimum cover logic/unit tests for mode selection and packet/session behavior for:

```text
1.19.3 -> 26.2      bridged re-sign
1.20.x -> 26.2      bridged re-sign
1.21.x -> 26.2      bridged re-sign
26.2 -> 26.2        transparent passthrough
26.2 -> >=1.19.3    bridged re-sign when versions differ
<1.19.3 endpoint     existing/upstream behavior
```

Also verify:

- transparent mode does not suppress client `CHAT_SESSION_UPDATE`
- transparent mode does not inject a replacement session
- re-sign mode does inject a valid backend-side session update when required
- re-sign mode signs the translated target-format chat packet
- configured backend account identity and signing certificate identity match
- chat remains functional in the original reproduced topology: Minecraft client -> ViaProxy -> Velocity -> backend

Where practical, retain a regression test for the specific secure-chat failure found with 26.2.

## Non-goals

- No Docker publishing workflow for the SLNE distribution.
- No automatic in-place update of running Pterodactyl servers.
- No new signed-chat guarantee for client or target versions below 1.19.3.
- No attempt to keep `main` as the SLNE runtime branch.
- No semantic-version replacement for upstream ViaProxy versioning; `build-N` is a fork build identifier only.
