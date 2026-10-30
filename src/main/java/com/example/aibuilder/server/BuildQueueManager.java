package com.example.aibuilder.server;

import com.example.aibuilder.config.AIConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedList;
import java.util.Queue;

public class BuildQueueManager {
    private static final Queue<PlacementTask> QUEUE = new LinkedList<>();

    public record PlacementTask(ServerLevel level, BlockPos pos, String blockId) {}

    public static void enqueueBuild(ServerLevel level, BlockPos origin, ListTag blockList) {
        for (int i = 0; i < blockList.size(); i++) {
            CompoundTag entry = blockList.getCompound(i);
            int dx = entry.getInt("x");
            int dy = entry.getInt("y");
            int dz = entry.getInt("z");
            String id = entry.getString("id");
            QUEUE.add(new PlacementTask(level, origin.offset(dx, dy, dz), id));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || QUEUE.isEmpty()) return;

        int maxPerTick = AIConfig.BLOCKS_PER_TICK.get();
        int placed = 0;

        while (!QUEUE.isEmpty() && placed < maxPerTick) {
            PlacementTask task = QUEUE.poll();
            if (task != null) {
                ResourceLocation rl = ResourceLocation.tryParse(task.blockId());
                Block block = rl != null ? ForgeRegistries.BLOCKS.getValue(rl) : Blocks.STONE;
                if (block == null || block == Blocks.AIR && !task.blockId().equals("minecraft:air")) {
                    block = Blocks.STONE;
                }
                task.level().setBlock(task.pos(), block.defaultBlockState(), 2 | 16);
                placed++;
            }
        }
    }
}
