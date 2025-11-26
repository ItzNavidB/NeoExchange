package com.badiei.neoexchange.blocks.entity.renderer;

import com.badiei.neoexchange.blocks.entity.NeoPlateEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class NeoPlateEntityRenderer implements BlockEntityRenderer<NeoPlateEntity, NeoPlateEntityRendererState> {
    private final ItemModelResolver itemModelResolver;

    public NeoPlateEntityRenderer(BlockEntityRendererProvider.Context context) {
        itemModelResolver = context.itemModelResolver();
    }

    @Override
    public NeoPlateEntityRendererState createRenderState() {
        return new NeoPlateEntityRendererState();
    }

    @Override
    public void extractRenderState(NeoPlateEntity blockEntity, NeoPlateEntityRendererState renderState, float partialTick, Vec3 cameraPosition, @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, renderState, partialTick, cameraPosition, breakProgress);

        renderState.lightPosition = blockEntity.getBlockPos();
        renderState.blockEntityLevel = blockEntity.getLevel();
        renderState.rotation = blockEntity.getRenderingRotation();


        itemModelResolver.updateForTopItem(renderState.itemStackRenderState,
                blockEntity.inventory.getStackInSlot(0), ItemDisplayContext.FIXED, blockEntity.getLevel(), null, 0);
    }

    @Override
    public void submit(NeoPlateEntityRendererState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        poseStack.pushPose();

        poseStack.translate(0.5f, 0.2f, 0.5f);
        poseStack.scale(0.5f, 0.5f, 0.5f);
        //poseStack.rotateAround(Axis.YP.rotationDegrees(renderState.rotation), 0, 0, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(renderState.rotation).rotateXYZ((float) Math.toRadians(90), 0 ,0));
        renderState.itemStackRenderState.submit(poseStack, submitNodeCollector, getLightLevel(renderState.blockEntityLevel,
                renderState.lightPosition), OverlayTexture.NO_OVERLAY, 0);

        poseStack.popPose();
    }

    private int getLightLevel(Level level, BlockPos pos) {
        int bLight = level.getBrightness(LightLayer.BLOCK, pos);
        int sLight = level.getBrightness(LightLayer.SKY, pos);
        return LightTexture.pack(bLight, sLight);
    }
}
