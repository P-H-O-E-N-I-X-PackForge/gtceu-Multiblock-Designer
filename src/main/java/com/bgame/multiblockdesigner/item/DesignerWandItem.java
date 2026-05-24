package com.bgame.multiblockdesigner.item;

import com.bgame.multiblockdesigner.definition.BlockRole;
import com.bgame.multiblockdesigner.definition.ScannedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// The Designer Wand — the primary tool for building dynamic multiblock definitions.
public class DesignerWandItem extends Item {

    // NBT keys
    private static final String TAG_CORNER_A    = "cornerA";
    private static final String TAG_CORNER_B    = "cornerB";
    private static final String TAG_SCANNED     = "scannedBlocks";
    private static final String TAG_SCAN_DONE   = "scanDone";

    private static final int MAX_SCAN_VOLUME = 32 * 32 * 32; // 32768

    public DesignerWandItem() {
        super(new Item.Properties()
                .stacksTo(1));
    }

    // Interaction
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();

        if (player == null) return InteractionResult.PASS;

        // Client side does nothing — all state lives on the server
        if (level.isClientSide) return InteractionResult.SUCCESS;

        // Enforce creative-only
        if (!player.isCreative()) {
            player.sendSystemMessage(Component.literal("[Designer] Only usable in creative mode.")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        ItemStack wand = ctx.getItemInHand();
        BlockPos clicked = ctx.getClickedPos();

        // If scan is done, reopen the GUI via packet
        if (isScanDone(wand)) {
            sendOpenGuiPacket(player, wand);
            return InteractionResult.SUCCESS;
        }

        if (!hasCornerA(wand)) {
            // First click → set Corner A
            setCornerA(wand, clicked);
            player.sendSystemMessage(Component.literal("[Designer] Corner A set: " + formatPos(clicked))
                    .withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            // Shift + right-click → set Corner B and scan
            BlockPos cornerA = getCornerA(wand);
            int volume = getVolume(cornerA, clicked);

            if (volume > MAX_SCAN_VOLUME) {
                player.sendSystemMessage(Component.literal(
                                "[Designer] Volume too large (%d blocks). Max is %d.".formatted(volume, MAX_SCAN_VOLUME))
                        .withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }

            setCornerB(wand, clicked);
            List<ScannedBlock> blocks = scanVolume(level, cornerA, clicked);
            saveScannedBlocks(wand, blocks);

            player.sendSystemMessage(Component.literal(
                            "[Designer] Scanned %d blocks. Opening classify GUI...".formatted(blocks.size()))
                    .withStyle(ChatFormatting.GREEN));

            sendOpenGuiPacket(player, wand);

        } else {
            // Non-sneak right-click with Corner A set → reset Corner A
            clearScan(wand);
            setCornerA(wand, clicked);
            player.sendSystemMessage(Component.literal("[Designer] Corner A reset: " + formatPos(clicked))
                    .withStyle(ChatFormatting.YELLOW));
        }

        return InteractionResult.SUCCESS;
    }

    private void sendOpenGuiPacket(Player player, ItemStack wand) {
        com.bgame.multiblockdesigner.network.ModNetwork.CHANNEL.sendTo(
                new com.bgame.multiblockdesigner.network.SPacketOpenClassifyGui(
                        wand.getOrCreateTag().copy()
                ),
                ((net.minecraft.server.level.ServerPlayer) player).connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT
        );
    }

    // Right-click in air (no block targeted) with a completed scan → open GUI.
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack wand = player.getItemInHand(hand);
        if (!player.isCreative()) return InteractionResultHolder.pass(wand);
        // Right-click in air: server sends open-GUI packet if scan is done
        if (!level.isClientSide && isScanDone(wand)) {
            sendOpenGuiPacket(player, wand);
        }
        return InteractionResultHolder.sidedSuccess(wand, level.isClientSide);
    }

    // Scan logic
    private List<ScannedBlock> scanVolume(Level level, BlockPos a, BlockPos b) {
        List<ScannedBlock> result = new ArrayList<>();

        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    if (!state.isAir()) {
                        result.add(new ScannedBlock(pos, state));
                    }
                }
            }
        }

        return result;
    }

    // NBT helpers — corners
    public static boolean hasCornerAPublic(ItemStack wand) {
        return wand.hasTag() && wand.getTag().contains("cornerA");
    }

    private boolean hasCornerA(ItemStack wand) {
        return wand.hasTag() && wand.getTag().contains(TAG_CORNER_A);
    }

    private void setCornerA(ItemStack wand, BlockPos pos) {
        wand.getOrCreateTag().put(TAG_CORNER_A, posToTag(pos));
    }

    private void setCornerB(ItemStack wand, BlockPos pos) {
        wand.getOrCreateTag().put(TAG_CORNER_B, posToTag(pos));
    }

    @Nullable
    public static BlockPos getCornerA(ItemStack wand) {
        if (!wand.hasTag() || !wand.getTag().contains(TAG_CORNER_A)) return null;
        return tagToPos(wand.getTag().getCompound(TAG_CORNER_A));
    }

    @Nullable
    public static BlockPos getCornerB(ItemStack wand) {
        if (!wand.hasTag() || !wand.getTag().contains(TAG_CORNER_B)) return null;
        return tagToPos(wand.getTag().getCompound(TAG_CORNER_B));
    }

    // NBT helpers — scanned blocks
    public static void saveScannedBlocksPublic(ItemStack wand, List<ScannedBlock> blocks) {
        ListTag list = new ListTag();
        for (ScannedBlock b : blocks) list.add(b.toNBT());
        CompoundTag tag = wand.getOrCreateTag();
        tag.put(TAG_SCANNED, list);
        tag.putBoolean(TAG_SCAN_DONE, true);
    }

    private void saveScannedBlocks(ItemStack wand, List<ScannedBlock> blocks) {
        saveScannedBlocksPublic(wand, blocks);
    }

    public static List<ScannedBlock> loadScannedBlocks(ItemStack wand) {
        List<ScannedBlock> result = new ArrayList<>();
        if (!wand.hasTag()) return result;
        ListTag list = wand.getTag().getList(TAG_SCANNED, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            result.add(ScannedBlock.fromNBT(list.getCompound(i)));
        }
        return result;
    }

    public static boolean isScanDone(ItemStack wand) {
        return wand.hasTag() && wand.getTag().getBoolean(TAG_SCAN_DONE);
    }

    public static void clearScanPublic(ItemStack wand) {
        if (!wand.hasTag()) return;
        CompoundTag tag = wand.getTag();
        tag.remove(TAG_CORNER_A);
        tag.remove(TAG_CORNER_B);
        tag.remove(TAG_SCANNED);
        tag.remove(TAG_SCAN_DONE);
    }

    private void clearScan(ItemStack wand) {
        clearScanPublic(wand);
    }

    // Tooltip
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        if (isScanDone(stack)) {
            int count = loadScannedBlocks(stack).size();
            tooltip.add(Component.literal("Scan ready: %d blocks".formatted(count))
                    .withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.literal("Right-click in air to classify")
                    .withStyle(ChatFormatting.GRAY));
        } else if (hasCornerA(stack)) {
            BlockPos a = getCornerA(stack);
            tooltip.add(Component.literal("Corner A: " + formatPos(a))
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.literal("Shift+right-click to set Corner B and scan")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Right-click a block to set Corner A")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // Utility
    private static int getVolume(BlockPos a, BlockPos b) {
        return (Math.abs(a.getX() - b.getX()) + 1)
                * (Math.abs(a.getY() - b.getY()) + 1)
                * (Math.abs(a.getZ() - b.getZ()) + 1);
    }

    private static String formatPos(BlockPos pos) {
        if (pos == null) return "null";
        return "(%d, %d, %d)".formatted(pos.getX(), pos.getY(), pos.getZ());
    }

    private static CompoundTag posToTag(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }

    private static BlockPos tagToPos(CompoundTag tag) {
        return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }
}