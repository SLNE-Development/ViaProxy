package net.raphimc.viaproxy.proxy.chat;

import com.viaversion.viaversion.api.minecraft.signature.storage.ChatSession1_19_3;
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.raphimc.netminecraft.constants.ConnectionState;
import net.raphimc.netminecraft.constants.MCPackets;
import net.raphimc.netminecraft.packet.PacketTypes;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;

public final class BackendChatSigningHandler extends ChannelOutboundHandlerAdapter {
    public static final String NAME = "viaproxy-backend-chat-signing";
    private final ProxyConnection proxyConnection;
    private final int targetChatMessageId;
    private final int targetChatSessionUpdateId;
    private ChannelHandlerContext context;

    public BackendChatSigningHandler(final ProxyConnection proxyConnection) {
        this.proxyConnection = proxyConnection;
        this.targetChatMessageId = MCPackets.C2S_CHAT.getId(proxyConnection.getServerVersion().getVersion());
        this.targetChatSessionUpdateId = MCPackets.C2S_CHAT_SESSION_UPDATE.getId(proxyConnection.getServerVersion().getVersion());
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        this.context = ctx;
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf buffer
                && this.proxyConnection.getP2sConnectionState() == ConnectionState.PLAY
                && ChatSigningModeResolver.resolve(this.proxyConnection) == ChatSigningMode.RESIGN
                && this.proxyConnection.getUserConnection().has(ChatSession1_19_3.class)) {
            TargetChatMessageSigner.rewrite(buffer, this.targetChatMessageId, this.proxyConnection.getServerVersion(),
                    this.proxyConnection.getUserConnection().get(ChatSession1_19_3.class));
        }
        ctx.write(msg, promise);
    }

    public void sendSessionUpdate(final ChatSession1_19_3 chatSession) {
        if (this.context == null) {
            throw new IllegalStateException("Backend chat signing handler is not attached to the pipeline");
        }
        final ByteBuf packet = Unpooled.buffer();
        PacketTypes.writeVarInt(packet, this.targetChatSessionUpdateId);
        PacketTypes.writeUuid(packet, chatSession.getSessionId());
        Types.PROFILE_KEY.write(packet, chatSession.getProfileKey());
        this.context.writeAndFlush(packet);
    }
}
