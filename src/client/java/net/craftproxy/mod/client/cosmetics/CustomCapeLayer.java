package net.craftproxy.mod.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import net.craftproxy.mod.client.managers.CapeManager;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.jspecify.annotations.NonNull;

public class CustomCapeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private final PlayerCapeModel model;
    private final EquipmentAssetManager equipmentAssets;

    public CustomCapeLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityModelSet modelSet, EquipmentAssetManager equipmentAssets) {
        super(renderer);
        this.model = new PlayerCapeModel(modelSet.bakeLayer(ModelLayers.PLAYER_CAPE));
        this.equipmentAssets = equipmentAssets;
    }

    private boolean hasLayer(ItemStack itemStack) {
        Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);
        if (equippable != null && equippable.assetId().isPresent()) {
            EquipmentClientInfo equipmentClientInfo = this.equipmentAssets.get(equippable.assetId().get());
            return !equipmentClientInfo.getLayers(EquipmentClientInfo.LayerType.WINGS).isEmpty();
        }
        return false;
    }

    @Override
    public void submit(@NonNull PoseStack poseStack, @NonNull SubmitNodeCollector submitNodeCollector, int lightCoords,
                       AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible || !state.showCape) return;
        if (hasLayer(state.chestEquipment)) return;
        Identifier capeTexture = CapeManager.getCapeFor(state.id);
        if (capeTexture == null) return;

        poseStack.pushPose();
        submitNodeCollector.submitModel(this.model, state, poseStack,
                RenderTypes.entitySolid(capeTexture), lightCoords, OverlayTexture.NO_OVERLAY,
                state.outlineColor, null);
        poseStack.popPose();
    }
}