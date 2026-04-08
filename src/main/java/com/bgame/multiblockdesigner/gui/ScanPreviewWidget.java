package com.bgame.multiblockdesigner.gui;

import com.bgame.multiblockdesigner.definition.BlockRole;
import com.bgame.multiblockdesigner.definition.ScannedBlock;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * A self-contained 3D preview widget for scanned blocks.
 * Ported from SchematicPreviewWidget (gtceuterminal).
 *
 * Renders each block with a color overlay based on its {BlockRole}.
 * Supports drag-to-rotate and scroll-to-zoom.
 */
public class ScanPreviewWidget {

    // Layout
    private final int x, y, width, height;

    // View state
    private float rotationX = 30.0F;
    private float rotationY = 45.0F;
    private float zoom      = 1.0F;

    private boolean isDragging = false;
    private double  lastMouseX = 0;
    private double  lastMouseY = 0;

    private static final float MIN_ZOOM  = 0.2F;
    private static final float MAX_ZOOM  = 4.0F;
    private static final float ZOOM_STEP = 0.15F;

    // Data & cache
    private List<BlockTypeGroup> groups = Collections.emptyList();

    private record BlockEntry(BlockPos pos, BlockState state, BlockRole role) {}

    private final List<BlockEntry>      renderCache  = new ArrayList<>();
    private       PreviewLevel          previewLevel = null;
    private       BlockPos              cachedMin    = BlockPos.ZERO;
    private       BlockPos              cachedSize   = BlockPos.ZERO;
    private       boolean               needsRebuild = true;

    // Role overlay colors  (ARGB, semi-transparent)
    private static int roleColor(BlockRole role) {
        return switch (role) {
            case UNKNOWN      -> 0x00000000; // transparent
            case CASING       -> 0x00000000; // transparent
            case ITEM_INPUT   -> 0x884466FF; // blue
            case ITEM_OUTPUT  -> 0x8844AAFF; // lighter blue
            case FLUID_INPUT  -> 0x8844FF88; // green
            case FLUID_OUTPUT -> 0x8888FF44; // yellow-green
            case ENERGY_INPUT -> 0x88FFCC00; // gold
            case MUFFLER      -> 0x88FF8844; // orange
            case MAINTENANCE  -> 0x88AA44FF; // purple
            case CONTROLLER   -> 0x88FF4444; // red
        };
    }

    // Fake level for block rendering context
    private static class PreviewLevel implements BlockAndTintGetter {
        private final Map<BlockPos, BlockState> blocks;

        PreviewLevel(Map<BlockPos, BlockState> blocks) {
            this.blocks = blocks;
        }

        @Override public @NotNull BlockState getBlockState(@NotNull BlockPos pos) {
            BlockState s = blocks.get(pos);
            return s != null ? s : Blocks.AIR.defaultBlockState();
        }
        @Override public @NotNull FluidState getFluidState(@NotNull BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }
        @Override public BlockEntity getBlockEntity(@NotNull BlockPos pos) { return null; }
        @Override public int getHeight()         { return 256; }
        @Override public int getMinBuildHeight() { return -64; }
        @Override public float getShade(@NotNull Direction direction, boolean shade) { return 1.0F; }
        @Override public int getBlockTint(@NotNull BlockPos pos, @NotNull ColorResolver r) { return 0xFFFFFFFF; }
        @Override public @NotNull LevelLightEngine getLightEngine() {
            return Objects.requireNonNull(Minecraft.getInstance().level).getLightEngine();
        }
    }

    // Constructor
    public ScanPreviewWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width  = width;
        this.height = height;
    }

    // Data update
    public void setGroups(List<BlockTypeGroup> groups) {
        this.groups      = groups;
        this.needsRebuild = true;
    }

    // Call after role assignments change so the overlay colors update
    public void markDirty() {
        this.needsRebuild = true;
    }

    private void rebuildCache() {
        renderCache.clear();

        if (groups.isEmpty()) {
            cachedMin  = BlockPos.ZERO;
            cachedSize = BlockPos.ZERO;
            needsRebuild = false;
            return;
        }

        // Collect all blocks with their roles
        List<ScannedBlock> allBlocks = new ArrayList<>();
        for (BlockTypeGroup g : groups) {
            allBlocks.addAll(g.blocks);
        }

        if (allBlocks.isEmpty()) {
            needsRebuild = false;
            return;
        }

        // Find bounding box (absolute positions)
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (ScannedBlock sb : allBlocks) {
            minX = Math.min(minX, sb.pos.getX()); minY = Math.min(minY, sb.pos.getY()); minZ = Math.min(minZ, sb.pos.getZ());
            maxX = Math.max(maxX, sb.pos.getX()); maxY = Math.max(maxY, sb.pos.getY()); maxZ = Math.max(maxZ, sb.pos.getZ());
        }

        BlockPos origin = new BlockPos(minX, minY, minZ);
        cachedMin  = BlockPos.ZERO;
        cachedSize = new BlockPos(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);

        Map<BlockPos, BlockState> levelMap = new HashMap<>();

        // Sort back-to-front for rendering (Y then Z then X)
        allBlocks.sort(Comparator.comparingInt((ScannedBlock sb) -> sb.pos.getY())
                .thenComparingInt(sb -> sb.pos.getZ())
                .thenComparingInt(sb -> sb.pos.getX()));

        for (ScannedBlock sb : allBlocks) {
            if (sb.state.isAir()) continue;
            // Normalize to origin-relative coords
            BlockPos rel = sb.pos.subtract(origin);
            renderCache.add(new BlockEntry(rel, sb.state, sb.role));
            levelMap.put(rel, sb.state);
        }

        previewLevel = new PreviewLevel(levelMap);
        needsRebuild = false;
    }

    // Render
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        if (needsRebuild) rebuildCache();

        // Background
        gfx.fill(x, y, x + width, y + height, 0xDD111111);
        // Border
        gfx.fill(x, y,             x + width, y + 1,          0xFF555555);
        gfx.fill(x, y + height - 1, x + width, y + height,    0xFF555555);
        gfx.fill(x, y,             x + 1,     y + height,     0xFF555555);
        gfx.fill(x + width - 1, y, x + width, y + height,     0xFF555555);

        if (renderCache.isEmpty()) {
            renderEmptyMessage(gfx);
            return;
        }

        render3D(gfx, partialTick);
        renderLegend(gfx);
        renderHint(gfx);
    }

    private void render3D(GuiGraphics gfx, float partialTick) {
        setupScissor();

        PoseStack poseStack = gfx.pose();
        poseStack.pushPose();

        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            Lighting.setupForFlatItems();

            float centerX = x + width  / 2.0F;
            float centerY = y + height / 2.0F;
            poseStack.translate(centerX, centerY, 400.0F);

            float maxDim    = Math.max(cachedSize.getX(), Math.max(cachedSize.getY(), cachedSize.getZ()));
            float baseScale = (Math.min(width, height) * 0.45F) / Math.max(maxDim, 1);
            float finalScale = baseScale * zoom;

            poseStack.scale(finalScale, finalScale, finalScale);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(rotationX));
            poseStack.mulPose(Axis.YP.rotationDegrees(rotationY));

            poseStack.translate(
                -(cachedMin.getX() + cachedSize.getX() / 2.0F),
                -(cachedMin.getY() + cachedSize.getY() / 2.0F),
                -(cachedMin.getZ() + cachedSize.getZ() / 2.0F)
            );

            renderBlocks(poseStack);

        } finally {
            Lighting.setupForFlatItems();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.disableScissor();
            poseStack.popPose();
        }
    }

    private void renderBlocks(PoseStack poseStack) {
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        for (BlockEntry entry : renderCache) {
            poseStack.pushPose();
            poseStack.translate(entry.pos().getX(), entry.pos().getY(), entry.pos().getZ());

            // Apply role color tint via shader color
            applyRoleTint(entry.role());

            try {
                blockRenderer.renderSingleBlock(
                    entry.state(), poseStack, bufferSource,
                    15728880, OverlayTexture.NO_OVERLAY
                );
            } catch (Exception ignored) {}

            poseStack.popPose();
        }

        bufferSource.endBatch();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void applyRoleTint(BlockRole role) {
        int color = roleColor(role);
        if (color == 0) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            return;
        }
        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >>  8) & 0xFF) / 255f;
        float b = ( color        & 0xFF) / 255f;
        // Mix: lerp between white (1,1,1) and role color by alpha
        RenderSystem.setShaderColor(
            1f - a + r * a,
            1f - a + g * a,
            1f - a + b * a,
            1f
        );
    }

    private void renderLegend(GuiGraphics gfx) {
        Minecraft mc = Minecraft.getInstance();
        int lx = x + 4;
        int ly = y + height - 6;
        int dotSize = 4;

        // Only show roles that are actually used
        Set<BlockRole> usedRoles = new LinkedHashSet<>();
        for (BlockTypeGroup g : groups) {
            if (g.role != BlockRole.UNKNOWN && g.role != BlockRole.CASING) {
                usedRoles.add(g.role);
            }
        }

        for (BlockRole role : usedRoles) {
            int color = roleColor(role) | 0xFF000000; // opaque for legend dot
            ly -= (dotSize + 2);
            gfx.fill(lx, ly, lx + dotSize, ly + dotSize, color);
            gfx.drawString(mc.font, roleName(role), lx + dotSize + 3, ly - 1, 0xFFCCCCCC, false);
        }
    }

    private void renderHint(GuiGraphics gfx) {
        Minecraft mc = Minecraft.getInstance();
        String hint = "Drag: rotate  |  Scroll: zoom  |  RMB: reset";
        gfx.drawString(mc.font, hint, x + 4, y + 4, 0xFF666666, false);
    }

    private void renderEmptyMessage(GuiGraphics gfx) {
        Minecraft mc = Minecraft.getInstance();
        String msg = "No blocks scanned";
        int tw = mc.font.width(msg);
        gfx.drawString(mc.font, msg, x + (width - tw) / 2, y + height / 2 - 4, 0xFF666666, false);
    }

    private void setupScissor() {
        Minecraft mc = Minecraft.getInstance();
        double scale    = mc.getWindow().getGuiScale();
        int screenH     = mc.getWindow().getHeight();
        int sx = (int)((x) * scale);
        int sy = (int)(screenH - (y + height) * scale);
        int sw = (int)(width  * scale);
        int sh = (int)(height * scale);
        RenderSystem.enableScissor(Math.max(0, sx), Math.max(0, sy), sw, sh);
    }

    // Mouse input — call from the parent Screen
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOver(mouseX, mouseY)) return false;
        if (button == 0) {
            isDragging = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        if (button == 1) {
            resetView();
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) isDragging = false;
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (!isDragging) return false;
        rotationY -= (float)(mouseX - lastMouseX) * 0.5F;
        rotationX += (float)(mouseY - lastMouseY) * 0.5F;
        rotationX  = Math.max(-80F, Math.min(80F, rotationX));
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isOver(mouseX, mouseY)) return false;
        zoom += (float) delta * ZOOM_STEP;
        zoom  = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
        return true;
    }

    private void resetView() {
        rotationX = 30F;
        rotationY = 45F;
        zoom      = 1F;
    }

    private boolean isOver(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    // Utility
    private static String roleName(BlockRole role) {
        return switch (role) {
            case UNKNOWN      -> "None";
            case CASING       -> "Casing";
            case ITEM_INPUT   -> "Item In";
            case ITEM_OUTPUT  -> "Item Out";
            case FLUID_INPUT  -> "Fluid In";
            case FLUID_OUTPUT -> "Fluid Out";
            case ENERGY_INPUT -> "Energy";
            case MUFFLER      -> "Muffler";
            case MAINTENANCE  -> "Maintenance";
            case CONTROLLER   -> "Controller";
        };
    }
}