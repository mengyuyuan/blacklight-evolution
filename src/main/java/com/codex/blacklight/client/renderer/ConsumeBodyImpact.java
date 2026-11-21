package com.codex.blacklight.client.renderer;

import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.combat.ConsumeReceiverScene;
import com.codex.blacklight.combat.ConsumeSequence;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.entity.QuillOrgan;
import com.codex.blacklight.power.PowerForm;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Short, contact-triggered follow-through, shared by the visible skin and its attachments. */
public final class ConsumeBodyImpact {
    private ConsumeBodyImpact() {}
    enum Stroke {
        CHEST(27,8,0,12), KNEE(31,0,7,10), STOMP(0,24,0,14), PUNCH(0,19,0,11),
        HAMMER(-21,12,0,16), GRIP(-13,12,12,8), SLASH(8,26,6,13), PULL(-22,12,0,12),
        NECK(5,21,6,7), HUG(-19,3,0,9), NELSON(-13,2,12,8), GUILLOTINE(20,6,5,8),
        JUGULAR(7,14,13,9), MOUNT(8,9,3,11), HUNTER(12,9,5,13);
        final float pitch,yaw,roll,actor;
        Stroke(float pitch,float yaw,float roll,float actor){this.pitch=pitch;this.yaw=yaw;this.roll=roll;this.actor=actor;}
        boolean heavy(){return this==MOUNT||this==HUNTER;}
    }
    static Stroke stroke(String key,int stage){
        if(stage==ConsumeVisual.MOUNT_CONSUME)return Stroke.MOUNT;
        if(stage==ConsumeVisual.HUNTER_CONSUME)return Stroke.HUNTER;
        return switch(key){
            case "normal_head_stomp"->Stroke.STOMP;
            case "normal_beatdown"->Stroke.PUNCH;
            case "normal_knee_head"->Stroke.KNEE;
            case "hammer","hammer_smush"->Stroke.HAMMER;
            case "claws_head","muscle_heart"->Stroke.GRIP;
            case "whip_lash","whip"->Stroke.PULL;
            case "claws","blade","muscle"->Stroke.SLASH;
            case "stealth_neck"->Stroke.NECK;
            case "stealth_bearhug"->Stroke.HUG;
            case "stealth_nelson"->Stroke.NELSON;
            case "stealth_guillotine"->Stroke.GUILLOTINE;
            case "stealth_jugular"->Stroke.JUGULAR;
            default->Stroke.CHEST;
        };
    }
    private record Hit(long scene,int owner,int victim,int ordinal,double began,Vec3 gripOffset,float facing,int side,Stroke stroke) {
        boolean matches(ConsumeVisual v){return v.consuming()&&scene==v.started()&&owner==v.consumerId();}
    }
    private static final Map<LivingEntity,Hit> VICTIMS=new WeakHashMap<>(), ACTORS=new WeakHashMap<>();
    public static void contact(LivingEntity victim,ConsumeVisual scene,int ordinal,Vec3 world){
        if(!scene.consuming()||ordinal<0||world==null)return;
        Hit previous=VICTIMS.get(victim);
        if(previous!=null&&previous.matches(scene)&&ordinal<=previous.ordinal)return;
        var actor=victim.level().getEntity(scene.consumerId());
        if(!(actor instanceof LivingEntity owner))return;
        float partial=Minecraft.getInstance().getFrameTime();
        var stroke=stroke(ConsumeSequence.key(PowerForm.values()[scene.form()],scene.variant()),scene.stage());
        Vec3 forward=Vec3.directionFromRotation(0,scene.facing());
        Vec3 offset=world.subtract(victim.getPosition(partial));
        int side=stroke==Stroke.PUNCH?(ConsumeReceiverScene.beatdownRightHand(ordinal)?1:-1):
                (offset.dot(new Vec3(forward.z,0,-forward.x))<0?-1:1);
        Hit hit=new Hit(scene.started(),scene.consumerId(),victim.getId(),ordinal,victim.level().getGameTime()+partial,offset,scene.facing(),side,stroke);
        VICTIMS.put(victim,hit);
        Hit current=ACTORS.get(owner);
        if(scene.drivesActor(owner.getId())&&(current==null||current.scene<=hit.scene))ACTORS.put(owner,hit);
    }
    private static double smooth(double x){x=Math.min(1,Math.max(0,x));return x*x*(3-2*x);}
    static double load(double age){
        if(age<=0||age>=5.75)return 0;
        if(age<=1.55)return smooth(age/1.55);
        if(age<=2.0)return 1;
        if(age<=3.95)return 1-1.19*smooth((age-2.0)/1.95);
        return -.19*(1-smooth((age-3.95)/1.80));
    }
    static final class Frame {
        final Vec3 pivot;
        final Quaternionf rotation;
        final double feet,height;
        final boolean plantedFeet;
        Frame(Vec3 pivot,Quaternionf rotation,double feet,double height,boolean plantedFeet){
            this.pivot=pivot;this.rotation=rotation;this.feet=feet;this.height=height;this.plantedFeet=plantedFeet;
        }
        double weight(double worldY){return plantedFeet?smooth((worldY-feet)/Math.max(.1,height*.60)):1;}
        Vec3 point(Vec3 p){
            Vec3 delta=p.subtract(pivot);
            var turned=rotation.transform(delta.toVector3f());
            return p.add((turned.x-delta.x)*weight(p.y),(turned.y-delta.y)*weight(p.y),(turned.z-delta.z)*weight(p.y));
        }
        Vector3f cameraPoint(Vector3f p,Vec3 camera){
            double x=p.x+(camera.x-pivot.x),y=p.y+(camera.y-pivot.y),z=p.z+(camera.z-pivot.z);
            double w=weight(p.y+camera.y);
            var q=rotation.transform(new Vector3f((float)x,(float)y,(float)z));
            return p.add((float)((q.x-x)*w),(float)((q.y-y)*w),(float)((q.z-z)*w));
        }
    }
    static Frame frame(Stroke stroke,float facing,int side,double age,Vec3 pivot,double feet,double height){
        double amount=load(age);
        if(amount==0)return null;
        double r=Math.PI/180*amount;
        Vec3 f=Vec3.directionFromRotation(0,facing);
        // A prone receiver twists in the plane of its support. Its contact patch stays planted.
        var turn=new Quaternionf().rotateY((float)(r*stroke.yaw*side))
                .rotateAxis((float)(r*stroke.pitch),(float)f.z,0,(float)-f.x)
                .rotateAxis((float)(r*stroke.roll*side),(float)f.x,0,(float)f.z);
        return new Frame(pivot,turn,feet,height,stroke.heavy());
    }
    static Frame receiver(LivingEntity victim,ConsumeVisual visual,float partial){
        Hit h=VICTIMS.get(victim);
        if(h==null||!h.matches(visual))return null;
        Vec3 feet=victim.getPosition(partial);
        return frame(h.stroke,h.facing,h.side,victim.level().getGameTime()+partial-h.began,
                feet.add(h.gripOffset),feet.y,victim.getBbHeight());
    }
    static Frame quill(LivingEntity victim,QuillOrgan organ,float partial){
        var feet=victim.getPosition(partial);var pivot=organ.getPosition(partial);
        return frame(Stroke.GRIP,victim.getYRot(),pivot.x>feet.x?1:-1,
                organ.age()+partial-QuillOrgan.PIERCE_TICK,pivot,feet.y,victim.getBbHeight());
    }
    static void poseAt(Frame hit,com.mojang.blaze3d.vertex.PoseStack stack,Vec3 renderOrigin){
        if(hit==null)return;
        var p=hit.pivot.subtract(renderOrigin);
        stack.translate(p.x,p.y,p.z);stack.mulPose(hit.rotation);stack.translate(-p.x,-p.y,-p.z);
    }
    public static void fallback(LivingEntity victim,ConsumeVisual visual,com.mojang.blaze3d.vertex.PoseStack stack,float partial){
        var hit=receiver(victim,visual,partial);
        if(hit==null||AdaptiveRagdoll.active(victim)||PigRagdoll.active(victim)||ConsumeFracture.visible(victim,partial))return;
        var placement=com.codex.blacklight.client.ClientCarryPlacement.consumeOffset(victim,partial);
        poseAt(hit,stack,victim.getPosition(partial).add(placement));
    }
    public static void actor(LivingEntity actor,ConsumeVisual visual,BlacklightPose pose,float partial){
        Hit h=ACTORS.get(actor);
        if(h==null||!h.matches(visual)||!visual.drivesActor(actor.getId()))return;
        actorPose(pose,h.stroke,h.side,load(actor.level().getGameTime()+partial-h.began));
    }
    static void actorPose(BlacklightPose pose,Stroke stroke,int side,double amount){
        if(amount==0)return;
        float drive=(float)Math.toRadians(stroke.actor*amount);
        pose.orElseEmpty("Chest").rotation().rotateX(-drive*.75F).rotateY(drive*.72F*side);
        pose.orElseEmpty("Head").rotation().rotateX(drive*.42F);
        // Counterbalance with the free shoulder. The striking hand stays on its sampled contact track at impact.
        pose.orElseEmpty(side>0?"Arm_L":"Arm_R").rotation().rotateX(drive*.8F).rotateZ(drive*.62F*side);
    }
}
