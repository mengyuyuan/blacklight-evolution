package com.codex.blacklight.combat;

import java.util.Arrays;
import net.minecraft.world.phys.Vec3;

/** Authored Brute recovery: roll onto support, push the heavy chest up, then stand. */
public final class BruteKnockdown {
    public static final int GETUP_TICKS=32;
    public static final int PHYSICS_BLEND_TICKS=8;
    public static final int MAX_CONTROL_TICKS=80+GETUP_TICKS-16;
    private BruteKnockdown(){}
    public static boolean controlled(HitReaction hit){return hit.duration()>0&&(hit.kind()==HitReaction.DOWN||hit.kind()==HitReaction.GETUP);}
    private static float radians(float degrees){return (float)Math.toRadians(degrees);}
    private static void angle(float[][] pose,String name,float x,float y,float z){int i=Arrays.asList(CreaturePoseTable.BRUTE.bones).indexOf(name);pose[i][0]=radians(x);pose[i][1]=radians(y);pose[i][2]=radians(z);}
    private static final class Keys {static final float[][][] POSES={makeKey(0),makeKey(1),makeKey(2),makeKey(3)};}
    private static final class Hip {static final Vec3 REST=hip(CreaturePoseTable.BRUTE.sample("idle",0));}
    private static Vec3 hip(float[][] p){return BruteContact.pivot(p,"right_leg").add(BruteContact.pivot(p,"left_leg")).scale(.5);}
    private static void centerHip(float[][] p){
        // Root pitch is stored on a feet-origin bone. Compensate its horizontal
        // orbit so the get-up pivots around the pelvis instead of dragging the
        // whole torso through the ground in front of the authoritative body.
        var shift=hip(p).subtract(Hip.REST);int root=Arrays.asList(CreaturePoseTable.BRUTE.bones).indexOf("root");
        p[root][3]+=(float)(shift.x*16);p[root][5]-=(float)(shift.z*16);
    }
    private static float[][] key(int stage){return Arrays.stream(Keys.POSES[stage]).map(float[]::clone).toArray(float[][]::new);}
    private static float[][] makeKey(int stage){
        var p=CreaturePoseTable.BRUTE.sample("idle",0);
        if(stage==0){
            angle(p,"root",-90,0,0);angle(p,"head",12,0,0);
            angle(p,"right_arm",22,0,-12);angle(p,"left_arm",22,0,12);
            angle(p,"right_forearm",150,0,0);angle(p,"left_forearm",150,0,0);
            angle(p,"right_leg",8,0,-5);angle(p,"left_leg",8,0,5);
            angle(p,"right_shin",-30,0,0);angle(p,"left_shin",-30,0,0);
        }else if(stage==1){
            angle(p,"root",-50,0,0);angle(p,"torso",0,0,0);angle(p,"head",32,0,0);
            angle(p,"right_arm",0,0,-12);angle(p,"left_arm",0,0,12);
            angle(p,"right_forearm",115,0,0);angle(p,"left_forearm",115,0,0);
            angle(p,"right_leg",50,0,-6);angle(p,"left_leg",50,0,6);
            angle(p,"right_shin",-90,0,0);angle(p,"left_shin",-90,0,0);
        }else if(stage==2){
            angle(p,"root",-8,0,0);angle(p,"torso",-32,0,0);angle(p,"head",22,0,0);
            angle(p,"right_arm",50,0,-8);angle(p,"left_arm",50,0,8);
            angle(p,"right_forearm",60,0,0);angle(p,"left_forearm",60,0,0);
            angle(p,"right_leg",60,0,-6);angle(p,"left_leg",60,0,6);
            angle(p,"right_shin",-52,0,0);angle(p,"left_shin",-52,0,0);
        }
        if(stage<3)plantHands(p,1);
        ground(p);return p;
    }
    private static boolean arm(String name){return name.endsWith("_arm")||name.endsWith("_forearm");}
    private static void ground(float[][] p){
        double low=BruteContact.wholeBody(p,Vec3.ZERO,180).stream().mapToDouble(s->s.box().bounds.minY).min().orElse(.01);
        int root=Arrays.asList(CreaturePoseTable.BRUTE.bones).indexOf("root");p[root][4]+=(float)(16*(.01-low));
    }
    /** Rest on the lower body and fit the existing rigid fists to that support plane.
     * Flexion changes at the elbows; bone lengths and fist geometry never stretch. */
    private static void plantHands(float[][] p,float weight){
        if(weight<=0)return;
        double low=BruteContact.wholeBody(p,Vec3.ZERO,180).stream().filter(s->!arm(s.bone())).mapToDouble(s->s.box().bounds.minY).min().orElse(.01);
        var names=Arrays.asList(CreaturePoseTable.BRUTE.bones);p[names.indexOf("root")][4]+=(float)(16*(.01-low))*weight;
        for(String bone:new String[]{"right_forearm","left_forearm"}){
            int index=names.indexOf(bone);float original=p[index][0],best=0;double bestY=Double.POSITIVE_INFINITY;
            // Find the low point, then use the flexed side of the arc. This keeps
            // elbows bent instead of extending through the chest to reach the floor.
            for(int degrees=0;degrees<=160;degrees+=10){p[index][0]=radians(degrees);double y=BruteContact.lowest(p,bone);if(y<bestY){bestY=y;best=degrees;}}
            float from=best,to=170;p[index][0]=radians(to);
            double raisedY=BruteContact.lowest(p,bone);
            if(bestY<=.01&&raisedY>=.01){
                for(int n=0;n<12;n++){float middle=(from+to)*.5F;p[index][0]=radians(middle);if(BruteContact.lowest(p,bone)<.01)from=middle;else to=middle;}
                best=to;
            }else if(raisedY<.01)best=to;
            p[index][0]=original+(radians(best)-original)*weight;
        }
    }
    public static float[][] pose(float progress){
        float t=net.minecraft.util.Mth.clamp(progress,0,1);float[][] p;
        if(t<=.25F)p=key(0);
        else if(t<.50F)p=BruteFlinch.blend(key(0),key(1),BruteFlinch.ease((t-.25F)/.25F));
        else if(t<.78F)p=BruteFlinch.blend(key(1),key(2),BruteFlinch.ease((t-.50F)/.28F));
        else p=BruteFlinch.blend(key(2),key(3),BruteFlinch.ease((t-.78F)/.22F));
        float planted=BruteFlinch.ease((t-.25F)/.12F)*(1-BruteFlinch.ease((t-.78F)/.10F));
        centerHip(p);plantHands(p,planted);ground(p);
        return p;
    }
    public static float[][] pose(HitReaction hit,long now,float partial){return pose(hit.kind()==HitReaction.GETUP?hit.progress(now,partial):0);}
}
