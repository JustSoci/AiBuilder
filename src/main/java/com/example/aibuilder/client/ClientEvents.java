package com.example.aibuilder.client;

import com.example.aibuilder.AIBuilderMod;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

public class ClientEvents {
    public static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            "key.aibuilder.open_gui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.aibuilder"
    );

    @Mod.EventBusSubscriber(modid = AIBuilderMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_GUI_KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = AIBuilderMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeBusEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (OPEN_GUI_KEY.consumeClick() && Minecraft.getInstance().screen == null) {
                Minecraft.getInstance().setScreen(new AIBuilderScreen());
            }
        }

        @SubscribeEvent
        public static void onRenderLevelStage(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player == null || mc.level == null) return;

            ItemStack held = player.getMainHandItem();
            if (!held.is(AIBuilderMod.BUILD_PLACER.get())) {
                held = player.getOffhandItem();
                if (!held.is(AIBuilderMod.BUILD_PLACER.get())) return;
            }

            CompoundTag tag = held.getTag();
            if (tag == null || !tag.contains("BuildData", Tag.TAG_LIST)) return;

            if (!(mc.hitResult instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
                return;
            }

            BlockPos origin = blockHit.getBlockPos().relative(blockHit.getDirection());
            ListTag blockList = tag.getList("BuildData", Tag.TAG_COMPOUND);

            PoseStack poseStack = event.getPoseStack();
            Vec3 camPos = event.getCamera().getPosition();
            MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

            poseStack.pushPose();
            poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            // Tint the rendered ghost blocks with 50% transparency
            RenderSystem.setShaderColor(0.8f, 0.9f, 1.0f, 0.5f);

            for (int i = 0; i < blockList.size(); i++) {
                CompoundTag entry = blockList.getCompound(i);
                int dx = entry.getInt("x");
                int dy = entry.getInt("y");
                int dz = entry.getInt("z");
                String id = entry.getString("id");

                if ("minecraft:air".equals(id)) continue;

                BlockPos targetPos = origin.offset(dx, dy, dz);
                ResourceLocation rl = ResourceLocation.tryParse(id);
                Block block = rl != null ? ForgeRegistries.BLOCKS.getValue(rl) : Blocks.STONE;
                if (block == null || block == Blocks.AIR) {
                    block = Blocks.STONE;
                }
                BlockState state = block.defaultBlockState();

                poseStack.pushPose();
                poseStack.translate(targetPos.getX(), targetPos.getY(), targetPos.getZ());
                poseStack.translate(0.05f, 0.05f, 0.05f);
                poseStack.scale(0.9f, 0.9f, 0.9f);

                mc.getBlockRenderer().renderSingleBlock(
                        state,
                        poseStack,
                        bufferSource,
                        15728880,
                        OverlayTexture.NO_OVERLAY,
                        ModelData.EMPTY,
                        RenderType.translucent()
                );

                poseStack.popPose();
            }

            bufferSource.endBatch(RenderType.translucent());
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            // Also draw cyan wireframe outlines around each block position so the footprint is unmistakable
            VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());
            for (int i = 0; i < blockList.size(); i++) {
                CompoundTag entry = blockList.getCompound(i);
                if ("minecraft:air".equals(entry.getString("id"))) continue;
                BlockPos p = origin.offset(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
                LevelRenderer.renderLineBox(
                        poseStack,
                        lineConsumer,
                        p.getX(), p.getY(), p.getZ(),
                        p.getX() + 1, p.getY() + 1, p.getZ() + 1,
                        0.2f, 0.85f, 1.0f, 0.45f
                );
            }
            bufferSource.endBatch(RenderType.lines());

            RenderSystem.disableBlend();
            poseStack.popPose();
        }
    }
}
