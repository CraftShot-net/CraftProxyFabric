package net.craftproxy.mod.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public class WardrobePreviewWidget extends AbstractWidget {
    private final Model.Simple wideModel;
    private final Model.Simple slimModel;
    private final Model.Simple capeModel;
    private final Supplier<PlayerSkin> skinSupplier;
    private final Supplier<Identifier> capeSupplier;
    private float rotationX = -5.0F;
    private float rotationY = 30.0F;

    public WardrobePreviewWidget(
            int width,
            int height,
            EntityModelSet modelSet,
            Supplier<PlayerSkin> skinSupplier,
            Supplier<Identifier> capeSupplier
    ) {
        super(0, 0, width, height, CommonComponents.EMPTY);
        this.wideModel = new Model.Simple(modelSet.bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent);
        this.slimModel = new Model.Simple(modelSet.bakeLayer(ModelLayers.PLAYER_SLIM), RenderTypes::entityTranslucent);
        this.capeModel = createPreviewCapeModel();
        this.skinSupplier = skinSupplier;
        this.capeSupplier = capeSupplier;
    }

    private static Model.Simple createPreviewCapeModel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        // Player body back is at Z = +2.0F.
        // BoxUV: texOffs(0, 0), box width 10, height 16, depth 1.
        // The outer emblem face is at u: 1..10, v: 1..16 (local Z-).
        // By rotating 180 degrees around Y (Math.PI), local Z- points directly towards world Z+ (away from player's back).
        // Offset at Z = +2.05F so local -Z becomes world +Z (hanging from Z = 2.05F to 3.05F behind the back).
        // Apply slight natural hanging tilt backwards (-0.08 radians ~ 4.5 degrees).
        body.addOrReplaceChild(
                "cape",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, 0.0F, -1.0F, 10.0F, 16.0F, 1.0F, CubeDeformation.NONE, 1.0F, 0.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 2.05F, -0.08F, (float) Math.PI, 0.0F)
        );
        LayerDefinition layer = LayerDefinition.create(mesh, 64, 64);
        return new Model.Simple(layer.bakeRoot(), RenderTypes::entitySolid);
    }

    @Override
    protected void extractWidgetRenderState(
            final @NonNull GuiGraphicsExtractor graphics,
            final int mouseX,
            final int mouseY,
            final float delta
    ) {
        float scale = 0.97F * (float) this.getHeight() / 2.125F;
        float pivotY = -1.0625F;
        PlayerSkin skin = this.skinSupplier.get();
        if (skin == null) return;

        Model.Simple playerModel = (skin.model() == PlayerModelType.SLIM) ? this.slimModel : this.wideModel;

        // Determine depth order:
        // In GuiSkinRenderer: Axis.YP.rotationDegrees(-rotationY).
        // Angle 0 = front facing camera (chest in front, cape behind).
        // Angle 180 = back facing camera (cape in front, chest behind).
        float normRotY = ((this.rotationY % 360.0F) + 360.0F) % 360.0F;
        boolean facingFront = (normRotY < 90.0F || normRotY > 270.0F);

        Identifier capeTexture = this.capeSupplier.get();

        if (facingFront) {
            // Front view: Cape is BEHIND player. Draw Cape first, then Body on top!
            if (capeTexture != null) {
                renderCape(graphics, capeTexture, scale, pivotY);
            }
            renderBody(graphics, playerModel, skin.body().texturePath(), scale, pivotY);
        } else {
            // Back view: Cape is IN FRONT OF player's back. Draw Body first, then Cape on top!
            renderBody(graphics, playerModel, skin.body().texturePath(), scale, pivotY);
            if (capeTexture != null) {
                renderCape(graphics, capeTexture, scale, pivotY);
            }
        }
    }

    private void renderBody(GuiGraphicsExtractor graphics, Model.Simple model, Identifier texture, float scale, float pivotY) {
        graphics.skin(
                model,
                texture,
                scale,
                this.rotationX,
                this.rotationY,
                pivotY,
                this.getX(),
                this.getY(),
                this.getRight(),
                this.getBottom()
        );
    }

    private void renderCape(GuiGraphicsExtractor graphics, Identifier texture, float scale, float pivotY) {
        graphics.skin(
                this.capeModel,
                texture,
                scale,
                this.rotationX,
                this.rotationY,
                pivotY,
                this.getX(),
                this.getY(),
                this.getRight(),
                this.getBottom()
        );
    }

    @Override
    protected void onDrag(final MouseButtonEvent event, final double dx, final double dy) {
        this.rotationX = Mth.clamp(this.rotationX - (float) dy * 2.5F, -50.0F, 50.0F);
        this.rotationY = this.rotationY + (float) dx * 2.5F;
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    @Override
    public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent event) {
        return null;
    }
}
