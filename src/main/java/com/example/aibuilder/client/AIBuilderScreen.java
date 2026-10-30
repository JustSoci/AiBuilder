package com.example.aibuilder.client;

import com.example.aibuilder.config.AIConfig.AIProvider;
import com.example.aibuilder.network.GenerateBuildPacket;
import com.example.aibuilder.network.PacketHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AIBuilderScreen extends Screen {
    private EditBox promptBox;
    private AIProvider selectedProvider = AIProvider.GEMINI;

    public AIBuilderScreen() {
        super(Component.literal("AI Builder"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 1. Text Bar for Prompt
        this.promptBox = new EditBox(this.font, centerX - 150, centerY - 35, 300, 20, Component.literal("Enter Build Prompt"));
        this.promptBox.setMaxLength(512);
        this.promptBox.setHint(Component.literal("Describe the build (e.g., medieval tower with create blocks)..."));
        this.addRenderableWidget(this.promptBox);
        this.setInitialFocus(this.promptBox);

        // 2. AI Provider Selector
        CycleButton<AIProvider> providerButton = CycleButton.builder((AIProvider provider) -> Component.literal(provider.name()))
                .withValues(AIProvider.values())
                .withInitialValue(this.selectedProvider)
                .create(centerX - 150, centerY - 5, 300, 20, Component.literal("AI Provider"), (btn, value) -> {
                    this.selectedProvider = value;
                });
        this.addRenderableWidget(providerButton);

        // 3. Create Button
        Button createButton = Button.builder(Component.literal("Create"), btn -> {
            String prompt = this.promptBox.getValue().trim();
            if (!prompt.isEmpty()) {
                PacketHandler.INSTANCE.sendToServer(new GenerateBuildPacket(prompt, this.selectedProvider));
                this.onClose();
            }
        }).bounds(centerX - 150, centerY + 25, 145, 20).build();
        this.addRenderableWidget(createButton);

        // 4. Cancel Button
        Button cancelButton = Button.builder(Component.literal("Cancel"), btn -> {
            this.onClose();
        }).bounds(centerX + 5, centerY + 25, 145, 20).build();
        this.addRenderableWidget(cancelButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 55, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
