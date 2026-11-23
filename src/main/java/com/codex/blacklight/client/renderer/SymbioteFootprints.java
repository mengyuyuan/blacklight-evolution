package com.codex.blacklight.client.renderer;

import com.codex.blacklight.item.SymbioteCharm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.ArrayDeque;

/** Three small lobes of biomass peel away at each footfall and dry up on the actual support surface. */
// Archived footprint renderer. Equipped armor renders exclusively in SymbioteLayer.
public final class SymbioteFootprints {
    private record Print(Vec3 position,Vec3 forward,double side,long birth,int light) {}
    private static final ArrayDeque<Print> marks=new ArrayDeque<>();
    private static final Vec3 UP=new Vec3(0,1,0);
    private static ClientLevel world;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(world!=mc.level){marks.clear();world=mc.level;}
        if(world==null || mc.isPaused())return;
        long now=world.getGameTime();
        marks.removeIf(mark->now<mark.birth || now-mark.birth>=16);
        for(var player:world.players())footstep(player,now);
    }

    private static void footstep(Player p,long now) {
        if(!p.onGround() || p.isInvisible() || p.isSpectator() || !SymbioteCharm.wearing(p,EquipmentSlot.FEET))return;
        int step=(int)(p.walkDist*2);
        if(step==(int)(p.walkDistO*2))return;
        var forward=Vec3.directionFromRotation(0,p.yBodyRot);
        double side=step%2==0?1:-1;
        var toe=p.position().add(forward.z*.13*side,.22,-forward.x*.13*side);
        var hit=world.clip(new ClipContext(toe,toe.add(0,-.73,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(hit.getType()!=HitResult.Type.BLOCK || hit.getDirection()!=net.minecraft.core.Direction.UP)return;
        var at=hit.getLocation().add(0,.009,0);
        if(marks.size()>=96)marks.removeFirst();
        marks.addLast(new Print(at,forward,side,now,LevelRenderer.getLightColor(world,BlockPos.containing(at))));
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES || marks.isEmpty() || Minecraft.getInstance().level!=world)return;
        var camera=event.getCamera().getPosition();
        var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
        var type=RenderType.entityTranslucent(ConsumedMobVisuals.WHITE);
        var pose=event.getPoseStack();
        var out=buffers.getBuffer(type);
        double now=world.getGameTime()+event.getPartialTick();
        pose.pushPose();
        try{
            pose.translate(-camera.x,-camera.y,-camera.z);
            for(var mark:marks){
                double t=(now-mark.birth)/16;
                if(t<0 || t>=1 || mark.position.distanceToSqr(camera)>48*48)continue;
                Vec3 tangent=new Vec3(mark.forward.z,0,-mark.forward.x).scale(mark.side);
                for(int i=0;i<3;i++){
                    var center=mark.position.add(tangent.scale((i-1)*(.045+.028*t))).add(mark.forward.scale(i*.033));
                    var tip=center.add(mark.forward.scale(.065*(1-t))).add(tangent.scale(.017));
                    var heel=center.subtract(mark.forward.scale((.10+i*.025)*(1-t)));
                    var edge=tangent.scale((.017+i*.004)*(1-t));
                    int color=((int)(106*(1-t))<<24) | (i==1?0x343441:0x171820);
                    leaf(pose,out,mark.light,tip,center.add(edge),heel,center.subtract(edge),color);
                }
            }
        }finally{pose.popPose();buffers.endBatch(type);}
    }
    private static void leaf(PoseStack pose,VertexConsumer out,int light,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color) {
        ConsumedMobVisuals.vertex(pose,out,light,a,UP,color,.5F,.5F);
        ConsumedMobVisuals.vertex(pose,out,light,b,UP,color,.5F,.5F);
        ConsumedMobVisuals.vertex(pose,out,light,c,UP,color,.5F,.5F);
        ConsumedMobVisuals.vertex(pose,out,light,d,UP,color,.5F,.5F);
    }
}
