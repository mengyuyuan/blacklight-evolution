package com.codex.blacklight.client;

import com.codex.blacklight.network.ContextActionPayload;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.codex.blacklight.forge.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** One intent per physical edge; screens/focus loss cannot queue an attack. */
public final class ContextActionInput {
    public static final KeyMapping KEY=new KeyMapping("key.blacklight.context_action",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_C,"key.categories.blacklight");
    private static boolean wasDown;
    private static Object owner;
    private ContextActionInput(){}
    public static void poll(){
        var mc=Minecraft.getInstance();boolean down=SkillBindings.down(KEY);
        while(KEY.consumeClick()){} // Drain repeats even while a screen owns input.
        if(owner!=mc.player){owner=mc.player;wasDown=down;return;}
        boolean edge=down&&!wasDown;wasDown=down;
        if(!edge||mc.player==null||mc.level==null||mc.screen!=null||!mc.isWindowActive()||mc.isPaused()
                ||!mc.player.isAlive()||mc.player.isSpectator()||mc.player.isPassenger())return;
        var state=com.codex.blacklight.registry.ModAttachments.getData(mc.player,ModAttachments.PLAYER_STATE);
        if(state.isInfected()&&state.isPrototypeMode())PacketDistributor.sendToServer(new ContextActionPayload());
    }
}
