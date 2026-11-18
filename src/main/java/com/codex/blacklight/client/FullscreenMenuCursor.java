package com.codex.blacklight.client;

import com.codex.blacklight.config.PassiveHudConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/** Software menu pointer for Windows fullscreen drivers that lose the native cursor. */
@EventBusSubscriber(modid = "blacklight", value = Dist.CLIENT)
public final class FullscreenMenuCursor {
    private static final String[] ARROW = {
            "X", "XX", "XOX", "XOOX", "XOOOX", "XOOOOX", "XOOOOOX",
            "XOOOOOOX", "XOOOOOOOX", "XOOOOOOOOX", "XOOOOXXXXX",
            "XOOXOX", "XOX XOX", "XX  XOX", "X    XOX", "     XOX", "      X"
    };
    private static long hiddenWindow;

    private FullscreenMenuCursor() {}

    private static boolean active(Minecraft mc) {
        return mc.getWindow().isFullscreen() && mc.screen != null && mc.isWindowActive()
                && !mc.mouseHandler.isMouseGrabbed() && PassiveHudConfig.FULLSCREEN_MENU_CURSOR.get();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!active(mc)) restore(mc);
    }

    private static void restore(Minecraft mc) {
        if (hiddenWindow == 0) return;
        // Never release vanilla's captured camera, or touch a window we no longer own.
        if (hiddenWindow == mc.getWindow().getWindow()
                && GLFW.glfwGetInputMode(hiddenWindow, GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_HIDDEN)
            GLFW.glfwSetInputMode(hiddenWindow, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        hiddenWindow = 0;
    }

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Post event) {
        var mc = Minecraft.getInstance();
        if (!active(mc) || event.getScreen() != mc.screen) { restore(mc); return; }
        long window = mc.getWindow().getWindow();
        int mode = GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR);
        if (mode == GLFW.GLFW_CURSOR_DISABLED) return;
        if (mode == GLFW.GLFW_CURSOR_NORMAL) {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
            hiddenWindow = window;
        }
        var graphics = event.getGuiGraphics();
        var pose = graphics.pose();
        // MouseHandler uses window pixels. Keep the hotspot exact and the arrow's
        // physical size stable even at Auto GUI scale on a 4K display.
        double sx = (double) graphics.guiWidth() / mc.getWindow().getScreenWidth();
        double sy = (double) graphics.guiHeight() / mc.getWindow().getScreenHeight();
        pose.pushPose();
        pose.translate(mc.mouseHandler.xpos() * sx, mc.mouseHandler.ypos() * sy, 1000);
        pose.scale((float) (sx * 1.5), (float) (sy * 1.5), 1);
        for (int y = 0; y < ARROW.length; y++) for (int x = 0; x < ARROW[y].length(); x++) {
            char pixel = ARROW[y].charAt(x);
            if (pixel != ' ') graphics.fill(x, y, x + 1, y + 1, pixel == 'X' ? 0xFF101010 : 0xFFFFFFFF);
        }
        pose.popPose();
    }
}
