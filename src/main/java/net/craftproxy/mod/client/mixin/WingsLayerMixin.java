package net.craftproxy.mod.client.mixin;

// From 1.21.9 CraftProxy capes are drawn by CustomCapeLayer, so the vanilla elytra never sees them
// and falls back to the Mojang cape. This hands our cape texture to the elytra as well.
// Before 1.21.9 AbstractClientPlayerMixin already covers the elytra.

//? if >=1.21.9 {
import net.craftproxy.mod.client.managers.CapeManager;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WingsLayer.class)
public abstract class WingsLayerMixin {

    @Inject(method = "getPlayerElytraTexture", at = @At("HEAD"), cancellable = true)
    private static void craftproxy$useCustomCape(HumanoidRenderState state, CallbackInfoReturnable<Identifier> cir) {
        if (!(state instanceof AvatarRenderState avatarState) || !avatarState.showCape) return;
        Identifier cape = CapeManager.getCapeFor(avatarState.id);
        if (cape != null) cir.setReturnValue(cape);
    }
}
//?}
