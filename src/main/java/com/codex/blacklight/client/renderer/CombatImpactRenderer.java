package com.codex.blacklight.client.renderer;

import com.codex.blacklight.network.CombatImpactPayload;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import java.util.*;

/** Brief broken radial cracks and pressure flashes; no permanent edits to blocks. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class CombatImpactRenderer {
    private record Crack(Vec3 a,Vec3 b,double distance){}
    private record Burst(ClientLevel level,CombatImpactPayload event,long started,List<Crack> cracks){}
    private static final List<Burst> BURSTS=new ArrayList<>();
    public static void accept(CombatImpactPayload p){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||!Double.isFinite(p.x()+p.y()+p.z()))return;
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
        if(BURSTS.size()>=40)BURSTS.remove(0);
        if(p.ground()&&!p.slash()){
            // dx carries an explicit shockwave strength (0 = derive from the contact size).
            float strength=p.dx()>0?p.dx():(float)Math.max(.12,Math.min(.5,p.radius()/7.0));
            TerrainRipple.accept(p.x(),p.y(),p.z(),p.dx()>0?p.radius():p.radius()*1.7,strength);
        }
        BURSTS.add(new Burst(mc.level,p,mc.level.getGameTime(),List.copyOf(cracks)));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();if(mc.level==null){BURSTS.clear();return;}
        double time=mc.level.getGameTime()+e.getPartialTick();
        TerrainRipple.render(e.getPoseStack(),e.getCamera().getPosition(),time);
        BURSTS.removeIf(b->b.level!=mc.level||time-b.started>(b.event.ground()?18:b.event.slash()?8:7));
        if(BURSTS.isEmpty())return;
        boolean rich=com.codex.blacklight.config.PassiveHudConfig.CINEMATIC_COMBAT.get();
        var type=RenderType.entityTranslucent(ConsumedMobVisuals.WHITE);var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(type);
        var stack=e.getPoseStack();var camera=e.getCamera().getPosition();stack.pushPose();stack.translate(-camera.x,-camera.y,-camera.z);
        var glows=new ArrayList<Vec3[]>();var glowColors=new ArrayList<Integer>();
        for(var b:BURSTS){
            var p=b.event;double age=Math.max(0,time-b.started);var center=new Vec3(p.x(),p.y()+.02,p.z());
            int seed=(int)(b.started*31+Double.hashCode(p.x())*7+Double.hashCode(p.z()));
            if(p.slash()){
                var along=new Vec3(p.dx(),p.dy(),p.dz()).normalize();
                var facing=camera.subtract(center).normalize();var side=along.cross(facing).normalize();
                if(side.lengthSqr()<.001)side=along.cross(new Vec3(0,1,0)).normalize();
                if(side.lengthSqr()<.001)side=new Vec3(1,0,0);
                // Fast open, then the cut lingers as a dark wound line.
                double open=1-Math.pow(1-Math.min(1,age/1.4),3);
                double extent=p.radius()*(.55+.5*open);
                int alpha=(int)(170*net.minecraft.util.Mth.clamp(1-age/8,0,1));
                cut(stack,out,center,along,side,extent,.11,alpha<<24|0x3a070c);
                cut(stack,out,center.add(facing.scale(.003)),along,side,extent*.96,.06,(int)(alpha*.9)<<24|0x8e1420);
                if(age<2.6){
                    double k=1-age/2.6;
                    cut(stack,out,center.add(facing.scale(.006)),along,side,extent*.92,.022,(int)(245*k)<<24|0xfff1e6);
                    if(rich){
                        glows.add(cutPoints(center.add(facing.scale(.008)),along,side,extent*1.08,.24*k+.05));glowColors.add((int)(150*k)<<24|0xc41a26);
                        // Offset afterimage arc gives the stroke a sense of sweep.
                        var echo=center.subtract(side.scale(.16*(1+age))).add(facing.scale(.004));
                        cut(stack,out,echo,along,side,extent*.75,.035,(int)(120*k)<<24|0xd9333f);
                    }
                }
                if(rich&&age<5)droplets(stack,out,center,along,side,facing,p.radius(),age,seed);
            }else if(p.ground()){
                double extent=p.radius()*Math.min(1,(age+1)/3);int alpha=(int)(210*net.minecraft.util.Mth.clamp((18-age)/9,0,1));
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
                var facing=camera.subtract(center).normalize();var right=facing.cross(new Vec3(0,1,0)).normalize();
                if(right.lengthSqr()<.001)right=new Vec3(1,0,0);
                var up=right.cross(facing).normalize();
                double life=7;double k=net.minecraft.util.Mth.clamp(1-age/life,0,1);
                // Spark rays of uneven length, pushed out fast and thinning.
                int rays=rich?11:6;
                for(int i=0;i<rays;i++){
                    double r0=hash(seed+i*13);double a=i*Math.PI*2/rays+r0*.5;
                    var dir=right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
                    double reach=p.radius()*(.45+r0*.9)*(1-Math.pow(1-Math.min(1,age/3),2));
                    double base=p.radius()*.12*(1-Math.min(1,age/4));
                    var from=center.add(dir.scale(base));var tip=center.add(dir.scale(base+reach));
                    var w=right.scale(-Math.sin(a)).add(up.scale(Math.cos(a))).scale(p.radius()*.05*k);
                    int alpha=(int)(225*net.minecraft.util.Mth.clamp(1-age/4.5,0,1));
                    quad(stack,out,alpha<<24|(i%3==0?0xffd9b8:0xfff4ea),from.add(w),tip,tip,from.subtract(w));
                }
                if(rich&&age<life){
                    double r=p.radius()*(.25+1.1*(1-Math.pow(1-age/life,2)));double thick=p.radius()*.09*k+.01;
                    for(int i=0;i<24;i++){double a=i*Math.PI/12,c=a+Math.PI/12;
                        var a1=center.add(right.scale(Math.cos(a)*r)).add(up.scale(Math.sin(a)*r));var c1=center.add(right.scale(Math.cos(c)*r)).add(up.scale(Math.sin(c)*r));
                        var a0=center.add(right.scale(Math.cos(a)*(r-thick))).add(up.scale(Math.sin(a)*(r-thick)));var c0=center.add(right.scale(Math.cos(c)*(r-thick))).add(up.scale(Math.sin(c)*(r-thick)));
                        quad(stack,out,(int)(110*k)<<24|0xf3e1d2,a0,a1,c1,c0);}
                    if(age<3){double g=p.radius()*(.55+age*.12);double gk=1-age/3;
                        glows.add(new Vec3[]{center.add(right.scale(-g)).add(up.scale(-g)),center.add(right.scale(g)).add(up.scale(-g)),center.add(right.scale(g)).add(up.scale(g)),center.add(right.scale(-g)).add(up.scale(g))});glowColors.add((int)(120*gk)<<24|0xff3b2e);}
                }
            }
        }
        buffers.endBatch(type);
        if(!glows.isEmpty()){
            var glowType=RenderType.lightning();var glow=buffers.getBuffer(glowType);
            for(int i=0;i<glows.size();i++)for(var v:glows.get(i))glowVertex(stack,glow,v,glowColors.get(i));
            buffers.endBatch(glowType);
        }
        stack.popPose();
    }
    private static void droplets(PoseStack stack,VertexConsumer out,Vec3 center,Vec3 along,Vec3 side,Vec3 facing,double radius,double age,int seed){
        double t=age/20.0;
        for(int i=0;i<9;i++){
            double r0=hash(seed+i*7),r1=hash(seed+i*11+3);
            var origin=center.add(along.scale((r0-.5)*radius*1.4));
            var velocity=side.scale((r1-.5)*2.2).add(along.scale((r0-.5)*.8)).add(0,1.4+r1,0).scale(1.6);
            var at=origin.add(velocity.scale(t)).add(0,-9.8*t*t,0);
            var dir=velocity.add(0,-19.6*t,0).normalize().scale(.06+r1*.08);
            var w=dir.cross(facing).normalize().scale(.018+.012*r0);
            int alpha=(int)(220*net.minecraft.util.Mth.clamp(1-age/5,0,1));
            quad(stack,out,alpha<<24|(i%3==0?0x2a0306:0x7d0a12),at.subtract(dir).add(w),at.add(dir),at.add(dir),at.subtract(dir).subtract(w));
        }
    }
    private static double hash(int x){x^=x>>>16;x*=0x7feb352d;x^=x>>>15;x*=0x846ca68b;x^=x>>>16;return (x>>>8)/(double)(1<<24);}
    private static Vec3[] cutPoints(Vec3 center,Vec3 along,Vec3 side,double length,double width){
        return new Vec3[]{center.subtract(along.scale(length)),center.add(side.scale(width)),center.add(along.scale(length)),center.subtract(side.scale(width))};
    }
    private static void cut(PoseStack stack,VertexConsumer out,Vec3 center,Vec3 along,Vec3 side,double length,double width,int color){
        // A tapering slice aligned to the strike, not a camera-facing explosion.
        var a=center.subtract(along.scale(length));var b=center.add(along.scale(length));
        quad(stack,out,color,a,center.add(side.scale(width)),b,center.subtract(side.scale(width)));
    }
    private static Vec3 point(Vec3 c,double a,double r){return c.add(Math.cos(a)*r,0,Math.sin(a)*r);}
    private static void glowVertex(PoseStack stack,VertexConsumer out,Vec3 p,int color){
        out.vertex(stack.last().pose(),(float)p.x,(float)p.y,(float)p.z).color(color).endVertex();
    }
    private static void quad(PoseStack stack,VertexConsumer out,int color,Vec3... points){
        for(var p:points)out.vertex((stack.last()).pose(),(float)p.x,(float)p.y,(float)p.z).color(color).uv(.5F,.5F)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xf000f0).normal((stack.last()).normal(),0,1,0).endVertex();
    }
}
