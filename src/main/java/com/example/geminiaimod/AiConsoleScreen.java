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
    private Button sendButton;

    // Static, damit der Chatverlauf beim Schließen und Wiederöffnen erhalten bleibt
    private static final List<String> CHAT_HISTORY = new ArrayList<>();
    private int scrollOffset = 0;
    private boolean isWaitingForResponse = false;

    private static final int LINE_HEIGHT = 11;

    public AiConsoleScreen() {
        super(Component.literal("Gemini KI-Konsole"));
        if (CHAT_HISTORY.isEmpty()) {
            CHAT_HISTORY.add("§b[System]: §fWillkommen! Stelle eine Frage oder setze deinen Key mit §e/apikey DEIN_KEY");
        }
    }

    @Override
    protected void init() {
        super.init();

        int inputWidth = this.width - 105;
        this.inputField = new EditBox(this.font, 15, this.height - 30, inputWidth, 20, Component.literal("Nachricht..."));
        // Wichtig: Standardmäßig erlaubt Minecraft nur 32 Zeichen (schneidet API-Keys sonst ab!)
        this.inputField.setMaxLength(512);
        this.inputField.setFocused(true);
        this.addRenderableWidget(this.inputField);
        this.setInitialFocus(this.inputField);

        this.sendButton = Button.builder(Component.literal("Senden"), btn -> this.handleSendMessage())
                .bounds(this.width - 85, this.height - 30, 70, 20)
                .build();
        this.addRenderableWidget(this.sendButton);

        scrollToBottom();
    }

    private void handleSendMessage() {
        String text = this.inputField.getValue().trim();
        if (text.isEmpty()) return;

        this.inputField.setValue("");

        // Eigener Befehl zum Leeren des Fensters
        if (text.equalsIgnoreCase("/clear")) {
            CHAT_HISTORY.clear();
            this.scrollOffset = 0;
            return;
        }

        // API-Key Befehl abfangen (unterstützt /apikey und !apikey)
        if (text.toLowerCase().startsWith("/apikey ") || text.toLowerCase().startsWith("!apikey ")) {
            String newKey = text.substring(8).trim();
            GeminiService.saveApiKey(newKey);
            addMessage("§a[System]: API-Key wurde erfolgreich in config/geminiaimod.json gespeichert!");
            return;
        }

        if (this.isWaitingForResponse) {
            addMessage("§e[System]: Bitte warte kurz, die KI verarbeitet noch deine letzte Anfrage...");
            return;
        }

        addMessage("§e[Du]: §f" + text);
        addMessage("§7[Gemini denkt nach...]");
        this.isWaitingForResponse = true;

        GeminiService.askGeminiAsync(text, response -> {
            // Entferne den "denkt nach..." Platzhalter wieder
            if (!CHAT_HISTORY.isEmpty() && CHAT_HISTORY.get(CHAT_HISTORY.size() - 1).equals("§7[Gemini denkt nach...]")) {
                CHAT_HISTORY.remove(CHAT_HISTORY.size() - 1);
            }
            this.isWaitingForResponse = false;
            addMessage("§b[Gemini]: §f" + response);
        });
    }

    public void addMessage(String msg) {
        CHAT_HISTORY.add(msg);
        scrollToBottom();
    }

    private List<FormattedCharSequence> getWrappedLines(int maxTextWidth) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String msg : CHAT_HISTORY) {
            for (String paragraph : msg.split("\n")) {
                List<FormattedCharSequence> split = this.font.split(Component.literal(paragraph), maxTextWidth);
                if (split.isEmpty()) {
                    lines.add(FormattedCharSequence.EMPTY);
                } else {
                    lines.addAll(split);
                }
            }
        }
        return lines;
    }

    private void scrollToBottom() {
        if (this.font == null) return;
        int boxWidth = Math.max(50, this.width - 45);
        int boxHeight = Math.max(20, this.height - 70);
        int maxVisibleLines = Math.max(1, (boxHeight - 10) / LINE_HEIGHT);
        List<FormattedCharSequence> allLines = getWrappedLines(boxWidth);
        this.scrollOffset = Math.max(0, allLines.size() - maxVisibleLines);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int boxWidth = Math.max(50, this.width - 45);
        int boxHeight = Math.max(20, this.height - 70);
        int maxVisibleLines = Math.max(1, (boxHeight - 10) / LINE_HEIGHT);
        List<FormattedCharSequence> allLines = getWrappedLines(boxWidth);
        int maxScroll = Math.max(0, allLines.size() - maxVisibleLines);

        if (delta > 0) {
            this.scrollOffset = Math.max(0, this.scrollOffset - 2);
        } else if (delta < 0) {
            this.scrollOffset = Math.min(maxScroll, this.scrollOffset + 2);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Senden per Enter-Taste oder Numpad-Enter
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            this.handleSendMessage();
            return true;
        }
        // Auch mit Pfeiltasten Hoch/Runter oder Bild-Auf/Ab scrollen
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            this.mouseScrolled(0, 0, 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            this.mouseScrolled(0, 0, -1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // Titel oben mittig
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        int boxLeft = 15;
        int boxTop = 25;
        int boxRight = this.width - 15;
        int boxBottom = this.height - 40;
        int boxHeight = boxBottom - boxTop;
        int textWidth = (boxRight - boxLeft) - 18;

        // Hintergrund des Chat-Kastens + dezenter Rahmen
        graphics.fill(boxLeft, boxTop, boxRight, boxBottom, 0xA0000000);
        graphics.fill(boxLeft, boxTop, boxRight, boxTop + 1, 0xFF555555);
        graphics.fill(boxLeft, boxBottom - 1, boxRight, boxBottom, 0xFF555555);

        List<FormattedCharSequence> allLines = getWrappedLines(Math.max(50, textWidth));
        int maxVisibleLines = Math.max(1, (boxHeight - 10) / LINE_HEIGHT);
        int maxScroll = Math.max(0, allLines.size() - maxVisibleLines);

        if (this.scrollOffset > maxScroll) this.scrollOffset = maxScroll;
        if (this.scrollOffset < 0) this.scrollOffset = 0;

        // Sichtbare Zeilen zeichnen
        int y = boxTop + 6;
        for (int i = this.scrollOffset; i < allLines.size() && i < this.scrollOffset + maxVisibleLines; i++) {
            graphics.drawString(this.font, allLines.get(i), boxLeft + 6, y, 0xFFFFFF, false);
            y += LINE_HEIGHT;
        }

        // Visueller Scrollbalken auf der rechten Seite (erscheint, sobald der Text länger als das Fenster ist)
        if (maxScroll > 0) {
            int scrollbarX = boxRight - 6;
            int trackTop = boxTop + 4;
            int trackHeight = boxHeight - 8;

            graphics.fill(scrollbarX, trackTop, scrollbarX + 3, trackTop + trackHeight, 0x55FFFFFF);

            int thumbHeight = Math.max(12, (int) ((float) maxVisibleLines / allLines.size() * trackHeight));
            int thumbY = trackTop + (int) ((float) this.scrollOffset / maxScroll * (trackHeight - thumbHeight));
            graphics.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbHeight, 0xFFAAAAAA);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
