package net.craftproxy.mod.client.mixin;

import net.craftproxy.mod.client.TunnelClient;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

@Mixin(ClientConnection.class)
public class IpSpoofMixin {

    @Inject(method = "getAddress", at = @At("RETURN"), cancellable = true)
    private void spoofRemoteAddress(CallbackInfoReturnable<SocketAddress> cir) {
        SocketAddress original = cir.getReturnValue();
        if (original instanceof InetSocketAddress addr && addr.getAddress() != null) {
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