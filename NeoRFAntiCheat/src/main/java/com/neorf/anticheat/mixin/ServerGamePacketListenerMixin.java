package com.neorf.anticheat.mixin;

import com.neorf.anticheat.NeoRFAntiCheat;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {

    @Inject(method = "handleAttack", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER))
    private void neorf$afterServerThreadHandoff(ServerboundAttackPacket packet, CallbackInfo ci) {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        NeoRFAntiCheat mod = NeoRFAntiCheat.getInstance();
        if (mod == null || mod.getCheckRegistry() == null) return;

        Entity target = listener.player.level().getEntityOrPart(packet.entityId());
        mod.getCheckRegistry().onServerAttack(listener.player, target);
    }
}