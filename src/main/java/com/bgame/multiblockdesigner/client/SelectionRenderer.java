package com.bgame.multiblockdesigner.client;

import com.bgame.multiblockdesigner.item.CopyToolItem;
import com.bgame.multiblockdesigner.item.DesignerWandItem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import org.joml.Matrix4f;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Renders the selection box and corner marker when using the Designer Wand or Copy Tool.
 * Only active when holding the wand/tool, and corners are being set.
 */
@OnlyIn(Dist.CLIENT)
public class SelectionRenderer {

    // Fill color: cyan, low alpha
    private static final float FILL_R = 0.2f, FILL_G = 0.8f, FILL_B = 1.0f, FILL_A = 0.15f;
    // Outline color: bright cyan, fully opaque
    private static final float LINE_R = 0.2f, LINE_G = 0.9f, LINE_B = 1.0f, LINE_A = 1.0f;
    // Corner A marker color: yellow
    private static final float MARK_R = 1.0f, MARK_G = 0.9f, MARK_B = 0.0f, MARK_A = 1.0f;

    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Only active when holding the wand or copy tool
        ItemStack held = mc.player.getMainHandItem();
        boolean isWand = held.getItem() instanceof DesignerWandItem;
        boolean isCopyTool = held.getItem() instanceof CopyToolItem;

        if (!isWand && !isCopyTool) {
            held = mc.player.getOffhandItem();
            isWand = held.getItem() instanceof DesignerWandItem;
            isCopyTool = held.getItem() instanceof CopyToolItem;
            if (!isWand && !isCopyTool) return;
        }

        BlockPos cornerA = null;
        BlockPos cornerB = null;
        boolean showSelection = false;

        if (isWand) {
            // Only show when Corner A is set and scan is NOT done yet
            if (DesignerWandItem.hasCornerAPublic(held) && !DesignerWandItem.isScanDone(held)) {
                cornerA = DesignerWandItem.getCornerA(held);
                cornerB = getLookedAtBlock(mc);
                showSelection = true;
            }
        } else {
            cornerA = CopyToolItem.getPosFromStack(held, CopyToolItem.NBT_POS1);
            cornerB = CopyToolItem.getPosFromStack(held, CopyToolItem.NBT_POS2);
            showSelection = cornerA != null || cornerB != null;
        }

        if (!showSelection || (cornerA == null && cornerB == null)) return;

        Camera camera = event.getCamera();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(
                -camera.getPosition().x,
                -camera.getPosition().y,
                -camera.getPosition().z
        );

        if (isWand) {
            // Always draw Corner A marker for Wand
            if (cornerA != null) renderCornerMarker(poseStack, cornerA);

            if (cornerB != null && !cornerB.equals(cornerA)) {
                renderVolume(poseStack, cornerA, cornerB);
            }
        } else {
            // Copy tool rendering
            if (cornerA != null && cornerB != null) {
                renderVolume(poseStack, cornerA, cornerB);
            } else {
                BlockPos single = (cornerA != null) ? cornerA : cornerB;
                renderCornerMarker(poseStack, single);
            }
        }

        poseStack.popPose();
    }

    // Volume fill + outline
    private void renderVolume(PoseStack poseStack, BlockPos a, BlockPos b) {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX()) + 1;
        int maxY = Math.max(a.getY(), b.getY()) + 1;
        int maxZ = Math.max(a.getZ(), b.getZ()) + 1;

        AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

        renderFill(poseStack, box);
        renderOutline(poseStack, box);
    }

    private void renderFill(PoseStack poseStack, AABB box) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f mat = poseStack.last().pose();
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;

        // Bottom face (Y-)
        buf.vertex(mat, x0, y0, z0).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        buf.vertex(mat, x1, y0, z0).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        buf.vertex(mat, x1, y0, z1).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        buf.vertex(mat, x0, y0, z1).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        // Top face (Y+)
        buf.vertex(mat, x0, y1, z0).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        buf.vertex(mat, x0, y1, z1).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        buf.vertex(mat, x1, y1, z1).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        buf.vertex(mat, x1, y1, z0).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        // North face (Z-)
        buf.vertex(mat, x0, y0, z0).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        buf.vertex(mat, x0, y1, z0).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        buf.vertex(mat, x1, y1, z0).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        buf.vertex(mat, x1, y0, z0).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        // South face (Z+)
        buf.vertex(mat, x0, y0, z1).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        buf.vertex(mat, x1, y0, z1).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        buf.vertex(mat, x1, y1, z1).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        buf.vertex(mat, x0, y1, z1).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        // West face (X-)
        buf.vertex(mat, x0, y0, z0).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        buf.vertex(mat, x0, y0, z1).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        buf.vertex(mat, x0, y1, z1).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        buf.vertex(mat, x0, y1, z0).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        // East face (X+)
        buf.vertex(mat, x1, y0, z0).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        buf.vertex(mat, x1, y1, z0).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        buf.vertex(mat, x1, y1, z1).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();
        buf.vertex(mat, x1, y0, z1).color(FILL_R, FILL_G, FILL_B, FILL_A).endVertex();

        tesselator.end();

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void renderOutline(PoseStack poseStack, AABB box) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(2.0f);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.getBuilder();
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f mat = poseStack.last().pose();
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;

        // Bottom edges
        line(buf, mat, x0,y0,z0, x1,y0,z0);
        line(buf, mat, x1,y0,z0, x1,y0,z1);
        line(buf, mat, x1,y0,z1, x0,y0,z1);
        line(buf, mat, x0,y0,z1, x0,y0,z0);
        // Top edges
        line(buf, mat, x0,y1,z0, x1,y1,z0);
        line(buf, mat, x1,y1,z0, x1,y1,z1);
        line(buf, mat, x1,y1,z1, x0,y1,z1);
        line(buf, mat, x0,y1,z1, x0,y1,z0);
        // Vertical edges
        line(buf, mat, x0,y0,z0, x0,y1,z0);
        line(buf, mat, x1,y0,z0, x1,y1,z0);
        line(buf, mat, x1,y0,z1, x1,y1,z1);
        line(buf, mat, x0,y0,z1, x0,y1,z1);

        tesselator.end();

        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // Corner A marker — small yellow outline on the single block
    private void renderCornerMarker(PoseStack poseStack, BlockPos pos) {
        AABB box = new AABB(pos).inflate(0.002);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(2.5f);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.getBuilder();
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f mat = poseStack.last().pose();
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;

        line(buf, mat, x0,y0,z0, x1,y0,z0, MARK_R, MARK_G, MARK_B, MARK_A);
        line(buf, mat, x1,y0,z0, x1,y0,z1, MARK_R, MARK_G, MARK_B, MARK_A);
        line(buf, mat, x1,y0,z1, x0,y0,z1, MARK_R, MARK_G, MARK_B, MARK_A);
        line(buf, mat, x0,y0,z1, x0,y0,z0, MARK_R, MARK_G, MARK_B, MARK_A);
        line(buf, mat, x0,y1,z0, x1,y1,z0, MARK_R, MARK_G, MARK_B, MARK_A);
        line(buf, mat, x1,y1,z0, x1,y1,z1, MARK_R, MARK_G, MARK_B, MARK_A);
        line(buf, mat, x1,y1,z1, x0,y1,z1, MARK_R, MARK_G, MARK_B, MARK_A);
        line(buf, mat, x0,y1,z1, x0,y1,z0, MARK_R, MARK_G, MARK_B, MARK_A);
        line(buf, mat, x0,y0,z0, x0,y1,z0, MARK_R, MARK_G, MARK_B, MARK_A);
        line(buf, mat, x1,y0,z0, x1,y1,z0, MARK_R, MARK_G, MARK_B, MARK_A);
        line(buf, mat, x1,y0,z1, x1,y1,z1, MARK_R, MARK_G, MARK_B, MARK_A);
        line(buf, mat, x0,y0,z1, x0,y1,z1, MARK_R, MARK_G, MARK_B, MARK_A);

        tesselator.end();

        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // Helpers
    private static BlockPos getLookedAtBlock(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult blockHit
                && blockHit.getType() != HitResult.Type.MISS) {
            return blockHit.getBlockPos();
        }
        return null;
    }

    private static void line(BufferBuilder buf, Matrix4f mat,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1) {
        line(buf, mat, x0, y0, z0, x1, y1, z1, LINE_R, LINE_G, LINE_B, LINE_A);
    }

    private static void line(BufferBuilder buf, Matrix4f mat,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float r, float g, float b, float a) {
        buf.vertex(mat, x0, y0, z0).color(r, g, b, a).endVertex();
        buf.vertex(mat, x1, y1, z1).color(r, g, b, a).endVertex();
    }
}