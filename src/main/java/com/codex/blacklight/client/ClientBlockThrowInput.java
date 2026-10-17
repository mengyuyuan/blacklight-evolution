package com.codex.blacklight.client;
import com.codex.blacklight.combat.BlockThrowSystem;
import com.codex.blacklight.network.ClawActionPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/** Physical edges follow rebound keys; a lost focus/menu cancels instead of releasing. */
public final class ClientBlockThrowInput {
    private static net.minecraft.client.player.LocalPlayer owner;
    private static int blockId=-1,sentAt;
    private static boolean previousLeft,previousRight,charging;
    private ClientBlockThrowInput(){}
    private static void send(ClawActionPayload.Action action){
        if(action==ClawActionPayload.Action.BLOCK_THROW_RELEASE){ClientMovementEvents.sendAttackDirection(Minecraft.getInstance());ClientBlockThrowAim.update(true);}
        PacketDistributor.sendToServer(new ClawActionPayload(action));
    }
    /** Use the callback edge itself: a short click may end before the next tick polls GLFW. */
    public static boolean mouseEdge(int button,int action){
        var mc=Minecraft.getInstance();
        if(mc.player==null||BlockThrowSystem.held(mc.player)==null)return false;
        boolean attack=SkillBindings.mouse(mc.options.keyAttack,button),use=SkillBindings.mouse(mc.options.keyUse,button);
        if(!attack&&!use)return false;
        boolean down=action==org.lwjgl.glfw.GLFW.GLFW_PRESS;
        return poll(attack?down:SkillBindings.down(mc.options.keyAttack),use?down:SkillBindings.down(mc.options.keyUse));
    }
    public static boolean poll(boolean left,boolean right){
        var mc=Minecraft.getInstance();
        if(owner!=mc.player){owner=mc.player;blockId=-1;charging=false;previousLeft=left;previousRight=right;}
        var block=mc.player==null?null:BlockThrowSystem.held(mc.player);
        boolean active=block!=null&&mc.screen==null&&mc.isWindowActive()&&mc.getCameraEntity()==mc.player;
        if(!active){
            if(charging&&mc.getConnection()!=null)send(ClawActionPayload.Action.BLOCK_THROW_CANCEL);
            charging=false;blockId=-1;previousLeft=left;previousRight=right;return block!=null;
        }
        ClientBlockThrowAim.update(false);
        mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);
        if(blockId!=block.getId()){blockId=block.getId();charging=false;previousRight=right;}
        if(right&&!previousRight){send(ClawActionPayload.Action.CONSUME);charging=false;}
        else if(!right){
            if(left&&!previousLeft&&!block.throwing()){send(ClawActionPayload.Action.BLOCK_THROW_START);charging=true;sentAt=mc.player.tickCount;}
            else if(left&&charging&&mc.player.tickCount-sentAt>=10){send(ClawActionPayload.Action.BLOCK_THROW_START);sentAt=mc.player.tickCount;}
            if(!left&&previousLeft&&charging){
                // Renew before release so a stalled client cannot silently lose a valid throw.
                // An expired server charge restarts at zero; it never grants an unearned bonus.
                send(ClawActionPayload.Action.BLOCK_THROW_START);
                send(ClawActionPayload.Action.BLOCK_THROW_RELEASE);charging=false;
            }
        }
        previousLeft=left;previousRight=right;return true;
    }
}
