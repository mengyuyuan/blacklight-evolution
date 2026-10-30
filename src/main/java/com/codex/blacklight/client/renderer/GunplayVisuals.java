package com.codex.blacklight.client.renderer;

import com.codex.blacklight.firearms.GunplayEffects;
import com.codex.blacklight.network.GunplayFeedbackPayload;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.util.*;

/** Bounded local geometry from server receipts; no damage, collision or client-to-server messages. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class GunplayVisuals {
    private record Effect(GunplayFeedbackPayload data,double at){}
    private static final ArrayDeque<Effect> ACTIVE=new ArrayDeque<>();
    private static ClientLevel world;
    private static long window=-1,received;
    private static int count;
    private static double kickAt=-1000,kickStrength;
    public static long received(){return received;}
    public static void accept(GunplayFeedbackPayload p){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||!p.valid())return;
        if(world!=mc.level){ACTIVE.clear();world=mc.level;window=-1;kickAt=-1000;}
        var point=new Vec3(p.x(),p.y(),p.z());if(mc.player.position().distanceToSqr(point)>112*112)return;
        long now=mc.level.getGameTime();if(window!=now){window=now;count=0;}
        if(count++>=32&&p.event()==0)return;
        while(ACTIVE.size()>=com.codex.blacklight.config.GunplayClientConfig.EFFECT_LIMIT.get())ACTIVE.removeFirst();ACTIVE.addLast(new Effect(p,now));received++;
        boolean own=p.actor()==mc.player.getId()||mc.player.getVehicle()!=null&&p.actor()==mc.player.getVehicle().getId();
        if(own&&p.event()==0){kickAt=now;kickStrength=GunplayEffects.cannon(p.weapon())?.65:.14;}
    }
    @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload e){if(e.getLevel()==world){ACTIVE.clear();world=null;kickAt=-1000;}}
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e){
        var mc=Minecraft.getInstance();if(mc.level!=world||mc.player==null||mc.screen!=null||mc.isPaused()||!com.codex.blacklight.config.GunplayClientConfig.CAMERA_KICK.get()
                ||!mc.options.getCameraType().isFirstPerson())return;
        double age=mc.level.getGameTime()+e.getPartialTick()-kickAt;
        if(age>=0&&age<6){float kick=(float)(Math.exp(-age*.7)*kickStrength);e.setPitch(e.getPitch()-kick);}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.level!=world){ACTIVE.clear();world=mc.level;return;}
        double now=mc.level.getGameTime()+e.getPartialTick().getGameTimeDeltaPartialTick(false);ACTIVE.removeIf(f->now<f.at||now-f.at>24);
        if(ACTIVE.isEmpty())return;
        var pose=e.getPoseStack();var camera=e.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();var type=RenderType.entityTranslucent(ConsumedMobVisuals.WHITE);var out=buffers.getBuffer(type);
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        for(var fx:ACTIVE){var p=fx.data;double age=now-fx.at;Vec3 start=new Vec3(p.x(),p.y(),p.z()),end=new Vec3(p.endX(),p.endY(),p.endZ());
            if(start.distanceToSqr(camera)>96*96)continue;
            if(p.event()==1){explosion(pose,out,end,p,age);continue;}
            var axis=end.subtract(start).normalize();if(axis.lengthSqr()<.01)axis=new Vec3(0,0,1);
            boolean local=mc.player!=null&&p.actor()==mc.player.getId()&&mc.options.getCameraType().isFirstPerson();
            if(age<1.8&&!local){double size=(GunplayEffects.cannon(p.weapon())?.85:GunplayEffects.bullet(p.weapon())?.23:.4)*(1-age/1.8);
                GunplayGeometry.rod(pose,out,start,start.add(axis.scale(size*2.2)),size*.45,0,0xffffc15b,LightTexture.FULL_BRIGHT);
                GunplayGeometry.rod(pose,out,start,start.add(axis.scale(size*1.2)),size*.24,0,0xfffff5c3,LightTexture.FULL_BRIGHT);
            }
            if(GunplayEffects.bullet(p.weapon())){
                if(age<3&&com.codex.blacklight.config.GunplayClientConfig.TRACERS.get()){double length=start.distanceTo(end),head=Math.min(length,age*36+2),tail=Math.max(.5,head-7);
                    if(head>tail)GunplayGeometry.rod(pose,out,start.add(axis.scale(tail)),start.add(axis.scale(head)),.009,.006,GunplayGeometry.alpha(0xffdf9e,1-age/3),LightTexture.FULL_BRIGHT);}
                if(com.codex.blacklight.config.GunplayClientConfig.CASINGS.get())casing(pose,out,start,axis,p.seed(),age);
            }
            if(p.impact()>0&&age<10)impact(pose,out,end,p,age);
        }
        pose.popPose();buffers.endBatch(type);
    }
    private static void casing(PoseStack pose,VertexConsumer out,Vec3 start,Vec3 axis,long seed,double age){
        var right=axis.cross(new Vec3(0,1,0)).normalize();double t=age/20;
        var p=start.subtract(axis.scale(.32)).add(right.scale(.15+t*(1.1+(seed&3)*.12))).add(0,.08+t*.9-t*t*2.8,0);
        if(t>.9)return;var direction=new Vec3(Math.sin(seed+age*.7),Math.cos(age*.6),Math.cos(seed+age*.7)).scale(.045);
        GunplayGeometry.rod(pose,out,p.subtract(direction),p.add(direction),.014,.014,GunplayGeometry.alpha(0xd2b06a,Math.min(1,(1.1-t)*4)),0x00b000b0);
    }
    private static void impact(PoseStack pose,VertexConsumer out,Vec3 end,GunplayFeedbackPayload p,double age){
        var n=new Vec3(p.nx(),p.ny(),p.nz()).normalize();if(n.lengthSqr()<.01)n=new Vec3(0,1,0);
        var u=n.cross(new Vec3(0,1,0));if(u.lengthSqr()<.01)u=new Vec3(1,0,0);u=u.normalize();var v=n.cross(u);double t=age/20;
        int rgb=p.impact()==2?0x872b34:p.impact()==3?0xffd490:0xa19b8c;
        for(int i=0;i<(p.impact()==3?6:4);i++){
            double a=i*2.399+(p.seed()&15),speed=.55+i*.12;var d=u.scale(Math.cos(a)*speed).add(v.scale(Math.sin(a)*speed)).add(n.scale(.8));
            var head=end.add(n.scale(.025)).add(d.scale(t)).add(0,-t*t*1.8,0);
            if(p.impact()==3)GunplayGeometry.rod(pose,out,head.subtract(d.scale(.025)),head,.009,0,GunplayGeometry.alpha(rgb,1-age/10),LightTexture.FULL_BRIGHT);
            else GunplayGeometry.box(pose,out,head,.025,.025,.025,GunplayGeometry.alpha(rgb,1-age/10),0x00b000b0);
        }
    }
    private static void explosion(PoseStack pose,VertexConsumer out,Vec3 at,GunplayFeedbackPayload p,double age){
        double radius=Math.max(.5,p.size()),t=age/20,fade=Math.max(0,1-age/24);boolean infected=p.weapon()==7;
        var normal=new Vec3(p.nx(),p.ny(),p.nz()).normalize();if(normal.lengthSqr()<.01)normal=new Vec3(0,1,0);
        var side=normal.cross(new Vec3(0,1,0));if(side.lengthSqr()<.01)side=new Vec3(1,0,0);side=side.normalize();var up=normal.cross(side);
        if(age<5){double r=radius*.32*(1-age/5);GunplayGeometry.box(pose,out,at.add(normal.scale(.12)),r,r,r,GunplayGeometry.alpha(infected?0x8d2856:0xfff0c6,1-age/5),LightTexture.FULL_BRIGHT);}
        if(age<8)for(int i=0;i<7;i++){
            double angle=i*2.399+(p.seed()&7)*.13,reveal=Math.min(1,(age+.3)/2),decay=1-age/8;
            var direction=normal.scale(.8).add(side.scale(Math.cos(angle)*.65)).add(up.scale(Math.sin(angle)*.65)).normalize();
            var from=at.add(normal.scale(.08));var to=from.add(direction.scale(radius*(.35+(i%3)*.09)*reveal));
            GunplayGeometry.rod(pose,out,from,to,radius*.10*decay,.005,GunplayGeometry.alpha(infected?0xa63857:i%2==0?0xffb84c:0xe9752d,decay*.8),LightTexture.FULL_BRIGHT);
        }
        for(int i=0;i<9;i++){
            double a=i*Math.PI*2/9+(p.seed()&7)*.13;var radial=new Vec3(Math.cos(a),0,Math.sin(a));
            double spread=radius*(.2+t*.95);var smoke=at.add(radial.scale(spread)).add(0,.15+t*(.7+(i%3)*.2),0);
            double size=(.12+t*.38)*radius;
            GunplayGeometry.box(pose,out,smoke,size,size*.8,size,GunplayGeometry.alpha(infected?0x542832:i%2==0?0x68615b:0x918471,fade*.42),0x00900090);
            if(age<11){var debris=at.add(radial.scale(t*(2.2+radius*.4))).add(0,.2+t*(1.7+i%3)-t*t*4,0);
                GunplayGeometry.box(pose,out,debris,.075,.06,.075,GunplayGeometry.alpha(0x766b60,Math.max(0,1-age/14)),0x00b000b0);}
            if(age<7){double head=radius*Math.min(1,age/6);GunplayGeometry.rod(pose,out,at.add(radial.scale(head*.7)).add(0,.03,0),at.add(radial.scale(head)).add(0,.03,0),.016,.004,GunplayGeometry.alpha(0xd2c1a0,1-age/7),LightTexture.FULL_BRIGHT);}
        }
    }
    private GunplayVisuals(){}
}
