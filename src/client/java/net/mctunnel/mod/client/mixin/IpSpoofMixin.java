package net.mctunnel.mod.client.mixin;

import net.mctunnel.mod.client.TunnelClient;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

@Mixin(Connection.class)
public class IpSpoofMixin {

    @Inject(method = "getRemoteAddress", at = @At("RETURN"))
    private void spoofRemoteAddress(CallbackInfoReturnable<SocketAddress> cir) {
        SocketAddress original = cir.getReturnValue();
        if (original instanceof InetSocketAddress addr) {
            String ip = addr.getAddress().getHostAddress();
            if (ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1")) {
                String playerIp = ((ConnectionAccessorMixin) this).getChannel()
                        .attr(TunnelClient.PLAYER_IP).get();
                if (playerIp != null) {
                    cir.setReturnValue(new InetSocketAddress(playerIp, addr.getPort()));
                }
            }
        }
    }
}