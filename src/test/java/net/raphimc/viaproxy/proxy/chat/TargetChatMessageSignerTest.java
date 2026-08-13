package net.raphimc.viaproxy.proxy.chat;

import com.viaversion.viaversion.api.minecraft.ProfileKey;
import com.viaversion.viaversion.api.minecraft.signature.storage.ChatSession1_19_3;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.raphimc.netminecraft.packet.PacketTypes;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.BitSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TargetChatMessageSignerTest {

    @Test
    void rewritesTargetFormatChatWithFreshSignatureAndAckState() throws Exception {
        final int chatPacketId = 42;
        final String message = "signed through ViaProxy";
        final long timestamp = 1_786_654_321_000L;
        final long salt = 0x1234ABCDL;
        final ChatSession1_19_3 session = createSession();

        final ByteBuf packet = Unpooled.buffer();
        try {
            PacketTypes.writeVarInt(packet, chatPacketId);
            PacketTypes.writeString(packet, message);
            packet.writeLong(timestamp);
            packet.writeLong(salt);
            Types.OPTIONAL_SIGNATURE_BYTES.write(packet, null);
            PacketTypes.writeVarInt(packet, 7);
            final BitSet oldAck = new BitSet(20);
            oldAck.set(3);
            Types.ACKNOWLEDGED_BIT_SET.write(packet, oldAck);
            packet.writeByte(99);

            assertTrue(TargetChatMessageSigner.rewrite(
                    packet,
                    chatPacketId,
                    ProtocolVersion.v1_21_5,
                    session
            ));

            assertEquals(chatPacketId, PacketTypes.readVarInt(packet));
            assertEquals(message, PacketTypes.readString(packet, 256));
            assertEquals(timestamp, packet.readLong());
            assertEquals(salt, packet.readLong());

            final byte[] signature = Types.OPTIONAL_SIGNATURE_BYTES.read(packet);
            assertNotNull(signature);
            assertTrue(signature.length > 0);
            assertEquals(0, PacketTypes.readVarInt(packet));
            assertTrue(Types.ACKNOWLEDGED_BIT_SET.read(packet).isEmpty());
            assertEquals(0, packet.readUnsignedByte());
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    @Test
    void leavesNonChatPacketUntouched() throws Exception {
        final ByteBuf packet = Unpooled.buffer();
        try {
            PacketTypes.writeVarInt(packet, 7);
            packet.writeLong(123456789L);
            final byte[] before = ByteBufUtil.getBytes(packet);

            assertFalse(TargetChatMessageSigner.rewrite(
                    packet,
                    42,
                    ProtocolVersion.v1_21_5,
                    createSession()
            ));
            assertArrayEquals(before, ByteBufUtil.getBytes(packet));
            assertEquals(0, packet.readerIndex());
        } finally {
            packet.release();
        }
    }

    private static ChatSession1_19_3 createSession() throws Exception {
        final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        final KeyPair keyPair = generator.generateKeyPair();
        final UUID profileId = UUID.randomUUID();
        final ProfileKey profileKey = new ProfileKey(
                Long.MAX_VALUE,
                keyPair.getPublic().getEncoded(),
                new byte[]{1}
        );
        return new ChatSession1_19_3(profileId, keyPair.getPrivate(), profileKey);
    }
}
