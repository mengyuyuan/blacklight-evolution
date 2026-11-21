package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Two-link leg solve in the authored rest space. Client mesh and server foot contacts use the same rotations. */
public final class SpiderLegPose {
    private SpiderLegPose(){}
    public record Pair(Quaternionf hip,Quaternionf knee){}
    public static Pair solve(Vec3 hip,Vec3 knee,Vec3 tip,Vec3 target){
        Vec3 first=knee.subtract(hip),last=tip.subtract(knee),line=target.subtract(hip);
        double a=first.length(),b=last.length();
        if(line.lengthSqr()<1e-7 || a<.01 || b<.01)return new Pair(new Quaternionf(),new Quaternionf());
        Vec3 direction=line.normalize();
        double distance=Mth.clamp(line.length(),Math.abs(a-b)+.002,a+b-.002);
        Vec3 plane=first.subtract(direction.scale(first.dot(direction)));
        if(plane.lengthSqr()<1e-6)plane=new Vec3(0,1,0).subtract(direction.scale(direction.y));
        if(plane.lengthSqr()<1e-6)plane=new Vec3(1,0,0);
        double along=(a*a-b*b+distance*distance)/(2*distance);
        Vec3 bend=direction.scale(along).add(plane.normalize().scale(Math.sqrt(Math.max(0,a*a-along*along))));
        var hipRot=rotation(first,bend);
        var lower=rotation(last,direction.scale(distance).subtract(bend));
        return new Pair(hipRot,new Quaternionf(hipRot).conjugate().mul(lower));
    }
    private static Quaternionf rotation(Vec3 from,Vec3 to){
        return new Quaternionf().rotationTo(from.normalize().toVector3f(),to.normalize().toVector3f());
    }
    public static Pair action(EncounterMob boss,int leg,float partial){
        var action=boss.spider.actions;var move=action.move();var st=boss.spiderData();int chosen=st.getInt("Foot");
        if(move==SpiderActions.Move.SLAM && leg>=0 && !boss.spider.broken(leg)){
            var bind=SpiderAnatomy.shin(leg);var tip=SpiderAnatomy.TIPS.get(bind);
            var base=new org.joml.Matrix4f().rotateY((float)Math.toRadians(180-boss.skinYaw(partial))).scale(1.9824204F);
            var ground=new Vec3(SpiderAnatomy.root(boss,partial).invert().mul(base).transformPosition(tip.toVector3f()));
            return solve(SpiderAnatomy.PIVOTS.get(bind.replace("tibia","coxa")),SpiderAnatomy.PIVOTS.get(bind),tip,ground);
        }
        boolean pair=move==SpiderActions.Move.PINCER || move==SpiderActions.Move.SWALLOW;
        if(leg<0 || boss.spider.broken(leg) || !(move==SpiderActions.Move.STAB || move==SpiderActions.Move.SWEEP
            || move==SpiderActions.Move.POUNCE || pair) || !(leg==chosen || pair && leg%4==chosen%4))return null;
        var bind=SpiderAnatomy.shin(leg);var hip=SpiderAnatomy.PIVOTS.get(bind.replace("tibia","coxa"));
        var knee=SpiderAnatomy.PIVOTS.get(bind);var tip=SpiderAnatomy.TIPS.get(bind);
        float hit=move==SpiderActions.Move.POUNCE?29:move==SpiderActions.Move.SWEEP?22:move==SpiderActions.Move.SWALLOW?24:18;
        float time=action.age(partial);
        double reach=Mth.clamp(time/hit,0,1)*Mth.clamp((hit+22-time)/18,0,1);
        reach=reach*reach*(3-2*reach);
        var aim=new Vec3(st.getDouble("Ax"),st.getDouble("Ay")+.35,st.getDouble("Az"));
        if(move==SpiderActions.Move.POUNCE && time<21 && st.contains("Brace")){
            var support=new Vec3(st.getDouble("WallX"),st.getDouble("WallY"),st.getDouble("WallZ"));
            aim=support.lerp(aim,Mth.clamp((time-16)/5F,0,1));
            reach=Mth.clamp(time/11F,0,1);
        }
        if(pair)aim=aim.add(new Vec3(leg<4?-.55:.55,0,0).yRot((float)Math.toRadians(-boss.skinYaw(partial))));
        var local=new Vec3(SpiderAnatomy.root(boss,partial).invert().transformPosition(aim.subtract(boss.getPosition(partial)).toVector3f()));
        Vec3 dest=tip.lerp(local,reach);
        if(time<hit)dest=dest.add(0,Math.sin(Math.PI*time/hit)*(move==SpiderActions.Move.SWEEP?.8:2.2),0);
        return solve(hip,knee,tip,dest);
    }
    public static Vec3 worldTip(EncounterMob boss,int leg,float partial){
        var name=SpiderAnatomy.shin(leg);var matrix=boss.spider.jointMatrix(SpiderAnatomy.root(boss,partial),name,partial);
        return boss.getPosition(partial).add(new Vec3(matrix.transformPosition(SpiderAnatomy.TIPS.get(name).toVector3f())));
    }
}
