package net.raphimc.viaproxy.proxy.chat;

import com.viaversion.viaversion.api.minecraft.PlayerMessageSignature;
import com.viaversion.viaversion.api.minecraft.signature.model.MessageMetadata;
import com.viaversion.viaversion.api.minecraft.signature.storage.ChatSession1_19_3;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.raphimc.netminecraft.packet.PacketTypes;

import java.util.BitSet;

public final class TargetChatMessageSigner {

    private TargetChatMessageSigner() {
    }

    public static boolean rewrite(
            final ByteBuf packet,
            final int targetChatMessageId,
            final ProtocolVersion targetVersion,
            final ChatSession1_19_3 chatSession
    ) throws Exception {
        packet.markReaderIndex();
        final int packetId = PacketTypes.readVarInt(packet);
        if (packetId != targetChatMessageId) {
            packet.resetReaderIndex();
            return false;
        }

        final String message = PacketTypes.readString(packet, 256);
        final long timestamp = packet.readLong();
        final long salt = packet.readLong();
        packet.resetReaderIndex();

        final byte[] signature = chatSession.signChatMessage(
                new MessageMetadata(null, timestamp, salt),
                message,
                new PlayerMessageSignature[0]
        );

        final ByteBuf rewritten = Unpooled.buffer();
        try {
            PacketTypes.writeVarInt(rewritten, targetChatMessageId);
            PacketTypes.writeString(rewritten, message);
            rewritten.writeLong(timestamp);
            rewritten.writeLong(salt);
            Types.OPTIONAL_SIGNATURE_BYTES.write(rewritten, signature);
            PacketTypes.writeVarInt(rewritten, 0);
            Types.ACKNOWLEDGED_BIT_SET.write(rewritten, new BitSet(20));
            if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_5)) {
                rewritten.writeByte(0);
            }

            packet.clear();
            packet.writeBytes(rewritten);
            return true;
        } finally {
            rewritten.release();
        }
    }
}
