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
        if(leg<0 || boss.spider.broken(leg) || !boss.spider.live())return null;
        var bind=SpiderAnatomy.shin(leg);var tip=SpiderAnatomy.TIPS.get(bind);
        var hip=SpiderAnatomy.PIVOTS.get(bind.replace("tibia","coxa"));var knee=SpiderAnatomy.PIVOTS.get(bind);
        var phase=boss.spider;var action=phase.actions;var move=action.move();
        var state=boss.spiderData();int chosen=state.getInt("Foot");float time=action.age(partial);
        Vec3 rest=phase.footing.foot(leg,partial),dest=rest;
        var leap=action.leapOffset(partial);
        if(move==SpiderActions.Move.POUNCE){
            // The last feet to push off also land last. A leg does not pin a flying torso to the floor.
            double air=SpiderGesture.ease(time,3+leg%4,13+leg%4)*(1-SpiderGesture.ease(time,27+leg%3,34+leg%3));
            dest=rest.add(leap.scale(air)).add(0,Math.sin(Math.PI*air)*.8,0);
        }
        boolean paired=move==SpiderActions.Move.PINCER || move==SpiderActions.Move.SWALLOW;
        boolean attacking=move==SpiderActions.Move.STAB||move==SpiderActions.Move.SWEEP||move==SpiderActions.Move.POUNCE||paired;
        if(attacking && chosen>=0 && (leg==chosen || paired && leg%4==chosen%4)){
            var aim=new Vec3(state.getDouble("Ax"),state.getDouble("Ay")+.34,state.getDouble("Az"));
            float impact=move==SpiderActions.Move.POUNCE?29:move==SpiderActions.Move.SWEEP?22:move==SpiderActions.Move.SWALLOW?24:18;
            var side=new Vec3(1,0,0).yRot((float)Math.toRadians(-boss.skinYaw(partial))).scale(leg<4?-1:1);
            if(paired)aim=aim.add(side.scale(.55));
            if(move==SpiderActions.Move.POUNCE && state.contains("Brace")){
                var wall=new Vec3(state.getDouble("WallX"),state.getDouble("WallY"),state.getDouble("WallZ"));
                float climbTime=state.contains("Slip")?Math.min(time,state.getInt("Slip")):time;
                var perch=rest.lerp(wall,SpiderGesture.ease(climbTime,0,13));
                if(state.contains("Slip")){
                    dest=perch.lerp(rest,SpiderGesture.ease(time,state.getInt("Slip"),state.getInt("Slip")+8));
                }else{
                    var plunge=perch.lerp(aim,SpiderGesture.ease(time,16,29));
                    dest=plunge.lerp(rest,SpiderGesture.ease(time,34,52));
                }
            }else if(move==SpiderActions.Move.SWEEP){
                var entry=aim.add(side.scale(4.8)).add(0,.47,0);
                var exit=aim.subtract(side.scale(4.5)).add(0,.22,0);
                var wall=boss.level().clip(new net.minecraft.world.level.ClipContext(entry,exit,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,boss));
                if(wall.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK)exit=wall.getLocation().add(side.scale(.12));
                double swing=SpiderGesture.ease(time,13,31);
                var arc=entry.lerp(exit,swing).add(0,Math.sin(swing*Math.PI)*.31,0);
                dest=rest.lerp(arc,SpiderGesture.phrase(time,0,13,32,45));
            }else{
                float load=SpiderGesture.phrase(time,0,impact*.52F,impact*.60F,impact);
                float reach=SpiderGesture.ease(time,impact*.40F,impact)*(1-SpiderGesture.ease(time,impact+6,impact+23));
                dest=dest.lerp(aim,reach).add(side.scale((paired?2.6:1.1)*load)).add(0,(paired?2.3:4.3)*load,0);
                dest=dest.add(0,Math.sin(Math.PI*SpiderGesture.ease(time,impact+6,impact+23))*1.15,0);
            }
        }
        if(state.contains("Flinch"+leg) && (!attacking || leg!=chosen)){
            float since=boss.level().getGameTime()-state.getLong("Flinch"+leg)+partial;
            float recoil=SpiderGesture.phrase(since,0,3,4,13)*state.getFloat("Recoil"+leg);
            if(recoil>0){
                Vec3 inward=boss.getPosition(partial).subtract(dest).multiply(1,0,1).normalize();
                dest=dest.add(inward.scale(.85*recoil)).add(0,1.45*recoil,0);
            }
        }
        var local=new Vec3(SpiderAnatomy.root(boss,partial).invert().transformPosition(dest.subtract(boss.getPosition(partial)).toVector3f()));
        return solve(hip,knee,tip,local);
    }

    public static Vec3 worldTip(EncounterMob boss,int leg,float partial){
        var name=SpiderAnatomy.shin(leg);var matrix=boss.spider.jointMatrix(SpiderAnatomy.root(boss,partial),name,partial);
        return boss.getPosition(partial).add(new Vec3(matrix.transformPosition(SpiderAnatomy.TIPS.get(name).toVector3f())));
    }
}
