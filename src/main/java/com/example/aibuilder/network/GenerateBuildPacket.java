package com.example.aibuilder.network;

import com.example.aibuilder.config.AIConfig.AIProvider;
import com.example.aibuilder.server.AIAgentService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class GenerateBuildPacket {
    private final String prompt;
    private final AIProvider provider;

    public GenerateBuildPacket(String prompt, AIProvider provider) {
        this.prompt = prompt;
        this.provider = provider;
    }

    public static void encode(GenerateBuildPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.prompt, 512);
        buf.writeEnum(msg.provider);
    }

    public static GenerateBuildPacket decode(FriendlyByteBuf buf) {
        return new GenerateBuildPacket(buf.readUtf(512), buf.readEnum(AIProvider.class));
    }

    public static void handle(GenerateBuildPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                AIAgentService.generateAndGivePlacer(player, msg.prompt, msg.provider);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
