package com.bgame.multiblockdesigner.gui;

import com.bgame.multiblockdesigner.definition.BlockRole;
import com.bgame.multiblockdesigner.definition.ScannedBlock;
import com.bgame.multiblockdesigner.item.DesignerWandItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// The screen for classifying scanned blocks into roles before saving a multiblock definition.
public class WandClassifyScreen extends Screen {

    private static final int W            = 800;
    private static final int H            = 260;
    private static final int LIST_W       = 155;
    private static final int PANEL_W      = 185;
    private static final int PREVIEW_X    = LIST_W + 6;
    private static final int PANEL_X      = W - PANEL_W - 4;
    private static final int PREVIEW_W    = PANEL_X - PREVIEW_X - 4;
    private static final int CONTENT_Y    = 22;
    private static final int CONTENT_H    = H - CONTENT_Y - 4;
    private static final int ENTRY_H      = 13;
    private static final int VISIBLE_ROWS = CONTENT_H / ENTRY_H;

    private static final int C_BG     = 0xFF222222;
    private static final int C_PANEL  = 0xFF1A1A1A;
    private static final int C_BORDER = 0xFF555555;
    private static final int C_SEL    = 0xFF2E5070;
    private static final int C_HOVER  = 0xFF333333;
    private static final int C_TEXT   = 0xFFDDDDDD;
    private static final int C_DIM    = 0xFF777777;
    private static final int C_ROLE   = 0xFFFFCC44;

    private final ItemStack            wand;
    private final List<BlockTypeGroup> groups;
    private int                        scrollOffset  = 0;
    @Nullable private BlockTypeGroup   selectedGroup = null;

    private ScanPreviewWidget    preview;
    private final List<Button>   roleButtons = new ArrayList<>();
    private Button               confirmButton;
    private Button               resetButton;
    private int gx, gy;

    public WandClassifyScreen(ItemStack wand) {
        super(Component.literal("Classify Blocks"));
        this.wand   = wand;
        this.groups = BlockTypeGroup.groupFrom(DesignerWandItem.loadScannedBlocks(wand));
    }

    @Override
    protected void init() {
        super.init();
        gx = (width  - W) / 2;
        gy = (height - H) / 2;

        preview = new ScanPreviewWidget(gx + PREVIEW_X, gy + CONTENT_Y, PREVIEW_W, CONTENT_H);
        preview.setGroups(groups);

        roleButtons.clear();
        BlockRole[] roles = BlockRole.values();
        int btnW   = 84;
        int btnH   = 15;
        int col0X  = gx + PANEL_X + 4;
        int col1X  = col0X + btnW + 3;
        int startY = gy + CONTENT_Y + 28;

        for (int i = 0; i < roles.length; i++) {
            BlockRole role = roles[i];
            int col = i % 2;
            int row = i / 2;
            int bx  = (col == 0) ? col0X : col1X;
            int by  = startY + row * (btnH + 3);
            Button btn = Button.builder(Component.literal(roleName(role)), b -> assignRole(role))
                    .pos(bx, by).size(btnW, btnH).build();
            roleButtons.add(btn);
            addRenderableWidget(btn);
        }

        confirmButton = Button.builder(Component.literal("Confirm & Save"), b -> confirmAndSave())
                .pos(col0X, gy + H - 24).size(btnW * 2 + 3 - 44, 18).build();
        addRenderableWidget(confirmButton);

        resetButton = Button.builder(Component.literal("↺ Reset"), b -> resetScan())
                .pos(col0X + btnW * 2 + 3 - 41, gy + H - 24).size(41, 18).build();
        addRenderableWidget(resetButton);

        updateButtonStates();
    }


    // Renders the whole screen, including the list, right panel, and 3D preview.
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx);

        gfx.fill(gx, gy, gx + W, gy + H, C_BG);
        drawBorder(gfx, gx, gy, W, H, C_BORDER);

        // Title bar
        gfx.fill(gx, gy, gx + W, gy + CONTENT_Y, 0xFF1A1A1A);
        gfx.drawString(font, "Classify Scanned Blocks", gx + 6, gy + 7, C_TEXT, false);
        String summary = groups.size() + " types  |  " + totalBlocks() + " blocks";
        gfx.drawString(font, summary, gx + W - font.width(summary) - 6, gy + 7, C_DIM, false);

        // Column dividers
        gfx.fill(gx + LIST_W + 3,  gy + CONTENT_Y, gx + LIST_W + 4,  gy + H, C_BORDER);
        gfx.fill(gx + PANEL_X - 3, gy + CONTENT_Y, gx + PANEL_X - 2, gy + H, C_BORDER);

        // Right panel bg
        gfx.fill(gx + PANEL_X, gy + CONTENT_Y, gx + W - 2, gy + H - 2, C_PANEL);

        renderList(gfx, mouseX, mouseY);
        renderPanel(gfx);

        // 3D preview
        preview.render(gfx, mouseX, mouseY, partialTick);

        // Buttons on top
        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void renderList(GuiGraphics gfx, int mouseX, int mouseY) {
        int lx = gx + 3;
        int ly = gy + CONTENT_Y + 2;
        List<Object> rows = buildRows();
        int end = Math.min(rows.size(), scrollOffset + VISIBLE_ROWS);

        for (int i = scrollOffset; i < end; i++) {
            Object row = rows.get(i);
            int ry = ly + (i - scrollOffset) * ENTRY_H;
            boolean hovered = mouseX >= lx && mouseX < lx + LIST_W - 4
                    && mouseY >= ry  && mouseY < ry + ENTRY_H;
            if (row instanceof BlockTypeGroup g)  renderGroupRow(gfx, g, lx, ry, hovered);
            else if (row instanceof ScannedBlock s) renderPosRow(gfx, s, lx, ry, hovered);
        }

        if (rows.size() > VISIBLE_ROWS) {
            int barH  = Math.max(10, CONTENT_H * VISIBLE_ROWS / rows.size());
            int barMaxY = CONTENT_H - barH;
            int barY  = scrollOffset * barMaxY / Math.max(1, rows.size() - VISIBLE_ROWS);
            gfx.fill(gx + LIST_W, gy + CONTENT_Y + barY, gx + LIST_W + 2, gy + CONTENT_Y + barY + barH, 0xFFAAAAAA);
        }
    }

    private void renderGroupRow(GuiGraphics gfx, BlockTypeGroup g, int x, int y, boolean hovered) {
        boolean sel = g == selectedGroup;
        if (sel)          gfx.fill(x, y, x + LIST_W - 4, y + ENTRY_H, C_SEL);
        else if (hovered) gfx.fill(x, y, x + LIST_W - 4, y + ENTRY_H, C_HOVER);

        gfx.drawString(font, g.expanded ? "v" : ">", x + 2, y + 2, C_DIM, false);

        String name = blockName(g.representativeState);
        String count = " x" + g.count();
        int maxW = LIST_W - 4 - font.width(count) - 14;
        while (font.width(name) > maxW && name.length() > 3)
            name = name.substring(0, name.length() - 1);

        gfx.drawString(font, name,  x + 12, y + 2, C_TEXT, false);
        gfx.drawString(font, count, x + 12 + font.width(name), y + 2, C_DIM, false);

        if (g.role != BlockRole.UNKNOWN) {
            gfx.fill(x + 3, y + ENTRY_H - 4, x + 7, y + ENTRY_H - 1, roleDotColor(g.role));
        }
    }

    private void renderPosRow(GuiGraphics gfx, ScannedBlock sb, int x, int y, boolean hovered) {
        if (hovered) gfx.fill(x, y, x + LIST_W - 4, y + ENTRY_H, C_HOVER);
        BlockPos p = sb.pos;
        gfx.drawString(font, "  (%d,%d,%d)".formatted(p.getX(), p.getY(), p.getZ()), x + 2, y + 2, C_DIM, false);
    }

    private void renderPanel(GuiGraphics gfx) {
        int px = gx + PANEL_X + 4;
        int py = gy + CONTENT_Y + 4;
        if (selectedGroup != null) {
            gfx.drawString(font, blockName(selectedGroup.representativeState), px, py,      C_TEXT, false);
            gfx.drawString(font, "Role: " + roleName(selectedGroup.role),      px, py + 11, C_ROLE, false);
        } else {
            gfx.drawString(font, "Select a block type", px, py,      C_DIM, false);
            gfx.drawString(font, "from the list",        px, py + 11, C_DIM, false);
        }
        gfx.drawString(font, "Assign role:", px, gy + CONTENT_Y + 24, C_DIM, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (preview.mouseClicked(mx, my, btn)) return true;

        int lx = gx + 3;
        int ly = gy + CONTENT_Y + 2;
        if (mx >= lx && mx < lx + LIST_W - 4 && my >= ly) {
            int idx = scrollOffset + (int)(my - ly) / ENTRY_H;
            List<Object> rows = buildRows();
            if (idx < rows.size() && rows.get(idx) instanceof BlockTypeGroup g) {
                if (selectedGroup == g) g.expanded = !g.expanded;
                else                    selectedGroup = g;
                updateButtonStates();
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseReleased(double mx, double my, int btn) {
        preview.mouseReleased(mx, my, btn);
        return super.mouseReleased(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (preview.mouseDragged(mx, my, btn, dx, dy)) return true;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        if (preview.mouseScrolled(mx, my, delta)) return true;
        int maxScroll = Math.max(0, buildRows().size() - VISIBLE_ROWS);
        scrollOffset  = (int) Math.max(0, Math.min(maxScroll, scrollOffset - delta));
        return true;
    }

    private void assignRole(BlockRole role) {
        if (selectedGroup == null) return;
        selectedGroup.applyRole(role);
        preview.markDirty();
        updateButtonStates();
    }

    private void resetScan() {
        // Tell the server to clear the scan NBT — client doesn't own the item state
        com.bgame.multiblockdesigner.network.ModNetwork.CHANNEL.sendToServer(
                new com.bgame.multiblockdesigner.network.CPacketResetScan()
        );
        onClose();
    }

    private void confirmAndSave() {
        List<ScannedBlock> all = new ArrayList<>();
        for (BlockTypeGroup g : groups) all.addAll(g.blocks);

        boolean hasController = all.stream().anyMatch(b -> b.role == BlockRole.CONTROLLER);
        if (!hasController) {
            confirmButton.setMessage(Component.literal("Need a CONTROLLER!"));
            return;
        }

        // Write roles back into the wand NBT so the server packet carries them
        DesignerWandItem.saveScannedBlocksPublic(wand, all);

        // Derive a display name from the controller block
        String name = all.stream()
                .filter(b -> b.role == BlockRole.CONTROLLER)
                .findFirst()
                .map(b -> {
                    net.minecraft.resources.ResourceLocation key =
                            net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(b.state.getBlock());
                    return key != null ? key.getPath().replace("_", " ") : "Custom Multiblock";
                })
                .orElse("Custom Multiblock");

        com.bgame.multiblockdesigner.network.ModNetwork.CHANNEL.sendToServer(
                new com.bgame.multiblockdesigner.network.CPacketSaveDefinition(
                        wand.getOrCreateTag().copy(), name
                )
        );
        onClose();
    }

    private void updateButtonStates() {
        boolean hasSel = selectedGroup != null;
        for (Button b : roleButtons) b.active = hasSel;
    }

    private List<Object> buildRows() {
        List<Object> rows = new ArrayList<>();
        for (BlockTypeGroup g : groups) {
            rows.add(g);
            if (g.expanded) rows.addAll(g.blocks);
        }
        return rows;
    }

    private int totalBlocks() { return groups.stream().mapToInt(BlockTypeGroup::count).sum(); }

    private static String blockName(BlockState state) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (key == null) return "Unknown";
        String[] parts = key.getPath().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        return sb.toString().trim();
    }

    private static String roleName(BlockRole role) {
        return switch (role) {
            case UNKNOWN -> "None"; case CASING -> "Casing";
            case ITEM_INPUT -> "Item In"; case ITEM_OUTPUT -> "Item Out";
            case FLUID_INPUT -> "Fluid In"; case FLUID_OUTPUT -> "Fluid Out";
            case ENERGY_INPUT -> "Energy"; case MUFFLER -> "Muffler";
            case MAINTENANCE -> "Maintenance"; case CONTROLLER -> "Controller";
        };
    }

    private static int roleDotColor(BlockRole role) {
        return switch (role) {
            case ITEM_INPUT -> 0xFF4466FF; case ITEM_OUTPUT -> 0xFF44AAFF;
            case FLUID_INPUT -> 0xFF44FF88; case FLUID_OUTPUT -> 0xFF88FF44;
            case ENERGY_INPUT -> 0xFFFFCC00; case MUFFLER -> 0xFFFF8844;
            case MAINTENANCE -> 0xFFAA44FF; case CONTROLLER -> 0xFFFF4444;
            default -> 0xFF888888;
        };
    }

    // Utility to draw a simple border around a rectangle
    private static void drawBorder(GuiGraphics gfx, int x, int y, int w, int h, int c) {
        gfx.fill(x, y, x+w, y+1, c); gfx.fill(x, y+h-1, x+w, y+h, c);
        gfx.fill(x, y, x+1, y+h, c); gfx.fill(x+w-1, y, x+w, y+h, c);
    }

    @Override public boolean isPauseScreen() { return false; }
}