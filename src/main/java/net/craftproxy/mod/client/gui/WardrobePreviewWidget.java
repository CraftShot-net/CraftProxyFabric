package net.craftproxy.mod.client.gui;

import net.craftproxy.mod.client.compat.SkinData;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
//? if >=1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
//?}
//? if >=26.2 {
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?} else if >=1.21.9 {
/*import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
*///?} else {
/*import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
*///?}

import java.util.function.Supplier;

public class WardrobePreviewWidget extends AbstractWidget {
    //? if >=26.2 {
    private final Model.Simple wideModel;
    private final Model.Simple slimModel;
    private final Model.Simple capeModel;
    //?} else if >=1.21.9 {
    /*private final PlayerModel wideModel;
    private final PlayerModel slimModel;
    private final PlayerModel capeModel;
    *///?} else {
    /*private final PlayerModel<?> wideModel;
    private final PlayerModel<?> slimModel;
    private final ModelPart capeModel;
    *///?}
    private final Supplier<SkinData> skinSupplier;
    private final Supplier<Identifier> capeSupplier;
    private float rotationX = -5.0F;
    private float rotationY = 30.0F;

    public WardrobePreviewWidget(
            int width,
            int height,
            EntityModelSet modelSet,
            Supplier<SkinData> skinSupplier,
            Supplier<Identifier> capeSupplier
    ) {
        super(0, 0, width, height, CommonComponents.EMPTY);
        //? if >=26.2 {
        this.wideModel = new Model.Simple(modelSet.bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent);
        this.slimModel = new Model.Simple(modelSet.bakeLayer(ModelLayers.PLAYER_SLIM), RenderTypes::entityTranslucent);
        //?} else if >=1.21.9 {
        /*this.wideModel = new PlayerModel(modelSet.bakeLayer(ModelLayers.PLAYER), false);
        this.slimModel = new PlayerModel(modelSet.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        *///?} else {
        /*this.wideModel = new PlayerModel<>(modelSet.bakeLayer(ModelLayers.PLAYER), false);
        this.slimModel = new PlayerModel<>(modelSet.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        *///?}
        this.capeModel = createPreviewCapeModel();
        this.skinSupplier = skinSupplier;
        this.capeSupplier = capeSupplier;
    }

    private static void addPreviewCape(PartDefinition body) {
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
    }

    //? if >=26.2 {
    private static Model.Simple createPreviewCapeModel() {
        MeshDefinition mesh = new MeshDefinition();
        addPreviewCape(mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO));
        LayerDefinition layer = LayerDefinition.create(mesh, 64, 64);
        return new Model.Simple(layer.bakeRoot(), RenderTypes::entitySolid);
    }
    //?} else if >=1.21.9 {
    /*// The GUI skin renderer only accepts a PlayerModel here, so the cape is attached to a
    // full player model whose own cubes are all skipped.
    private static PlayerModel createPreviewCapeModel() {
        MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
        addPreviewCape(mesh.getRoot().getChild("body"));
        ModelPart root = LayerDefinition.create(mesh, 64, 64).bakeRoot();
        root.getAllParts().forEach(part -> part.skipDraw = true);
        root.getChild("body").getChild("cape").skipDraw = false;
        return new PlayerModel(root, false);
    }
    *///?} else {
    /*private static ModelPart createPreviewCapeModel() {
        MeshDefinition mesh = new MeshDefinition();
        addPreviewCape(mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO));
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }
    *///?}

    //? if >=26.1 {
    @Override
    protected void extractWidgetRenderState(
            final GuiGraphicsExtractor graphics,
            final int mouseX,
            final int mouseY,
            final float delta
    ) {
    //?} else {
    /*@Override
    protected void renderWidget(
            final GuiGraphics graphics,
            final int mouseX,
            final int mouseY,
            final float delta
    ) {
    *///?}
        float scale = 0.97F * (float) this.getHeight() / 2.125F;
        float pivotY = -1.0625F;
        SkinData skin = this.skinSupplier.get();
        if (skin == null) return;

        //? if >=26.2 {
        Model.Simple playerModel = skin.slim() ? this.slimModel : this.wideModel;
        //?} else if >=1.21.9 {
        /*PlayerModel playerModel = skin.slim() ? this.slimModel : this.wideModel;
        *///?} else {
        /*PlayerModel<?> playerModel = skin.slim() ? this.slimModel : this.wideModel;
        *///?}

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
            renderBody(graphics, playerModel, skin.texture(), scale, pivotY);
        } else {
            // Back view: Cape is IN FRONT OF player's back. Draw Body first, then Cape on top!
            renderBody(graphics, playerModel, skin.texture(), scale, pivotY);
            if (capeTexture != null) {
                renderCape(graphics, capeTexture, scale, pivotY);
            }
        }
    }

    //? if >=26.2 {
    private void renderBody(GuiGraphicsExtractor graphics, Model.Simple model, Identifier texture, float scale, float pivotY) {
        graphics.skin(model, texture, scale, this.rotationX, this.rotationY, pivotY,
                this.getX(), this.getY(), this.getRight(), this.getBottom());
    }

    private void renderCape(GuiGraphicsExtractor graphics, Identifier texture, float scale, float pivotY) {
        graphics.skin(this.capeModel, texture, scale, this.rotationX, this.rotationY, pivotY,
                this.getX(), this.getY(), this.getRight(), this.getBottom());
    }
    //?} else if >=26.1 {
    /*private void renderBody(GuiGraphicsExtractor graphics, PlayerModel model, Identifier texture, float scale, float pivotY) {
        graphics.skin(model, texture, scale, this.rotationX, this.rotationY, pivotY,
                this.getX(), this.getY(), this.getRight(), this.getBottom());
    }

    private void renderCape(GuiGraphicsExtractor graphics, Identifier texture, float scale, float pivotY) {
        graphics.skin(this.capeModel, texture, scale, this.rotationX, this.rotationY, pivotY,
                this.getX(), this.getY(), this.getRight(), this.getBottom());
    }
    *///?} else if >=1.21.9 {
    /*private void renderBody(GuiGraphics graphics, PlayerModel model, Identifier texture, float scale, float pivotY) {
        graphics.submitSkinRenderState(model, texture, scale, this.rotationX, this.rotationY, pivotY,
                this.getX(), this.getY(), this.getRight(), this.getBottom());
    }

    private void renderCape(GuiGraphics graphics, Identifier texture, float scale, float pivotY) {
        graphics.submitSkinRenderState(this.capeModel, texture, scale, this.rotationX, this.rotationY, pivotY,
                this.getX(), this.getY(), this.getRight(), this.getBottom());
    }
    *///?} else {
    /*// Older versions have no GUI skin renderer: draw the models directly into the GUI pose stack.
    // The depth buffer sorts body and cape, so the draw order above does not matter here.
    private void renderBody(GuiGraphics graphics, PlayerModel<?> model, Identifier texture, float scale, float pivotY) {
        PoseStack pose = beginModel(graphics, scale);
        MultiBufferSource.BufferSource buffers = graphics.bufferSource();
        renderLegacyModel(model, pose, buffers.getBuffer(model.renderType(texture)));
        endModel(graphics);
    }

    private void renderCape(GuiGraphics graphics, Identifier texture, float scale, float pivotY) {
        PoseStack pose = beginModel(graphics, scale);
        this.capeModel.render(pose, graphics.bufferSource().getBuffer(RenderType.entitySolid(texture)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        endModel(graphics);
    }

    private PoseStack beginModel(GuiGraphics graphics, float scale) {
        graphics.flush();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(this.getX() + this.getWidth() / 2.0F, this.getY() + this.getHeight() / 2.0F, 150.0F);
        pose.scale(scale, scale, -scale);
        pose.mulPose(Axis.XP.rotationDegrees(this.rotationX));
        pose.mulPose(Axis.YP.rotationDegrees(-this.rotationY));
        // The player model spans y = -0.5 (top of head) to 1.5 (feet); center it.
        pose.translate(0.0F, -0.5F, 0.0F);
        Lighting.setupForEntityInInventory();
        return pose;
    }

    private void endModel(GuiGraphics graphics) {
        graphics.flush();
        Lighting.setupFor3DItems();
        graphics.pose().popPose();
    }
    *///?}

    //? if >=1.21 && <1.21.9 {
    /*private static void renderLegacyModel(PlayerModel<?> model, PoseStack pose, VertexConsumer consumer) {
        model.renderToBuffer(pose, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    }
    *///?} else if <1.21 {
    /*private static void renderLegacyModel(PlayerModel<?> model, PoseStack pose, VertexConsumer consumer) {
        model.renderToBuffer(pose, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
    }
    *///?}

    //? if >=1.21.9 {
    @Override
    protected void onDrag(final MouseButtonEvent event, final double dx, final double dy) {
        rotate(dx, dy);
    }
    //?} else {
    /*@Override
    protected void onDrag(final double mouseX, final double mouseY, final double dx, final double dy) {
        rotate(dx, dy);
    }
    *///?}

    private void rotate(double dx, double dy) {
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
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        return null;
    }
}
