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
        return solve(hip,knee,tip,target,0);
    }
    private static Pair solve(Vec3 hip,Vec3 knee,Vec3 tip,Vec3 target,double twist){
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
        if(twist!=0){
            var radial=bend.subtract(direction.scale(along));
            bend=direction.scale(along).add(radial.scale(Math.cos(twist))).add(direction.cross(radial).scale(Math.sin(twist)));
        }
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
            int row=leg%4;
            float folded=SpiderGesture.phrase(time,5+row,13+row,21+row,29+row);
            var under=boss.getPosition(partial).subtract(rest).multiply(1,0,1);
            float airborne=SpiderGesture.phrase(time,4+row,13+row,25+row,33+row);
            dest=rest.add(leap.scale(airborne)).add(under.scale(.25*folded)).add(0,(2.4+row*.37)*folded,0);
            // Hook the near wall with a reachable limb even when the attacking foot is on the far side.
            if(state.contains("WallLeg") && leg==state.getInt("WallLeg") && leg!=chosen){
                var wall=new Vec3(state.getDouble("WallX"),state.getDouble("WallY"),state.getDouble("WallZ"));
                float contact=SpiderGesture.phrase(time,0,10,17,24);
                if(state.contains("Slip"))contact=SpiderGesture.phrase(state.getInt("Slip"),0,10,17,24)
                        *(1-SpiderGesture.ease(time,state.getInt("Slip"),state.getInt("Slip")+8));
                dest=dest.lerp(wall,contact);
            }
        }
        boolean paired=move==SpiderActions.Move.PINCER || move==SpiderActions.Move.SWALLOW;
        boolean heavy=move==SpiderActions.Move.HEAVY_STAMP;
        boolean attacking=heavy||move==SpiderActions.Move.STAB||move==SpiderActions.Move.SWEEP||move==SpiderActions.Move.POUNCE||paired;
        if(attacking && chosen>=0 && (leg==chosen || paired && leg%4==chosen%4)){
            var aim=new Vec3(state.getDouble("Ax"),state.getDouble("Ay")+.34,state.getDouble("Az"));
            float impact=move==SpiderActions.Move.POUNCE?29:move==SpiderActions.Move.SWEEP?22:move==SpiderActions.Move.SWALLOW?24:18;
            var side=new Vec3(1,0,0).yRot((float)Math.toRadians(-boss.skinYaw(partial))).scale(leg<4?-1:1);
            if(paired)aim=aim.add(side.scale(.55));
            if(move==SpiderActions.Move.POUNCE && state.contains("Brace")){
                var wall=new Vec3(state.getDouble("WallX"),state.getDouble("WallY"),state.getDouble("WallZ"));
                float climbTime=state.contains("Slip")?Math.min(time,state.getInt("Slip")):time;
                boolean hooking=!state.contains("WallLeg") || state.getInt("WallLeg")==leg;
                var perch=hooking?rest.lerp(wall,SpiderGesture.ease(climbTime,0,12)):dest;
                if(state.contains("Slip")){
                    dest=perch.lerp(rest,SpiderGesture.ease(time,state.getInt("Slip"),state.getInt("Slip")+8));
                }else{
                    var plunge=perch.lerp(aim,SpiderGesture.ease(time,18,29));
                    float release=SpiderGesture.ease(time,33,52);
                    dest=plunge.lerp(rest,release).add(0,1.6*Math.sin(Math.PI*release),0);
                }
            }else if(move==SpiderActions.Move.SWEEP){
                var pivot=boss.getPosition(partial);
                var radius=aim.subtract(pivot).multiply(1,0,1);
                float sign=leg<4?-1:1;
                float travel=SpiderGesture.ease(time,12,31);
                var entry=pivot.add(radius.yRot(sign*.65F));entry=new Vec3(entry.x,aim.y+.56,entry.z);
                Vec3 arc=entry;
                // Test the curved sweep in short chords, so a player-built pillar can catch the blade.
                Vec3 previous=entry;
                for(int i=1;i<=4;i++){
                    float u=travel*i/4;
                    var xy=pivot.add(radius.yRot(sign*(.65F-1.30F*u)));
                    var next=new Vec3(xy.x,aim.y+.56-.38*u,xy.z);
                    var wall=boss.level().clip(new net.minecraft.world.level.ClipContext(previous,next,
                            net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,boss));
                    if(wall.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK){arc=wall.getLocation().lerp(previous,.04);break;}
                    previous=next;arc=next;
                }
                float gather=SpiderGesture.phrase(time,0,10,32,45);
                dest=rest.lerp(arc,gather).add(0,1.1*SpiderGesture.phrase(time,1,6,7,12),0);
            }else if(heavy || move==SpiderActions.Move.STAB){
                if(heavy)aim=aim.add(0,-.56,0);
                var away=rest.subtract(aim).multiply(1,0,1).normalize();
                var cocked=rest.lerp(aim,.26).add(away.scale(heavy?2.45:1.8)).add(0,heavy?8.4:6.4,0);
                var raised=rest.lerp(cocked,SpiderGesture.ease(time,2,12));
                var driven=raised.lerp(aim,SpiderGesture.ease(time,12,18));
                float pull=heavy?SpiderGesture.ease(time,58,70):SpiderGesture.ease(time,23,41);
                dest=driven.lerp(rest,pull).add(away.scale(-1.3*Math.sin(Math.PI*pull)))
                        .add(0,3.1*Math.sin(Math.PI*pull),0);
            }else{
                // One foot fences off the exit before the other closes across the prey.
                float lead=leg==chosen?0:3;
                float open=SpiderGesture.phrase(time,lead,8+lead,11+lead,impact);
                float reach=SpiderGesture.ease(time,5+lead,impact)*(1-SpiderGesture.ease(time,impact+6,impact+23));
                double spread=move==SpiderActions.Move.SWALLOW?2.6:3.65;
                dest=rest.lerp(aim,reach).add(side.scale(spread*open)).add(0,(leg==chosen?2.2:3.1)*open,0);
                dest=dest.add(0,Math.sin(Math.PI*SpiderGesture.ease(time,impact+6,impact+23))*1.15,0);
            }
        }
        if(move==SpiderActions.Move.STAB && leg==chosen && state.contains("FirstStabPoint") && time>18){
            var first=state.getCompound("FirstStabPoint");
            var plant=new Vec3(first.getDouble("X"),first.getDouble("Y"),first.getDouble("Z"));
            var loaded=plant.lerp(rest,.34).add(0,7.4,0);
            var raisedTip=plant.lerp(loaded,SpiderGesture.ease(time,20,31));
            var strike=action.aim().add(0,.20,0);
            dest=raisedTip.lerp(strike,SpiderGesture.ease(time,40,46)).lerp(rest,SpiderGesture.ease(time,49,62));
        }
        if(move==SpiderActions.Move.SWALLOW && leg%4==chosen%4 && state.getBoolean("Gripped") && time>=24 && time<50){
            // Carry the captured hips between the two tips until the hands reach the rim.
            var axis=new Vec3(leg<4?-.56:.56,0,0).yRot((float)Math.toRadians(-boss.skinYaw(partial)));
            var hold=SpiderMouthStruggle.feet(boss,time,partial).add(axis).add(0,.66,0);
            dest=dest.lerp(hold,SpiderGesture.phrase(time,24,27,39,50));
        }
        if(move==SpiderActions.Move.MARCH && (leg==chosen || leg==state.getInt("RushSecond"))){
            boolean second=leg!=chosen;
            float clock=state.contains("RushStopped")?Math.min(time,state.getInt("RushStopped")):time;
            float t=clock-(second?17:0);
            var goal=action.marchGoal(second);var from=rest.lerp(goal,SpiderGesture.ease(t,4,21));
            float release=SpiderGesture.ease(t,24,second?37:39);
            var stomp=from.lerp(rest,release).add(0,6.2*SpiderGesture.phrase(t,1,12,13,21)+1.4*Math.sin(Math.PI*release),0);
            dest=state.contains("RushStopped")?stomp.lerp(rest,SpiderGesture.ease(time-clock,0,13)):stomp;
        }
        if(move==SpiderActions.Move.PINCER && paired && leg%4==chosen%4 && state.getBoolean("Gripped") && time>=18){
            var side=new Vec3(leg<4?-.57:.57,0,0).yRot((float)Math.toRadians(-boss.skinYaw(partial)));
            var catchAt=action.pincerCarry(partial).add(side).add(0,.38,0);
            dest=catchAt.lerp(rest,SpiderGesture.ease(time,28,44));
        }
        boolean pinned=heavy && chosen==leg && time>=18 && time<58;
        double kneeTwist=pinned?(leg<4?1:-1)*(.084*SpiderGesture.phrase(time,26,31,32,38)+.13*SpiderGesture.phrase(time,44,50,51,57)):0;
        if(state.contains("Flinch"+leg)){
            float since=boss.level().getGameTime()-state.getLong("Flinch"+leg)+partial;
            float recoil=SpiderGesture.phrase(since,0,3,4,13)*state.getFloat("Recoil"+leg);
            if(recoil>0){
                Vec3 inward=boss.getPosition(partial).subtract(dest).multiply(1,0,1).normalize();
                var push=new Vec3(state.getFloat("PushX"+leg),0,state.getFloat("PushZ"+leg));
                if(push.lengthSqr()<.04)push=inward;
                if(pinned){
                    // A strike bends the knee around the hip-to-tip axis while the foot stays caught.
                    kneeTwist+=(push.dot(inward)>=0?1:-1)*.23*recoil;
                    recoil=0;
                }else if(attacking && leg==chosen)recoil*=.36F;
                dest=dest.add(push.normalize().scale(1.7*recoil)).add(inward.scale(.35*recoil)).add(0,2.15*recoil,0);
            }
        }
        var local=new Vec3(SpiderAnatomy.root(boss,partial).invert().transformPosition(dest.subtract(boss.getPosition(partial)).toVector3f()));
        return solve(hip,knee,tip,local,kneeTwist);
    }

    public static Vec3 worldTip(EncounterMob boss,int leg,float partial){
        var name=SpiderAnatomy.shin(leg);var matrix=boss.spider.jointMatrix(SpiderAnatomy.root(boss,partial),name,partial);
        return boss.getPosition(partial).add(new Vec3(matrix.transformPosition(SpiderAnatomy.TIPS.get(name).toVector3f())));
    }
}
