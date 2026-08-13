package net.raphimc.viaproxy.proxy.chat;

import java.util.UUID;

public final class ChatSigningModeResolver {

    private ChatSigningModeResolver() {
    }

    public static ChatSigningMode resolve(
            final boolean supportedVersions,
            final boolean sameProtocol,
            final UUID frontendProfileId,
            final UUID backendProfileId,
            final boolean chatSigningEnabled,
            final boolean hasProxyChatSession
    ) {
        if (!supportedVersions || !chatSigningEnabled || !hasProxyChatSession) {
            return ChatSigningMode.UPSTREAM;
        }
        if (sameProtocol && frontendProfileId != null && frontendProfileId.equals(backendProfileId)) {
            return ChatSigningMode.PASSTHROUGH;
        }
        return ChatSigningMode.RESIGN;
    }
}
