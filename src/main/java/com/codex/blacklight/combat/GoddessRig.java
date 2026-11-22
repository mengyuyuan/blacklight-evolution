package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Bind-local receiver patches, GPU skinning and shader-pack rendering use identical joint samples. */
public final class GoddessRig {
    public static final List<String> PARTS=List.of("goddess_head","goddess_chest","goddess_base","goddess_left_shoulder",
            "goddess_left_elbow","goddess_left_wrist","goddess_left_hand","goddess_right_shoulder","goddess_right_elbow",
            "goddess_right_wrist","goddess_right_hand","goddess_hair");
    public static final int JOINTS=35;
    private record Bone(int part,float[] idle,Vec3[] centers,Vec3[] half) {}
    private static final Bone[] bones=new Bone[JOINTS];
    private static final int frames,rate;
    private static final byte[] colonies;
    private static final float[][] falls=new float[3][];
    private static final int fallFrames;
    static {
        try (var in=stream("greene_goddess_receivers.bin.gz"); var clip=stream("greene_execution_first.pose.gz")) {
            if (in.readInt()!=0x47524731 || in.readInt()!=JOINTS) throw new IOException("Invalid goddess anatomy");
            frames=in.readInt();rate=in.readInt();
            for (int i=0;i<JOINTS;i++) {
                String name=in.readUTF();
                float[] idle=readFloats(in,frames*7);
                int count=in.readInt();
                Vec3[] centers=new Vec3[count], half=new Vec3[count];
                for(int b=0;b<count;b++){centers[b]=vector(in);half[b]=vector(in);}
                bones[i]=new Bone(region(name),idle,centers,half);
            }
            colonies=in.readNBytes(in.readInt());
            if(clip.readInt()!=0x47455831) throw new IOException("Invalid execution timeline");
            fallFrames=clip.readInt();
            if(clip.readInt()!=JOINTS)throw new IOException("Incompatible execution rig");
            falls[0]=readFloats(clip,fallFrames*JOINTS*10);
            for(int i=1;i<=2;i++)try(var action=stream(i==1?"greene_execution_second.pose.gz":"greene_execution_head.pose.gz")){
                if(action.readInt()!=0x47455831 || action.readInt()!=fallFrames || action.readInt()!=JOINTS)throw new IOException("Mismatched fall rig");
                falls[i]=readFloats(action,fallFrames*JOINTS*10);
            }
        } catch(IOException ex){throw new ExceptionInInitializerError(ex);}
    }
    private static DataInputStream stream(String name) throws IOException {
        var raw=GoddessRig.class.getResourceAsStream("/assets/blacklight/models/"+name);
        if(raw==null)throw new FileNotFoundException(name);
        return new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)));
    }
    private static Vec3 vector(DataInputStream in) throws IOException {return new Vec3(in.readFloat(),in.readFloat(),in.readFloat());}
    private static float[] readFloats(DataInputStream in,int count)throws IOException{
        var a=new float[count];for(int i=0;i<count;i++)a[i]=in.readFloat();return a;
    }
    private static int region(String name){
        if(name.startsWith("near_")||name.startsWith("far_")){
            int offset=name.startsWith("near_")?3:7;
            return offset+(name.endsWith("_forearm")?1:name.endsWith("_arm")?0:name.endsWith("_hand")?2:3);
        }
        return switch(name){case "greene_head"->0;case "thorax"->1;case "crown","crown_tip"->11;default->2;};
    }
    public static int carrier(int joint){return joint<JOINTS?joint:colonies[(joint-JOINTS)/12];}
    private static int flag(int part){return part>=3&&part<=6?GoddessVitals.LEFT_ARM:part>=7&&part<=10?GoddessVitals.RIGHT_ARM:part==0||part==11?GoddessVitals.HEAD:0;}
    static boolean belongsTo(String bone,int part){
        int slot=PARTS.indexOf(bone);return slot>=0 && flag(slot)==part;
    }
    public static boolean visible(EncounterMob boss,int joint){return (boss.goddess.removed()&flag(bones[joint].part))==0;}
    private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    private static Matrix4f interpolate(float[] data,int a,int b,float mix,boolean scale){
        var rotation=new Quaternionf(data[a+3],data[a+4],data[a+5],data[a+6])
                .slerp(new Quaternionf(data[b+3],data[b+4],data[b+5],data[b+6]),mix);
        var matrix=new Matrix4f().translationRotate(lerp(data[a],data[b],mix),lerp(data[a+1],data[b+1],mix),lerp(data[a+2],data[b+2],mix),rotation);
        return scale?matrix.scale(lerp(data[a+7],data[b+7],mix),lerp(data[a+8],data[b+8],mix),lerp(data[a+9],data[b+9],mix)):matrix;
    }
    private static Matrix4f idle(int joint,float ticks){
        float sample=Math.max(0,ticks)*rate/20F%frames;
        int f=(int)sample;
        return interpolate(bones[joint].idle,7*f,7*((f+1)%frames),sample-f,false);
    }
    private static float executionFrame(EncounterMob boss,float partial) {
        float age=boss.goddess.age(partial);
        return switch(boss.goddess.action()){
            case FLYING -> 0;
            case STAGGER -> age<20?1+age*3.6F:age<100?73:1;
            case FALLING -> Math.min(80,1+age*2.1F);
            case DOWN,GRIPPED -> 80;
            case EXTRACTING -> Math.min(fallFrames,80+age*1.2F);
            case RISING -> Math.max(1,80-age*1.58F);
        };
    }
    public static Matrix4f[] pose(EncounterMob boss,float partial){
        return pose(boss,partial,executionFrame(boss,partial));
    }
    private static Matrix4f[] pose(EncounterMob boss,float partial,float frame){
        var result=new Matrix4f[JOINTS];
        var fall=falls[Integer.bitCount(boss.goddess.poseMask()&3)];
        for(int j=0;j<JOINTS;j++){
            if(frame==0) result[j]=new Matrix4f().translation(0,4+.15F*(float)Math.sin((boss.tickCount+partial)*.054),0).mul(idle(j,boss.tickCount+partial));
            else {
                float sample=Math.min(fallFrames-1,frame-1);
                int f=(int)sample, a=(f*JOINTS+j)*10, b=(Math.min(fallFrames-1,f+1)*JOINTS+j)*10;
                result[j]=interpolate(fall,a,b,sample-f,true).mul(idle(j,0));
            }
        }
        return result;
    }
    public static Matrix4f[] renderOffsets(EncounterMob boss,float partial){
        var output=pose(boss,partial);
        for(int j=0;j<JOINTS;j++){
            boolean inTransfer=boss.goddess.action()==GoddessEncounter.Action.EXTRACTING && flag(bones[j].part)==boss.goddess.part() && executionFrame(boss,partial)<202;
            if(!visible(boss,j) && !inTransfer)output[j].zero();
            else output[j].mul(idle(j,boss.tickCount+partial).invert());
        }
        return output;
    }
    public static Matrix4f root(EncounterMob boss,float partial){return new Matrix4f().translate(0,(float)boss.goddess.lift(partial),0).rotateY((float)Math.toRadians(180-boss.goddess.facing(partial))).scale(2);}
    public static List<HydraContact.Region> regions(EncounterMob boss,Vec3 origin,float partial){
        return build(boss,origin,root(boss,partial),pose(boss,partial));
    }
    static List<HydraContact.Region> resting(EncounterMob boss,float yaw){
        return build(boss,boss.position(),new Matrix4f().rotateY((float)Math.toRadians(180-yaw)).scale(2),pose(boss,1,80));
    }
    private static List<HydraContact.Region> build(EncounterMob boss,Vec3 origin,Matrix4f root,Matrix4f[] posed){
        var slots=new ArrayList<List<HydraContact.Box>>(PARTS.size());
        for(int i=0;i<PARTS.size();i++) slots.add(new ArrayList<>());
        for(int j=0;j<JOINTS;j++){
            if(!visible(boss,j))continue;
            var bone=bones[j];var m=new Matrix4f(root).mul(posed[j]);
            var x=new Vec3(m.transformDirection(new Vector3f(1,0,0)));
            var y=new Vec3(m.transformDirection(new Vector3f(0,1,0)));
            var z=new Vec3(m.transformDirection(new Vector3f(0,0,1)));
            if(x.lengthSqr()<.00001||y.lengthSqr()<.00001||z.lengthSqr()<.00001)continue;
            for(int i=0;i<bone.centers.length;i++){
                Vec3 center=origin.add(new Vec3(m.transformPosition(bone.centers[i].toVector3f())));
                slots.get(bone.part).add(HydraContact.affine(center,bone.half[i],x,y,z));
            }
        }
        var result=new ArrayList<HydraContact.Region>();
        for(int i=0;i<slots.size();i++){
            var boxes=slots.get(i);
            var bounds=boxes.isEmpty()?new AABB(origin,origin):boxes.get(0).bounds;
            for(int b=1;b<boxes.size();b++)bounds=bounds.minmax(boxes.get(b).bounds);
            result.add(new HydraContact.Region(PARTS.get(i),boxes,bounds));
        }
        return List.copyOf(result);
    }
    public static boolean reachable(EncounterMob boss,Player player,int part){
        if(player.distanceToSqr(boss)>80*80)return false;
        var regions=boss.hurtRegions(0);
        for(int i=0;i<PARTS.size();i++)if(flag(i)==part && !regions.get(i).boxes().isEmpty()
                && GreeneContact.focus(regions,i,player,4)!=null)return true;
        return false;
    }
    private GoddessRig(){}
}
