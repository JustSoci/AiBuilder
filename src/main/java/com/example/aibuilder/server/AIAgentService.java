package com.example.aibuilder.server;

import com.example.aibuilder.AIBuilderMod;
import com.example.aibuilder.config.AIConfig;
import com.example.aibuilder.config.AIConfig.AIProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AIAgentService {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public static void generateAndGivePlacer(ServerPlayer player, String prompt, AIProvider provider) {
        String apiKey = switch (provider) {
            case GEMINI -> AIConfig.GEMINI_API_KEY.get();
            case OPENAI -> AIConfig.OPENAI_API_KEY.get();
            case CLAUDE -> AIConfig.CLAUDE_API_KEY.get();
        };

        if (apiKey == null || apiKey.isBlank()) {
            player.sendSystemMessage(Component.literal("Error: No API key configured for " + provider.name() + " in config/aibuilder-common.toml")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        player.sendSystemMessage(Component.literal("Generating build with " + provider.name() + "... Please wait.")
                .withStyle(ChatFormatting.YELLOW));

        // Specifically extract non-vanilla modded blocks so the AI sees all installed modded blocks first
        List<String> moddedBlocks = ForgeRegistries.BLOCKS.getKeys().stream()
                .filter(rl -> !rl.getNamespace().equals("minecraft"))
                .map(ResourceLocation::toString)
                .limit(2000)
                .collect(Collectors.toList());

        String moddedPalette = moddedBlocks.isEmpty()
                ? "None installed (use standard minecraft: blocks)"
                : String.join(", ", moddedBlocks);

        String systemInstruction = "You are a Minecraft 1.20.1 structure generator. Output ONLY valid raw JSON with no markdown backticks. " +
                "Format: {\"blocks\":[{\"x\":0,\"y\":0,\"z\":0,\"id\":\"minecraft:stone_bricks\"}]}. " +
                "Coordinates are relative to origin (0,0,0) where y>=0. " +
                "You can use any standard 'minecraft:' block ID, AND you have full access to these installed modded block IDs: [" + moddedPalette + "]. " +
                "Prioritize using matching modded blocks from this list whenever relevant to the user's request.";

        CompletableFuture.supplyAsync(() -> callProviderAPI(provider, apiKey, systemInstruction, prompt))
                .thenAccept(jsonResult -> {
                    player.getServer().execute(() -> {
                        try {
                            ListTag blockList = parseBlocksToNBT(jsonResult);
                            if (blockList.isEmpty()) {
                                player.sendSystemMessage(Component.literal("AI returned 0 blocks or invalid JSON.").withStyle(ChatFormatting.RED));
                                return;
                            }

                            ItemStack placerStack = new ItemStack(AIBuilderMod.BUILD_PLACER.get());
                            CompoundTag tag = placerStack.getOrCreateTag();
                            tag.put("BuildData", blockList);
                            tag.putString("Prompt", prompt);
                            tag.putString("Provider", provider.name());
                            placerStack.setHoverName(Component.literal("AI Placer: " + (prompt.length() > 20 ? prompt.substring(0, 20) + "..." : prompt))
                                    .withStyle(ChatFormatting.GOLD));

                            if (!player.getInventory().add(placerStack)) {
                                player.drop(placerStack, false);
                            }

                            player.sendSystemMessage(Component.literal("Build ready! Use the AI Build Placer item on any block to place it.")
                                    .withStyle(ChatFormatting.GREEN));
                        } catch (Exception e) {
                            player.sendSystemMessage(Component.literal("Failed to parse AI build: " + e.getMessage())
                                    .withStyle(ChatFormatting.RED));
                        }
                    });
                });
    }

    private static String callProviderAPI(AIProvider provider, String apiKey, String systemPrompt, String userPrompt) {
        try {
            return switch (provider) {
                case GEMINI -> callGemini(apiKey, systemPrompt, userPrompt);
                case OPENAI -> callOpenAI(apiKey, systemPrompt, userPrompt);
                case CLAUDE -> callClaude(apiKey, systemPrompt, userPrompt);
            };
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static String callGemini(String apiKey, String systemPrompt, String userPrompt) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;

        JsonObject payload = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject contentObj = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", systemPrompt + "\n\nUser request: " + userPrompt);
        parts.add(part);
        contentObj.add("parts", parts);
        contents.add(contentObj);
        payload.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("responseMimeType", "application/json");
        payload.add("generationConfig", generationConfig);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        return root.getAsJsonArray("candidates")
                .get(0).getAsJsonObject()
                .getAsJsonObject("content")
                .getAsJsonArray("parts")
                .get(0).getAsJsonObject()
                .get("text").getAsString();
    }

    private static String callOpenAI(String apiKey, String systemPrompt, String userPrompt) throws Exception {
        String url = "https://api.openai.com/v1/chat/completions";

        JsonObject payload = new JsonObject();
        payload.addProperty("model", "gpt-4o-mini");

        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");
        payload.add("response_format", responseFormat);

        JsonArray messages = new JsonArray();
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        messages.add(sysMsg);

        JsonObject usrMsg = new JsonObject();
        usrMsg.addProperty("role", "user");
        usrMsg.addProperty("content", userPrompt);
        messages.add(usrMsg);

        payload.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        return root.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();
    }

    private static String callClaude(String apiKey, String systemPrompt, String userPrompt) throws Exception {
        String url = "https://api.anthropic.com/v1/messages";

        JsonObject payload = new JsonObject();
        payload.addProperty("model", "claude-3-5-sonnet-latest");
        payload.addProperty("max_tokens", 4096);
        payload.addProperty("system", systemPrompt);

        JsonArray messages = new JsonArray();
        JsonObject usrMsg = new JsonObject();
        usrMsg.addProperty("role", "user");
        usrMsg.addProperty("content", userPrompt);
        messages.add(usrMsg);
        payload.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        return root.getAsJsonArray("content")
                .get(0).getAsJsonObject()
                .get("text").getAsString();
    }

    private static ListTag parseBlocksToNBT(String rawJson) {
        String cleaned = rawJson.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            int lastBackticks = cleaned.lastIndexOf("```");
            if (firstNewline != -1 && lastBackticks > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastBackticks).trim();
            }
        }

        JsonObject obj = JsonParser.parseString(cleaned).getAsJsonObject();
        JsonArray arr = obj.getAsJsonArray("blocks");
        ListTag listTag = new ListTag();

        if (arr != null) {
            for (JsonElement el : arr) {
                JsonObject b = el.getAsJsonObject();
                CompoundTag tag = new CompoundTag();
                tag.putInt("x", b.get("x").getAsInt());
                tag.putInt("y", b.get("y").getAsInt());
                tag.putInt("z", b.get("z").getAsInt());
                tag.putString("id", b.get("id").getAsString());
                listTag.add(tag);
            }
        }
        return listTag;
    }
}
