package com.codex.blacklight.client;

import com.codex.blacklight.power.BiomassSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import net.minecraftforge.client.event.MovementInputUpdateEvent;

@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class ClientBiomass {
    private static final java.util.Map<Player,Float> SIZES=new java.util.WeakHashMap<>();
    private static Player cameraOwner;
    private static CameraType previousCamera;
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.level==null){SIZES.clear();cameraOwner=null;previousCamera=null;return;}
        for(var p:mc.level.players()){
            boolean active=BiomassSystem.active(p);
            float size=active?com.codex.blacklight.power.BiomassEvolution.scale(p):0;
            if(SIZES.getOrDefault(p,0F)!=size){p.refreshDimensions();SIZES.put(p,size);}
        }
        if(mc.player!=null&&BiomassSystem.active(mc.player)){
            if(cameraOwner!=mc.player){cameraOwner=mc.player;previousCamera=mc.options.getCameraType();mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);}
        }else if(cameraOwner!=null){
            if(cameraOwner==mc.player&&mc.options.getCameraType()==CameraType.THIRD_PERSON_BACK&&previousCamera!=null)mc.options.setCameraType(previousCamera);
            cameraOwner=null;previousCamera=null;
        }
    }
    public static void camera(net.minecraft.client.Camera camera,float partial){
        var mc=Minecraft.getInstance();var p=mc.player;
        if(p==null||!BiomassSystem.active(p)||camera.getEntity()!=p||mc.options.getCameraType().isFirstPerson())return;
        var access=(com.codex.blacklight.mixin.client.PrototypeCameraAccess)camera;
        var eye=p.getEyePosition(partial);var raised=eye.add(0,.45,0);
        var hit=p.level().clip(new net.minecraft.world.level.ClipContext(eye,raised,net.minecraft.world.level.ClipContext.Block.VISUAL,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
        if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)raised=eye;
        access.blacklight$setPosition(raised);
        float pitch=net.minecraft.util.Mth.clamp(camera.getXRot()+18,-35,70);
        access.blacklight$setRotation(camera.getYRot(),pitch);
        access.blacklight$move(-Math.max(0,access.blacklight$getMaxZoom(3F)-.05F),0,0);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void input(MovementInputUpdateEvent e){
        var s=BiomassSystem.state(e.getEntity());if(!s.active())return;
        var input=e.getInput();input.jumping=false;input.shiftKeyDown=false;
        if(s.immobilized()){input.forwardImpulse=0;input.leftImpulse=0;input.up=input.down=input.left=input.right=false;}
        e.getEntity().setSprinting(false);
    }
}
