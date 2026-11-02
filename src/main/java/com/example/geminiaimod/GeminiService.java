package com.example.geminiaimod;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeminiService {
    private static String apiKey = "DEIN_GEMINI_API_KEY_HIER";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private static final String SYSTEM_PROMPT =
            "Du bist eine direkt in Minecraft 1.20.1 (Forge) integrierte KI. Antworte kurz und präzise auf Deutsch. " +
            "Du kannst im Spiel komplexe Befehle ausführen und die Spiel-Configs bearbeiten, indem du Steuerungs-Tags anhängst:\n" +
            "1. Für Minecraft-Befehle nutze: [CMD: /befehl] (auch mehrere hintereinander für komplexe Strukturen, NBT-Items, Kreaturen, Redstone oder Gamerules).\n" +
            "2. Für Minecraft-Einstellungen (options.txt) nutze: [CFG: schluessel=wert]. Unterstützte Schlüssel: " +
            "fov (30-110), renderDistance (2-32), simulationDistance (5-32), gamma (0.0-10.0), guiScale (0-4), fullscreen (true/false), autoJump (true/false), bobView (true/false).\n" +
            "3. Für Mod-Config-Dateien im config/-Ordner nutze: [MODCFG: dateiname.toml|schluessel=neuer_wert].\n" +
            "Beispiel: 'Mach Tag, gib mir ein Schärfe 10 Diamantschwert und stell das FOV auf 105' -> " +
            "'Erledigt! [CMD: /time set day] [CMD: /give @s diamond_sword{Enchantments:[{id:\"minecraft:sharpness\",lvl:10s}]} 1] [CFG: fov=105]'";

    public static Path getConfigFile() {
        return FMLPaths.CONFIGDIR.get().resolve("geminiaimod.json");
    }

    public static void loadConfig() {
        Path cfg = getConfigFile();
        if (Files.exists(cfg)) {
            try {
                String json = Files.readString(cfg, StandardCharsets.UTF_8);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                if (obj.has("apiKey")) {
                    apiKey = obj.get("apiKey").getAsString().trim();
                }
            } catch (Exception ignored) {
            }
        } else {
            saveApiKey(apiKey);
        }
    }

    public static void saveApiKey(String newKey) {
        apiKey = newKey != null ? newKey.trim() : "";
        JsonObject obj = new JsonObject();
        obj.addProperty("apiKey", apiKey);
        try {
            Files.writeString(getConfigFile(), obj.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equals("DEIN_GEMINI_API_KEY_HIER");
    }

    public static void askGeminiAsync(String userPrompt, Consumer<String> callback) {
        if (!hasApiKey()) {
            callback.accept("§cKein API-Key hinterlegt! Tippe in dieser Konsole: §e/apikey DEIN_GEMINI_KEY");
            return;
        }

        try {
            String cleanKey = apiKey.trim();
            // URL ohne ?key= Parameter, damit AQ.-Keys nicht abgelehnt werden
            String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

            JsonObject root = new JsonObject();
            JsonArray contents = new JsonArray();
            JsonObject contentObj = new JsonObject();
            JsonArray parts = new JsonArray();
            JsonObject part = new JsonObject();

            LocalPlayer player = Minecraft.getInstance().player;
            String contextInfo = player != null
                    ? String.format(" (Spieler: %s, Pos: %d %d %d, Dimension: %s) ",
                    player.getName().getString(),
                    player.getBlockX(), player.getBlockY(), player.getBlockZ(),
                    player.level().dimension().location())
                    : "";

            part.addProperty("text", SYSTEM_PROMPT + "\n\nKontext:" + contextInfo + "\nAnfrage: " + userPrompt);
            parts.add(part);
            contentObj.add("parts", parts);
            contents.add(contentObj);
            root.add("contents", contents);

            // Übergabe über x-goog-api-key Header (funktioniert für AQ.-Keys und AIzaSy-Keys)
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", cleanKey)
                    .POST(HttpRequest.BodyPublishers.ofString(root.toString(), StandardCharsets.UTF_8))
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            String errMsg = extractErrorMessage(response.body());
                            Minecraft.getInstance().execute(() ->
                                    callback.accept("§cAPI-Fehler (HTTP " + response.statusCode() + "): " + errMsg));
                            return;
                        }

                        String aiText = extractTextFromJson(response.body());
                        Minecraft.getInstance().execute(() -> {
                            String cleanedText = processActionsAndCleanText(aiText);
                            callback.accept(cleanedText);
                        });
                    })
                    .exceptionally(ex -> {
                        Minecraft.getInstance().execute(() ->
                                callback.accept("§cNetzwerkfehler: " + ex.getMessage()));
                        return null;
                    });
        } catch (Exception e) {
            callback.accept("§cFehler: " + e.getMessage());
        }
    }

    private static String extractTextFromJson(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            return obj.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();
        } catch (Exception e) {
            return "Fehler beim Lesen der KI-Antwort.";
        }
    }

    private static String extractErrorMessage(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("error")) {
                return obj.getAsJsonObject("error").get("message").getAsString();
            }
        } catch (Exception ignored) {
        }
        return "Prüfe deinen Key mit /apikey!";
    }

    private static String processActionsAndCleanText(String aiResponse) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        // 1. Minecraft-Befehle ausführen ([CMD: /...])
        Pattern cmdPattern = Pattern.compile("\\[CMD:\\s*/?([^\\]]+)\\]");
        Matcher cmdMatcher = cmdPattern.matcher(aiResponse);
        while (cmdMatcher.find()) {
            String command = cmdMatcher.group(1).trim();
            if (player != null && player.connection != null) {
                player.connection.sendCommand(command);
            }
        }

        // 2. Client-Optionen ändern ([CFG: key=value])
        Pattern cfgPattern = Pattern.compile("\\[CFG:\\s*([a-zA-Z0-9_]+)=([^\\]]+)\\]");
        Matcher cfgMatcher = cfgPattern.matcher(aiResponse);
        Options opts = mc.options;
        boolean configChanged = false;

        while (cfgMatcher.find()) {
            String key = cfgMatcher.group(1).trim();
            String val = cfgMatcher.group(2).trim();
            try {
                switch (key) {
                    case "fov" -> opts.fov().set(Integer.parseInt(val));
                    case "renderDistance" -> opts.renderDistance().set(Integer.parseInt(val));
                    case "simulationDistance" -> opts.simulationDistance().set(Integer.parseInt(val));
                    case "gamma" -> opts.gamma().set(Double.parseDouble(val));
                    case "guiScale" -> opts.guiScale().set(Integer.parseInt(val));
                    case "autoJump" -> opts.autoJump().set(Boolean.parseBoolean(val));
                    case "bobView" -> opts.bobView().set(Boolean.parseBoolean(val));
                    case "fullscreen" -> opts.fullscreen().set(Boolean.parseBoolean(val));
                }
                configChanged = true;
            } catch (Exception ignored) {
            }
        }

        if (configChanged) {
            opts.save();
        }

        // 3. Externe Mod-Configs im config/-Ordner anpassen ([MODCFG: datei.toml|key=value])
        Pattern modCfgPattern = Pattern.compile("\\[MODCFG:\\s*([^|\\]]+)\\|([a-zA-Z0-9_.-]+)=([^\\]]+)\\]");
        Matcher modCfgMatcher = modCfgPattern.matcher(aiResponse);
        while (modCfgMatcher.find()) {
            String fileName = modCfgMatcher.group(1).trim();
            String key = modCfgMatcher.group(2).trim();
            String newVal = modCfgMatcher.group(3).trim();
            updateModConfigFile(fileName, key, newVal);
        }

        return aiResponse
                .replaceAll("\\[CMD:[^\\]]+\\]", "")
                .replaceAll("\\[CFG:[^\\]]+\\]", "")
                .replaceAll("\\[MODCFG:[^\\]]+\\]", "")
                .trim();
    }

    private static void updateModConfigFile(String fileName, String key, String newValue) {
        try {
            Path configDir = FMLPaths.CONFIGDIR.get();
            Path targetFile = configDir.resolve(fileName).normalize();
            if (!targetFile.startsWith(configDir) || !Files.exists(targetFile)) return;

            String content = Files.readString(targetFile, StandardCharsets.UTF_8);
            String regex = "(?m)^(\\s*" + Pattern.quote(key) + "\\s*=\\s*).*$";
            String updated = content.replaceAll(regex, "$1" + Matcher.quoteReplacement(newValue));
            Files.writeString(targetFile, updated, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }
}
