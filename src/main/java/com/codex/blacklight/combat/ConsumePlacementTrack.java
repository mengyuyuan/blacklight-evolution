package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

/** Contact points share the animated rig's frame; no independent origin rebasing. */
public final class ConsumePlacementTrack {
    private record Tracks(Map<String,List<double[]>> contacts,Map<String,List<double[]>> projections,Map<String,List<double[]>> motion,Map<String,List<double[]>> actorMotion,Map<String,List<double[]>> contactCues,Map<String,List<double[]>> releasePalms,Map<String,List<double[]>> villagerPairs) {}
    private static Tracks tracks;
    private ConsumePlacementTrack() {}
    /** Root travel is shared scene distance; receiver body scale only affects its bind contact. */
    public static Vec3 sceneActorAnchor(Vec3 origin,float yaw,String key,float t){
        double[] p=sample(data().actorMotion,key,t,false).orElse(new double[3]);
        Vec3 forward=Vec3.directionFromRotation(0,yaw),right=new Vec3(-forward.z,0,forward.x);
        return origin.add(right.scale(p[0]*.9375)).add(forward.scale(p[2]*.9375)).add(0,p[1]*.9375,0);
    }
    public static Optional<Vec3> sceneAnchor(Vec3 origin,float yaw,LivingEntity victim,String key,float t){
        return contactFrame(key,0).map(first->{
            double scale=ConsumeSequence.receiverScale(victim);
            double[] from=sample(data().motion,key,0,false).orElse(new double[3]);
            double[] to=sample(data().motion,key,t,false).orElse(from);
            double[] pose=contactFrame(key,t).orElse(first);
            Vec3 local=new Vec3(first[0]*.9375-first[3]*scale+(to[0]-from[0])*.9375,
                    first[1]*.9375-first[4]*scale+(to[1]-from[1])*.9375,
                    first[2]*.9375-first[5]*scale+(to[2]-from[2])*.9375);
            Vec3 forward=Vec3.directionFromRotation(0,yaw),right=new Vec3(-forward.z,0,forward.x);
            return origin.add(right.scale(local.x)).add(forward.scale(local.z)).add(0,Math.max(-pose[6]*scale,local.y),0);
        });
    }
    private static synchronized Tracks data(){if(tracks==null)tracks=load();return tracks;}
    private static Optional<double[]> sample(Map<String,List<double[]>> map,PowerForm form,float t,boolean rotations) {
        return sample(map,ConsumeSequence.key(form),t,rotations);
    }
    private static Optional<double[]> sample(Map<String,List<double[]>> map,String key,float t,boolean rotations) {
        var frames=map.get(key);if(frames==null)return Optional.empty();
        double f=Math.clamp(t,0,1)*(frames.size()-1);int i=(int)f;float u=(float)(f-i);
        double[] a=frames.get(i),b=frames.get(Math.min(i+1,frames.size()-1)),out=new double[a.length];
        for(int n=0;n<out.length;n++)out[n]=a[n]+(b[n]-a[n])*u;
        if(rotations)for(int n=3;n+3<out.length;n+=4){
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
        return quadrupedBody(victim,form,t,ConsumeVariant.DEFAULT);
    }
    private static org.joml.Matrix4f quadrupedBody(LivingEntity victim,PowerForm form,float t,int variant){return rigidBody(victim,ConsumeSequence.key(form,variant),t);}
    public static org.joml.Matrix4f rigidBody(LivingEntity victim,String key,float t) {
        var matrix=new org.joml.Matrix4f();
        if (villagerReceiver(victim) || (key.equals("normal_beatdown") && ConsumeSequence.humanoid(victim))) {
            t=villagerProgress(key,t);
            var pair=sample(data().villagerPairs,key,t,true);
            if (pair.isPresent()) {
                double[] row=pair.get();
                var facing=quaternion(row,3);
                // On the street the back of the robe rests flat. A human pelvis can
                // roll onto a hip, while the villager's rigid coat would stand on one corner.
                double support=key.equals("normal_beatdown")?groundWeight(PowerForm.NONE,ConsumeVariant.BEATDOWN,t):
                        key.equals("normal_head_stomp")?groundWeight(PowerForm.NONE,ConsumeVariant.HEAD_STOMP,t):0;
                if(support>0){
                    var along=key.equals("normal_beatdown")
                            ?new org.joml.Vector3f(BEATDOWN_SIDE,0,BEATDOWN_FORWARD)
                            :facing.transform(new org.joml.Vector3f(0,1,0));
                    along.y=0;
                    if(along.lengthSquared()>.0001F){
                        along.normalize();
                        var back=new org.joml.Vector3f(0,-1,0);
                        var across=new org.joml.Vector3f(along).cross(back);
                        var axes=new org.joml.Matrix3f().setColumn(0,across).setColumn(1,along).setColumn(2,back);
                        facing.slerp(axes.getNormalizedRotation(new Quaternionf()),(float)support);
                    }
                }
                return matrix.translate(0,(float)(row[7]*victim.getBbHeight()/1.95),0)
                        .rotate(facing).translate(0,-victim.getBbHeight()*.84F,0);
            }
        }
        var frame=projection(key,t).orElse(null);if(frame==null)return matrix;
        float scale=.9375F,pivot=victim.getBbHeight()*.5F;
        var chest=quaternion(frame,11); // Vanilla model space -> Minecraft render space.
        chest.set(-chest.x,-chest.y,chest.z,chest.w);
        // A quadruped's long body axis is horizontal in bind pose. Align that axis
        // with the receiver's spine before applying the authored lift/fall rotation.
        return matrix.translate((float)frame[0]*scale,(float)frame[1]*scale,(float)frame[2]*scale)
                .translate(0,pivot,0).rotate(quaternion(frame,3)).rotate(chest)
                .rotateX(ConsumeSequence.quadruped(victim)?(float)Math.PI/2:0).translate(0,-pivot,0);
    }
    // P1 beatdown frames 46–85: horizontal pelvis-to-ankles direction,
    // expressed in the shared scene frame. Keep it through alternating punches.
    private static final float BEATDOWN_SIDE = -.1123F, BEATDOWN_FORWARD = -.9937F;

    /** The held pose and the world anchor settle on the same footfall. */
    static float villagerProgress(String key,float t) {
        if(!key.equals("normal_head_stomp"))return t;
        var cues=data().contactCues.get(key);
        return cues==null?t:Math.min(t,(float)cues.get(0)[0]);
    }

    public static boolean villagerReceiver(LivingEntity victim) {
        return victim instanceof net.minecraft.world.entity.npc.AbstractVillager
                || victim instanceof com.codex.blacklight.entity.BlacklightInfectedHost host
                && (host.speciesId().equals("minecraft:villager") || host.speciesId().equals("minecraft:wandering_trader"));
    }

    public static boolean rigidReceiver(LivingEntity victim){
        if(ConsumeSequence.quadruped(victim)||victim instanceof net.minecraft.world.entity.npc.AbstractVillager
                ||victim instanceof net.minecraft.world.entity.monster.Creeper)return true;
        if(victim instanceof com.codex.blacklight.entity.BlacklightInfectedHost host)
            return host.creeper()||host.speciesId().equals("minecraft:villager")||host.speciesId().equals("minecraft:wandering_trader");
        return false;
    }
    private static Vec3 rigidCenter(LivingEntity victim,PowerForm form,float t,int variant){
        var p=quadrupedBody(victim,form,t,variant).transformPosition(new org.joml.Vector3f(0,victim.getBbHeight()*.5F,0));return new Vec3(p.x,p.y,-p.z);
    }
    private static Vec3 rigidContact(LivingEntity victim,PowerForm form,float t,int variant){
        boolean head=form==PowerForm.CLAWS||form==PowerForm.HAMMERFISTS;
        boolean quad=ConsumeSequence.quadruped(victim);
        float height=victim.getBbHeight()*(quad?(head?.65F:.5F):(head?.86F:.65F));
        var p=quadrupedBody(victim,form,t,variant).transformPosition(new org.joml.Vector3f(0,height,quad&&head?-victim.getBbWidth()*.45F:0));
        return new Vec3(p.x,p.y,-p.z);
    }
    private static double rigidFloor(LivingEntity victim,PowerForm form,float t,int variant){
        var matrix=quadrupedBody(victim,form,t,variant);float w=victim.getBbWidth()*.5F,h=victim.getBbHeight();double min=Double.POSITIVE_INFINITY;
        for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})for(int y:new int[]{0,1})min=Math.min(min,matrix.transformPosition(new org.joml.Vector3f(x*w,y*h,z*w)).y);
        return min;
    }
    public static Vec3 actorAnchor(Vec3 origin,float yaw,PowerForm form,float t,boolean large){
        return actorAnchor(origin,yaw,form,t,large,ConsumeVariant.DEFAULT);
    }
    public static Vec3 actorAnchor(Vec3 origin,float yaw,PowerForm form,float t,boolean large,int variant){
        double[] p=sample(data().actorMotion,ConsumeSequence.clipKey(form,large,variant),t,false).orElse(new double[3]);
        Vec3 local=new Vec3(p[0],p[1],p[2]).scale(.9375);
        if(large)local=new Vec3(Math.clamp(local.x,-.35,.35),Math.clamp(local.y,-.18,.3),Math.clamp(local.z,-.2,.12));
        Vec3 forward=Vec3.directionFromRotation(0,yaw),right=new Vec3(-forward.z,0,forward.x);
        return origin.add(right.scale(local.x)).add(forward.scale(local.z)).add(0,local.y,0);
    }
    public static Optional<Vec3> anchor(Vec3 origin,float yaw,LivingEntity victim,PowerForm form,float t) {
        return anchor(origin,yaw,victim,form,t,ConsumeVariant.DEFAULT);
    }
    public static Optional<Vec3> anchor(Vec3 origin,float yaw,LivingEntity victim,PowerForm form,float t,int variant) {
        String key=ConsumeSequence.key(form,variant);
        if(villagerReceiver(victim))t=villagerProgress(key,t);
        var goal=authoredAnchor(origin,yaw,victim,form,t,variant);
        var cues=data().contactCues.get(key);
        if(goal.isEmpty())return goal;
        if(cues==null)return villagerReceiver(victim)||thrownScene(key)
                ?Optional.of(supported(goal.get(),origin,victim,form,t,variant)):goal;
        double[] first=cues.get(0),last=cues.get(cues.size()-1);
        if(key.equals("normal_beatdown") && ConsumeSequence.humanoid(victim)){
            // Both fists aim at one grounded head. Use the paired neck after the
            // throw's first contact window, independently of which hand is striking.
            double planted=groundWeight(form,variant,t);
            Vec3 position=goal.get();
            if(planted>0){
                // Frame 35 is the first settled neck pose after the slam.
                float restTime=35F/(30*ConsumeReceiverScene.clipSeconds(form,variant));
                double[] marker=sample(data().villagerPairs,key,restTime,false).orElse(null);
                if(marker==null)return goal;
                Vec3 forward=Vec3.directionFromRotation(0,yaw);
                Vec3 right=new Vec3(-forward.z,0,forward.x);
                Vec3 neck=actorAnchor(origin,yaw,form,restTime,false,variant)
                        .add(right.scale(marker[0])).subtract(forward.scale(marker[2]));
                // The paired marker is at the punch. The head extends
                // about a quarter block from the neck in the opposite direction to the feet.
                double halfHead=.25*victim.getBbHeight()/1.95;
                Vec3 rest=neck.add(right.scale(-BEATDOWN_SIDE*halfHead)).add(forward.scale(BEATDOWN_FORWARD*halfHead));
                position=position.lerp(new Vec3(rest.x,position.y,rest.z),planted);
            }
            return Optional.of(supported(position,origin,victim,form,t,variant));
        }
        if(t<=first[0]-first[1]||t>=last[0]+last[2])return Optional.of(supported(goal.get(),origin,victim,form,t,variant));
        double weight=ease((t-first[0]+first[1])/first[1])*ease((last[0]+last[2]-t)/last[2]);
        int next=0;
        while(next<cues.size()-1&&cues.get(next)[0]<t)next++;
        double[] b=cues.get(next),a=cues.get(Math.max(0,next-1));
        Vec3 offset=contactOffset(origin,yaw,victim,form,key,variant,b);
        if(a!=b && t<b[0]){
            Vec3 before=contactOffset(origin,yaw,victim,form,key,variant,a);
            offset=before.lerp(offset,ease((t-a[0])/(b[0]-a[0])));
        }
        return Optional.of(supported(goal.get().add(offset.scale(weight)),origin,victim,form,t,variant));
    }
    /** The receiver rotates about its pelvis. Support the lowest posed corner at
     * the landing surface underneath this scene, including slabs and steps. */
    private static Vec3 supported(Vec3 position,Vec3 origin,LivingEntity victim,PowerForm form,float time,int variant){
        float poseTime=ConsumeSequence.receiverProgress(form,time,false,variant);
        // The precomputed villager root already sits at the lowest posed skin vertex.
        var pair=villagerReceiver(victim)?sample(data().villagerPairs,ConsumeSequence.key(form,variant),poseTime,true):Optional.<double[]>empty();
        boolean pairedVillager=pair.isPresent();
        double bottom=pairedVillager?0:rigidReceiver(victim)?rigidFloor(victim,form,poseTime,variant)
                :contactFrame(ConsumeSequence.key(form,variant),poseTime)
                .map(frame->frame[6]*ConsumeSequence.receiverScale(victim)).orElse(0.0);
        double planted=groundWeight(form,variant,time);
        Vec3 start=new Vec3(position.x,origin.y+1.15,position.z);
        if(planted==0 && !pairedVillager && position.y+bottom>=start.y)return position;
        var ground=victim.level().clip(new net.minecraft.world.level.ClipContext(start,start.add(0,-3,0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,victim));
        if(ground.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)return position;
        double surface=ground.getLocation().y;
        if(planted>0){
            double rest=surface-bottom;
            return new Vec3(position.x,Math.max(rest,position.y+(rest-position.y)*planted),position.z);
        }
        if(pairedVillager && surface>=origin.y-1.01) {
            // Compare the rendered sole against the paired source skeleton. Contact fitting
            // may lift an airborne captive, but cannot pull planted feet off the street.
            double clearance=Math.min(Math.max(0,position.y-surface),pair.get()[8]);
            return new Vec3(position.x,surface+clearance,position.z);
        }
        double lift=surface-position.y-bottom;
        return lift>0?position.add(0,lift,0):position;
    }
    /** Collision tops under both ends of the posed torso. The victim's standing
     * entity box and the player's feet do not describe a body lying on stairs. */
    public static java.util.OptionalDouble landingSurface(LivingEntity victim,Vec3 base,float yaw,
                                                           int variant,float t,double nearbyY){
        var forward=Vec3.directionFromRotation(0,yaw);
        var right=new Vec3(-forward.z,0,forward.x);
        double top=Double.NEGATIVE_INFINITY;
        var rotation=rigidBody(victim,ConsumeSequence.key(PowerForm.NONE,variant),t);
        for(float height:new float[]{.44F,.69F}){
            double site=Double.NEGATIVE_INFINITY;
            for(int side=-1;side<=1;side++){
                var local=rotation.transformPosition(new org.joml.Vector3f(side*victim.getBbWidth()*.28F,
                        height*victim.getBbHeight(),0));
                Vec3 sample=base.add(right.scale(local.x)).add(forward.scale(-local.z));
                var from=new Vec3(sample.x,nearbyY+1.05,sample.z);
                var hit=victim.level().clip(new net.minecraft.world.level.ClipContext(from,
                        from.add(0,-2.55,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,victim));
                if(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK)
                    site=Math.max(site,hit.getLocation().y);
            }
            if(!Double.isFinite(site))return java.util.OptionalDouble.empty();
            top=Math.max(top,site);
        }
        return java.util.OptionalDouble.of(top);
    }
    public static double groundWeight(PowerForm form,int variant,float progress){
        int gate=ConsumeVisual.groundGate(form.ordinal(),variant);
        if(gate<0)return 0;
        // Progress arrives normalized to the specific clip. Lower the shoulders
        // during the wind-up so they can carry weight before the first strike.
        double ticks=progress*20*ConsumeReceiverScene.clipSeconds(form,variant);
        // The throw reaches its apex on source frame 24. Plant the receiver
        // only during the final descent into frame 30.
        double landingTicks=variant==ConsumeVariant.BEATDOWN?3:8;
        return ease((ticks-gate+landingTicks)/landingTicks);
    }
    private static double ease(double value){
        double t=Math.clamp(value,0,1);return t*t*(3-2*t);
    }
    /** Offset just the encounter's root path. The punch and its receiver still play
     * independent poses and detached tissue never chases a live wrist. */
    private static Vec3 contactOffset(Vec3 origin,float yaw,LivingEntity victim,PowerForm form,String key,int variant,double[] cue){
        float time=(float)cue[0];
        Vec3 base=authoredAnchor(origin,yaw,victim,form,time,variant).orElse(origin);
        var frame=contactFrame(key,time).orElseThrow();
        Vec3 receiver;
        boolean arm=key.equals("muscle");
        boolean chest=key.equals("normal") || key.equals("muscle_heart") && cue==data().contactCues.get(key).get(0);
        if(rigidReceiver(victim)){
            boolean quad=ConsumeSequence.quadruped(victim);
            float height=victim.getBbHeight(),width=victim.getBbWidth();
            float y=chest&&villagerReceiver(victim)?height*.59F:chest?height*.5F:height*(quad?.65F:arm?.66F:.88F);
            float z=chest&&villagerReceiver(victim)?-width*.30F:quad&&!chest?-width*.45F:0;
            var socket=new org.joml.Vector3f(arm&&!villagerReceiver(victim)?width*.18F:0,y,z);
            rigidBody(victim,key,time).transformPosition(socket);
            receiver=new Vec3(socket.x,socket.y,-socket.z);
        }else{
            int joint=arm||chest?10:3;
            receiver=new Vec3(frame[joint],frame[joint+1],frame[joint+2]).scale(ConsumeSequence.receiverScale(victim));
        }
        Vec3 actor=actorAnchor(origin,yaw,form,time,false,variant);
        Vec3 local=new Vec3(cue[3],cue[4],cue[5]).scale(.9375).subtract(receiver);
        Vec3 forward=Vec3.directionFromRotation(0,yaw),right=new Vec3(-forward.z,0,forward.x);
        return actor.add(right.scale(local.x)).add(forward.scale(local.z)).add(0,local.y,0).subtract(base);
    }
    public static Vec3 ripFollowThrough(LivingEntity carrier,ConsumeVisual visual,int side){
        var palms=data().releasePalms.get("muscle");
        if(palms==null || palms.size()!=2)return null;
        var point=palms.get(side);
        var forward=Vec3.directionFromRotation(0,visual.facing());var right=new Vec3(-forward.z,0,forward.x);
        double scale=.9375*carrier.getScale();
        return carrier.position().add(right.scale(point[0]*scale)).add(forward.scale(point[2]*scale)).add(0,point[1]*scale,0);
    }
    /** Use the baked sole or fist when the consumer's skin is outside the camera. */
    public static Vec3 impactSocket(LivingEntity owner,ConsumeVisual visual,float partial){
        PowerForm form=PowerForm.values()[visual.form()];
        String key=ConsumeSequence.key(form,visual.variant());
        float t=visual.motionProgress(owner.level().getGameTime(),partial);
        var cues=data().contactCues.get(key);
        double[] point=cues==null?contactFrame(key,t).orElse(null):
                cues.stream().min(java.util.Comparator.comparingDouble(c->Math.abs(c[0]-t))).orElse(null);
        if(point==null)return null;
        int i=cues==null?0:3;
        Vec3 forward=Vec3.directionFromRotation(0,visual.facing()),right=new Vec3(-forward.z,0,forward.x);
        return owner.getPosition(partial).add(right.scale(point[i]*.9375)).add(forward.scale(point[i+2]*.9375)).add(0,point[i+1]*.9375,0);
    }

    private static boolean thrownScene(String scene){
        return scene.equals("claws")||scene.equals("blade")||scene.equals("whip")||scene.equals("whip_lash")||scene.equals("hammer");
    }
    private static Optional<Vec3> authoredAnchor(Vec3 origin,float yaw,LivingEntity victim,PowerForm form,float t,int variant){
        String scene=ConsumeSequence.key(form,variant);
        boolean villager=villagerReceiver(victim);
        // A rigid receiver cannot fold at the waist during a lash. Its travel
        // follows the original Motion_Root below, with its neck free to rotate.
        boolean rigidLash = scene.equals("whip_lash") && rigidReceiver(victim);
        if (villager || (thrownScene(scene) && !rigidLash)) {
            float receiverTime=ConsumeSequence.receiverProgress(form,t,false,variant);
            var pair=sample(data().villagerPairs,scene,receiverTime,true);
            if (pair.isPresent()) {
                double[] point=pair.get();
                Vec3 actor=actorAnchor(origin,yaw,form,receiverTime,false,variant);
                Vec3 forward=Vec3.directionFromRotation(0,yaw),right=new Vec3(-forward.z,0,forward.x);
                double clearance=ConsumeVariant.stealth(form,variant)?(variant==ConsumeVariant.STEALTH_BEARHUG?.08:.26):0;
                Vec3 head=actor.add(right.scale(point[0])).add(forward.scale(clearance-point[2])).add(0,point[1],0);
                if(villager)return Optional.of(head.add(0,-point[7]*victim.getBbHeight()/1.95,0));
                return carryContact(victim,scene,receiverTime).map(local->head.subtract(right.scale(local.x))
                        .subtract(forward.scale(local.z)).add(0,-local.y,0));
            }
        }
        if(form==PowerForm.NONE&&ConsumeVariant.normalize(form,variant)>=ConsumeVariant.HEAD_STOMP)
            return sceneAnchor(origin,yaw,victim,ConsumeSequence.key(form,variant),t);
        // Establish the scene once. Sampling the animated hand every frame enslaved
        // the receiver's root path to the actor and destroyed the paired choreography.
        // A compressed head stays in the animated palm until release. Baking its
        // first-frame offset and then only adding root travel loses that contact.
        String key=ConsumeSequence.key(form,variant);
        boolean headGrip=headGrip(form,variant);
        float contactTime=headGrip?Math.min(t,gripEnd(form)):0;
        float receiverTime=ConsumeSequence.receiverProgress(form,t,false,variant);
        Vec3 contactOrigin=headGrip?actorAnchor(origin,yaw,form,contactTime,false,variant):origin;
        return heldAnchor(contactOrigin,yaw,victim,form,contactTime,variant).map(base->{
            double[] from=sample(data().motion,key,contactTime,false).orElse(new double[3]);
            double[] to=sample(data().motion,key,receiverTime,false).orElse(from);
            double scale=ConsumeSequence.humanoid(victim)?ConsumeSequence.receiverScale(victim):Math.clamp(victim.getBbHeight()/1.8,.3,1.5);
            Vec3 forward=Vec3.directionFromRotation(0,yaw),right=new Vec3(-forward.z,0,forward.x);
            // Scene travel is shared world distance, not the receiver's body scale.
            // Scaling a pig's path by its height made it land inside the actor.
            // The lash lifts by a body length. Scale only its vertical arc to the rigid
            // receiver; the horizontal throw, apex time and landing beat stay in scene space.
            double liftScale=rigidLash?Math.min(1,victim.getBbHeight()/1.95):1;
            Vec3 goal=base.add(right.scale((to[0]-from[0])*.9375)).add(forward.scale((to[2]-from[2])*.9375))
                    .add(0,(to[1]-from[1])*.9375*liftScale,0);
            double[] pose=contactFrame(key,receiverTime).orElse(null);
            double floor=origin.y-(rigidReceiver(victim)?rigidFloor(victim,form,receiverTime,variant):ConsumeSequence.humanoid(victim)&&pose!=null?pose[6]*scale:0);
            goal=new Vec3(goal.x,Math.max(floor,goal.y),goal.z);
            if(pose!=null){
                // Detached pieces retain the split frame's world anchor. The
                // actor's recovery must not keep towing or pushing their root.
                goal=separateTorso(goal,origin,yaw,victim,form,receiverTime,pose,scale,variant);
            }
            return goal;
        });
    }
    /** Largest actor lag the receiver follows; beyond it the pair was broken by a teleport or wall. */
    public static final double MAX_ACTOR_DRIFT=3;
    /** Keeps the paired scene rigid around the actor's real position. Collision, the
     * per-tick speed clamp and network lag all move the actor off its authored root
     * path; the receiver follows that same drift for the whole scene instead of only
     * during a head grip, so the bodies never separate or interpenetrate. */
    public static Vec3 pairedGoal(Vec3 trackGoal,Vec3 actorActual,Vec3 actorGoal){
        Vec3 drift=actorActual.subtract(actorGoal);
        if(!Double.isFinite(drift.lengthSqr()))return trackGoal;
        double length=drift.length();
        if(length>MAX_ACTOR_DRIFT)drift=drift.scale(MAX_ACTOR_DRIFT/length);
        return trackGoal.add(drift);
    }
    public static float gripEnd(PowerForm form){return form==PowerForm.CLAWS?.72F:ConsumeSequence.release(form);}
    public static boolean headGrip(PowerForm form){
        return headGrip(form,ConsumeVariant.DEFAULT);
    }
    public static boolean headGrip(PowerForm form,int variant){
        var receiver=ConsumeReceiverScene.profile(form,false,variant);
        return form==PowerForm.CLAWS&&receiver!=null&&receiver.style().equals("HEAD_CRUSH");
    }
    /** The posed torso has volume. A horizontal receiver cannot use standing width
     * and a fixed vertical threshold: its long axis then passes through the actor. */
    private static Vec3 separateTorso(Vec3 goal,Vec3 origin,float yaw,LivingEntity victim,PowerForm form,float t,double[] pose,double scale,int variant){
        Vec3 forward=Vec3.directionFromRotation(0,yaw),right=new Vec3(-forward.z,0,forward.x);
        String key=ConsumeSequence.key(form,variant);
        var actorPose=contactFrame(key,t).orElse(pose);
        Vec3 actor=actorAnchor(origin,yaw,form,t,false,variant).add(right.scale(actorPose[7]*.9375)).add(forward.scale(actorPose[9]*.9375)).add(0,actorPose[8]*.9375,0);
        float receiverTime=ConsumeSequence.receiverProgress(form,t,false,variant);
        Vec3 center=rigidReceiver(victim)?rigidCenter(victim,form,receiverTime,variant):new Vec3(pose[10]*scale,pose[11]*scale,pose[12]*scale);
        Vec3 gap=goal.add(right.scale(center.x)).add(forward.scale(center.z)).add(0,center.y,0).subtract(actor);
        double distance=gap.horizontalDistance();Vec3 direction=distance>.08?gap.multiply(1,0,1).scale(1/distance):forward;
        org.joml.Matrix4f orientation;
        if(rigidReceiver(victim))orientation=quadrupedBody(victim,form,receiverTime,variant);
        else {
            var row=projection(key,receiverTime).orElse(null);orientation=new org.joml.Matrix4f();
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
        // Push toward the actor's front, not along the gap: the gap direction flips when the
        // authored path crosses the actor's centre, which teleported the receiver to the
        // other side. The vertical cut-off also fades instead of switching on in one frame.
        double overlap=Math.clamp((vertical+.4+.25-Math.abs(gap.y))/.25,0,1);
        if(overlap>0&&distance<minimum)goal=goal.add(forward.scale((minimum-distance)*overlap));
        return goal;
    }
    /** Receiver-space contact shared by placement and the actor's final grip fitting. */
    public static Optional<Vec3> carryContact(LivingEntity victim,String key,float t) {
        return contactFrame(key,t).map(p->carryContact(victim,key,t,p));
    }
    private static Vec3 carryContact(LivingEntity victim,String key,float t,double[] frame) {
        if(victim instanceof com.codex.blacklight.entity.EncounterMob flyer&&flyer.kind()==com.codex.blacklight.entity.EncounterKind.FLYER){
            boolean blade=com.codex.blacklight.registry.ModAttachments.peekData(flyer,com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY).form()==PowerForm.BLADE.ordinal();
            return FlyerReceiverPose.center(flyer,key,t,blade?"torso":"head");
        }
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
            if(victim instanceof com.codex.blacklight.entity.EncounterMob flyer&&flyer.kind()==com.codex.blacklight.entity.EncounterKind.FLYER)center=FlyerReceiverPose.center(flyer,key,t,"torso");
            if(rigidReceiver(victim)){
                var q=rigidBody(victim,key,t).transformPosition(new org.joml.Vector3f(0,victim.getBbHeight()*.5F,0));center=new Vec3(q.x,q.y,-q.z);
            }
            Vec3 gap=local.add(center).subtract(new Vec3(p[7],p[8],p[9]).scale(.9375));
            double distance=gap.horizontalDistance(),minimum=.34+victim.getBbWidth()*.5;
            if(victim instanceof com.codex.blacklight.entity.EncounterMob flyer&&flyer.kind()==com.codex.blacklight.entity.EncounterKind.FLYER)minimum=.34+FlyerReceiverPose.torsoRadius();
            if(Math.abs(gap.y)<.85&&distance<minimum)local=local.add((distance>.02?gap.multiply(1,0,1).scale(1/distance):new Vec3(0,0,1)).scale(minimum-distance));
            Vec3 forward=Vec3.directionFromRotation(0,yaw),right=new Vec3(-forward.z,0,forward.x);
            return origin.add(right.scale(local.x)).add(forward.scale(local.z)).add(0,Math.max(0,local.y),0);
        });
    }
    private static Optional<Vec3> heldAnchor(Vec3 origin,float yaw,LivingEntity victim,PowerForm form,float t,int variant) {
        return contactFrame(ConsumeSequence.key(form,variant),t).map(p->{
            double scale=ConsumeSequence.humanoid(victim)?ConsumeSequence.receiverScale(victim):Math.clamp(victim.getBbHeight()/1.8,.3,1.5);
            Vec3 actor=new Vec3(p[0],p[1],p[2]).scale(.9375);
            Vec3 receiver=ConsumeSequence.humanoid(victim)?new Vec3(p[3],p[4],p[5]).scale(scale)
                    :new Vec3(0,victim.getBbHeight()*(form==PowerForm.CLAWS||form==PowerForm.HAMMERFISTS?.82:.62),0);
            if(rigidReceiver(victim))receiver=rigidContact(victim,form,t,variant);
            Vec3 local=actor.subtract(receiver);
            Vec3 actorBody=new Vec3(p[7],p[8],p[9]).scale(.9375);
            Vec3 receiverBody=ConsumeSequence.humanoid(victim)?new Vec3(p[10],p[11],p[12]).scale(scale):new Vec3(0,victim.getBbHeight()*.5,0);
            if(rigidReceiver(victim))receiverBody=rigidCenter(victim,form,t,variant);
            Vec3 separation=local.add(receiverBody).subtract(actorBody);
            double radius=Math.max(.42,.30+victim.getBbWidth()*.5-.08),horizontal=separation.horizontalDistance();
            // Clear the actor's body along its own front (local +Z). Pushing along the separation
            // flipped side when the authored path crossed the actor's centre and snapped the receiver
            // ~0.7 blocks in one frame; the vertical cut-off fades instead of switching.
            double clear=Math.clamp((.9-Math.abs(separation.y))/.15,0,1);
            if(clear>0 && horizontal<radius){double front=Math.sqrt(radius*radius-separation.x*separation.x);if(separation.z<front)local=local.add(0,0,(front-separation.z)*clear);}
            double sole=rigidReceiver(victim)?rigidFloor(victim,form,t,variant):ConsumeSequence.humanoid(victim)?p[6]*scale:0;
            local=new Vec3(local.x,Math.max(local.y,-sole),local.z);
            Vec3 forward=Vec3.directionFromRotation(0,yaw),right=new Vec3(-forward.z,0,forward.x);
            return origin.add(right.scale(local.x)).add(forward.scale(local.z)).add(0,local.y,0);
        });
    }
    private static Tracks load() {
        var resource=MotionResources.read("consume-placement.data",8*1024*1024);
        if(resource.isEmpty())return new Tracks(Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of());
        try {
            var root=resource.orElseThrow();
            if(root.get("schema").getAsInt()!=4)throw new IllegalArgumentException("Consume scene profile must use schema 4");
            return new Tracks(read(root.getAsJsonObject("contacts"),46),read(root.getAsJsonObject("projections"),51),read(root.getAsJsonObject("motion"),3),read(root.getAsJsonObject("actorMotion"),3),
                    root.has("contactCues")?read(root.getAsJsonObject("contactCues"),6):Map.of(),
                    root.has("releasePalms")?read(root.getAsJsonObject("releasePalms"),3):Map.of(),
                    root.has("villagerPairs")?read(root.getAsJsonObject("villagerPairs"),9):Map.of());
        } catch(RuntimeException e){throw new IllegalStateException("Cannot load paired consume placement",e);}
    }
    private static Map<String,List<double[]>> read(com.google.gson.JsonObject object,int width){
        Map<String,List<double[]>> result=new HashMap<>();
        for(var entry:object.entrySet()){
            var rows=entry.getValue().getAsJsonArray();if(rows.size()<(width==6?1:2)||rows.size()>1200)throw new IllegalArgumentException("Invalid consume duration");
            var points=new ArrayList<double[]>();
            for(var row:rows){
                var v=row.getAsJsonArray();if(v.size()!=width)throw new IllegalArgumentException("Invalid consume frame");
                double[] p=new double[width];for(int i=0;i<width;i++){p[i]=v.get(i).getAsDouble();if(!Double.isFinite(p[i])||Math.abs(p[i])>20)throw new IllegalArgumentException("Invalid consume transform");}
                if(width==6 && (p[0]<0 || p[0]>1 || p[1]<=0 || p[2]<=0
                        || !points.isEmpty() && p[0]<=points.get(points.size()-1)[0]))
                    throw new IllegalArgumentException("Invalid consume contact cue");
                points.add(p);
            }
            result.put(entry.getKey(),List.copyOf(points));
        }
        return Map.copyOf(result);
    }
}
