package com.neorf.anticheat.mixin;

import com.neorf.anticheat.NeoRFAntiCheat;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin {

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"))
    private void neorf$countInboundPacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        PacketListener packetListener = ((Connection) (Object) this).getPacketListener();
        if (!(packetListener instanceof ServerGamePacketListenerImpl listener)) return;

        NeoRFAntiCheat mod = NeoRFAntiCheat.getInstance();
        if (mod != null && mod.getCheckRegistry() != null) {
            mod.getCheckRegistry().onInboundPacket(listener.player);
        }
    }
}