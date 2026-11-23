package com.codex.blacklight.combat;

import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.entity.EncounterMob;
import java.io.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Companion track from the same Blender frame as the receiving skeleton, in native mesh units. */
public final class GoddessActor {
    private static final String[] JOINTS={"Chest","Head","Arm_R","Hand_R","Tool_R","Arm_L","Hand_L","Tool_L","Thigh_R","Leg_R","Thigh_L","Leg_L"};
    private static final int STRIDE=23+JOINTS.length*4;
    private static final float[][] clips=new float[3][249*STRIDE];
    static {
        try(var raw=GoddessActor.class.getResourceAsStream("/assets/blacklight/models/greene_execution_actor.pose.gz");
            var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(java.util.Objects.requireNonNull(raw))))){
            if(in.readInt()!=0x47504131 || in.readInt()!=3 || in.readInt()!=249 || in.readInt()!=JOINTS.length)throw new IOException("Unexpected Greene actor clip");
            for(var clip:clips)for(int i=0;i<clip.length;i++)clip[i]=in.readFloat();
        }catch(IOException ex){throw new ExceptionInInitializerError(ex);}
    }
    private record Sample(float[] clip,int a,int b,float mix){
        float at(int i){return clip[a+i]+(clip[b+i]-clip[a+i])*mix;}
        Quaternionf rotation(int i){return new Quaternionf(clip[a+i],clip[a+i+1],clip[a+i+2],clip[a+i+3]).slerp(new Quaternionf(clip[b+i],clip[b+i+1],clip[b+i+2],clip[b+i+3]),mix);}
    }
    private static Sample sample(EncounterMob boss,float partial){
        int clip=Integer.bitCount(boss.goddess.poseMask()&3);
        float frame=boss.goddess.action()==GoddessEncounter.Action.GRIPPED ? 79 : Math.min(248,79+boss.goddess.age(partial)*1.2F);
        int index=(int)frame;
        return new Sample(clips[clip],index*STRIDE,Math.min(248,index+1)*STRIDE,frame-index);
    }
    public static Vec3 localPoint(EncounterMob boss,int joint,float partial){
        var s=sample(boss,partial);
        return new Vec3(s.at(joint*3),s.at(joint*3+1),s.at(joint*3+2));
    }
    public static Vec3 framePoint(int stage,int joint,int frame){
        int i=(frame-1)*STRIDE+joint*3;
        return new Vec3(clips[stage][i],clips[stage][i+1],clips[stage][i+2]);
    }
    public static Vec3 point(EncounterMob boss,int joint,float partial){
        return boss.getPosition(partial).add(new Vec3(GoddessRig.root(boss,partial)
                .transformPosition(localPoint(boss,joint,partial).toVector3f())));
    }
    /** The intake meets the skinned MC glove, including the actual collision-limited render position. */
    public static org.joml.Vector3f gloveLocal(EncounterMob boss,float partial){
        var state=boss.goddessData();
        var player=state.hasUUID("Actor")?boss.level().getPlayerByUUID(state.getUUID("Actor")):null;
        if(player==null)return localPoint(boss,1,partial).toVector3f();
        var local=BlockThrowMotion.palm(pose(boss,partial),"R");
        var skin=BlockThrowMotion.world(player,local,yaw(boss,partial),partial).add(boss.goddess.drawnActorOffset(player,partial));
        return GoddessRig.root(boss,partial).invert().transformPosition(skin.subtract(boss.getPosition(partial)).toVector3f());
    }
    public static float yaw(EncounterMob boss,float partial){
        var s=sample(boss,partial);
        var d=GoddessRig.root(boss,partial).transformDirection(new org.joml.Vector3f(s.at(21),0,s.at(22)));
        return (float)Math.toDegrees(Math.atan2(-d.x,d.z));
    }
    public static BlacklightPose pose(EncounterMob boss,float partial){
        var s=sample(boss,partial);var pose=new BlacklightPose();
        for(int i=0;i<JOINTS.length;i++)pose.orElseEmpty(JOINTS[i]).rotation().set(s.rotation(23+i*4));
        return pose;
    }
    private GoddessActor(){}
}
