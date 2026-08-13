package net.raphimc.viaproxy.proxy.chat;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatSigningModeResolverTest {
    @Test
    void resolvesModes() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(ChatSigningMode.PASSTHROUGH, ChatSigningModeResolver.resolve(true, true, a, a, true, true));
        assertEquals(ChatSigningMode.RESIGN, ChatSigningModeResolver.resolve(true, false, a, a, true, true));
        assertEquals(ChatSigningMode.RESIGN, ChatSigningModeResolver.resolve(true, true, a, b, true, true));
        assertEquals(ChatSigningMode.UPSTREAM, ChatSigningModeResolver.resolve(false, false, a, a, true, true));
    }
}
