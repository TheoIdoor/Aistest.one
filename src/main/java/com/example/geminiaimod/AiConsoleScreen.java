package com.example.geminiaimod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class AiConsoleScreen extends Screen {
    private EditBox inputField;
    private static final List<String> chatHistory = new ArrayList<>();

    public AiConsoleScreen() {
        super(Component.literal("Gemini KI-Konsole"));
        if (chatHistory.isEmpty()) {
            chatHistory.add("§b[Gemini]:§r Willkommen in der KI-Konsole!");
            if (!GeminiService.hasApiKey()) {
                chatHistory.add("§e[Info]:§r Bitte gib zuerst deinen Gemini API-Key ein mit: §a/apikey DEIN_KEY");
            } else {
                chatHistory.add("§7Tipp: Schreibe Befehle, Wünsche oder Config-Änderungen einfach als normalen Satz.");
            }
        }
    }

    @Override
    protected void init() {
        super.init();

        this.inputField = new EditBox(this.font, 20, this.height - 35, this.width - 155, 20, Component.literal("Eingabe"));
        this.inputField.setMaxLength(1024);
        this.inputField.setFocused(true);
        this.addRenderableWidget(this.inputField);
        this.setInitialFocus(this.inputField);

        Button sendButton = Button.builder(Component.literal("Senden"), button -> sendPrompt())
                .bounds(this.width - 130, this.height - 35, 55, 20)
                .build();
        this.addRenderableWidget(sendButton);

        Button clearButton = Button.builder(Component.literal("Leeren"), button -> chatHistory.clear())
                .bounds(this.width - 70, this.height - 35, 50, 20)
                .build();
        this.addRenderableWidget(clearButton);
    }

    private void sendPrompt() {
        String prompt = this.inputField.getValue().trim();
        if (prompt.isEmpty()) return;

        this.inputField.setValue("");

        if (prompt.startsWith("/apikey ")) {
            String newKey = prompt.substring("/apikey ".length()).trim();
            GeminiService.saveApiKey(newKey);
            chatHistory.add("§a[System]:§r Gemini API-Key wurde erfolgreich in config/geminiaimod.json gespeichert!");
            return;
        }

        chatHistory.add("§a[Du]:§r " + prompt);
        chatHistory.add("§7[Gemini denkt nach...]§r");

        GeminiService.askGeminiAsync(prompt, response -> {
            chatHistory.removeIf(msg -> msg.equals("§7[Gemini denkt nach...]§r"));
            chatHistory.add("§b[Gemini]:§r " + response);
        });
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            sendPrompt();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.fill(15, 25, this.width - 15, this.height - 45, 0xCC101018);
        guiGraphics.renderOutline(15, 25, this.width - 30, this.height - 70, 0xFF3A86FF);

        guiGraphics.drawCenteredString(this.font, "§l§bGemini KI-Konsole & Live Config-Manager", this.width / 2, 10, 0xFFFFFF);

        int yOffset = 35;
        int maxWidth = this.width - 45;
        int startIndex = Math.max(0, chatHistory.size() - 12);

        for (int i = startIndex; i < chatHistory.size(); i++) {
            List<FormattedCharSequence> lines = this.font.split(Component.literal(chatHistory.get(i)), maxWidth);
            for (FormattedCharSequence line : lines) {
                if (yOffset < this.height - 55) {
                    guiGraphics.drawString(this.font, line, 22, yOffset, 0xFFFFFF);
                    yOffset += 11;
                }
            }
            yOffset += 4;
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
