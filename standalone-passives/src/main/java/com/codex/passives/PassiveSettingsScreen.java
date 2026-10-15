package com.codex.passives;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class PassiveSettingsScreen extends Screen {
    private final Screen parent;
    public PassiveSettingsScreen(Screen parent) { super(Component.translatable("config.adaptive_passives.title")); this.parent = parent; }
    private Component label() { return Component.translatable(PassiveDisplayConfig.SHOW_STACKS.get() ? "config.adaptive_passives.on" : "config.adaptive_passives.off"); }
    @Override protected void init() {
        addRenderableWidget(Button.builder(label(), button -> { PassiveDisplayConfig.toggle(); button.setMessage(label()); }).bounds(width / 2 - 110, height / 2 - 15, 220, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose()).bounds(width / 2 - 60, height / 2 + 45, 120, 20).build());
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        renderBackground(g); super.render(g, x, y, partial);
        g.drawCenteredString(font, title, width / 2, height / 2 - 50, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("config.adaptive_passives.hint"), width / 2, height / 2 + 15, 0xBBBBBB);
    }
}
