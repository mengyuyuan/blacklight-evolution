package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

/** Contact points share the animated rig's frame; no independent origin rebasing. */
public final class ConsumePlacementTrack {
    private record Tracks(Map<String,List<double[]>> contacts,Map<String,List<double[]>> projections,Map<String,List<double[]>> motion,Map<String,List<double[]>> actorMotion) {}
    private static Tracks tracks;
    private ConsumePlacementTrack() {}
    private static synchronized Tracks data(){if(tracks==null)tracks=load();return tracks;}
    private static Optional<double[]> sample(Map<String,List<double[]>> map,PowerForm form,float t,boolean rotations) {
        return sample(map,ConsumeSequence.key(form),t,rotations);
    }
    private static Optional<double[]> sample(Map<String,List<double[]>> map,String key,float t,boolean rotations) {
        var frames=map.get(key);if(frames==null)return Optional.empty();
        double f=Math.clamp(t,0,1)*(frames.size()-1);int i=(int)f;float u=(float)(f-i);
        double[] a=frames.get(i),b=frames.get(Math.min(i+1,frames.size()-1)),out=new double[a.length];
        for(int n=0;n<out.length;n++)out[n]=a[n]+(b[n]-a[n])*u;
        if(rotations)for(int n=3;n<out.length;n+=4){
            Quaternionf q=quaternion(a,n).slerp(quaternion(b,n),u).normalize();
            out[n]=q.x;out[n+1]=q.y;out[n+2]=q.z;out[n+3]=q.w;
        }
        return Optional.of(out);
    }
    public static Quaternionf quaternion(double[] row,int n){return new Quaternionf((float)row[n],(float)row[n+1],(float)row[n+2],(float)row[n+3]);}
    public static Optional<double[]> projection(PowerForm form,float t){return sample(data().projections,form,t,true);}
    public static Optional<double[]> projection(String key,float t){return sample(data().projections,key,t,true);}
    public static Optional<double[]> contactFrame(String key,float t){return sample(data().contacts,key,t,false);}
    public static Optional<double[]> projection(PowerForm form,float t,boolean large){return sample(data().projections,ConsumeSequence.clipKey(form,large),t,true);}
    public static Optional<double[]> contactFrame(PowerForm form,float t){return sample(data().contacts,form,t,false);}
    public static Optional<double[]> contactFrame(PowerForm form,float t,boolean large){return sample(data().contacts,ConsumeSequence.clipKey(form,large),t,false);}
    /** Rigid body projection for a quadruped; never stretch its sockets onto a human rig. */
    public static org.joml.Matrix4f quadrupedBody(LivingEntity victim,PowerForm form,float t) {
        return rigidBody(victim,ConsumeSequence.key(form),t);
    }
    public static org.joml.Matrix4f rigidBody(LivingEntity victim,String key,float t) {
        var frame=projection(key,t).orElse(null);var matrix=new org.joml.Matrix4f();if(frame==null)return matrix;
        float scale=.9375F,pivot=victim.getBbHeight()*.5F;
        var chest=quaternion(frame,11); // Vanilla model space -> Minecraft render space.
        chest.set(-chest.x,-chest.y,chest.z,chest.w);
        // A quadruped's long body axis is horizontal in bind pose. Align that axis
        // with the receiver's spine before applying the authored lift/fall rotation.
        return matrix.translate((float)frame[0]*scale,(float)frame[1]*scale,(float)frame[2]*scale)
                .translate(0,pivot,0).rotate(quaternion(frame,3)).rotate(chest)
                .rotateX(ConsumeSequence.quadruped(victim)?(float)Math.PI/2:0).translate(0,-pivot,0);
    }
    public static boolean rigidReceiver(LivingEntity victim){
        if(ConsumeSequence.quadruped(victim)||victim instanceof net.minecraft.world.entity.npc.AbstractVillager
                ||victim instanceof net.minecraft.world.entity.monster.Creeper)return true;
        if(victim instanceof com.codex.blacklight.entity.BlacklightInfectedHost host)
            return host.creeper()||host.speciesId().equals("minecraft:villager")||host.speciesId().equals("minecraft:wandering_trader");
        return false;
    }
    private static Vec3 rigidCenter(LivingEntity victim,PowerForm form,float t){
        var p=quadrupedBody(victim,form,t).transformPosition(new org.joml.Vector3f(0,victim.getBbHeight()*.5F,0));return new Vec3(p.x,p.y,-p.z);
    }
    private static Vec3 rigidContact(LivingEntity victim,PowerForm form,float t){
        boolean head=form==PowerForm.CLAWS||form==PowerForm.HAMMERFISTS;
        boolean quad=ConsumeSequence.quadruped(victim);
        float height=victim.getBbHeight()*(quad?(head?.65F:.5F):(head?.86F:.65F));
        var p=quadrupedBody(victim,form,t).transformPosition(new org.joml.Vector3f(0,height,quad&&head?-victim.getBbWidth()*.45F:0));
        return new Vec3(p.x,p.y,-p.z);
    }
    private static double rigidFloor(LivingEntity victim,PowerForm form,float t){
        var matrix=quadrupedBody(victim,form,t);float w=victim.getBbWidth()*.5F,h=victim.getBbHeight();double min=Double.POSITIVE_INFINITY;
        for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})for(int y:new int[]{0,1})min=Math.min(min,matrix.transformPosition(new org.joml.Vector3f(x*w,y*h,z*w)).y);
        return min;
    }
    public static Vec3 actorAnchor(Vec3 origin,float yaw,PowerForm form,float t,boolean large){
        double[] p=sample(data().actorMotion,ConsumeSequence.clipKey(form,large),t,false).orElse(new double[3]);
        Vec3 local=new Vec3(p[0],p[1],p[2]).scale(.9375);
        if(large)local=new Vec3(Math.clamp(local.x,-.35,.35),Math.clamp(local.y,-.18,.3),Math.clamp(local.z,-.2,.12));
        Vec3 forward=Vec3.directionFromRotation(0,yaw),right=new Vec3(-forward.z,0,forward.x);
        return origin.add(right.scale(local.x)).add(forward.scale(local.z)).add(0,local.y,0);
    }
    public static Optional<Vec3> anchor(Vec3 origin,float yaw,LivingEntity victim,PowerForm form,float t) {
        // Establish the scene once. Sampling the animated hand every frame enslaved
        // the receiver's root path to the actor and destroyed the paired choreography.
        // A compressed head stays in the animated palm until release. Baking its
        // first-frame offset and then only adding root travel loses that contact.
        boolean headGrip=headGrip(form);
        float contactTime=headGrip?Math.min(t,gripEnd(form)):0;
        float receiverTime=ConsumeSequence.receiverProgress(form,t,false);
        Vec3 contactOrigin=headGrip?actorAnchor(origin,yaw,form,contactTime,false):origin;
        return heldAnchor(contactOrigin,yaw,victim,form,contactTime).map(base->{
            double[] from=sample(data().motion,form,contactTime,false).orElse(new double[3]);
            double[] to=sample(data().motion,form,receiverTime,false).orElse(from);
            double scale=ConsumeSequence.humanoid(victim)?ConsumeSequence.receiverScale(victim):Math.clamp(victim.getBbHeight()/1.8,.3,1.5);
            Vec3 forward=Vec3.directionFromRotation(0,yaw),right=new Vec3(-forward.z,0,forward.x);
            // Scene travel is shared world distance, not the receiver's body scale.
            // Scaling a pig's path by its height made it land inside the actor.
            Vec3 goal=base.add(right.scale((to[0]-from[0])*.9375)).add(forward.scale((to[2]-from[2])*.9375)).add(0,(to[1]-from[1])*.9375,0);
            double[] pose=contactFrame(form,receiverTime).orElse(null);
            double floor=origin.y-(rigidReceiver(victim)?rigidFloor(victim,form,receiverTime):ConsumeSequence.humanoid(victim)&&pose!=null?pose[6]*scale:0);
            goal=new Vec3(goal.x,Math.max(floor,goal.y),goal.z);
            if(pose!=null){
                // Detached pieces retain the split frame's world anchor. The
                // actor's recovery must not keep towing or pushing their root.
                goal=separateTorso(goal,origin,yaw,victim,form,receiverTime,pose,scale);
            }
            return goal;
        });
    }
    public static float gripEnd(PowerForm form){return form==PowerForm.CLAWS?.72F:ConsumeSequence.release(form);}
    public static boolean headGrip(PowerForm form){
        var receiver=ConsumeReceiverScene.profile(form,false);
        return form==PowerForm.CLAWS&&receiver!=null&&receiver.style().equals("HEAD_CRUSH");
    }
    /** The posed torso has volume. A horizontal receiver cannot use standing width
     * and a fixed vertical threshold: its long axis then passes through the actor. */
    private static Vec3 separateTorso(Vec3 goal,Vec3 origin,float yaw,LivingEntity victim,PowerForm form,float t,double[] pose,double scale){
        Vec3 forward=Vec3.directionFromRotation(0,yaw),right=new Vec3(-forward.z,0,forward.x);
        var actorPose=contactFrame(form,t).orElse(pose);
        Vec3 actor=actorAnchor(origin,yaw,form,t,false).add(right.scale(actorPose[7]*.9375)).add(forward.scale(actorPose[9]*.9375)).add(0,actorPose[8]*.9375,0);
        float receiverTime=ConsumeSequence.receiverProgress(form,t,false);
        Vec3 center=rigidReceiver(victim)?rigidCenter(victim,form,receiverTime):new Vec3(pose[10]*scale,pose[11]*scale,pose[12]*scale);
        Vec3 gap=goal.add(right.scale(center.x)).add(forward.scale(center.z)).add(0,center.y,0).subtract(actor);
        double distance=gap.horizontalDistance();Vec3 direction=distance>.08?gap.multiply(1,0,1).scale(1/distance):forward;
        org.joml.Matrix4f orientation;
        if(rigidReceiver(victim))orientation=quadrupedBody(victim,form,receiverTime);
        else {
            var row=projection(form,receiverTime).orElse(null);orientation=new org.joml.Matrix4f();
            if(row!=null){var chest=quaternion(row,11);chest.set(-chest.x,-chest.y,chest.z,chest.w);orientation.rotate(quaternion(row,3)).rotate(chest);}
        }
        // Render Z is opposite the local forward convention used by placement data.
        var axisX=orientation.transformDirection(new org.joml.Vector3f(1,0,0));
        var axisY=orientation.transformDirection(new org.joml.Vector3f(0,1,0));
        var axisZ=orientation.transformDirection(new org.joml.Vector3f(0,0,1));
        double nx=direction.dot(right),nz=-direction.dot(forward);
        double halfW=victim.getBbWidth()*.48,halfH=victim.getBbHeight()*.38,halfD=victim.getBbWidth()*.42;
        double support=Math.abs(nx*axisX.x+nz*axisX.z)*halfW+Math.abs(nx*axisY.x+nz*axisY.z)*halfH+Math.abs(nx*axisZ.x+nz*axisZ.z)*halfD;
        double vertical=Math.abs(axisX.y)*halfW+Math.abs(axisY.y)*halfH+Math.abs(axisZ.y)*halfD;
        double minimum=.30+support+.06;
        if(Math.abs(gap.y)<vertical+.4&&distance<minimum)goal=goal.add(direction.scale(minimum-distance));
        return goal;
    }
    /** Receiver-space contact shared by placement and the actor's final grip fitting. */
    public static Optional<Vec3> carryContact(LivingEntity victim,String key,float t) {
        return contactFrame(key,t).map(p->carryContact(victim,key,t,p));
    }
    private static Vec3 carryContact(LivingEntity victim,String key,float t,double[] frame) {
        double scale=ConsumeSequence.humanoid(victim)?ConsumeSequence.receiverScale(victim):Math.clamp(victim.getBbHeight()/1.8,.3,1.5);
        if(rigidReceiver(victim)) {
            boolean quad=ConsumeSequence.quadruped(victim);
            var point=rigidBody(victim,key,t).transformPosition(new org.joml.Vector3f(0,victim.getBbHeight()*(quad?.65F:.86F),quad?-victim.getBbWidth()*.45F:0));
            return new Vec3(point.x,point.y,-point.z);
        }
        return new Vec3(frame[3],frame[4],frame[5]).scale(scale);
    }
    /** Carrying keeps a neck contact while each participant plays its own full pose. */
    public static Optional<Vec3> carryAnchor(Vec3 origin,float yaw,LivingEntity victim,String key,float t){
        return contactFrame(key,t).map(p->{
            double scale=ConsumeSequence.humanoid(victim)?ConsumeSequence.receiverScale(victim):Math.clamp(victim.getBbHeight()/1.8,.3,1.5);
            Vec3 actor=new Vec3(p[0],p[1],p[2]).scale(.9375);
            Vec3 receiver=carryContact(victim,key,t,p);
            Vec3 local=actor.subtract(receiver);
            Vec3 center=new Vec3(p[10],p[11],p[12]).scale(scale);
            if(rigidReceiver(victim)){
                var q=rigidBody(victim,key,t).transformPosition(new org.joml.Vector3f(0,victim.getBbHeight()*.5F,0));center=new Vec3(q.x,q.y,-q.z);
            }
            Vec3 gap=local.add(center).subtract(new Vec3(p[7],p[8],p[9]).scale(.9375));
            double distance=gap.horizontalDistance(),minimum=.34+victim.getBbWidth()*.5;
            if(Math.abs(gap.y)<.85&&distance<minimum)local=local.add((distance>.02?gap.multiply(1,0,1).scale(1/distance):new Vec3(0,0,1)).scale(minimum-distance));
            Vec3 forward=Vec3.directionFromRotation(0,yaw),right=new Vec3(-forward.z,0,forward.x);
            return origin.add(right.scale(local.x)).add(forward.scale(local.z)).add(0,Math.max(0,local.y),0);
        });
    }
    private static Optional<Vec3> heldAnchor(Vec3 origin,float yaw,LivingEntity victim,PowerForm form,float t) {
        return contactFrame(form,t).map(p->{
            double scale=ConsumeSequence.humanoid(victim)?ConsumeSequence.receiverScale(victim):Math.clamp(victim.getBbHeight()/1.8,.3,1.5);
            Vec3 actor=new Vec3(p[0],p[1],p[2]).scale(.9375);
            Vec3 receiver=ConsumeSequence.humanoid(victim)?new Vec3(p[3],p[4],p[5]).scale(scale)
                    :new Vec3(0,victim.getBbHeight()*(form==PowerForm.CLAWS||form==PowerForm.HAMMERFISTS?.82:.62),0);
            if(rigidReceiver(victim))receiver=rigidContact(victim,form,t);
            Vec3 local=actor.subtract(receiver);
            Vec3 actorBody=new Vec3(p[7],p[8],p[9]).scale(.9375);
            Vec3 receiverBody=ConsumeSequence.humanoid(victim)?new Vec3(p[10],p[11],p[12]).scale(scale):new Vec3(0,victim.getBbHeight()*.5,0);
            if(rigidReceiver(victim))receiverBody=rigidCenter(victim,form,t);
            Vec3 separation=local.add(receiverBody).subtract(actorBody);
            double radius=Math.max(.42,.30+victim.getBbWidth()*.5-.08),horizontal=separation.horizontalDistance();
            if(Math.abs(separation.y)<.75 && horizontal<radius){Vec3 direction=horizontal>.02?separation.multiply(1,0,1).scale(1/horizontal):new Vec3(0,0,1);local=local.add(direction.scale(radius-horizontal));}
            double sole=rigidReceiver(victim)?rigidFloor(victim,form,t):ConsumeSequence.humanoid(victim)?p[6]*scale:0;
            local=new Vec3(local.x,Math.max(local.y,-sole),local.z);
            Vec3 forward=Vec3.directionFromRotation(0,yaw),right=new Vec3(-forward.z,0,forward.x);
            return origin.add(right.scale(local.x)).add(forward.scale(local.z)).add(0,local.y,0);
        });
    }
    private static Tracks load() {
        var resource=MotionResources.read("consume-placement.data",4*1024*1024);
        if(resource.isEmpty())return new Tracks(Map.of(),Map.of(),Map.of(),Map.of());
        try {
            var root=resource.orElseThrow();
            if(root.get("schema").getAsInt()!=4)throw new IllegalArgumentException("Consume scene profile must use schema 4");
            return new Tracks(read(root.getAsJsonObject("contacts"),46),read(root.getAsJsonObject("projections"),51),read(root.getAsJsonObject("motion"),3),read(root.getAsJsonObject("actorMotion"),3));
        } catch(RuntimeException e){throw new IllegalStateException("Cannot load paired consume placement",e);}
    }
    private static Map<String,List<double[]>> read(com.google.gson.JsonObject object,int width){
        Map<String,List<double[]>> result=new HashMap<>();
        for(var entry:object.entrySet()){
            var rows=entry.getValue().getAsJsonArray();if(rows.size()<2||rows.size()>1200)throw new IllegalArgumentException("Invalid consume duration");
            var points=new ArrayList<double[]>();
            for(var row:rows){
                var v=row.getAsJsonArray();if(v.size()!=width)throw new IllegalArgumentException("Invalid consume frame");
                double[] p=new double[width];for(int i=0;i<width;i++){p[i]=v.get(i).getAsDouble();if(!Double.isFinite(p[i])||Math.abs(p[i])>20)throw new IllegalArgumentException("Invalid consume transform");}
                points.add(p);
            }
            result.put(entry.getKey(),List.copyOf(points));
        }
        return Map.copyOf(result);
    }
}
