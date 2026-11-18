package com.codex.blacklight.combat;

import com.codex.blacklight.entity.*;
import java.util.*;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;

/** FIG chooses the hand and active window; the unchanged mesh supplies its size.
 * Shared by server contact, render QA and the AI's cached reach preview. */
public final class JuggernautContact {
    public record Fist(Vec3 center,List<Vec3> corners) {}
    public static final double RADIUS=.55;
    public static boolean uses(EncounterMove move){return move==EncounterMove.HAMMER||move==EncounterMove.SLAM;}
    public static boolean active(SourceBossAction.Window w,float before,float now){return now>=w.begin()*20&&before<=w.end()*20;}
    public static boolean guarding(float age){return age>=10&&age<100F/3;}
    public static float[][] pose(EncounterMove move,float age){return SourceBossMotion.get(EncounterKind.JUGGERNAUT).actionPose(SourceBossAction.get(EncounterKind.JUGGERNAUT,move),age);}
    public static Fist fist(EncounterMove move,float age,boolean left,Vec3 origin,float yaw){
        var table=SourceBossMotion.get(EncounterKind.JUGGERNAUT);var pose=pose(move,age);
        int index=Arrays.asList(table.bones).indexOf(left?"left_hand":"right_hand");var m=table.transforms(pose)[index];
        float side=left?-1:1;
        var center=world(m.transformPosition(new Vector3f(side*24.84F/16,11.34F/16,-5.22F/16)),origin,yaw);
        var corners=new ArrayList<Vec3>(8);
        for(int x:new int[]{-1,1})for(int y:new int[]{-1,1})for(int z:new int[]{-1,1})
            corners.add(world(m.transformPosition(new Vector3f((side*24.84F+x*6.39F)/16,(11.34F+y*6.21F)/16,(-5.22F+z*6.12F)/16)),origin,yaw));
        return new Fist(center,List.copyOf(corners));
    }
    private static Vec3 world(Vector3f p,Vec3 origin,float yaw){p.rotateY((float)Math.toRadians(180-yaw));return origin.add(p.x,p.y+.01,p.z);}
    /** Guard sparks belong on the incoming face of the oriented fist, not inside its mesh. */
    public static Vec3 guardContact(float age,Vec3 origin,float yaw,Vec3 from){
        var table=SourceBossMotion.get(EncounterKind.JUGGERNAUT);var transforms=table.transforms(pose(EncounterMove.GUARD,age));
        Vec3 closest=null;double distance=Double.POSITIVE_INFINITY;
        for(boolean left:new boolean[]{true,false}){
            var matrix=transforms[Arrays.asList(table.bones).indexOf(left?"left_hand":"right_hand")];
            Vec3 relative=from.subtract(origin).add(0,-.01,0);
            var local=new Vector3f((float)relative.x,(float)relative.y,(float)relative.z).rotateY((float)Math.toRadians(yaw-180));
            new org.joml.Matrix4f(matrix).invert().transformPosition(local);
            double x=(left?-1:1)*24.84/16,y=11.34/16,z=-5.22/16;
            var box=new AABB(x-6.39/16,y-6.21/16,z-6.12/16,x+6.39/16,y+6.21/16,z+6.12/16);
            var point=box.clip(new Vec3(local.x,local.y,local.z),box.getCenter()).orElse(box.getCenter());
            var contact=world(matrix.transformPosition(new Vector3f((float)point.x,(float)point.y,(float)point.z)),origin,yaw);
            double d=contact.distanceToSqr(from);if(d<distance){distance=d;closest=contact;}
        }
        return closest.add(from.subtract(closest).normalize().scale(.035));
    }
    public static Vec3 contact(EncounterMove move,SourceBossAction.Window window,float before,float now,Vec3 origin,float yaw,AABB target){
        if(!active(window,before,now))return null;
        float start=Math.max(before,window.begin()*20),end=Math.min(now,window.end()*20);Vec3 previous=null;
        for(int step=0;step<=4;step++){
            var fist=fist(move,start+(end-start)*step/4,window.joint().equals("Wrist_L"),origin,yaw);
            if(HydraContact.capsuleTouches(target,previous==null?fist.center:previous,fist.center,RADIUS))return HydraContact.nearest(target,fist.center);
            previous=fist.center;
        }return null;
    }
    private static final class Reach {
        static final Map<EncounterMove,List<Vec3>> SAMPLES=build();
        private static Map<EncounterMove,List<Vec3>> build(){var out=new EnumMap<EncounterMove,List<Vec3>>(EncounterMove.class);
            for(var move:new EncounterMove[]{EncounterMove.HAMMER,EncounterMove.SLAM}){var points=new ArrayList<Vec3>();
                for(var w:SourceBossAction.get(EncounterKind.JUGGERNAUT,move).windows)
                    for(float t=w.begin()*20;t<=w.end()*20;t+=.25F)points.add(fist(move,t,w.joint().equals("Wrist_L"),Vec3.ZERO,0).center);
                out.put(move,List.copyOf(points));
            }return out;
        }
    }
    public static boolean canReach(EncounterMove move,Vec3 origin,float yaw,AABB target){
        if(!uses(move))return false;double a=Math.toRadians(yaw),c=Math.cos(a),s=Math.sin(a);
        for(var p:Reach.SAMPLES.get(move)){var q=origin.add(p.x*c-p.z*s,p.y,p.x*s+p.z*c);if(HydraContact.nearest(target,q).distanceToSqr(q)<=RADIUS*RADIUS)return true;
            // A grounded victim between the two fists can be reached by the small
            // impact shock. The server still requires actual fist/block contact.
            if(move==EncounterMove.SLAM&&p.y<1&&Math.abs(target.minY-origin.y)<.5&&target.getCenter().subtract(q).horizontalDistance()<2.1)return true;
        }return false;
    }
    private JuggernautContact(){}
}
