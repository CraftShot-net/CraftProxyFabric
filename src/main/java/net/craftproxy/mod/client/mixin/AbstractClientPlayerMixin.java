package net.craftproxy.mod.client.mixin;

// Before 1.21.9 CraftProxy capes are shown by handing their texture to the vanilla cape
// (and elytra) layer. Newer versions use CustomCapeLayer instead.

//? if >=1.20.2 && <1.21.9 {
/*import net.craftproxy.mod.client.managers.CapeManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void craftproxy$useCustomCape(CallbackInfoReturnable<PlayerSkin> cir) {
        Identifier cape = CapeManager.getCapeForUUID(((Entity) (Object) this).getUUID());
        if (cape == null) return;
        PlayerSkin skin = cir.getReturnValue();
        cir.setReturnValue(new PlayerSkin(skin.texture(), skin.textureUrl(), cape, cape, skin.model(), skin.secure()));
    }
}
*///?}

//? if <1.20.2 {
/*import net.craftproxy.mod.client.managers.CapeManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(method = {"getCloakTextureLocation", "getElytraTextureLocation"}, at = @At("RETURN"), cancellable = true)
    private void craftproxy$useCustomCape(CallbackInfoReturnable<Identifier> cir) {
        Identifier cape = CapeManager.getCapeForUUID(((Entity) (Object) this).getUUID());
        if (cape != null) cir.setReturnValue(cape);
    }

    @Inject(method = {"isCapeLoaded", "isElytraLoaded"}, at = @At("RETURN"), cancellable = true)
    private void craftproxy$customCapeLoaded(CallbackInfoReturnable<Boolean> cir) {
        if (CapeManager.getCapeForUUID(((Entity) (Object) this).getUUID()) != null) cir.setReturnValue(true);
    }
}
*///?}
