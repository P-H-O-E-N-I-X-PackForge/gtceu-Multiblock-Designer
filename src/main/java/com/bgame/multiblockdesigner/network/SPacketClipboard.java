package com.bgame.multiblockdesigner.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SPacketClipboard {
    public static final String END_MARKER = "CLIPBOARD_END_PACKET";
    private static final StringBuilder accumulator = new StringBuilder();

    private final String chunk;

    public SPacketClipboard(String chunk) {
        this.chunk = chunk;
    }

    public static void encode(SPacketClipboard msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.chunk, 32767);
    }

    public static SPacketClipboard decode(FriendlyByteBuf buf) {
        return new SPacketClipboard(buf.readUtf(32767));
    }

    public static void handle(SPacketClipboard msg, Supplier<NetworkEvent.Context> ctxGetter) {
        NetworkEvent.Context ctx = ctxGetter.get();
        ctx.enqueueWork(() -> {
            if (msg.chunk.equals(END_MARKER)) {
                String fullData = accumulator.toString();
                accumulator.setLength(0);
                Minecraft.getInstance().keyboardHandler.setClipboard(fullData);
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("Selection copied to clipboard!"),
                            true
                    );
                }
            } else {
                accumulator.append(msg.chunk);
            }
        });
        ctx.setPacketHandled(true);
    }
}
