package com.codex.blacklight.client.camera;

import com.codex.blacklight.combat.GoddessEncounter;
import com.codex.blacklight.combat.GoddessActor;
import com.codex.blacklight.combat.GoddessRig;
import org.joml.Vector3f;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.mixin.client.PrototypeCameraAccess;
import net.minecraft.client.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class GoddessExecutionCamera {
    private static EncounterMob actor;
    private static float yaw,pitch;
    private static double fov;
    public static boolean active(){
        var mc=Minecraft.getInstance();
        return actor!=null && mc.level==actor.level() && mc.player!=null && mc.getCameraEntity()==mc.player
                && mc.player.isAlive() && !actor.isRemoved() && actor.goddess.action()==GoddessEncounter.Action.EXTRACTING;
    }
    public static boolean update(net.minecraft.world.entity.Entity view){
        var mc=Minecraft.getInstance();actor=view==mc.player && mc.player!=null && mc.player.isAlive()?GoddessEncounter.paired(mc.player):null;
        if(actor==null)framed=null;
        return active();
    }
    private static EncounterMob framed;
    private static Vec3 focus,axis,viewEye;
    private static double distance;
    private static float lastAge;
    private static int stage=-1;

    // A single side of the tug line per performance, selected across all frames by Blender ray tests.
    private static final double[] AZIMUTH={30,30,-15},ELEVATION={25,14,36},END_ELEVATION={10,9,16};
    private static final int[][] LANDMARKS={{5,6,7,10,13,16,18},{19,20,21,24,27,30,32},{0,33,34}};
    private static Vec3[] fullSize;
    private static double side;
    private static double ease(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    private static Vec3 direction(int part,org.joml.Matrix4f root,double extraYaw,double close){
        double az=Math.toRadians(AZIMUTH[part]+extraYaw);
        double el=Math.toRadians(ELEVATION[part]+(END_ELEVATION[part]-ELEVATION[part])*close);
        var v=new Vector3f((float)(-Math.cos(az)*Math.cos(el)),(float)Math.sin(el),(float)(Math.sin(az)*Math.cos(el)));
        return new Vec3(root.transformDirection(v)).normalize();
    }
    private static Vec3[] landmarks(int part,org.joml.Matrix4f[] pose,org.joml.Matrix4f root,Vec3 origin){
        var ids=LANDMARKS[part];var pts=new Vec3[ids.length];
        for(int i=0;i<ids.length;i++){
            var v=pose[ids[i]].getTranslation(new Vector3f());
            pts[i]=origin.add(new Vec3(root.transformPosition(v)));
        }
        return pts;
    }
    private record Shot(Vec3 focus,double distance){}
    private static Shot frame(Vec3 palm,Vec3 face,Vec3[] source,Vec3 axis,double wide,double flow,int part,double aspect){
        Vec3 side=new Vec3(0,1,0).cross(axis).normalize();
        Vec3 up=axis.cross(side).normalize();
        double far=1;
        for(Vec3 p:source)far=Math.max(far,p.distanceTo(palm));
        var near=palm.lerp(face,.23);
        Vec3[] points=new Vec3[source.length+3];
        // A late-moving fingertip still needs room after the skin nearest the glove starts to peel.
        for(int i=0;i<source.length;i++){
            double order=source[i].distanceTo(palm)/far;
            double consumed=ease((flow-.52*order)/(.49-.02*order));
            points[i]=near.lerp(source[i],wide*(1-consumed));
        }
        points[source.length]=palm;
        points[source.length+1]=face.add(0,.22,0);
        points[source.length+2]=face.add(0,-1.48,0);
        double l=0,r=0,b=0,t=0,d=0;
        for(Vec3 p:points){
            var v=p.subtract(near);
            l=Math.min(l,v.dot(side));r=Math.max(r,v.dot(side));
            b=Math.min(b,v.dot(up));t=Math.max(t,v.dot(up));
        }
        var focus=near.add(side.scale((l+r)*.5)).add(up.scale((t+b)*.5));
        // Compose for the visible play area between boss bar and hotbar, including mesh beyond joint origins.
        double margin=(part==2?1.65:1.25)*(1-ease((flow-.54)/.44))+.28;
        for(Vec3 p:points){
            var v=p.subtract(focus);
            double lateral=(Math.abs(v.dot(side))+margin)/(.41421*aspect*.85);
            double vertical=(Math.abs(v.dot(up))+margin)/(.41421*.74);
            d=Math.max(d,v.dot(axis)+Math.max(lateral,vertical));
        }
        return new Shot(focus,Math.max(part==2?3.85:3.6,d+.2));
    }
    private static double clearDistance(Minecraft mc,Vec3 at,Vec3 forward,double wanted){
        var hit=mc.level.clip(new ClipContext(at,at.add(forward.scale(wanted)),ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,mc.player));
        return hit.getType()==HitResult.Type.MISS?wanted:Math.max(.32,at.distanceTo(hit.getLocation())-.24);
    }
    private static boolean lensClear(Minecraft mc,Vec3 point){
        var box=new AABB(point.x-.22,point.y-.22,point.z-.22,point.x+.22,point.y+.22,point.z+.22);
        return !mc.level.getBlockCollisions(mc.player,box).iterator().hasNext();
    }
    private static Vec3 clearLens(Minecraft mc,Vec3 at,Vec3 axis,double distance){
        var eye=at.add(axis.scale(distance));
        if(lensClear(mc,eye))return eye;
        // A ledge between subject and camera can cover an ankle; it must never pull a distant lens to the ground.
        for(double up=.4;up<=2.81;up+=.4){
            var lifted=eye.add(0,up,0);if(lensClear(mc,lifted))return lifted;
        }
        for(double d=distance-.6;d>=2.4;d-=.6){
            var inside=at.add(axis.scale(d));if(lensClear(mc,inside))return inside;
        }
        return viewEye;
    }
    public static double verticalFov(){return fov;}
    public static void apply(Camera camera,float partial){
        var mc=Minecraft.getInstance();
        int part=Integer.bitCount(actor.goddess.poseMask()&3);
        float t=actor.goddess.age(partial);
        var root=GoddessRig.root(actor,partial);
        var pose=GoddessRig.pose(actor,partial);
        int joint=part==0?7:part==1?21:0;
        var offset=part==2?new Vector3f(-.14974F,1.05882F,-.93772F):new Vector3f();
        var source=actor.getPosition(partial).add(new Vec3(new org.joml.Matrix4f(root).mul(pose[joint]).transformPosition(offset)));
        Vec3 right=GoddessActor.point(actor,1,partial),left=GoddessActor.point(actor,4,partial);
        double frame=80+1.2*t;
        int start=GoddessRig.flowStart(part);
        double flow=Math.max(0,Math.min(1,(frame-start)/(GoddessRig.flowEnd(part)-start)));
        double close=ease((flow-.25)/.69);
        boolean entering=actor!=framed || part!=stage;
        if(entering){side=0;fullSize=null;}
        axis=direction(part,root,side,close);
        var origin=actor.getPosition(partial);
        if(flow==0 || fullSize==null)fullSize=landmarks(part,pose,root,origin);
        Vec3 face=actor.goddess.actorPosition(partial).add(0,1.53,0);
        // At entry show the face or point of contact. Open out to both ends before the actual tear.
        double wide=.32+.68*ease((frame-113)/40);
        var visible=fullSize.clone();
        if(part==2)visible[0]=source;
        else if(flow==0)visible[2]=source;
        double aspect=mc.getWindow().getWidth()/(double)Math.max(1,mc.getWindow().getHeight());
        Shot shot=frame(right.lerp(left,.11),face,visible,axis,wide,flow,part,aspect);
        var target=shot.focus();double wanted=shot.distance();
        if(entering){
            framed=actor;stage=part;focus=target;
            double room=clearDistance(mc,right,axis,wanted);
            if(room<wanted*.65)for(double extra:new double[]{-18,18,-32,32}){
                var other=direction(part,root,extra,close);
                double open=clearDistance(mc,right,other,wanted);
                if(open>room+.4){axis=other;side=extra;room=open;}
            }
            distance=wanted;lastAge=t;viewEye=null;
        }
        double dt=Math.max(0,t-lastAge);lastAge=Math.max(t,lastAge);
        double follow=1-Math.exp(-.50*dt);
        var shift=target.subtract(focus).scale(follow);
        if(shift.length()>1.1*dt && dt>0)shift=shift.normalize().scale(1.1*dt);
        focus=focus.add(shift);
        distance+=Math.max(-.72*dt,Math.min(.72*dt,(wanted-distance)*follow));
        var safe=clearLens(mc,focus,axis,distance);
        if(viewEye==null)viewEye=safe==null?mc.player.getEyePosition(partial):safe;
        else if(safe!=null && dt>0){
            var travel=safe.subtract(viewEye);
            double limit=1.5*dt;
            if(travel.length()>limit)travel=travel.normalize().scale(limit);
            var next=viewEye.add(travel);
            if(lensClear(mc,next))viewEye=next;
            else{
                var raised=viewEye.add(0,Math.min(limit,.5*dt),0);
                if(lensClear(mc,raised))viewEye=raised;
            }
        }
        var eye=viewEye;
        var look=eye.subtract(focus).normalize();
        float heading=(float)Math.toDegrees(Math.atan2(look.x,-look.z));
        float tilt=(float)Math.toDegrees(Math.asin(look.y));
        float turn=(float)(1.8*dt);
        yaw=entering?heading:net.minecraft.util.Mth.approachDegrees(yaw,heading,turn);
        pitch=entering?tilt:net.minecraft.util.Mth.approach(pitch,tilt,turn);
        fov=45;
        var access=(PrototypeCameraAccess)camera;
        access.blacklight$setPosition(eye);access.blacklight$setRotation(yaw,pitch,0);
    }
    @SubscribeEvent
    public static void actorTick(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event){
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || mc.isPaused())return;
        var boss=actor;
        if(boss!=null && boss.level()==mc.level)boss.goddess.placeActor(mc.player);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void angles(ViewportEvent.ComputeCameraAngles event){
        if(active()){event.setYaw(yaw);event.setPitch(pitch);event.setRoll(0);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void fov(ViewportEvent.ComputeFov event){if(active() && fov>0 && event.usedConfiguredFov())event.setFOV(fov);}
    private GoddessExecutionCamera(){}
}
