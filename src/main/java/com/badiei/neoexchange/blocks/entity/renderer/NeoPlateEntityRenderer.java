package com.badiei.neoexchange.blocks.entity.renderer;

import com.badiei.neoexchange.blocks.entity.NeoPlateEntity;
import com.badiei.neoexchange.config.ClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
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

    /**
     * Extracts the data we need from the block entity to render it.
     * This runs every frame and prepares the render state.
     *
     * @param blockEntity The actual block entity in the world
     * @param renderState The state object we're filling with render data
     * @param partialTick How far between ticks we are (0.0 to 1.0) - used for smooth animation
     * @param cameraPosition Where the camera is
     * @param breakProgress Block breaking overlay info (can be null)
     */
    @Override
    public void extractRenderState(NeoPlateEntity blockEntity, NeoPlateEntityRendererState renderState,
                                   float partialTick, Vec3 cameraPosition,
                                   @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, renderState, partialTick, cameraPosition, breakProgress);

        renderState.lightPosition = blockEntity.getBlockPos();
        renderState.blockEntityLevel = blockEntity.getLevel();

        // Get animation values - these are FPS-independent now!
        renderState.rotation = blockEntity.getRenderingRotation();
        renderState.yLocation = blockEntity.getYLocation(); // Fixed method name

        // Update the item model
        itemModelResolver.updateForTopItem(
                renderState.itemStackRenderState,
                blockEntity.inventory.getStackInSlot(0),
                ItemDisplayContext.FIXED,
                blockEntity.getLevel(),
                null,
                0
        );
    }

    /**
     * Actually renders the item in 3D space.
     * This is where we position, scale, and rotate the item.
     *
     * @param renderState The state we prepared in extractRenderState
     * @param poseStack The transformation stack (like layers in Photoshop)
     * @param submitNodeCollector Collects rendering operations
     * @param cameraRenderState Camera information
     */
    @Override
    public void submit(NeoPlateEntityRendererState renderState, PoseStack poseStack,
                       SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        // Start a new transformation layer
        poseStack.pushPose();

        // POSITION: Center the item in the block and set height
        // 0.5f, 0.5f = center of block horizontally
        // yLocation/4 + 0.6f = vertical position (with bobbing animation)
        // Check if animations are enabled in config
        float yOffset = ClientConfig.ENABLE_ITEM_ANIMATIONS.get() ? renderState.yLocation / 4 + 0.6f : 0.6f;
        poseStack.translate(0.5f, yOffset, 0.5f);

        // SCALE: Make the item half size (0.5 = 50%)
        poseStack.scale(0.5f, 0.5f, 0.5f);

        // ROTATION: Spin the item around the Y axis (vertical spin)
        // Using just Y rotation now - the item spins flat
        // Only rotate if animations are enabled
        if (ClientConfig.ENABLE_ITEM_ANIMATIONS.get()) {
            poseStack.mulPose(Axis.YP.rotationDegrees(renderState.rotation));
        }

        // Submit the item for rendering with proper lighting
        renderState.itemStackRenderState.submit(
                poseStack,
                submitNodeCollector,
                getLightLevel(renderState.blockEntityLevel, renderState.lightPosition),  // Dynamic lighting
                OverlayTexture.NO_OVERLAY,  // No damage overlay
                0  // Flags
        );

        // Restore the transformation stack (clean up after ourselves)
        poseStack.popPose();
    }

    /**
     * Calculate the light level at the block's position.
     * Combines block light (torches, etc.) and sky light (sun/moon).
     *
     * @param level The world
     * @param pos The position to check
     * @return Packed light value for rendering
     */
    private int getLightLevel(Level level, BlockPos pos) {
        int bLight = level.getBrightness(LightLayer.BLOCK, pos);  // Torch/block light
        int sLight = level.getBrightness(LightLayer.SKY, pos);    // Sunlight/moonlight
        return LightTexture.pack(bLight, sLight);                 // Combine them
    }
}