package com.example.aibuilder.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class AIConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.EnumValue<AIProvider> ACTIVE_PROVIDER;
    public static final ForgeConfigSpec.ConfigValue<String> GEMINI_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> OPENAI_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> CLAUDE_API_KEY;
    public static final ForgeConfigSpec.IntValue BLOCKS_PER_TICK;

    public enum AIProvider {
        GEMINI,
        OPENAI,
        CLAUDE
    }

    static {
        BUILDER.comment("AI Builder Mod Configuration").push("api_keys");

        ACTIVE_PROVIDER = BUILDER
                .comment("Default AI provider (GEMINI, OPENAI, or CLAUDE)")
                .defineEnum("activeProvider", AIProvider.GEMINI);

        GEMINI_API_KEY = BUILDER
                .comment("Google Gemini API Key")
                .define("geminiApiKey", "");

        OPENAI_API_KEY = BUILDER
                .comment("OpenAI ChatGPT API Key")
                .define("openaiApiKey", "");

        CLAUDE_API_KEY = BUILDER
                .comment("Anthropic Claude API Key")
                .define("claudeApiKey", "");

        BLOCKS_PER_TICK = BUILDER
                .comment("Number of blocks placed per server tick when placing a build")
                .defineInRange("blocksPerTick", 50, 1, 1000);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
