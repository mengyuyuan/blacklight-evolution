package com.codex.blacklight.client;

import com.codex.blacklight.network.ClawActionPayload;
import com.codex.blacklight.power.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;
import com.codex.blacklight.forge.PacketDistributor;

public final class GazeInput {
    public static final KeyMapping KEY=new KeyMapping("key.blacklight.gaze",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_J,"key.categories.blacklight");
    private static LocalPlayer owner;
    private static boolean held,wasDown;
    public static void poll(){
        var mc=Minecraft.getInstance();
        var p=mc.player;
        boolean down=KEY.isDown();
        if(p==null || owner!=p || mc.screen!=null || !mc.isWindowActive() || p.isPassenger() || !UtilityPowerSystem.active(p,PowerForm.GAZE_VACUOLE)){
            owner=p;
            held=false;
        }else if(down && !wasDown){
            held=true;
        }else if(held && !down){
            held=false;
            PacketDistributor.sendToServer(new ClawActionPayload(ClawActionPayload.Action.GAZE_RELEASE));
        }
        wasDown=down;
    }
    public static boolean preview(){return held;}
    private GazeInput(){}
}
