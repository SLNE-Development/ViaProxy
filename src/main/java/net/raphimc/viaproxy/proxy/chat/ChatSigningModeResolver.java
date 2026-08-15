package net.raphimc.viaproxy.proxy.chat;

import com.viaversion.viaversion.api.minecraft.signature.storage.ChatSession1_19_3;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.raphimc.viaproxy.ViaProxy;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;

import java.util.UUID;

public final class ChatSigningModeResolver {

    private ChatSigningModeResolver() {
    }

    public static ChatSigningMode resolve(final ProxyConnection connection) {
        final boolean supportedVersions = connection.getClientVersion().newerThanOrEqualTo(ProtocolVersion.v1_19_3)
                && connection.getServerVersion().newerThanOrEqualTo(ProtocolVersion.v1_19_3);
        final boolean hasProxyChatSession = connection.getUserConnection() != null
                && connection.getUserConnection().has(ChatSession1_19_3.class);
        final UUID backendProfileId = connection.getGameProfile() != null ? connection.getGameProfile().getId() : null;

        return resolve(
                supportedVersions,
                connection.getClientVersion().equals(connection.getServerVersion()),
                connection.getFrontendProfileId(),
                backendProfileId,
                ViaProxy.getConfig().shouldSignChat(),
                hasProxyChatSession
        );
    }

    public static ChatSigningMode resolve(
            final boolean supportedVersions,
            final boolean sameProtocol,
            final UUID frontendProfileId,
            final UUID backendProfileId,
            final boolean chatSigningEnabled,
            final boolean hasProxyChatSession
    ) {
        if (!supportedVersions || !chatSigningEnabled) {
            return ChatSigningMode.UPSTREAM;
        }
        if (sameProtocol && frontendProfileId != null && frontendProfileId.equals(backendProfileId)) {
            return ChatSigningMode.PASSTHROUGH;
        }
        if (!hasProxyChatSession) {
            return ChatSigningMode.UPSTREAM;
        }
        return ChatSigningMode.RESIGN;
    }
}
