package com.example.aibuilder.item;

import com.example.aibuilder.client.AIBuilderScreen;
import com.example.aibuilder.server.BuildQueueManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BuildPlacerItem extends Item {
    public BuildPlacerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        BlockPos origin = context.getClickedPos().relative(context.getClickedFace());

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("BuildData", Tag.TAG_LIST)) {
            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                ListTag blockList = tag.getList("BuildData", Tag.TAG_COMPOUND);
                BuildQueueManager.enqueueBuild(serverLevel, origin, blockList);
                if (player != null) {
                    player.sendSystemMessage(Component.literal("Placing AI build (" + blockList.size() + " blocks) at " + origin.toShortString() + "!")
                            .withStyle(ChatFormatting.GREEN));
                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            if (level.isClientSide) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Minecraft.getInstance().setScreen(new AIBuilderScreen()));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() || !stack.hasTag() || !stack.getTag().contains("BuildData")) {
            if (level.isClientSide) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Minecraft.getInstance().setScreen(new AIBuilderScreen()));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("BuildData", Tag.TAG_LIST)) {
            String prompt = tag.getString("Prompt");
            String provider = tag.getString("Provider");
            int count = tag.getList("BuildData", Tag.TAG_COMPOUND).size();
            tooltip.add(Component.literal("Prompt: " + prompt).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.literal("AI: " + provider + " | Blocks: " + count).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Right-click a block to place the build!").withStyle(ChatFormatting.YELLOW));
        } else {
            tooltip.add(Component.literal("Empty Placer - Right-click or press B to open AI GUI").withStyle(ChatFormatting.GRAY));
        }
    }
}
