package com.codex.blacklight.client.renderer;

import com.codex.blacklight.network.CombatImpactPayload;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;

/** Ground cracks retain their terrain presentation; body contacts use directional spray. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class CombatImpactRenderer {
    private record Crack(Vec3 a,Vec3 b,double distance){}
    private record Burst(ClientLevel level,CombatImpactPayload event,double started,List<Crack> cracks){}
    private static final List<Burst> BURSTS=new ArrayList<>();
    public static void accept(CombatImpactPayload p){
        var level=Minecraft.getInstance().level;if(level!=null)acceptAt(p,level.getGameTime());
    }
    public static void acceptAt(CombatImpactPayload p,double started){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||!Double.isFinite(started)||!Double.isFinite(p.x()+p.y()+p.z()))return;
        var center=new Vec3(p.x(),p.y()+.016,p.z());var cracks=new ArrayList<Crack>();
        if(p.ground())for(int ray=0;ray<13;ray++){
            Vec3 prior=center;double angle=ray*Math.PI*2/13;
            for(int step=1;step<=4;step++){
                double length=p.radius()*step/4.0*(.72+.18*Math.sin(ray*4.7));double a=angle+Math.sin(ray*7+step*4.1)*.13;
                var at=center.add(Math.cos(a)*length,0,Math.sin(a)*length);
                var surface=mc.level.clip(new ClipContext(at.add(0,.22,0),at.add(0,-.25,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
                if(surface.getType()!=HitResult.Type.BLOCK||surface.getDirection()!=net.minecraft.core.Direction.UP)break;
                var next=surface.getLocation().add(0,.018,0);cracks.add(new Crack(prior,next,length));prior=next;
            }
        }
        if(BURSTS.size()>=40)BURSTS.removeFirst();
        if(p.ground()&&!p.slash()){
            // dx carries an explicit shockwave strength (0 = derive from the contact size).
            float strength=p.dx()>0?p.dx():(float)Math.max(.12,Math.min(.5,p.radius()/7.0));
            TerrainRipple.acceptAt(p.x(),p.y(),p.z(),p.dx()>0?p.radius():p.radius()*1.7,strength,started);
        }
        BURSTS.add(new Burst(mc.level,p,started,List.copyOf(cracks)));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=com.codex.blacklight.client.ShaderPackCompat.effectStage())return;
        var mc=Minecraft.getInstance();if(mc.level==null){BURSTS.clear();return;}
        double time=com.codex.blacklight.combat.ContactEffectsQueue.renderTime(mc.level.getGameTime(),e.getPartialTick().getGameTimeDeltaPartialTick(false));
        TerrainRipple.render(e.getPoseStack(),e.getCamera().getPosition(),time);
        BURSTS.removeIf(b->b.level!=mc.level||time-b.started>(b.event.ground()?18:b.event.slash()?14:7));
        if(BURSTS.isEmpty())return;
        boolean rich=com.codex.blacklight.config.PassiveHudConfig.CINEMATIC_COMBAT.get();
        var type=RenderType.entityTranslucent(ConsumedMobVisuals.WHITE);var buffers=com.codex.blacklight.client.ShaderPackCompat.buffers();
        var sprayType=ImpactSprayMaterial.type();
        var stack=e.getPoseStack();var camera=e.getCamera().getPosition();stack.pushPose();stack.translate(-camera.x,-camera.y,-camera.z);
        var glows=new ArrayList<Vec3[]>();var glowColors=new ArrayList<Integer>();
        for(var b:BURSTS){
            var p=b.event;double age=Math.max(0,time-b.started);var center=new Vec3(p.x(),p.y()+(p.ground()?.02:0),p.z());
            int seed=(int)(b.started*31+Double.hashCode(p.x())*7+Double.hashCode(p.z()));
            if(!p.ground()&&(center.distanceToSqr(camera)>64*64||!e.getFrustum().isVisible(
                    new net.minecraft.world.phys.AABB(center.add(-2,-2,-2),center.add(2,2,2)))))continue;
            // Immediate buffer sources finish the previous non-fixed material
            // when switching. Never retain a consumer across getBuffer calls.
            var out=buffers.getBuffer(p.ground()?type:sprayType);
            if(p.slash()){
                int light=net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level,net.minecraft.core.BlockPos.containing(center));
                ImpactSpray.flesh(stack,out,center,new Vec3(p.dx(),p.dy(),p.dz()),camera,
                        com.codex.blacklight.infection.InfectedAttackStyle.TEAR,age,p.radius(),seed,false,light);
            }else if(p.ground()){
                double extent=p.radius()*Math.min(1,(age+1)/3);int alpha=(int)(210*Math.clamp((18-age)/9,0,1));
                for(var crack:b.cracks)if(crack.distance<=extent){
                    var along=crack.b.subtract(crack.a);var side=new Vec3(-along.z,0,along.x).normalize().scale(.034*(1-crack.distance/(p.radius()*1.4)));
                    quad(stack,out,alpha<<24|0x1d1512,crack.a.add(side),crack.b.add(side),crack.b.subtract(side),crack.a.subtract(side));
                    if(rich&&age<6){
                        var inner=side.scale(.35);int hot=(int)(190*(1-age/6));
                        quad(stack,out,hot<<24|0xb3182a,crack.a.add(inner).add(0,.003,0),crack.b.add(inner).add(0,.003,0),crack.b.subtract(inner).add(0,.003,0),crack.a.subtract(inner).add(0,.003,0));
                    }
                }
                if(age<8)for(int i=0;i<36;i++){
                    double a=i*Math.PI*2/36,c=a+Math.PI*1.6/36,ease=1-Math.pow(1-Math.min(1,(age+.6)/8),3);
                    double r=p.radius()*1.15*ease*(1+.04*Math.sin(i*3+seed));
                    double inner=Math.max(0,r-.22-.25*ease);int opacity=(int)(78*(1-age/8));
                    quad(stack,out,opacity<<24|0xc7b6a6,point(center,a,inner),point(center,a,r),point(center,c,r),point(center,c,inner));
                }
                if(rich&&age<4){
                    // Bright flash disc at the contact, then gone.
                    double k=1-age/4,r=p.radius()*(.35+.25*age/4);
                    for(int i=0;i<16;i++){double a=i*Math.PI/8,c=a+Math.PI/8;
                        glows.add(new Vec3[]{center.add(0,.03,0),point(center.add(0,.03,0),a,r),point(center.add(0,.03,0),c,r),center.add(0,.03,0)});glowColors.add((int)(130*k)<<24|0xff6a3c);}
                }
            }else{
                int light=net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level,net.minecraft.core.BlockPos.containing(center));
                ImpactSpray.sparks(stack,out,center,new Vec3(p.dx(),p.dy(),p.dz()),camera,age,p.radius(),seed,light);
            }
        }
        buffers.endBatch(type);
        buffers.endBatch(sprayType);
        if(!glows.isEmpty()){
            var glowType=RenderType.lightning();var glow=buffers.getBuffer(glowType);
            for(int i=0;i<glows.size();i++)for(var v:glows.get(i))glowVertex(stack,glow,v,glowColors.get(i));
            buffers.endBatch(glowType);
        }
        stack.popPose();
    }
    private static Vec3 point(Vec3 c,double a,double r){return c.add(Math.cos(a)*r,0,Math.sin(a)*r);}
    private static void glowVertex(PoseStack stack,VertexConsumer out,Vec3 p,int color){
        out.addVertex(stack.last(),(float)p.x,(float)p.y,(float)p.z).setColor(color);
    }
    private static void quad(PoseStack stack,VertexConsumer out,int color,Vec3... points){
        for(var p:points)out.addVertex(stack.last(),(float)p.x,(float)p.y,(float)p.z).setColor(color).setUv(.5F,.5F)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xf000f0).setNormal(stack.last(),0,1,0);
    }
}
