package net.raphimc.viaproxy.proxy.chat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatSigningModeResolverTest {
    @Test
    void resolvesModes() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertEquals(ChatSigningMode.PASSTHROUGH, ChatSigningModeResolver.resolve(true, a, a, true, true));
        assertEquals(ChatSigningMode.RESIGN, ChatSigningModeResolver.resolve(true, a, b, true, true));
        assertEquals(ChatSigningMode.UPSTREAM, ChatSigningModeResolver.resolve(false, a, a, true, true));
        assertEquals(ChatSigningMode.UPSTREAM, ChatSigningModeResolver.resolve(true, a, b, true, false));
    }

    @Test
    void sameIdentityPassesThroughWithoutProxyChatSession() {
        UUID profileId = UUID.randomUUID();

        assertEquals(
                ChatSigningMode.PASSTHROUGH,
                ChatSigningModeResolver.resolve(true, profileId, profileId, true, false)
        );
    }
}
