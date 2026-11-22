package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.entity.GreeneTendrilPod;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.ArrayList;
import java.util.List;

final class SpiderWoundedActions {
    private SpiderWoundedActions(){}
    static boolean unstable(int mask){
        return (mask&15)==15 || (mask&240)==240 || Integer.bitCount(mask&255)>=6;
    }
    static SpiderActions.Move select(int mask,double range,int turn){
        if(!unstable(mask))return null;
        boolean legless=(mask&255)==255;
        if(!legless && turn%3==0)return null;
        if(range<=16 && (turn%3!=1 || range<7))return SpiderActions.Move.ORAL_SCISSORS;
        if(range<=12 && turn%3==1)return SpiderActions.Move.SLAM;
        return SpiderActions.Move.POD_CAST;
    }
    private static Vec3 aim(EncounterMob b){
        var s=b.spiderData();return new Vec3(s.getDouble("Ax"),s.getDouble("Ay"),s.getDouble("Az"));
    }
    static List<Vec3> lash(EncounterMob b,int slot,float partial,Vec3 lip){
        if(slot>1)return List.of();
        float age=b.spider.actions.age(partial);
        double extend=SpiderGesture.phrase(age,3+slot*2,17,30,41);
        if(extend<.002)return List.of();
        var origin=b.getPosition(partial);
        var delta=aim(b).subtract(origin).multiply(1,0,1);
        var dir=delta.lengthSqr()>.01?delta.normalize():new Vec3(0,0,1).yRot((float)Math.toRadians(-b.skinYaw(partial)));
        double swing=SpiderGesture.ease(age,17+slot,27+slot);
        double angle=(slot==0?1:-1)*(1.08-1.32*swing);
        var reach=dir.yRot((float)angle);
        double length=Mth.clamp(delta.length()+1.3,5,18.5);
        var tip=origin.add(reach.scale(length));tip=new Vec3(tip.x,aim(b).y+.46,tip.z);
        var cross=new Vec3(-reach.z,0,reach.x);
        var points=new ArrayList<Vec3>(13);points.add(lip);
        for(int i=1;i<=12;i++){
            double u=i/12D*extend;
            double arch=Math.sin(Math.PI*u);
            var p=lip.lerp(tip,u).add(cross.scale((slot==0?1:-1)*arch*(1.9-.9*swing)))
                .add(0,-Math.min(2.6,Math.max(0,lip.y-tip.y)*.44)*arch+.20*arch*Math.sin(10*u-age*.26),0);
            var hit=clip(b,points.get(points.size()-1),p);
            if(hit.getType()==HitResult.Type.BLOCK){points.add(hit.getLocation());break;}
            points.add(p);
        }
        return points;
    }
    private static BlockHitResult clip(EncounterMob b,Vec3 from,Vec3 to){
        return b.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,b));
    }
    static final int RELEASE=17;
    static Vec3 seed(EncounterMob b,float age){
        var s=b.spiderData();
        if(!s.contains("SeedX") || s.contains("SeedHit") || age<RELEASE)return null;
        var from=new Vec3(s.getDouble("SeedX"),s.getDouble("SeedY"),s.getDouble("SeedZ"));
        var to=aim(b).add(0,-.35,0);
        double u=Mth.clamp((age-RELEASE)/s.getFloat("SeedFlight"),0,1);
        double arc=Math.min(7,1.6+.13*from.distanceTo(to));
        return from.lerp(to,u).add(0,4*arc*u*(1-u),0);
    }
    static void cast(EncounterMob b,int t){
        if(t==4)SpiderFoley.play(b,b.spider.mouth(1),SpiderFoley.Cue.INHALE,1.55F);
        if(t==RELEASE){
            var from=b.spider.mouth(1);var s=b.spiderData().copy();
            s.putDouble("SeedX",from.x);s.putDouble("SeedY",from.y);s.putDouble("SeedZ",from.z);
            s.putFloat("SeedFlight",Mth.clamp((float)Math.ceil(from.distanceTo(aim(b))/1.3),9,42));
            b.spiderData(s);SpiderFoley.play(b,from,SpiderFoley.Cue.SHED,1.75F);
        }
        if(t<=RELEASE || b.spiderData().contains("SeedHit"))return;
        var prev=seed(b,t-1);var next=seed(b,t);
        if(prev==null || next==null)return;
        var hit=clip(b,prev,next);
        if(hit.getType()==HitResult.Type.BLOCK || t>=RELEASE+b.spiderData().getFloat("SeedFlight")){
            if(hit.getType()==HitResult.Type.BLOCK){
                if(hit.getDirection()==Direction.UP)GreeneTendrilPod.plant(b,hit.getLocation());
                else SpiderFoley.play(b,hit.getLocation(),SpiderFoley.Cue.TEAR,1.25F);
            }
            var s=b.spiderData().copy();s.putInt("SeedHit",t);b.spiderData(s);
        }
    }
    static boolean scissors(EncounterMob b,int t,ServerPlayer player){
        if(t==4)SpiderFoley.play(b,b.spider.mouth(1),SpiderFoley.Cue.LOAD,1.5F);
        if(t!=16 && (t<18 || t>29))return false;
        boolean touched=false;
        for(int slot=1;slot<=2;slot++){
            var path=b.spider.rootPath(slot,0);
            if(path.size()<2)continue;
            if(t==16 || t==24)SpiderFoley.ground(b,path.get(path.size()-1),1.3F,SpiderFoley.Cue.SCRAPE);
            if(t<18 || b.spiderData().getBoolean("Struck"))continue;
            // At full extension the tips travel several blocks per tick.
            for(int step=0;step<=4 && !touched;step++){
                var sample=step==0?path:b.spider.rootPath(slot,step*.25F);
                touched=touches(b,sample,player);
            }
        }
        return touched;
    }
    private static boolean touches(EncounterMob b,List<Vec3> path,ServerPlayer player){
        var box=player.getBoundingBox();var center=box.getCenter();
        for(int i=1;i<path.size();i++){
            var a=path.get(i-1);var c=path.get(i);
            double width=SpiderPhase.jawCordRadius(i);
            if(DevastatorCollision.capsuleDistanceSquared(a,c,box)>width*width)continue;
            var ab=c.subtract(a);
            var p=a.add(ab.scale(Mth.clamp(center.subtract(a).dot(ab)/Math.max(.00001,ab.lengthSqr()),0,1)));
            if(clip(b,p,center).getType()==HitResult.Type.MISS)return true;
        }
        return false;
    }
}
