package com.bgame.multiblockdesigner.gui;

import com.bgame.multiblockdesigner.definition.BlockRole;
import com.bgame.multiblockdesigner.definition.ScannedBlock;
import com.bgame.multiblockdesigner.item.CopyToolItem;
import com.bgame.multiblockdesigner.item.DesignerWandItem;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Redesigned WandClassifyScreen — Phantasia-style dark UI.
 *
 * Layout (fixed 460 × 260 window):
 *
 *  ┌─[TITLE BAR 16px — accent underline]──────────────────────────[KJS|JAVA]─┐
 *  │ [LEFT LIST 130px] │ [3D PREVIEW — fills middle] │ [RIGHT PANEL 158px]   │
 *  │                   │                              │  ┌ Info chip          │
 *  │  Block type rows  │   ScanPreviewWidget          │  ├ ROLE section       │
 *  │  (collapsible)    │   drag/zoom/rmb-reset        │  ├ ABILITIES section  │
 *  │                   │                              │  └ (scrollable)       │
 *  ├───────────────────┴──────────────────────────────┴───────────────────────┤
 *  │ [Pin block]           [✔ Confirm & Save]                      [↺ Reset] │
 *  └───────────────────────────────────────────────────────────────────────────┘
 *
 * Visual language:
 *   • #111111 base + #1A1A1A surface + #212121 raised panel
 *   • Accent: #2B6CB0 (electric blue) / active: #3A8CCC
 *   • Role chips: coloured left-edge bar + faint fill
 *   • Selection: cyan-tinted row fill + 2px left border
 *   • Buttons: flat dark fill, accent border on hover, no vanilla widget shadows
 *
 * All vanilla Button widgets are replaced with hand-drawn hit-tested buttons
 * (same pattern as PhantasiaSceneScreen's regBtn) so we fully control rendering
 * without fighting Minecraft's button theme.
 */
public class WandClassifyScreen extends Screen {

    // ── Layout ────────────────────────────────────────────────────────────────

    private static final int W           = 460;
    private static final int H           = 264;

    private static final int TITLE_H     = 16;
    private static final int FOOTER_H    = 22;

    private static final int LIST_W      = 130;
    private static final int PANEL_W     = 158;

    private static final int CONTENT_Y   = TITLE_H;
    private static final int CONTENT_H   = H - TITLE_H - FOOTER_H;

    // Derived columns (computed in init)
    private int gx, gy;
    private int previewX, previewW, panelX;

    private static final int ENTRY_H     = 13;
    private static final int VISIBLE_ROWS = CONTENT_H / ENTRY_H;

    // ── Palette (Phantasia-style) ─────────────────────────────────────────────

    // Backgrounds
    private static final int C_BG           = 0xFF111111;
    private static final int C_SURFACE      = 0xFF1A1A1A;
    private static final int C_PANEL        = 0xFF181818;
    private static final int C_FOOTER       = 0xFF131313;
    private static final int C_RAISED       = 0xFF212121;

    // Borders / dividers
    private static final int C_BORDER       = 0xFF2D2D2D;
    private static final int C_DIVIDER      = 0xFF242424;
    private static final int C_ACCENT_LINE  = 0xFF2B6CB0;

    // Selection states
    private static final int C_SEL          = 0x331E5A8A;  // blue tint, translucent
    private static final int C_SEL_EDGE     = 0xFF2B6CB0;
    private static final int C_SEL_BLOCK    = 0x2200AA66;
    private static final int C_SEL_BLOCK_EDGE = 0xFF00AA66;
    private static final int C_HOVER        = 0xFF1E1E1E;

    // Text
    private static final int C_TEXT         = 0xFFCCCCCC;
    private static final int C_DIM          = 0xFF555555;
    private static final int C_LABEL        = 0xFF666666;
    private static final int C_ROLE_TEXT    = 0xFFFFCC44;
    private static final int C_ABILITY_TEXT = 0xFF44CCCC;
    private static final int C_WARN         = 0xFFFF5555;
    private static final int C_OK           = 0xFF55DD88;

    // Buttons
    private static final int C_BTN          = 0xFF1E2530;
    private static final int C_BTN_HOV      = 0xFF253040;
    private static final int C_BTN_BORDER   = 0xFF2B6CB0;
    private static final int C_BTN_ACT      = 0xFF1A3F60;
    private static final int C_BTN_ACT_BORDER = 0xFF3A8CCC;

    // ── Role → PartAbility mapping ────────────────────────────────────────────

    private static final Map<BlockRole, String> ROLE_TO_ABILITY = new EnumMap<>(BlockRole.class);
    static {
        ROLE_TO_ABILITY.put(BlockRole.MUFFLER,      "MUFFLER");
        ROLE_TO_ABILITY.put(BlockRole.MAINTENANCE,  "MAINTENANCE");
        ROLE_TO_ABILITY.put(BlockRole.ENERGY_INPUT, "INPUT_ENERGY");
        ROLE_TO_ABILITY.put(BlockRole.ITEM_INPUT,   "IMPORT_ITEMS");
        ROLE_TO_ABILITY.put(BlockRole.ITEM_OUTPUT,  "EXPORT_ITEMS");
        ROLE_TO_ABILITY.put(BlockRole.FLUID_INPUT,  "IMPORT_FLUIDS");
        ROLE_TO_ABILITY.put(BlockRole.FLUID_OUTPUT, "EXPORT_FLUIDS");
    }

    // ── Block → registry expression (reflection, same as original) ───────────

    private static final Class<?>[] REGISTRY_CLASSES;
    static {
        List<Class<?>> cls = new ArrayList<>();
        cls.add(GTMachines.class);
        cls.add(GTBlocks.class);
        tryAddClass(cls, "com.gregtechceu.gtceu.common.data.GCYMBlocks");
        tryAddClass(cls, "com.gregtechceu.gtceu.common.data.GCYMMachines");
        tryAddClass(cls, "com.phoenix.common.data.PhoenixBlocks");
        tryAddClass(cls, "com.phoenix.common.data.PhoenixFissionBlocks");
        REGISTRY_CLASSES = cls.toArray(new Class[0]);
    }

    private static void tryAddClass(List<Class<?>> list, String fqn) {
        try { list.add(Class.forName(fqn)); } catch (ClassNotFoundException ignored) {}
    }

    private static volatile Map<Block, String> blockExprMap;

    private static Map<Block, String> getBlockExprMap() {
        if (blockExprMap != null) return blockExprMap;
        synchronized (WandClassifyScreen.class) {
            if (blockExprMap != null) return blockExprMap;
            Map<Block, String> map = new LinkedHashMap<>();
            for (Class<?> cls : REGISTRY_CLASSES) {
                String n = cls.getSimpleName();
                for (Field f : cls.getFields()) {
                    if (!Modifier.isStatic(f.getModifiers())) continue;
                    try {
                        Object val = f.get(null);
                        if (val instanceof MachineDefinition[] arr) {
                            for (int i = 0; i < arr.length; i++) {
                                if (arr[i] == null) continue;
                                Block b = arr[i].getBlock();
                                if (b != null) map.putIfAbsent(b, n + "." + f.getName() + "[" + i + "]");
                            }
                        } else if (val instanceof MachineDefinition def) {
                            Block b = def.getBlock();
                            if (b != null) map.putIfAbsent(b, n + "." + f.getName());
                        } else if (val instanceof com.tterrag.registrate.util.entry.BlockEntry<?> entry) {
                            Block b = entry.get();
                            if (b != null) map.putIfAbsent(b, n + "." + f.getName() + ".get()");
                        } else if (val instanceof Block b) {
                            map.putIfAbsent(b, n + "." + f.getName());
                        }
                    } catch (Exception ignored) {}
                }
            }
            blockExprMap = map;
            return map;
        }
    }

    public static String predicateExpr(ScannedBlock sb) {
        if (!sb.abilities.isEmpty()) {
            String inner = sb.abilities.stream()
                    .map(a -> "PartAbility." + a)
                    .reduce((x, y) -> x + ", " + y).orElse("");
            return "Predicates.abilities(" + inner + ")";
        }
        if (!sb.pinSpecific && ROLE_TO_ABILITY.containsKey(sb.role)) {
            return "Predicates.abilities(PartAbility." + ROLE_TO_ABILITY.get(sb.role) + ")";
        }
        Block block = sb.state.getBlock();
        String expr = getBlockExprMap().get(block);
        if (expr == null) {
            ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
            expr = key != null
                    ? "ForgeRegistries.BLOCKS.getValue(new ResourceLocation(\"" + key + "\"))"
                    : "/* unknown block */";
        }
        return "Predicates.blocks(" + expr + ".getBlock())";
    }

    // ── PartAbility name list ─────────────────────────────────────────────────

    private static final List<String> ALL_ABILITIES = new ArrayList<>();
    static {
        for (Field f : PartAbility.class.getFields()) {
            if (Modifier.isPublic(f.getModifiers()) && Modifier.isStatic(f.getModifiers())
                    && f.getType() == PartAbility.class) {
                try { ALL_ABILITIES.add(((PartAbility) f.get(null)).getName().toUpperCase()); }
                catch (IllegalAccessException ignored) {}
            }
        }
        ALL_ABILITIES.sort(String::compareToIgnoreCase);
    }

    // ── Hit-tested button record ──────────────────────────────────────────────

    private record HitBtn(int x, int y, int w, int h, Runnable action) {
        boolean isOver(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    // ── Screen state ──────────────────────────────────────────────────────────

    private final ItemStack            wand;
    private final List<BlockTypeGroup> groups;

    @Nullable private BlockTypeGroup selectedGroup = null;
    @Nullable private ScannedBlock   selectedBlock = null;

    private int   scrollOffset  = 0;
    private int   abilityScroll = 0;
    private boolean exportJava  = false;

    private ScanPreviewWidget preview;

    // Per-frame button list — rebuilt every render
    private final List<HitBtn> frameButtons = new ArrayList<>();

    // Persisted state for buttons that need it
    private String confirmMsg = "✔  Confirm & Save";
    private boolean confirmWarn = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public WandClassifyScreen(ItemStack wand) {
        super(Component.literal("Multiblock Designer"));
        this.wand   = wand;
        this.groups = BlockTypeGroup.groupFrom(loadScannedBlocks(wand));
    }

    private static List<ScannedBlock> loadScannedBlocks(ItemStack stack) {
        List<ScannedBlock> out = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag == null) return out;
        String key = tag.contains("scannedBlocks") ? "scannedBlocks" : CopyToolItem.TAG_SCANNED;
        if (!tag.contains(key)) return out;
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) out.add(ScannedBlock.fromNBT(list.getCompound(i)));
        return out;
    }

    // ── init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        gx = (width  - W) / 2;
        gy = (height - H) / 2;

        panelX   = gx + W - PANEL_W;
        previewX = gx + LIST_W + 1;
        previewW = panelX - previewX - 1;

        preview = new ScanPreviewWidget(previewX, gy + CONTENT_Y, previewW, CONTENT_H);
        preview.setGroups(groups);
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx);
        frameButtons.clear();

        drawChrome(gfx, mouseX, mouseY);

        withScissor(gfx, gx + 1, gy + CONTENT_Y, LIST_W, CONTENT_H,
                () -> renderList(gfx, mouseX, mouseY));

        preview.render(gfx, mouseX, mouseY, partialTick);

        withScissor(gfx, panelX, gy + CONTENT_Y, PANEL_W, CONTENT_H,
                () -> renderPanel(gfx, mouseX, mouseY));

        drawFooter(gfx, mouseX, mouseY);

        // No super.render() — we bypass vanilla widget rendering intentionally.
        // The preview mouse delegation still works via our override below.
    }

    // ── Chrome (title bar + borders + dividers) ───────────────────────────────

    private void drawChrome(GuiGraphics gfx, int mx, int my) {
        // Window fill
        gfx.fill(gx, gy, gx + W, gy + H, C_BG);

        // ── Title bar ────────────────────────────────────────────────────────
        gfx.fill(gx, gy, gx + W, gy + TITLE_H, C_SURFACE);
        // Accent underline
        gfx.fill(gx, gy + TITLE_H - 1, gx + W, gy + TITLE_H, C_ACCENT_LINE);

        gfx.drawString(font, "MULTIBLOCK DESIGNER", gx + 7, gy + 4, C_TEXT, false);

        String summary = groups.size() + " types  ·  " + totalBlocks() + " blocks";
        int summaryX = gx + W - 96 - font.width(summary);
        if (summaryX > gx + 7 + font.width("MULTIBLOCK DESIGNER") + 10)
            gfx.drawString(font, summary, summaryX, gy + 4, C_DIM, false);

        // Format toggle button (top-right) — KJS / JAVA
        int fmtX = gx + W - 44;
        boolean fmtHov = isOver(mx, my, fmtX, gy + 2, 38, 12);
        drawFlatBtn(gfx, fmtX, gy + 2, 38, 12,
                exportJava ? "JAVA" : "KJS",
                fmtHov, false);
        frameButtons.add(new HitBtn(fmtX, gy + 2, 38, 12,
                () -> exportJava = !exportJava));

        // ── Outer border ─────────────────────────────────────────────────────
        drawBorder(gfx, gx, gy, W, H, C_BORDER);

        // ── Column dividers ───────────────────────────────────────────────────
        int cy  = gy + CONTENT_Y;
        int cBt = gy + H - FOOTER_H;
        // Left  (list | preview)
        gfx.fill(gx + LIST_W, cy, gx + LIST_W + 1, cBt, C_DIVIDER);
        // Right (preview | panel)
        gfx.fill(panelX - 1, cy, panelX, cBt, C_DIVIDER);

        // Panel background
        gfx.fill(panelX, cy, gx + W - 1, cBt, C_PANEL);
    }

    // ── Footer (pin / confirm / reset) ───────────────────────────────────────

    private void drawFooter(GuiGraphics gfx, int mx, int my) {
        int fy = gy + H - FOOTER_H;
        gfx.fill(gx, fy, gx + W, gy + H, C_FOOTER);
        gfx.fill(gx, fy, gx + W, fy + 1, C_BORDER);

        int by = fy + (FOOTER_H - 14) / 2;   // vertically centred in footer

        // ── Pin block ────────────────────────────────────────────────────────
        ScannedBlock tgt = effectiveTarget();
        boolean canPin = tgt != null && ROLE_TO_ABILITY.containsKey(effectiveRole(tgt));
        boolean pinned = tgt != null && tgt.pinSpecific;
        String  pinLabel = pinned ? "Pinned ✔" : "Pin block";
        boolean pinHov = canPin && isOver(mx, my, gx + 3, by, 66, 14);
        drawFlatBtn(gfx, gx + 3, by, 66, 14, pinLabel, pinHov, pinned, !canPin);
        if (canPin) frameButtons.add(new HitBtn(gx + 3, by, 66, 14, this::togglePinSpecific));

        // ── Confirm & Save ────────────────────────────────────────────────────
        int confirmX = gx + 75;
        int confirmW = W - 75 - 70;
        boolean confHov = isOver(mx, my, confirmX, by, confirmW, 14);
        int confBg  = confirmWarn ? 0xFF3A1010 : C_BTN_ACT;
        int confBdr = confirmWarn ? C_WARN    : C_BTN_ACT_BORDER;
        drawFlatBtnColored(gfx, confirmX, by, confirmW, 14, confirmMsg, confHov, confBg, confBdr);
        frameButtons.add(new HitBtn(confirmX, by, confirmW, 14, this::confirmAndSave));

        // ── Reset ─────────────────────────────────────────────────────────────
        int resetX = gx + W - 66;
        boolean rstHov = isOver(mx, my, resetX, by, 62, 14);
        drawFlatBtnColored(gfx, resetX, by, 62, 14, "↺  Reset", rstHov,
                rstHov ? 0xFF3A1010 : C_BTN, rstHov ? C_WARN : C_BTN_BORDER);
        frameButtons.add(new HitBtn(resetX, by, 62, 14, this::resetScan));
    }

    // ── Left list ─────────────────────────────────────────────────────────────

    private void renderList(GuiGraphics gfx, int mx, int my) {
        int lx  = gx + 1;
        int ly  = gy + CONTENT_Y + 1;
        List<Object> rows = buildRows();
        int end = Math.min(rows.size(), scrollOffset + VISIBLE_ROWS);

        for (int i = scrollOffset; i < end; i++) {
            Object  row = rows.get(i);
            int     ry  = ly + (i - scrollOffset) * ENTRY_H;
            boolean hov = isOver(mx, my, lx, ry, LIST_W, ENTRY_H);

            if (row instanceof BlockTypeGroup g) renderGroupRow(gfx, g, lx, ry, hov);
            else if (row instanceof ScannedBlock s) renderBlockRow(gfx, s, lx, ry, hov);
        }

        // Scrollbar
        if (rows.size() > VISIBLE_ROWS) {
            int track = CONTENT_H;
            int barH  = Math.max(8, track * VISIBLE_ROWS / rows.size());
            int barY  = scrollOffset * (track - barH) / Math.max(1, rows.size() - VISIBLE_ROWS);
            gfx.fill(gx + LIST_W - 2, gy + CONTENT_Y,
                    gx + LIST_W - 1, gy + CONTENT_Y + track, C_RAISED);
            gfx.fill(gx + LIST_W - 2, gy + CONTENT_Y + barY,
                    gx + LIST_W - 1, gy + CONTENT_Y + barY + barH, C_ACCENT_LINE);
        }
    }

    private void renderGroupRow(GuiGraphics gfx, BlockTypeGroup g, int x, int y, boolean hov) {
        boolean sel = g == selectedGroup && selectedBlock == null;

        if (sel) {
            gfx.fill(x, y, x + LIST_W, y + ENTRY_H, C_SEL);
            gfx.fill(x, y, x + 2,      y + ENTRY_H, C_SEL_EDGE);
        } else if (hov) {
            gfx.fill(x, y, x + LIST_W, y + ENTRY_H, C_HOVER);
        }

        // Expand arrow
        gfx.drawString(font, g.expanded ? "▾" : "▸", x + 3, y + 2,
                sel ? C_SEL_EDGE : C_DIM, false);

        // Role colour dot
        if (g.role != BlockRole.UNKNOWN)
            gfx.fill(x + 13, y + 4, x + 16, y + 8, roleDotColor(g.role));

        // Per-block overrides indicator
        boolean hasOverrides = g.blocks.stream()
                .anyMatch(b -> b.role != BlockRole.UNKNOWN || !b.abilities.isEmpty() || b.pinSpecific);
        String count = "×" + g.count() + (hasOverrides ? " *" : "");
        int cntW  = font.width(count);
        int maxW  = LIST_W - 20 - cntW - 2;
        String name = trimToWidth(blockName(g.representativeState), maxW);

        gfx.drawString(font, name,  x + 19, y + 2,
                sel ? C_TEXT : (g.role == BlockRole.UNKNOWN ? C_DIM : C_TEXT), false);
        gfx.drawString(font, count, x + 19 + font.width(name) + 3, y + 2, C_DIM, false);
    }

    private void renderBlockRow(GuiGraphics gfx, ScannedBlock sb, int x, int y, boolean hov) {
        boolean sel = sb == selectedBlock;

        if (sel) {
            gfx.fill(x, y, x + LIST_W, y + ENTRY_H, C_SEL_BLOCK);
            gfx.fill(x, y, x + 2,      y + ENTRY_H, C_SEL_BLOCK_EDGE);
        } else if (hov) {
            gfx.fill(x, y, x + LIST_W, y + ENTRY_H, C_HOVER);
        }

        BlockRole eff = effectiveRoleForBlock(sb);
        if (eff != BlockRole.UNKNOWN)
            gfx.fill(x + 13, y + 4, x + 16, y + 8, roleDotColor(eff));

        boolean hasOverride = sb.role != BlockRole.UNKNOWN || !sb.abilities.isEmpty() || sb.pinSpecific;
        if (hasOverride) gfx.drawString(font, "✎", x + 3, y + 2, C_ABILITY_TEXT, false);

        BlockPos p = sb.pos;
        String coord = trimToWidth("%d,%d,%d".formatted(p.getX(), p.getY(), p.getZ()), LIST_W - 22);
        gfx.drawString(font, coord, x + 19, y + 2,
                hasOverride ? C_ABILITY_TEXT : 0xFF3D3D55, false);
    }

    // ── Right panel ───────────────────────────────────────────────────────────

    private void renderPanel(GuiGraphics gfx, int mx, int my) {
        int px   = panelX + 5;
        int py   = gy + CONTENT_Y + 4;
        int mw   = PANEL_W - 10;
        int cy   = gy + CONTENT_Y;
        int cBot = gy + H - FOOTER_H;

        // ── Info chip (top of panel) ──────────────────────────────────────────
        renderInfoChip(gfx, px, py, mw);

        // ── ROLE section ─────────────────────────────────────────────────────
        int roleY = py + 44;
        drawSectionLabel(gfx, px, roleY, mw, "ROLE");
        roleY += 10;

        BlockRole[] roles = BlockRole.values();
        int btnW = (mw - 2) / 2;
        int btnH = 12;

        for (int i = 0; i < roles.length; i++) {
            int bx = px + (i % 2) * (btnW + 2);
            int by = roleY + (i / 2) * (btnH + 2);
            BlockRole role = roles[i];

            boolean isActive = isRoleActive(role);
            boolean rHov = isOver(mx, my, bx, by, btnW, btnH);
            boolean hasSel = selectedGroup != null || selectedBlock != null;

            // Draw role button with role colour hint on left edge
            if (!hasSel) {
                drawFlatBtn(gfx, bx, by, btnW, btnH, roleName(role), false, false, true);
            } else {
                drawRoleBtn(gfx, bx, by, btnW, btnH, roleName(role), rHov, isActive, roleDotColor(role));
                frameButtons.add(new HitBtn(bx, by, btnW, btnH, () -> assignRole(role)));
            }
        }

        // ── ABILITIES section ─────────────────────────────────────────────────
        int rolesRows = (roles.length + 1) / 2;
        int abilY = roleY + rolesRows * (btnH + 2) + 8;
        drawSectionLabel(gfx, px, abilY, mw, "ABILITIES");

        // Scroll hint
        if (ALL_ABILITIES.size() > 10)
            gfx.drawString(font, "↕", px + mw - font.width("↕"), abilY, C_DIM, false);

        abilY += 10;

        Collection<String> activeAbilities = getActiveAbilities();
        int available = cBot - abilY - 2;
        int visRows   = available / (btnH + 2);
        int visAbs    = visRows * 2;

        for (int i = 0; i < visAbs && (abilityScroll * 2 + i) < ALL_ABILITIES.size(); i++) {
            String ability = ALL_ABILITIES.get(abilityScroll * 2 + i);
            boolean on     = activeAbilities.contains(ability);
            int bx = px + (i % 2) * (btnW + 2);
            int by = abilY + (i / 2) * (btnH + 2);
            boolean aHov = isOver(mx, my, bx, by, btnW, btnH);
            boolean hasSel = selectedGroup != null || selectedBlock != null;

            drawAbilityBtn(gfx, bx, by, btnW, btnH, ability.toLowerCase(), aHov, on, !hasSel);
            if (hasSel) {
                final String ab = ability;
                frameButtons.add(new HitBtn(bx, by, btnW, btnH, () -> toggleAbility(ab)));
            }
        }
    }

    // Info chip: block name + role badge (or "select…" prompt)
    private void renderInfoChip(GuiGraphics gfx, int px, int py, int mw) {
        if (selectedBlock != null) {
            BlockPos p = selectedBlock.pos;
            gfx.drawString(font, trimToWidth("@ %d,%d,%d".formatted(p.getX(), p.getY(), p.getZ()), mw),
                    px, py, C_ABILITY_TEXT, false);

            BlockRole eff = effectiveRoleForBlock(selectedBlock);
            boolean  isOverride = selectedBlock.role != BlockRole.UNKNOWN;
            String   rLabel = (isOverride ? "Override: " : "Inherited: ") + roleName(eff);
            drawRoleBadge(gfx, px, py + 11, rLabel, roleDotColor(eff));

            if (!selectedBlock.abilities.isEmpty()) {
                gfx.drawString(font, trimToWidth(String.join(", ", selectedBlock.abilities), mw),
                        px, py + 24, C_ABILITY_TEXT, false);
            }
            if (selectedBlock.pinSpecific)
                gfx.drawString(font, "Pinned → blocks()", px, py + 34, C_DIM, false);

        } else if (selectedGroup != null) {
            gfx.drawString(font, trimToWidth(blockName(selectedGroup.representativeState), mw),
                    px, py, C_TEXT, false);

            drawRoleBadge(gfx, px, py + 11, roleName(selectedGroup.role), roleDotColor(selectedGroup.role));

            if (!selectedGroup.abilities.isEmpty()) {
                gfx.drawString(font,
                        trimToWidth(String.join(", ", selectedGroup.abilities), mw),
                        px, py + 24, C_ABILITY_TEXT, false);
            }
            boolean hasOverrides = selectedGroup.blocks.stream()
                    .anyMatch(b -> b.role != BlockRole.UNKNOWN || !b.abilities.isEmpty() || b.pinSpecific);
            if (hasOverrides)
                gfx.drawString(font, "* per-block overrides", px, py + 34, C_DIM, false);

        } else {
            gfx.drawString(font, "Select a block type", px, py,      C_DIM, false);
            gfx.drawString(font, "from the list →",     px, py + 10, C_DIM, false);
        }
    }

    // Small inline role badge: coloured bar + text
    private void drawRoleBadge(GuiGraphics gfx, int x, int y, String label, int color) {
        gfx.fill(x, y, x + 3, y + 9, color);
        gfx.fill(x + 3, y, x + font.width(label) + 8, y + 9, (color & 0x00FFFFFF) | 0x22000000);
        gfx.drawString(font, label, x + 5, y + 1, C_ROLE_TEXT, false);
    }

    // Section label with horizontal rule
    private void drawSectionLabel(GuiGraphics gfx, int x, int y, int w, String label) {
        int lw = font.width(label);
        gfx.drawString(font, label, x, y, C_LABEL, false);
        gfx.fill(x + lw + 4, y + 3, x + w, y + 4, C_DIVIDER);
    }

    // ── Flat button primitives ────────────────────────────────────────────────

    /** Standard dark flat button. */
    private void drawFlatBtn(GuiGraphics gfx, int x, int y, int w, int h,
                             String label, boolean hov, boolean active) {
        drawFlatBtn(gfx, x, y, w, h, label, hov, active, false);
    }

    private void drawFlatBtn(GuiGraphics gfx, int x, int y, int w, int h,
                             String label, boolean hov, boolean active, boolean disabled) {
        int bg  = disabled ? 0xFF141414
                : active   ? C_BTN_ACT
                : hov      ? C_BTN_HOV
                :            C_BTN;
        int bdr = disabled ? C_BORDER
                : active   ? C_BTN_ACT_BORDER
                : hov      ? C_BTN_BORDER
                :            C_BORDER;
        drawFlatBtnColored(gfx, x, y, w, h, label, hov, bg, bdr);
        if (disabled) return;
        int tc = disabled ? C_DIM : (active ? C_TEXT : (hov ? 0xFFDDDDDD : C_TEXT));
        // Text is already drawn in drawFlatBtnColored — but we need to re-tint when disabled.
        // (handled by colour choice above — text drawn inside helper)
    }

    private void drawFlatBtnColored(GuiGraphics gfx, int x, int y, int w, int h,
                                    String label, boolean hov, int bg, int border) {
        gfx.fill(x, y, x + w, y + h, bg);
        drawBorder(gfx, x, y, w, h, border);
        int tw = font.width(label);
        int tx = x + (w - tw) / 2;
        int ty = y + (h - 8) / 2;
        gfx.drawString(font, label, tx, ty, C_TEXT, false);
    }

    /** Role button: left-edge coloured bar + text. */
    private void drawRoleBtn(GuiGraphics gfx, int x, int y, int w, int h,
                             String label, boolean hov, boolean active, int roleColor) {
        int bg  = active ? (roleColor & 0x00FFFFFF | 0x44000000) : (hov ? C_BTN_HOV : C_BTN);
        int bdr = active ? roleColor : (hov ? C_BTN_BORDER : C_BORDER);
        gfx.fill(x, y, x + w, y + h, bg);
        // Left colour accent bar
        gfx.fill(x, y, x + 2, y + h, roleColor);
        drawBorder(gfx, x, y, w, h, bdr);
        int tw = font.width(label);
        gfx.drawString(font, label, x + (w - tw) / 2 + 1, y + (h - 8) / 2, active ? C_TEXT : (hov ? 0xFFDDDDDD : C_TEXT), false);
    }

    /** Ability toggle button: checkbox-style prefix. */
    private void drawAbilityBtn(GuiGraphics gfx, int x, int y, int w, int h,
                                String label, boolean hov, boolean on, boolean disabled) {
        int bg  = disabled ? 0xFF141414 : on ? 0xFF112222 : (hov ? C_BTN_HOV : C_BTN);
        int bdr = disabled ? C_BORDER   : on ? C_ABILITY_TEXT : (hov ? C_BTN_BORDER : C_BORDER);
        gfx.fill(x, y, x + w, y + h, bg);
        drawBorder(gfx, x, y, w, h, bdr);
        String prefix = on ? "✔ " : "  ";
        int tc = disabled ? C_DIM : (on ? C_ABILITY_TEXT : (hov ? 0xFFDDDDDD : C_TEXT));
        String full = trimToWidth(prefix + label, w - 4);
        gfx.drawString(font, full, x + 3, y + (h - 8) / 2, tc, false);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // 1. Delegate to preview
        if (preview.mouseClicked(mx, my, btn)) return true;

        // 2. Hit-test frame buttons (panel roles, abilities, footer)
        if (btn == 0) {
            for (HitBtn hb : frameButtons) {
                if (hb.isOver(mx, my)) {
                    hb.action().run();
                    return true;
                }
            }
        }

        // 3. List clicks
        int lx = gx + 1;
        int ly = gy + CONTENT_Y + 1;
        if (isOver((int) mx, (int) my, lx, ly, LIST_W, CONTENT_H)) {
            int idx  = scrollOffset + (int) ((my - ly) / ENTRY_H);
            List<Object> rows = buildRows();
            if (idx >= 0 && idx < rows.size()) {
                Object row = rows.get(idx);
                if (row instanceof BlockTypeGroup g) {
                    if (selectedGroup == g && selectedBlock == null) g.expanded = !g.expanded;
                    else { selectedGroup = g; selectedBlock = null; }
                    return true;
                } else if (row instanceof ScannedBlock sb) {
                    selectedBlock = (selectedBlock == sb) ? null : sb;
                    selectedGroup = findGroup(sb);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        preview.mouseReleased(mx, my, btn);
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (preview.mouseDragged(mx, my, btn, dx, dy)) return true;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (preview.mouseScrolled(mx, my, delta)) return true;

        // Panel ability scroll
        if (mx >= panelX && mx < gx + W) {
            int max = Math.max(0, (ALL_ABILITIES.size() + 1) / 2 - 5);
            abilityScroll = (int) Math.max(0, Math.min(max, abilityScroll - delta));
            return true;
        }
        // List scroll
        int max = Math.max(0, buildRows().size() - VISIBLE_ROWS);
        scrollOffset = (int) Math.max(0, Math.min(max, scrollOffset - delta));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void assignRole(BlockRole role) {
        ScannedBlock tgt = effectiveTarget();
        if (tgt != null) {
            tgt.role = role;
        } else if (selectedGroup != null) {
            selectedGroup.applyRole(role);
        }
        preview.markDirty();
        confirmWarn = false;
        confirmMsg  = "✔  Confirm & Save";
    }

    private void toggleAbility(String ability) {
        ScannedBlock tgt = effectiveTarget();
        if (tgt == null) return;
        tgt.setAbility(ability, !tgt.abilities.contains(ability));
    }

    private void togglePinSpecific() {
        ScannedBlock tgt = effectiveTarget();
        if (tgt == null) return;
        tgt.pinSpecific = !tgt.pinSpecific;
    }

    private void resetScan() {
        com.bgame.multiblockdesigner.network.ModNetwork.CHANNEL.sendToServer(
                new com.bgame.multiblockdesigner.network.CPacketResetScan());
        onClose();
    }

    private void confirmAndSave() {
        List<ScannedBlock> all = new ArrayList<>();
        for (BlockTypeGroup g : groups) {
            for (ScannedBlock sb : g.blocks) {
                if (sb.role == BlockRole.UNKNOWN)  sb.role = g.role;
                if (sb.abilities.isEmpty())         sb.abilities.addAll(g.abilities);
            }
            all.addAll(g.blocks);
        }

        boolean hasController = all.stream().anyMatch(b -> b.role == BlockRole.CONTROLLER);
        if (!hasController) {
            confirmMsg  = "⚠  Need CONTROLLER!";
            confirmWarn = true;
            return;
        }

        DesignerWandItem.saveScannedBlocksPublic(wand, all);

        String name = all.stream()
                .filter(b -> b.role == BlockRole.CONTROLLER).findFirst()
                .map(b -> {
                    ResourceLocation k = ForgeRegistries.BLOCKS.getKey(b.state.getBlock());
                    return k != null ? k.getPath().replace("_", " ") : "Custom Multiblock";
                })
                .orElse("Custom Multiblock");

        com.bgame.multiblockdesigner.network.ModNetwork.CHANNEL.sendToServer(
                new com.bgame.multiblockdesigner.network.CPacketSaveDefinition(
                        wand.getOrCreateTag().copy(), name, exportJava));
        onClose();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Nullable
    private ScannedBlock effectiveTarget() { return selectedBlock; }

    @Nullable
    private BlockTypeGroup findGroup(ScannedBlock sb) {
        return groups.stream().filter(g -> g.blocks.contains(sb)).findFirst().orElse(null);
    }

    private BlockRole effectiveRole(ScannedBlock sb) {
        return sb.role != BlockRole.UNKNOWN ? sb.role : BlockRole.UNKNOWN;
    }

    private BlockRole effectiveRoleForBlock(ScannedBlock sb) {
        if (sb.role != BlockRole.UNKNOWN) return sb.role;
        BlockTypeGroup g = findGroup(sb);
        return (g != null) ? g.role : BlockRole.UNKNOWN;
    }

    private boolean isRoleActive(BlockRole role) {
        ScannedBlock tgt = effectiveTarget();
        if (tgt != null) return tgt.role == role;
        if (selectedGroup != null) return selectedGroup.role == role;
        return false;
    }

    private Collection<String> getActiveAbilities() {
        ScannedBlock tgt = effectiveTarget();
        if (tgt != null) return tgt.abilities;
        if (selectedGroup != null) return selectedGroup.abilities;
        return Collections.emptyList();
    }

    private List<Object> buildRows() {
        List<Object> rows = new ArrayList<>();
        for (BlockTypeGroup g : groups) {
            rows.add(g);
            if (g.expanded) rows.addAll(g.blocks);
        }
        return rows;
    }

    private int totalBlocks() {
        return groups.stream().mapToInt(BlockTypeGroup::count).sum();
    }

    private void withScissor(GuiGraphics gfx, int x, int y, int w, int h, Runnable draw) {
        gfx.enableScissor(x, y, x + w, y + h);
        draw.run();
        gfx.disableScissor();
    }

    private boolean isOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private String trimToWidth(String text, int maxW) {
        if (font.width(text) <= maxW) return text;
        while (text.length() > 1 && font.width(text + "…") > maxW)
            text = text.substring(0, text.length() - 1);
        return text + "…";
    }

    private static String blockName(BlockState state) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (key == null) return "Unknown";
        StringBuilder sb = new StringBuilder();
        for (String p : key.getPath().split("_"))
            if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        return sb.toString().trim();
    }

    private static String roleName(BlockRole role) {
        return switch (role) {
            case UNKNOWN      -> "None";         case CASING       -> "Casing";
            case ITEM_INPUT   -> "Item In";      case ITEM_OUTPUT  -> "Item Out";
            case FLUID_INPUT  -> "Fluid In";     case FLUID_OUTPUT -> "Fluid Out";
            case ENERGY_INPUT -> "Energy";       case MUFFLER      -> "Muffler";
            case MAINTENANCE  -> "Maintenance";  case CONTROLLER   -> "Controller";
        };
    }

    private static int roleDotColor(BlockRole role) {
        return switch (role) {
            case ITEM_INPUT   -> 0xFF4488FF;  case ITEM_OUTPUT  -> 0xFF44AAFF;
            case FLUID_INPUT  -> 0xFF44FF88;  case FLUID_OUTPUT -> 0xFF88FF44;
            case ENERGY_INPUT -> 0xFFFFCC00;  case MUFFLER      -> 0xFFFF8844;
            case MAINTENANCE  -> 0xFFAA44FF;  case CONTROLLER   -> 0xFFFF4444;
            default           -> 0xFF444444;
        };
    }

    private static void drawBorder(GuiGraphics gfx, int x, int y, int w, int h, int c) {
        gfx.fill(x,         y,         x + w, y + 1,     c);
        gfx.fill(x,         y + h - 1, x + w, y + h,     c);
        gfx.fill(x,         y,         x + 1, y + h,     c);
        gfx.fill(x + w - 1, y,         x + w, y + h,     c);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}