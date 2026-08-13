package net.raphimc.viaproxy.proxy.chat;

import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrontendIdentityTest {
    @Test
    void preservesFrontendIdentityWhenBackendProfileChanges() {
        ProxyConnection connection = new ProxyConnection(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
            }
        }, new EmbeddedChannel());
        UUID frontend = UUID.randomUUID();
        UUID backend = UUID.randomUUID();

        connection.setFrontendProfileId(frontend);
        connection.setGameProfile(new GameProfile(backend, "backend"));

        assertEquals(frontend, connection.getFrontendProfileId());
        assertEquals(backend, connection.getGameProfile().getId());
    }
}
