package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.animation.RagdollDrive;
import com.codex.blacklight.entity.QuillOrgan;
import com.codex.blacklight.client.animation.RagdollFatalHandoff;
import com.codex.blacklight.combat.RagdollImpact;
import com.codex.blacklight.mixin.client.ModelPartChildrenAccessor;
import com.codex.blacklight.network.RagdollImpactPayload;
import com.codex.blacklight.combat.FatalWeaponImpact;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;
import java.util.concurrent.*;

/** Species-independent visual physics. The renderer supplies the posed skin and skeleton;
 * health, damage, drops, hit acceptance and capture ownership remain server-authoritative.
 * Unsupported buffer formats pass through; a capture exceeding the budget is replayed intact. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class AdaptiveRagdoll {
    // alex_grap_beatdown_rcv finishes its initial fall at source frame 32 (30 fps).
    private static final double BEATDOWN_LANDING_TICK = 32.0 * 20 / 30;
    private static final int MAX_BODIES=16, MAX_VERTICES=24576, LIFETIME=100;
    // A detailed intact rig must leave room for actual severed fragments.
    static final int MAX_BASE_PARTS=48, MAX_PARTS=64;
    private static final Object WHOLE=new Object();
    private static final ThreadLocal<Frame> CURRENT=new ThreadLocal<>();
    private static final Map<LivingEntity,Pending> PENDING=new LinkedHashMap<>();
    // A server-confirmed fatal contact can arrive before synchronized HEALTH.
    // Never activate this cache while alive or guess a network-delay timeout.
    private static final Map<LivingEntity,RagdollFatalHandoff> NATIVE_FATAL=new LinkedHashMap<>();
    private static final Map<LivingEntity,Body> BODIES=new LinkedHashMap<>();
    private static final Map<LivingEntity,History> HISTORY=new WeakHashMap<>();
    private static final Map<LivingEntity,Long> COOLDOWN=new WeakHashMap<>();
    private record Release(Vec3 offset,long at){}
    private static final Map<LivingEntity,Release> RELEASES=new WeakHashMap<>();
    private static ClientLevel scene;
    // Geometry jobs read immutable skin/path snapshots. Entity, physics and render
    // state are only changed when the client thread accepts a completed result.
    private static final ThreadPoolExecutor CUT_WORKERS=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(32),r->{var t=new Thread(r,"blacklight-cut-geometry");t.setDaemon(true);return t;});
    private record Pending(List<RagdollImpactPayload> hits,long at) {}
    private record History(double time,Map<Object,Node> nodes) {}
    record Node(Object key,Object parent,Vec3 pivot,Quaternionf rotation,int region) {}
    private AdaptiveRagdoll() {}
    public static void invalidateModels(){PENDING.clear();NATIVE_FATAL.clear();BODIES.clear();HISTORY.clear();COOLDOWN.clear();RELEASES.clear();QuillWounds.clear();CombatSurfaceWounds.clear();}
    /** Preserve a small terrain accommodation when the native renderer takes over,
     * then settle smoothly to that renderer's own pose. Never move gameplay state. */
    public static Vec3 recoveryOffset(LivingEntity entity,float partial){
        var release=RELEASES.get(entity);if(release==null)return Vec3.ZERO;
        return release.offset.scale(RagdollRecovery.releaseWeight(entity.level().getGameTime()+partial-release.at));
    }

    public static boolean replacesDeathPoof(LivingEntity victim){
        var body=BODIES.get(victim);
        return QuillOrgan.consumedRemains(victim) || body!=null && (body.quill!=null || body.pressed || body.rupturedRegion!=0);
    }
    public static boolean supports(LivingEntity e){
        var owner=renderOwner(e);if(owner!=e)return supports(owner);
        // Blunt knockdowns, sharp hits and paired absorption must retain the same
        // captured skin and full-body solver. Format/budget fallback happens in
        // Frame; vanilla humanoids must not silently use a different corpse path.
        return RagdollImpact.supportedBody(e);
    }
    public static boolean active(LivingEntity e){
        var owner=renderOwner(e);if(owner!=e)return active(owner);
        if(com.codex.blacklight.combat.NativeBossBodyControl.ownsPose(e)||com.codex.blacklight.combat.FlyerPhysics.ownsLive(e))return false;
        if(RagdollDrive.captured(e)&&!e.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL).consuming())return false;
        return supports(e)&&!e.isInvisible()&&!COOLDOWN.containsKey(e)
                &&(BODIES.containsKey(e)||PENDING.containsKey(e)||RagdollDrive.captured(e)||penetrating(e)!=null);
    }
    private static QuillOrgan penetrating(LivingEntity host) {
        var q=QuillOrgan.extracting(host);
        return q!=null && q.age()>=QuillOrgan.FIRST_DRAW_TICK ? q : null;
    }

    public static Vec3 quillAnchor(LivingEntity host, QuillOrgan organ, float partial) {
        var body=BODIES.get(host);
        if(body==null)return organ.getPosition(partial);
        return body.quill==organ ? body.quillSink(partial) : body.physicalWound(organ.getPosition(partial),partial);
    }

    /** Remaining drawn skin extent for the outer carry strands. A negative value means
     * the receiver still uses its ordinary renderer and has no physical mesh to sample. */
    static float carrySkinFraction(LivingEntity mob,float partial){
        var body=BODIES.get(mob);
        if(body==null||scene!=mob.level()||!active(mob))return -1;
        double full=0,visible=0;
        for(var part:body.parts){
            double rest=part.half.length();
            full=Math.max(full,rest);
            if(part.renderedAt==scene.getGameTime() && Math.abs(part.renderedPartial-partial)<.011F
                    && part.renderedPosition!=null && part.flow<.995F){
                visible=Math.max(visible,rest*part.scale);
            }
        }
        return full>0?(float)Math.min(1,visible/full):0;
    }

    /** The renderer captures the model's actual part pivots before integrating the rigid bodies. */
    static Vec3[] chestConsumeJoints(LivingEntity entity,float partial){
        var body=BODIES.get(entity);
        if(body==null||scene!=entity.level())return null;
        Part torso=null;
        for(var part:body.parts){
            if(part.bind.region==32&&submittedSocketPart(body,part,partial)
                    &&(torso==null||part.volume()>torso.volume()))torso=part;
        }
        if(torso==null)return null;
        Vec3 center=torso.renderedPosition;
        Vec3 up=rotate(new Vec3(0,torso.half.y*torso.scale,0),torso.renderedRotation);
        Vec3 side=rotate(new Vec3(torso.half.x*torso.scale*.52,0,0),torso.renderedRotation);
        Vec3 neck=consumePivot(body,1,partial,center.add(up));
        // The longest outer layer can be a floor-length robe. The two leg pivots give the
        // hips even while the robe is compressed or rotated independently of its wearer.
        Vec3 lower=center.subtract(up);
        Vec3 hipR=consumePivot(body,8,partial,lower.subtract(side));
        Vec3 hipL=consumePivot(body,16,partial,lower.add(side));
        return ConsumeSocketFit.receiver(hipR,hipL,neck);
    }

    /** Bounds of the largest visible torso fragment in the exact transform used by render().
     * A prey split into pieces must not leave an envelope around its former entity box. */
    static ConsumeExteriorShape.Surface exteriorTorso(LivingEntity entity,float partial){
        var body=BODIES.get(entity);
        if(body==null||scene!=entity.level()||!active(entity))return null;
        Part torso=null;
        for(var part:body.parts)if(part.bind.region==32&&submittedSocketPart(body,part,partial)
                &&(torso==null||part.volume()*part.scale*part.scale*part.scale>torso.volume()*torso.scale*torso.scale*torso.scale))torso=part;
        if(torso==null)return null;
        var rot=torso.renderedRotation;double scale=torso.scale;
        return new ConsumeExteriorShape.Surface(torso.renderedPosition,rotate(new Vec3(0,1,0),rot),
                rotate(new Vec3(1,0,0),rot),rotate(new Vec3(0,0,1),rot),
                Math.max(.015,torso.half.x*scale),Math.max(.012,torso.half.z*scale),Math.max(.02,torso.half.y*scale));
    }

    private static boolean submittedSocketPart(Body body,Part part,float partial){
        return part.renderedAt==scene.getGameTime()&&Math.abs(part.renderedPartial-partial)<.021F
                &&part.renderedPosition!=null&&part.flow<.985F&&!body.trauma.hides(part);
    }

    /** Use the proximal model pivot. Children such as a shin, nose or hat must not supply it. */
    private static Vec3 consumePivot(Body body,int region,float partial,Vec3 estimate){
        Vec3 result=estimate;
        double nearest=Double.POSITIVE_INFINITY;
        for(var part:body.parts){
            if(part.bind.region!=region||!submittedSocketPart(body,part,partial)||!part.contains(part.bind.pivot))continue;
            var parent=body.bindNodes.get(part.bind.parent);
            if(parent!=null&&parent.region==region)continue;
            Vec3 point=part.renderedPosition.add(rotate(part.bind.pivot.subtract(part.center).scale(part.scale),part.renderedRotation));
            double distance=point.distanceToSqr(estimate);
            if(distance<nearest){nearest=distance;result=point;}
        }
        return result;
    }

    /** Retarget a source shoulder or knee to a submitted arm/leg when the receiver has one. */
    static Vec3 consumeLimbJoint(LivingEntity entity,float partial,int region,Vec3 estimate,boolean knee){
        var body=BODIES.get(entity);
        if(body==null||scene!=entity.level())return estimate;
        if(!knee)return consumePivot(body,region,partial,estimate);
        Vec3 closest=estimate;double distance=Double.POSITIVE_INFINITY;
        for(var part:body.parts){
            if(part.bind.region!=region||part.renderedAt!=scene.getGameTime()||Math.abs(part.renderedPartial-partial)>.02F
                    ||part.renderedPosition==null||part.flow>=.985F)continue;
            Vec3 axis=rotate(new Vec3(0,part.half.y*part.scale,0),part.renderedRotation);
            // A single-block leg bends at its midpoint. A segmented rig can supply the actual limb seam.
            for(int end=-1;end<=1;end+=knee?1:2){
                Vec3 at=part.renderedPosition.add(axis.scale(end));
                double error=at.distanceToSqr(estimate);
                if(error<distance){distance=error;closest=at;}
            }
        }
        return closest;
    }

    public static boolean drawingQuillBody(LivingEntity host) {
        var body=BODIES.get(host);
        return body!=null && body.quill!=null && body.quill==QuillOrgan.extracting(host);
    }

    public static void drawQuillTethers(LivingEntity host, PoseStack stack, MultiBufferSource buffers, int light, float partial) {
        var body=BODIES.get(host);
        if(body==null || body.quill==null || body.quill!=QuillOrgan.extracting(host))return;
        var origin=host.getPosition(partial);
        var roots=new ArrayList<ConsumeTendrils.Attachment>();
        for(var p:body.quillParts){
            if(p.renderedAt!=scene.getGameTime() || p.renderedPosition==null || p.flow>.98F)continue;
            roots.add(new ConsumeTendrils.Attachment(p.renderedPosition.subtract(origin),p.half.length()*.44*p.scale));
        }
        var organ=body.quill;
        float progress=net.minecraft.util.Mth.clamp((organ.age()+partial-QuillOrgan.PIERCE_TICK)/(QuillOrgan.RELEASE_TICK-QuillOrgan.PIERCE_TICK),0,1);
        var phase=new ConsumeFracture.Phase(com.codex.blacklight.power.PowerForm.BACKSPINES,progress,1,
                (float)QuillTendrils.ease((progress-.40)/.60),0,ConsumeFracture.Style.RIP,true);
        ConsumeAbsorptionFx.drawPosed(stack,buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE)),
                light,roots,body.quillSink(partial).subtract(origin),phase,host.getId());
    }
    static Vec3 consumeWound(LivingEntity entity,float partial){
        var body=BODIES.get(entity);
        return body==null || !body.trauma.open()?null:body.trauma.struckPoint(body.parts,body.connectedPose(partial).positions(),partial);
    }
    public static Vec3 contact(LivingEntity e,int region,float partial){
        var body=BODIES.get(e);if(body==null||!active(e))return null;
        if(region==32&&RagdollDrive.captured(e)&&body.consumeCutPart!=null){
            var p=body.consumeCutPart;var rotation=new Quaternionf(p.oldRotation).slerp(p.rotation,partial);
            return body.connectedPose(partial).positions()[body.parts.indexOf(p)].add(rotate(body.consumeCutPoint.subtract(p.center).scale(p.scale),rotation));
        }
        Part selected=null;
        for(var part:body.parts)if(part.bind.region==region&&(selected==null||part.volume()>selected.volume()))selected=part;
        if(selected==null)selected=body.root;
        return body.connectedPose(partial).positions()[body.parts.indexOf(selected)];
    }
    public record BeatdownSupport(Vec3 surface, Vec3 towardHead, double halfWidth, double bottom) {}

    public static BeatdownSupport beatdownSupport(LivingEntity victim, float partial) {
        var body=BODIES.get(victim);
        if(body==null)return null;
        double time=victim.level().getGameTime()+(double)partial;
        if(body.supportSample!=time){
            body.supportSample=time;
            // During absorption the torso moves into the consumer. Keep the
            // seated pelvis over the last weight-bearing patch of pavement.
            var phase=ConsumeFracture.phase(victim,partial);
            if(body.mountSupport==null || phase==null || phase.returnFlow()<=0)
                body.mountSupport=sampleBeatdownSupport(body,partial);
        }
        return body.mountSupport;
    }

    private static BeatdownSupport sampleBeatdownSupport(Body body,float partial){
        var positions=body.connectedPose(partial).positions();
        int head=-1;
        for(int i=0;i<body.parts.size();i++){
            var part=body.parts.get(i);
            if(part.bind.region==1 && (head<0 || part.volume()>body.parts.get(head).volume()))head=i;
        }
        if(head<0)return null;
        Part torso=null;
        int trunk=-1;
        for(int i=0;i<body.parts.size();i++){
            var part=body.parts.get(i);
            if(part.bind.region!=32 || part.scale<.85F)continue;
            // Some armour renderers label a helmet as a body layer. A head-sized
            // box at the very same socket cannot support a straddling pelvis.
            if(positions[i].subtract(positions[head]).horizontalDistanceSqr()<.04)continue;
            if(torso==null || part.volume()>torso.volume()){torso=part;trunk=i;}
        }
        if(torso==null)return null;
        Vec3 at=positions[trunk];
        Vec3 toHead=positions[head].subtract(at).multiply(1,0,1);
        Vec3 forward=toHead.normalize(), across=new Vec3(forward.z,0,-forward.x);
        Vec3 middle=at.subtract(toHead.scale(.10));
        var turn=new Quaternionf(torso.oldRotation).slerp(torso.rotation,partial);
        // Intersect the vertical line through the waist with the actual
        // torso faces. A tilted armour panel or a long coat has a different top
        // from a standing entity box. Ignore crossed arms and the head.
        double top=Double.NEGATIVE_INFINITY, bottom=Double.POSITIVE_INFINITY, width=0;
        for(int i=0;i+3<torso.mesh.size();i+=4){
            Vec3[] quad=new Vec3[4];
            for(int j=0;j<4;j++){
                Vec3 delta=rotate(torso.mesh.get(i+j).p.subtract(torso.center).scale(torso.scale),turn);
                quad[j]=at.add(delta);
                width=Math.max(width,Math.abs(delta.dot(across)));
                bottom=Math.min(bottom,quad[j].y);
            }
            top=Math.max(top,verticalHit(middle,quad[0],quad[1],quad[2]));
            top=Math.max(top,verticalHit(middle,quad[0],quad[2],quad[3]));
        }
        return Double.isFinite(top)?new BeatdownSupport(new Vec3(middle.x,top,middle.z),forward,width,bottom):null;
    }

    private static double verticalHit(Vec3 site,Vec3 a,Vec3 b,Vec3 c){
        double bx=b.x-a.x,bz=b.z-a.z,cx=c.x-a.x,cz=c.z-a.z;
        double area=bx*cz-bz*cx;
        if(Math.abs(area)<1e-9)return Double.NEGATIVE_INFINITY;
        double dx=site.x-a.x,dz=site.z-a.z;
        double u=(dx*cz-dz*cx)/area,v=(bx*dz-bz*dx)/area;
        return u>=-1e-6 && v>=-1e-6 && u+v<=1.000001
                ?a.y+u*(b.y-a.y)+v*(c.y-a.y):Double.NEGATIVE_INFINITY;
    }

    /** Villagers have a single long robe. Its centre is well below the sternum. */
    public static Vec3 villagerGrip(LivingEntity entity, float lateral, float partial) {
        var body=BODIES.get(entity);
        if(body==null || !com.codex.blacklight.combat.ConsumePlacementTrack.villagerReceiver(entity))return null;
        Part torso=null;
        int half=lateral>0?1:-1;
        for(var part:body.parts) {
            if(part.bind.region!=32 || lateral!=0 && part.consumeHalf!=0 && part.consumeHalf!=half)continue;
            if(torso==null || part.volume()>torso.volume())torso=part;
        }
        if(torso==null)return null;
        double size=entity.getBbHeight()/1.95;
        Vec3 site=torso.bind.pivot.add(rotate(new Vec3(lateral,.26,lateral==0?-.19:-.055).scale(size),torso.bind.rotation));
        var turn=new Quaternionf(torso.oldRotation).slerp(torso.rotation,partial);
        return body.connectedPose(partial).positions()[body.parts.indexOf(torso)]
                .add(rotate(site.subtract(torso.center).scale(torso.scale),turn));
    }
    private static void reset(){
        var level=Minecraft.getInstance().level;
        if(scene!=level){scene=level;PENDING.clear();NATIVE_FATAL.clear();BODIES.clear();HISTORY.clear();COOLDOWN.clear();RELEASES.clear();QuillWounds.clear();CombatSurfaceWounds.clear();}
    }
    private static void releaseNativeBoss(LivingEntity e){
        BODIES.remove(e);PENDING.remove(e);RELEASES.remove(e);COOLDOWN.remove(e);
    }
    private static void resumeNativeFatal(LivingEntity e){
        var pending=NATIVE_FATAL.get(e);if(pending==null)return;
        if(e.isRemoved()||e.level()!=scene){NATIVE_FATAL.remove(e);return;}
        if(e.isAlive()){
            // A new, valid dedicated consume owner supersedes this handoff.
            if(!com.codex.blacklight.combat.NativeBossBodyControl.ownsPose(e))NATIVE_FATAL.remove(e);
            return;
        }
        NATIVE_FATAL.remove(e);var body=BODIES.get(e);
        if(body!=null){for(var hit:pending.impacts())body.receive(hit);return;}
        var inputs=pending.bodyInputs();if(inputs.isEmpty())return;
        var ready=PENDING.get(e);
        if(ready==null){
            while(PENDING.size()>=MAX_BODIES)PENDING.remove(PENDING.keySet().iterator().next());
            PENDING.put(e,new Pending(new ArrayList<>(inputs),scene.getGameTime()));
        }else for(var hit:inputs)if(ready.hits.size()<8)ready.hits.add(hit);
    }
    public static void accept(LivingEntity e,RagdollImpactPayload hit){
        reset();if(scene==null||!supports(e)||e.isInvisible())return;
        var quill=QuillWounds.accept(e,hit);
        if(com.codex.blacklight.combat.NativeBossBodyControl.ownsPose(e)){
            releaseNativeBoss(e);
            if(hit.fatal())NATIVE_FATAL.computeIfAbsent(e,key->new RagdollFatalHandoff()).add(hit);
            return;
        }
        resumeNativeFatal(e);
        RELEASES.remove(e);
        if(com.codex.blacklight.combat.FlyerPhysics.ownsLive(e))return;
        var body=BODIES.get(e);
        if(body!=null){if(quill!=null)body.strikeQuill(quill);body.receive(hit);}
        else if(RagdollDrive.fullImpact(hit)){
            var pending=PENDING.get(e);
            if(pending!=null){if(pending.hits.size()<8)pending.hits.add(hit);}
            else {while(PENDING.size()>=MAX_BODIES)PENDING.remove(PENDING.keySet().iterator().next());PENDING.put(e,new Pending(new ArrayList<>(List.of(hit)),scene.getGameTime()));}
        }
    }
    /** Called once around the actual dispatcher render, including custom entity renderers. */
    public static Frame begin(Entity entity,PoseStack stack,MultiBufferSource buffers,float partial){
        reset();
        if(!(entity instanceof LivingEntity e)||e.isInvisible()||scene==null)return null;
        // Player wounds can follow the standing skin while vanilla keeps control of the pose.
        if(!supports(e)&&!(e instanceof net.minecraft.world.entity.player.Player && CombatSurfaceWounds.active(e)))return null;
        resumeNativeFatal(e);
        // Keep collecting native pose history for a later death, while clearing
        // any prior physical body after a cancelled consume/old impact packet.
        if(com.codex.blacklight.combat.NativeBossBodyControl.ownsPose(e))releaseNativeBoss(e);
        if(com.codex.blacklight.combat.FlyerPhysics.ownsLive(e)){BODIES.remove(e);PENDING.remove(e);}
        // Capture/hold/carry uses one connected receiver pose. A prior knockdown
        // must not leave a live held character as independently drifting pieces.
        if(RagdollDrive.captured(e)&&!e.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL).consuming()){
            BODIES.remove(e);PENDING.remove(e);RELEASES.remove(e);
        }
        var player=Minecraft.getInstance().player;
        if(player==null||player.distanceToSqr(e)>64*64)return null;
        return new Frame(e,stack,buffers,partial,active(e)||!COOLDOWN.containsKey(e)&&(QuillWounds.has(e)||CombatSurfaceWounds.active(e)));
    }
    static void consumeBlades(LivingEntity actor,com.codex.blacklight.power.PowerForm form,Map<String,List<Vec3>> edges,float partial){
        for(var body:BODIES.values()){
            var visual=body.entity.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL);
            // A released receiver still belongs to this owner while a new victim can be grabbed.
            // Its retained mesh keeps old wounds. Fresh moving blades belong only to the held execution.
            if(visual.stage()==com.codex.blacklight.combat.ConsumeVisual.CONSUME
                    &&visual.consumerId()==actor.getId()&&visual.form()==form.ordinal())body.consumeBlades(edges,partial);
        }
    }
    /** A host's detached appearance supplies the skeleton, never a second physical body. */
    public static void bindModel(LivingEntity e,net.minecraft.client.model.EntityModel<?> model){
        var frame=CURRENT.get();if(frame==null)return;
        if(e!=frame.entity){
            if(!(frame.entity instanceof com.codex.blacklight.entity.BlacklightInfectedHost host)||host.appearance()!=e)return;
            frame.appearance=e;
        }
        if(model instanceof HumanoidModel<?> humanoid && QuillOrgan.extracting(e)!=null)
            frame.rigidOwners.put(humanoid.hat,humanoid.head);
        ConsumeAnatomy.model(model).forEach((part,id)->frame.regions.put(part,region(id)));
        if(model instanceof com.codex.blacklight.client.model.BlackwatchModel military)
            frame.rigidOwners.putAll(military.ragdollOwners());
    }
    private static LivingEntity renderOwner(LivingEntity e){
        var frame=CURRENT.get();return frame!=null&&frame.appearance==e?frame.entity:e;
    }
    public static void enterPart(ModelPart part){
        var frame=CURRENT.get();if(frame==null)return;
        Object parent=frame.parts.peekLast();frame.parents.putIfAbsent(part,parent);frame.parts.addLast(part);
        if(parent instanceof ModelPart p){
            ((ModelPartChildrenAccessor)(Object)p).blacklight$children().forEach((name,child)->{
                if(child==part){
                    int named=ConsumeAnatomy.named(name,-1);
                    if(named>=0)frame.regions.put(part,region(named));
                    else frame.regions.putIfAbsent(part,frame.regions.getOrDefault(parent,32));
                }
            });
        }
    }
    public static void leavePart(){var frame=CURRENT.get();if(frame!=null&&!frame.parts.isEmpty())frame.parts.removeLast();}
    public static void modelPart(ModelPart part,PoseStack.Pose pose){
        var frame=CURRENT.get();if(frame!=null)frame.node(part,frame.parents.get(part),pose.pose(),frame.regions.getOrDefault(part,32));
    }
    /** Public adapter hook for renderers with their own bone abstraction. Call clearPart afterwards. */
    public static void bone(Object key,Object parent,Matrix4f pivotMatrix,String name){
        var frame=CURRENT.get();if(frame!=null){
            int named=ConsumeAnatomy.named(name,-1);var ancestor=frame.nodes.get(parent);
            frame.node(key,parent,pivotMatrix,named>=0?region(named):ancestor==null?32:ancestor.region);
        }
    }
    public static void clearPart(){var frame=CURRENT.get();if(frame!=null)frame.current=null;}
    /** A geometry adapter's ModelPart is a view of a real bone, not another rigid body. */
    public static void attachmentAlias(Object part,Object bone){
        var frame=CURRENT.get();if(frame!=null)frame.attachmentAliases.put(part,bone);
    }
    static Object attachmentOwner(){var frame=CURRENT.get();return frame==null?null:frame.current;}
    /** Deferred tissue/glow passes must retain the skin's exact bone identity. Never
     * choose the closest body part: that changes ownership as an animated limb moves. */
    public static AttachmentScope attachment(Object part){return new AttachmentScope(CURRENT.get(),part);}
    public static final class AttachmentScope implements AutoCloseable {
        private final Frame frame;private final Object previous;
        private AttachmentScope(Frame frame,Object part){
            this.frame=frame;previous=frame==null?null:frame.current;
            if(frame!=null){
                Object key=frame.attachmentAliases.getOrDefault(part,part);
                frame.current=frame.nodes.containsKey(key)?key:null;frame.attachmentDepth++;
            }
        }
        @Override public void close(){if(frame!=null){frame.current=previous;frame.attachmentDepth--;}}
    }
    private static int region(int id){return switch(id){case 1->1;case 2->2;case 3->4;case 4->8;case 5->16;default->32;};}
    private static Vec3 transform(Matrix4f m,Vec3 p){return vec(m.transformPosition(p.toVector3f()));}
    private static Vec3 vec(Vector3f p){return new Vec3(p.x,p.y,p.z);}
    private static Vec3 rotate(Vec3 p,Quaternionf q){return vec(q.transform(p.toVector3f()));}
    private static Vec3 closest(Vec3 p,AABB box){return new Vec3(Math.clamp(p.x,box.minX,box.maxX),Math.clamp(p.y,box.minY,box.maxY),Math.clamp(p.z,box.minZ,box.maxZ));}
    static Quaternionf jointCorrection(Vec3 arm,Vec3 error){
        Vec3 axis=arm.cross(error);double length=axis.length();
        if(!Double.isFinite(length)||length<1e-7)return new Quaternionf();
        // Vec3.normalize() returns ZERO below 1e-4. JOML normalizes its axis again,
        // turning that zero into NaN and poisoning every linked body part.
        return new Quaternionf().rotationAxis((float)Math.min(.06,length/Math.max(.02,arm.lengthSqr())*.12),axis.scale(1/length).toVector3f());
    }
    static Vec3 fragmentVelocity(Vec3 linear,Vec3 angular,Vec3 offset,Quaternionf rotation){
        // Rigid-body velocity at the new centre: a rotating body does not give
        // every severed piece the same translation when its centre changes.
        return linear.add(rotate(angular.cross(offset),rotation));
    }
    static Vec3 localAngularImpulse(Vec3 worldAngular,Quaternionf rotation){
        // Contact lever arms and impulses are world-space; Part.spin is local
        // because Part.step integrates it with rotation.rotateXYZ().
        return rotate(worldAngular,new Quaternionf(rotation).conjugate());
    }
    static <T> double meshFloorLift(List<T> mesh,java.util.function.Function<T,Vec3> position,
                                   Vec3 center,Quaternionf turn,float scale,double atY,double floor){
        if(!Double.isFinite(floor))return 0;
        var m=new Matrix3f().rotation(turn);double lowest=Double.POSITIVE_INFINITY;
        for(var vertex:mesh){var p=position.apply(vertex).subtract(center);
            lowest=Math.min(lowest,(m.m01()*p.x+m.m11()*p.y+m.m21()*p.z)*scale);}
        return atY+lowest<floor?floor+.001-atY-lowest:0;
    }

    public static final class Frame implements AutoCloseable,MultiBufferSource {
        final LivingEntity entity;final MultiBufferSource output;final float partial;final boolean capturing;
        final Frame previous;final Matrix4f entry,inverse;final Matrix3f inverseNormal;final Vec3 origin;
        final PoseStack stack;boolean placed;
        final Map<Object,Node> nodes=new LinkedHashMap<>();
        final Map<Object,Object> parents=new IdentityHashMap<>();final Map<Object,Integer> regions=new IdentityHashMap<>();
        final Deque<Object> parts=new ArrayDeque<>();final Map<RenderType,Collector> collectors=new LinkedHashMap<>();
        final Map<Object,Object> attachmentAliases=new IdentityHashMap<>();
        final Map<Object,Object> rigidOwners=new IdentityHashMap<>();
        Object current;LivingEntity appearance;int vertexCount,attachmentDepth,attachmentVertices,unboundAttachmentVertices;boolean overflow,finished;
        Frame(LivingEntity e,PoseStack stack,MultiBufferSource output,float partial,boolean capturing){
            entity=e;this.stack=stack;this.output=output;this.partial=partial;this.capturing=capturing;
            entry=new Matrix4f(stack.last().pose());inverse=new Matrix4f(entry).invert();
            inverseNormal=new Matrix3f(stack.last().normal()).invert();origin=e.getPosition(partial);
            previous=CURRENT.get();CURRENT.set(this);
            if(!capturing){var offset=recoveryOffset(e,partial);if(offset.lengthSqr()>0){stack.pushPose();placed=true;stack.translate(offset.x,offset.y,offset.z);}}
            net.minecraft.client.renderer.entity.EntityRenderer<?> renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e);
            if(renderer instanceof LivingEntityRenderer<?,?> living)
                bindModel(entity,living.getModel());
            if(capturing&&RagdollDrive.captured(e)&&!(renderer instanceof LivingEntityRenderer<?,?>)
                    &&!(renderer instanceof AnatomicalGeoRenderer<?>)&&!(renderer instanceof InfectedHostRenderer)
                    &&!(renderer instanceof net.minecraft.client.renderer.entity.EnderDragonRenderer)){
                stack.pushPose();placed=true;
                var offset=com.codex.blacklight.client.ClientCarryPlacement.consumeOffset(e,partial);
                stack.translate(offset.x,offset.y,offset.z);
                com.codex.blacklight.client.animation.ConsumeVictimPose.body(e,stack,partial);
            }
        }
        void node(Object key,Object parent,Matrix4f pose,int region){
            if(nodes.size()>=256&&!nodes.containsKey(key)){current=null;return;}
            var local=new Matrix4f(inverse).mul(pose);var pivot=transform(local,Vec3.ZERO).add(origin);
            var rotation=local.getUnnormalizedRotation(new Quaternionf()).normalize();
            if(!Double.isFinite(pivot.lengthSqr())||!rotation.isFinite()){current=null;return;}
            nodes.put(key,new Node(key,parent,pivot,rotation,region));current=key;
            if(key instanceof ModelPart part)QuillSkinContact.part(entity,part,local,origin,partial);
        }
        public MultiBufferSource buffers(){return capturing?this:output;}
        @Override public VertexConsumer getBuffer(RenderType type){
            // Lines, labels, portals and procedural shaders retain their renderer's format.
            if(type.format()!=DefaultVertexFormat.NEW_ENTITY||type.mode()!=VertexFormat.Mode.QUADS)return output.getBuffer(type);
            return collectors.computeIfAbsent(type,t->new Collector(this,t));
        }
        void overflow(){
            if(overflow)return;overflow=true;
            com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Generic ragdoll fallback id={} reason=vertex-budget vertices={}",entity.getId(),vertexCount);
            for(var collector:collectors.values())collector.replay();
            BODIES.remove(entity);PENDING.remove(entity);COOLDOWN.put(entity,scene.getGameTime()+120);
        }
        public void finish(){
            if(finished)return;finished=true;
            for(var c:collectors.values())c.flush();
            double time=scene.getGameTime()+partial;var history=HISTORY.get(entity);
            if(capturing&&!overflow){
                boolean hasBones=collectors.values().stream().flatMap(c->c.vertices.stream()).anyMatch(v->v.key!=null);
                var vertices=new ArrayList<Vertex>();var untouched=new ArrayList<Vertex>();
                for(var c:collectors.values())for(var v:c.vertices){
                    if(hasBones&&v.key==null){untouched.add(v);continue;}
                    Vec3 p=transform(inverse,v.p).add(origin),n=vec(inverseNormal.transform(v.n.toVector3f())).normalize();
                    if(!Double.isFinite(p.lengthSqr())||p.distanceToSqr(origin)>256*256){overflow=true;break;}
                    vertices.add(v.world(rigidOwners.getOrDefault(v.key,v.key),p,n));
                }
                if(!overflow&&!vertices.isEmpty()){
                    QuillTendrils.fit(QuillOrgan.extracting(entity),entity,origin,vertices,partial);
                    for(var v:untouched)v.emit(output.getBuffer(v.type),v.p,v.n,v.color,v.overlay,v.light);
                    var body=BODIES.get(entity);
                    if(body==null&&(QuillWounds.has(entity)||CombatSurfaceWounds.active(entity))){
                        if(QuillWounds.has(entity)) vertices=QuillWounds.decorate(entity,vertices,nodes,partial);
                        if(!active(entity)&&CombatSurfaceWounds.active(entity))
                            vertices=CombatSurfaceWounds.live(entity,vertices,nodes,partial);
                        if(!active(entity)){
                            var normal=new Matrix3f(inverseNormal).invert();
                            for(var v:vertices)v.emit(output.getBuffer(v.type),transform(entry,v.p.subtract(origin)),
                                    vec(normal.transform(v.n.toVector3f())).normalize(),v.color,v.overlay,v.light);
                            if(history==null||time-history.time>.05)HISTORY.put(entity,new History(time,new LinkedHashMap<>(nodes)));
                            return;
                        }
                    }
                    if(body==null){
                        var pending=PENDING.remove(entity);
                        body=new Body(entity,vertices,nodes,parents,history,partial,pending==null?null:pending.hits.getFirst());
                        if(pending!=null)for(int i=1;i<pending.hits.size();i++)body.receive(pending.hits.get(i));
                        int count=body.parts.size();for(var other:BODIES.values())count+=other.parts.size();
                        while(!BODIES.isEmpty()&&(BODIES.size()>=MAX_BODIES||count>256)){
                            count-=BODIES.remove(BODIES.keySet().iterator().next()).parts.size();
                        }
                        BODIES.put(entity,body);
                        com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Generic ragdoll built victim={} id={} parts={} joints={} vertices={} meshCuts={} components={}",entity.getType().getDescriptionId(),entity.getId(),body.parts.size(),body.joints.size(),vertices.size(),body.appliedCuts,body.components);
                    }
                    body.releaseQuillChip();
                    body.attachmentVertices=attachmentVertices;body.unboundAttachmentVertices=unboundAttachmentVertices;
                    body.nativeSkin=vertices;
                    if(body.quill==null){
                        body.target(nodes,origin,partial);
                        var organ=penetrating(entity);
                        if(organ!=null)body.burst(organ);
                    }
                    if(CombatSurfaceWounds.needsBody(entity,body.parts))
                        CombatSurfaceWounds.body(entity,body.parts,body.connectedPose(partial).positions(),partial);
                    body.render(entry,output,origin,partial);
                }else {
                    com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Generic ragdoll fallback id={} reason={} vertices={}",entity.getId(),overflow?"invalid-coordinates":"no-compatible-vertices",vertices.size());
                    for(var c:collectors.values())c.replay();BODIES.remove(entity);PENDING.remove(entity);COOLDOWN.put(entity,scene.getGameTime()+120);
                }
            }
            if(history==null||time-history.time>.05)HISTORY.put(entity,new History(time,new LinkedHashMap<>(nodes)));
        }
        @Override public void close(){
            try{if(!finished)for(var c:collectors.values())c.replay();}
            finally{if(placed)stack.popPose();if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
        }
    }

    static final class Vertex {
        final Object key;final RenderType type;final Vec3 p,n;final float u,v;final int color,overlay,light;
        Vertex(Object key,RenderType type,Vec3 p,Vec3 n,float u,float v,int color,int overlay,int light){
            this.key=key;this.type=type;this.p=p;this.n=n;this.u=u;this.v=v;this.color=color;this.overlay=overlay;this.light=light;
        }
        Vertex world(Object owner,Vec3 p,Vec3 n){return new Vertex(owner==null?WHOLE:owner,type,p,n,u,v,color,overlay,light);}
        Vertex mix(Vertex other,double t){return new Vertex(key,type,p.lerp(other.p,t),n.lerp(other.n,t).normalize(),(float)(u+(other.u-u)*t),(float)(v+(other.v-v)*t),color,overlay,light);}
        void emit(VertexConsumer out,Vec3 p,Vec3 n,int color,int overlay,int light){
            out.addVertex((float)p.x,(float)p.y,(float)p.z).setColor(color).setUv(u,v).setOverlay(overlay).setLight(light).setNormal((float)n.x,(float)n.y,(float)n.z);
        }
    }
    private static final class Collector implements VertexConsumer {
        final Frame frame;final RenderType type;final List<Vertex> vertices=new ArrayList<>();
        Vec3 p,n=Vec3.ZERO;Object key;float u,v;int color=-1,overlay=OverlayTexture.NO_OVERLAY,light;VertexConsumer direct;
        Collector(Frame frame,RenderType type){this.frame=frame;this.type=type;}
        void flush(){if(p!=null){vertices.add(new Vertex(key,type,p,n,u,v,color,overlay,light));p=null;}}
        void replay(){flush();if(vertices.isEmpty())return;var out=frame.output.getBuffer(type);for(var v:vertices)v.emit(out,v.p,v.n,v.color,v.overlay,v.light);vertices.clear();}
        @Override public VertexConsumer addVertex(float x,float y,float z){
            flush();if(!frame.overflow&&++frame.vertexCount>MAX_VERTICES)frame.overflow();
            if(frame.attachmentDepth>0){frame.attachmentVertices++;if(frame.current==null)frame.unboundAttachmentVertices++;}
            if(frame.overflow){direct=frame.output.getBuffer(type);direct.addVertex(x,y,z);}else{p=new Vec3(x,y,z);key=frame.current;}
            return this;
        }
        @Override public VertexConsumer setColor(int r,int g,int b,int a){if(direct!=null)direct.setColor(r,g,b,a);else color=a<<24|r<<16|g<<8|b;return this;}
        @Override public VertexConsumer setUv(float u,float v){if(direct!=null)direct.setUv(u,v);else{this.u=u;this.v=v;}return this;}
        @Override public VertexConsumer setUv1(int u,int v){if(direct!=null)direct.setUv1(u,v);else overlay=u|(v<<16);return this;}
        @Override public VertexConsumer setUv2(int u,int v){if(direct!=null)direct.setUv2(u,v);else light=u|(v<<16);return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){if(direct!=null)direct.setNormal(x,y,z);else n=new Vec3(x,y,z);return this;}
    }

    private record Imprint(FatalWeaponImpact style,Vec3 point,Vec3 direction,double radius){
        Vec3 apply(Vec3 vertex){return style.displace(vertex,point,direction,radius);}
    }
    /** Project a pixel-art wound onto the captured skin. All fragments use the same
     * original point and axes, so a fracture splits the mark along the real seam. */
    private record StrikeMark(Vec3 center, Vec3 normal, Vec3 across, Vec3 up, double width, RenderType type) {
        static StrikeMark attach(Part part, Vec3 contact, Vec3 travel, FatalWeaponImpact style, double radius) {
            Vec3 surface = contact, normal = travel.scale(-1);
            double nearest = Double.POSITIVE_INFINITY;
            for (int i=0; i+3<part.mesh.size(); i+=4) {
                var quad = part.mesh.subList(i,i+4);
                var face = quad.get(0);
                Vec3 n = face.n.normalize();
                if (n.lengthSqr()<.5) continue;
                // Penalize exit faces if the hit lands exactly on a shared edge.
                double distance = bounds(quad).distanceToSqr(contact) + .0001*Math.max(0,n.dot(travel));
                if (distance>=nearest) continue;
                nearest = distance;
                surface = contact.subtract(n.scale(contact.subtract(face.p).dot(n)));
                normal = n;
            }
            Vec3 across = (Math.abs(normal.y)>.85 ? new Vec3(0,0,1) : new Vec3(0,1,0)).cross(normal).normalize();
            Vec3 up = normal.cross(across);
            String material = switch(style) {
                case FIST -> "bruise";
                case WHIP -> "welt";
                case HAMMER, HAMMER_BREAK, HAMMER_PRESS -> "crush";
                default -> "rupture";
            };
            double extent = radius*switch(style) { case FIST -> .8; case WHIP -> 1.36; default -> 1.18; };
            var texture = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/impact/"+material+".png");
            return new StrikeMark(surface,normal,across,up,Math.max(.16,extent),RenderType.entityCutoutNoCull(texture));
        }

        List<Vertex> project(Part part) {
            var result = new ArrayList<Vertex>();
            var skin=part.visibleSkin();
            for (int i=0; i+3<skin.size(); i+=4) {
                var quad = skin.subList(i,i+4);
                var first = quad.get(0);
                if (first.n.dot(normal)<.45 || Math.abs(first.p.subtract(center).dot(first.n))>width*.22
                        || bounds(quad).distanceToSqr(center)>width*width*.55) continue;
                for (var v : quad) {
                    Vec3 delta = v.p.subtract(center);
                    Vec3 p = (part.imprint==null?v.p:part.imprint.apply(v.p)).add(v.n.scale(.003));
                    result.add(new Vertex(v.key,type,p,v.n,(float)(.5+delta.dot(across)/width),
                            (float)(.5-delta.dot(up)/width),-1,v.overlay,v.light));
                }
            }
            return result;
        }
    }
    static final class Part {
        Imprint imprint;
        StrikeMark mark;
        private int markRevision=-1;
        private List<Vertex> markedSkin=List.of();
        List<Vertex> impactMark() {
            if (mark!=null && meshRevision!=markRevision) {
                markedSkin=mark.project(this);
                markRevision=meshRevision;
            }
            return markedSkin;
        }
        boolean detachedTissue;
        void imprintContact(Vec3 contact,Vec3 direction,FatalWeaponImpact style){
            double radius=Math.max(.18,Math.min(.55,half.length()*.87));
            imprint=new Imprint(style,contact,direction,radius);
            mark=StrikeMark.attach(this,contact,direction,style,radius);
            meshRevision++;
        }
        double landingSpeed;
        int consumeHalf;
        ContactCompression compression;
        private List<Vertex> pressedSkin;
        private int pressedRevision=-1;
        List<Vertex> visibleSkin(){
            if(compression==null)return mesh;
            if(pressedRevision!=meshRevision || pressedSkin==null){
                pressedSkin=compression.surface(mesh);
                pressedRevision=meshRevision;
            }
            return pressedSkin;
        }
        Vec3 skinPoint(Vertex vertex){
            Vec3 rest=imprint==null?vertex.p:imprint.apply(vertex.p);
            return compression==null?rest:compression.point(rest);
        }
        void grip(ContactCompression field){
            if(compression!=null&&compression.sameGrip(field.scene,field.actor,field.started))compression.addGrip(field);
            else compression=field;
            meshRevision++;
        }
        void press(Vec3 contact,Vec3 travel,double radius,double clock,boolean markContact){
            compression=new ContactCompression(contact,travel,radius,clock);
            if(markContact)mark=StrikeMark.attach(this,contact,travel,FatalWeaponImpact.FIST,radius);
            meshRevision++;
        }
        final RagdollPoseHistory poses=new RagdollPoseHistory();
        final Set<String> finishedCuts=new HashSet<>();
        final Map<String,List<Vertex>> incisions=new LinkedHashMap<>();
        final Map<String,BladeCutSequence> consumePaths=new LinkedHashMap<>();
        final Map<String,ContactSection.Plane> sectionContacts=new HashMap<>();
        final Map<String,Long> consumeCutTicks=new HashMap<>();
        final Map<String,Double> kerfTimes=new HashMap<>();int meshRevision;
        final Object key;final List<Vertex> mesh=new ArrayList<>();final Node bind;final Vec3 center;Vec3 half;final double invMass;
        final List<Boundary> boundaries=new ArrayList<>();
        Vec3 pos,old,velocity=Vec3.ZERO,target;Quaternionf rotation=new Quaternionf(),oldRotation=new Quaternionf(),targetRotation=new Quaternionf();
        Vec3 stepOrigin,predictedPosition;final Quaternionf stepRotation=new Quaternionf(),predictedRotation=new Quaternionf();
        Vec3 renderedPosition;Quaternionf renderedRotation;float renderedPartial;long renderedAt=-1;
        Vector3f spin=new Vector3f();float scale=1,flow;int still;
        Part(Object key,List<Vertex> mesh,Node node){
            this.key=key;this.mesh.addAll(mesh);var bounds=bounds(mesh);center=bounds.getCenter();
            half=new Vec3(Math.max(.02,bounds.getXsize()*.5),Math.max(.02,bounds.getYsize()*.5),Math.max(.02,bounds.getZsize()*.5));
            bind=node==null?new Node(key,null,center,new Quaternionf(),32):node;
            invMass=1/Math.max(.015,half.x*half.y*half.z*8);pos=old=target=center;
        }
        Vec3 pressAxis;
        double thickness=1;
        Vec3 pressedOffset(Vec3 offset){
            return pressAxis==null?offset:offset.add(pressAxis.scale(offset.dot(pressAxis)*(thickness-1)));
        }
        void press(double depth){
            pressAxis=rotate(new Vec3(0,1,0),new Quaternionf(rotation).conjugate()).normalize();
            thickness=depth;
            mesh.replaceAll(v->v.world(v.key,center.add(pressedOffset(skinPoint(v).subtract(center))),
                    v.n.add(pressAxis.scale(v.n.dot(pressAxis)*(1/depth-1))).normalize()));
            imprint=null; // The contact dent is now part of the compressed mesh.
            refit();
            still=0;
        }
        void refit(){
            var box=bounds(mesh);
            half=new Vec3(Math.max(.015,Math.max(box.maxX-center.x,center.x-box.minX)),
                    Math.max(.015,Math.max(box.maxY-center.y,center.y-box.minY)),
                    Math.max(.015,Math.max(box.maxZ-center.z,center.z-box.minZ)));
            meshRevision++;
        }
        double volume(){return half.x*half.y*half.z;}
        void wound(String key,List<Vertex> skin){
            if(skin.isEmpty())return;
            incisions.put(key,List.copyOf(skin.subList(0,Math.min(256,skin.size()))));
            while(incisions.values().stream().mapToInt(List::size).sum()>1024)incisions.remove(incisions.keySet().iterator().next());
        }
        boolean contains(Vec3 p){for(var boundary:boundaries)if(!boundary.allows(p))return false;return true;}
        Vec3 attachment(){return contains(bind.pivot)?bind.pivot:center;}
        boolean connectsTo(Part parent){
            for(var cut:boundaries){
                boolean shared=false;
                for(var other:parent.boundaries)if(cut.same(other)){
                    if(cut.domain==null&&other.domain==null&&cut.sign!=other.sign)return false;
                    shared=true;
                }
                // A distal slice can follow a matching slice of the underlying tissue,
                // but cannot reconnect to the uncut parent across its own wound.
                if(!shared&&!cut.allows(bind.pivot))return false;
            }
            return parent.contains(attachment());
        }
        void inherit(Part source){
            imprint=source.imprint;compression=source.compression;
            consumeHalf=source.consumeHalf;
            mark=source.mark;
            finishedCuts.addAll(source.finishedCuts);
            source.consumePaths.forEach((key,path)->consumePaths.put(key,path.copy()));consumeCutTicks.putAll(source.consumeCutTicks);
            sectionContacts.putAll(source.sectionContacts);
            kerfTimes.putAll(source.kerfTimes);
            Vec3 delta=center.subtract(source.center);
            poses.inherit(source.poses,delta);
            pos=source.pos.add(rotate(delta.scale(source.scale),source.rotation));
            old=source.old.add(rotate(delta.scale(source.scale),source.oldRotation));
            target=source.target.add(rotate(delta,source.targetRotation));
            rotation.set(source.rotation);oldRotation.set(source.oldRotation);targetRotation.set(source.targetRotation);
            velocity=fragmentVelocity(source.velocity,vec(source.spin),delta.scale(source.scale),source.rotation);
            spin.set(source.spin);scale=source.scale;flow=source.flow;
        }
        void target(Node node,Vec3 offset){
            if(node==null){target=center.add(offset);targetRotation.identity();return;}
            targetRotation.set(node.rotation).mul(new Quaternionf(bind.rotation).conjugate()).normalize();
            target=node.pivot.add(rotate(center.subtract(bind.pivot),targetRotation));
        }
        Vec3 extent(){return extent(rotation);}
        Vec3 extent(Quaternionf turn){
            var m=new Matrix3f().rotation(turn);
            return new Vec3(Math.abs(m.m00())*half.x+Math.abs(m.m10())*half.y+Math.abs(m.m20())*half.z,
                    Math.abs(m.m01())*half.x+Math.abs(m.m11())*half.y+Math.abs(m.m21())*half.z,
                    Math.abs(m.m02())*half.x+Math.abs(m.m12())*half.y+Math.abs(m.m22())*half.z).scale(scale);
        }
        Vec3 clip(Vec3 requested){
            var ext=extent();Vec3 allowed=requested;
            if(Math.max(ext.x,Math.max(ext.y,ext.z))<=2)
                allowed=Entity.collideBoundingBox(null,requested,new AABB(pos.subtract(ext),pos.add(ext)),scene,List.of());
            else {
                // Huge mod bosses must not scan a mansion-sized block volume per solver iteration.
                for(int i=0;i<9;i++){
                    Vec3 at=i==8?pos:pos.add((i&1)==0?-ext.x:ext.x,(i&2)==0?-ext.y:ext.y,(i&4)==0?-ext.z:ext.z);
                    var delta=Entity.collideBoundingBox(null,allowed,new AABB(at.add(-.035,-.035,-.035),at.add(.035,.035,.035)),scene,List.of());
                    allowed=new Vec3(allowed.x>=0?Math.min(allowed.x,delta.x):Math.max(allowed.x,delta.x),allowed.y>=0?Math.min(allowed.y,delta.y):Math.max(allowed.y,delta.y),allowed.z>=0?Math.min(allowed.z,delta.z):Math.max(allowed.z,delta.z));
                }
            }
            return allowed;
        }
        Vec3 move(Vec3 requested){
            Vec3 allowed=clip(requested);pos=pos.add(allowed);return allowed;
        }
        boolean supported(){
            // Reuse the real collision shapes and bounded large-rig probes.
            // A joint or an animation target alone is not ground support.
            return clip(new Vec3(0,-.025,0)).y>-.025+1e-5;
        }
        void step(){
            landingSpeed=0;
            if(still>=RagdollConstraintMotion.SLEEP_STEPS)return;
            rotation.rotateXYZ(spin.x*.5F,spin.y*.5F,spin.z*.5F).normalize();
            floorContact();
            boolean water=!scene.getFluidState(BlockPos.containing(pos)).isEmpty();
            velocity=RagdollDrive.limit(velocity.scale(water?.82:.975).add(0,(water?-.005:-.04)*(1-flow),0),1.5);
            Vec3 requested=velocity.scale(.5),allowed=move(requested);
            boolean x=Math.abs(allowed.x-requested.x)>1e-5,y=Math.abs(allowed.y-requested.y)>1e-5,z=Math.abs(allowed.z-requested.z)>1e-5;
            if(y && requested.y<0 && !water) landingSpeed=-velocity.y;
            if(x||y||z){double bounce=velocity.lengthSqr()>.04?.15:0;velocity=new Vec3(x?-velocity.x*bounce:velocity.x*.72,y?-velocity.y*bounce:velocity.y*.72,z?-velocity.z*bounce:velocity.z*.72);spin.mul(.65F);}
        }
        void finishConstraints(boolean supported){
            velocity=RagdollConstraintMotion.linear(velocity,pos.subtract(predictedPosition));
            spin.set(RagdollConstraintMotion.angular(spin,predictedRotation,rotation));
            still=RagdollConstraintMotion.still(still,supported,pos.subtract(stepOrigin),stepRotation,rotation,velocity,spin);
        }
        void floorContact(){
            // Both integration and joint torque can rotate an already grounded
            // part into a block. Recheck after the joint pass, before recording/rendering.
            var extent=extent();var floor=floorBelow(pos,extent);
            if(floor.getType()==HitResult.Type.BLOCK&&floor.getDirection()==net.minecraft.core.Direction.UP&&pos.y-extent.y<floor.getLocation().y)
                pos=new Vec3(pos.x,floor.getLocation().y+extent.y+.001,pos.z);
        }
        BlockHitResult floorBelow(Vec3 at,Vec3 extent){
            return scene.clip(new net.minecraft.world.level.ClipContext(
                    at.add(0,extent.y+.12,0),at.add(0,-extent.y-.025,0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,
                    net.minecraft.world.phys.shapes.CollisionContext.empty()));
        }
        double terrainSupport(Vec3 at,Quaternionf turn){
            var extent=extent(turn);
            // Keep the existing bounded large-boss fallback; do not enumerate a mansion-sized volume.
            if((extent.x*2+2)*(extent.y*2+2)*(extent.z*2+2)>512)return 0;
            var boxes=new ArrayList<AABB>();
            for(var shape:scene.getBlockCollisions(null,new AABB(at.subtract(extent),at.add(extent)).inflate(.002,.12,.002))){
                boxes.addAll(shape.toAabbs());if(boxes.size()>256)return 0;
            }
            if(boxes.isEmpty())return 0;
            var skin=new ArrayList<Vec3>(mesh.size());
            for(var vertex:mesh)skin.add(at.add(rotate(skinPoint(vertex).subtract(center).scale(scale),turn)));
            return RagdollTerrainSupport.lift(skin,boxes);
        }
        Vec3 renderPosition(float partial,Quaternionf turn){
            var at=old.lerp(pos,partial);var extent=extent(turn);var floor=floorBelow(at,extent);
            // Clear physical endpoints do not imply a clear interpolated rotation.
            // Only lift actual skin below the support, not empty corners of its box.
            if(floor.getType()==HitResult.Type.BLOCK&&floor.getDirection()==net.minecraft.core.Direction.UP&&at.y-extent.y<floor.getLocation().y)
                at=at.add(0,meshFloorLift(mesh,this::skinPoint,center,turn,scale,at.y,floor.getLocation().y),0);
            return at;
        }
    }
    private record Domain(List<FiniteMeshCut.Piece<Vertex>> pieces,int selected) {
        boolean contains(Vec3 point){
            double own=FiniteMeshCut.distance(point,pieces.get(selected).faces(),MESH_VERTICES);
            for(int i=0;i<pieces.size();i++)if(i!=selected&&FiniteMeshCut.distance(point,pieces.get(i).faces(),MESH_VERTICES)+1e-7<own)return false;
            return true;
        }
    }
    private static final FiniteMeshCut.Vertices<Vertex> MESH_VERTICES=new FiniteMeshCut.Vertices<>() {
        public Vec3 position(Vertex vertex){return vertex.p;}
        public Vertex mix(Vertex a,Vertex b,double t){return a.mix(b,t);}
    };
    private static List<Vertex> woundMesh(Object key,List<List<Vertex>> faces,List<FiniteMeshCut.Incision> incisions){
        var result=new ArrayList<Vertex>();var type=RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE);
        for(var line:incisions){
            Vec3 normal=faces.get(line.face()).getFirst().n,along=line.b().subtract(line.a());double length=along.length();if(length<.008)continue;
            Vec3 side=normal.cross(along.scale(1/length)).normalize();if(side.lengthSqr()<.5)continue;
            for(int layer=0;layer<2;layer++){
                var width=side.scale(layer==0?.012:.004);var lift=normal.scale(layer==0?.0015:.002);
                var a=line.a().add(lift);var b=line.b().add(lift);
                for(var point:List.of(a.add(width),b.add(width),b.subtract(width),a.subtract(width)))
                    result.add(new Vertex(key,type,point,normal,.5F,.5F,layer==0?0xff350b0d:0xffa32b31,OverlayTexture.NO_OVERLAY,0));
            }
        }return result;
    }
    private record Boundary(Vec3 point,Vec3 normal,int sign,SweptBladeSurface surface,String key,Domain domain) {
        Boundary(Vec3 point,Vec3 normal,int sign){this(point,normal,sign,null,"",null);}
        Boundary(Vec3 point,Vec3 normal,int sign,SweptBladeSurface surface,String key){this(point,normal,sign,surface,key,null);}
        Boundary side(int value){return new Boundary(point,normal,value,surface,key,domain);}
        double distance(Vec3 p){return surface==null?p.subtract(point).dot(normal):surface.distance(p);}
        boolean allows(Vec3 p){return domain==null?distance(p)*sign>=-.001:domain.contains(p);}
        boolean same(Boundary other){return !key.isEmpty()?key.equals(other.key):other.key.isEmpty()&&point.distanceToSqr(other.point)<1e-10&&normal.dot(other.normal)>.99999;}
    }
    static AABB bounds(List<Vertex> vertices){
        Vec3 lo=new Vec3(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY),hi=lo.scale(-1);
        for(var v:vertices){lo=new Vec3(Math.min(lo.x,v.p.x),Math.min(lo.y,v.p.y),Math.min(lo.z,v.p.z));hi=new Vec3(Math.max(hi.x,v.p.x),Math.max(hi.y,v.p.y),Math.max(hi.z,v.p.z));}
        return new AABB(lo,hi);
    }
    /** Subdivide the skin enough to resolve curvature between blade samples. */
    private static List<Vertex> detailed(List<Vertex> mesh,SweptBladeSurface surface){
        var faces=new ArrayList<List<Vertex>>();for(int i=0;i+3<mesh.size();i+=4)faces.add(mesh.subList(i,i+4));
        return FiniteMeshCut.refine(faces,MESH_VERTICES,surface,.075,MAX_VERTICES/4).stream().flatMap(List::stream).toList();
    }
    private record PreparedCut(Boundary boundary,List<Vertex> mesh,List<List<Vertex>> faces,FiniteMeshCut.Result<Vertex> result,List<Vertex> incision,double through,boolean more){}
    private static PreparedCut prepareCut(List<Vertex> original,Vec3 center,String key,List<RagdollImpactPayload.BladeSpan> path,double after,RenderType capType){
        var surface=new SweptBladeSurface(path);var box=bounds(original);
        if(surface.empty()||!surface.overlaps(new Vec3(box.minX,box.minY,box.minZ),new Vec3(box.maxX,box.maxY,box.maxZ),.045))return null;
        Vec3 point=surface.project(center);
        var boundary=new Boundary(point,surface.normal(point),1,surface,key);
        // Complete finite ribbons need no volumetric carving. Validate the
        // actual capped pieces: loose one-face islands are not a sever.
        if(original.size()<4096){
            var refined=detailed(original,surface);
            if(!refined.isEmpty()){
                var faces=new ArrayList<List<Vertex>>();for(int i=0;i+3<refined.size();i+=4)faces.add(refined.subList(i,i+4));
                var fast=FiniteMeshCut.trace(faces,MESH_VERTICES,surface);
                int size=fast.pieces().stream().mapToInt(p->p.faces().stream().mapToInt(f->f.size()==4?4:Math.max(0,f.size()-2)*4).sum()+p.caps().stream().mapToInt(c->c.triangles().size()*4).sum()).sum();
                if(fast.pieces().size()>1&&size<=MAX_VERTICES&&fast.pieces().stream().allMatch(p->!p.caps().isEmpty()&&p.faces().size()>=2&&closedPiece(p)))
                    return new PreparedCut(boundary,refined,faces,fast,List.of(),path.getLast().time(),false);
            }
        }
        var fresh=new ArrayList<RagdollImpactPayload.BladeSpan>();
        boolean timed=path.getLast().timed();
        for(int i=1;i<path.size();i++)if(!timed||path.get(i).time()>after){if(fresh.isEmpty())fresh.add(path.get(i-1));fresh.add(path.get(i));if(timed&&fresh.size()==2)break;}
        if(fresh.size()<2)return null;
        double through=fresh.getLast().time();boolean more=timed&&through<path.getLast().time();
        var adapter=new SolidBladeCut.Vertices<Vertex>(){
            public Vec3 position(Vertex v){return v.p;}public Vertex mix(Vertex a,Vertex b,double t){return a.mix(b,t);}
            public Vertex cap(Vec3 p,Vec3 n){return new Vertex(original.getFirst().key,capType,p,n,.5F,.5F,0xff70231f,OverlayTexture.NO_OVERLAY,0);}
            public Vertex flip(Vertex v){return new Vertex(v.key,v.type,v.p,v.n.scale(-1),v.u,v.v,v.color,v.overlay,v.light);}
            public boolean sameAttributes(Vertex a,Vertex b){return a.type==b.type&&a.key==b.key&&a.color==b.color&&a.overlay==b.overlay&&a.light==b.light&&Math.abs(a.u-b.u)<1e-6&&Math.abs(a.v-b.v)<1e-6&&a.n.distanceToSqr(b.n)<1e-8;}
        };
        var originalFaces=new ArrayList<List<Vertex>>();for(int i=0;i+3<original.size();i+=4)originalFaces.add(original.subList(i,i+4));
        var carved=SolidBladeCut.subtract(originalFaces,adapter,new SweptBladeSurface(fresh).triangles(),MAX_VERTICES/4);
        if(!System.getProperty("blacklight.test.ragdollVisual","").isEmpty())com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Blade solid key={} faces={} fresh={} reason={} pieces={}",key,originalFaces.size(),fresh.size(),carved.reason(),carved.pieces().size());
        var trace=new FiniteMeshCut.Result<Vertex>(List.of(),List.of(),carved.reason());
        if(carved.changed()){
            if(carved.pieces().size()==1)return new PreparedCut(boundary,original,originalFaces,trace,quads(carved.pieces().getFirst()),through,more);
            var pieces=carved.pieces().stream().map(p->new FiniteMeshCut.Piece<Vertex>(p,List.of())).toList();
            return new PreparedCut(boundary,original,originalFaces,new FiniteMeshCut.Result<>(pieces,List.of(),"solid-separated"),List.of(),through,more);
        }
        // Open decorative layers have no interior volume. Keep the surface-only
        // cutter for those layers, never manufacture solid tissue from them.
        if(carved.reason().equals("open-skin")){
            var mesh=detailed(original,surface);if(mesh.isEmpty())return null;
            var faces=new ArrayList<List<Vertex>>();for(int i=0;i+3<mesh.size();i+=4)faces.add(mesh.subList(i,i+4));
            return new PreparedCut(boundary,mesh,faces,FiniteMeshCut.trace(faces,MESH_VERTICES,surface),List.of(),path.getLast().time(),false);
        }
        boolean rejected=carved.reason().startsWith("budget")||carved.reason().startsWith("open-");
        return new PreparedCut(boundary,original,originalFaces,trace,List.of(),rejected?after:through,more&&!rejected);
    }
    private static boolean closedPiece(FiniteMeshCut.Piece<Vertex> piece){
        var faces=new ArrayList<List<Vec3>>();for(var face:piece.faces())faces.add(face.stream().map(v->v.p).toList());
        for(var cap:piece.caps())for(var triangle:cap.triangles())faces.add(List.of(triangle));
        return FiniteMeshCut.closed(faces,new FiniteMeshCut.Vertices<Vec3>(){
            public Vec3 position(Vec3 p){return p;}public Vec3 mix(Vec3 a,Vec3 b,double t){return a.lerp(b,t);}
        });
    }
    private static List<Vertex> quads(List<List<Vertex>> polygons){
        var result=new ArrayList<Vertex>();for(var polygon:polygons){if(polygon.size()==4)result.addAll(polygon);else for(int i=1;i+1<polygon.size();i++){result.add(polygon.getFirst());result.add(polygon.get(i));result.add(polygon.get(i+1));result.add(polygon.get(i+1));}}return result;
    }
    static List<Vertex> traumaClip(Part part,List<Vertex> mesh,Vec3 point,Vec3 axis,int sign){
        return clip(part,mesh,new Boundary(point,axis,sign),sign);
    }
    private static List<Vertex> clip(Part part,List<Vertex> mesh,Boundary cut,int sign){
        return clip(part,mesh,cut,sign,RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
    }
    private static List<Vertex> clip(Part part,List<Vertex> mesh,Boundary cut,int sign,RenderType capType){
        var output=new ArrayList<Vertex>();var seams=new ArrayList<Vec3[]>();
        for(int index=0;index+3<mesh.size();index+=4){
            var face=mesh.subList(index,index+4);var polygon=new ArrayList<Vertex>();var crossings=new ArrayList<Vec3>();
            Vertex a=face.getLast();double da=cut.distance(a.p)*sign;
            for(var b:face){double db=cut.distance(b.p)*sign;
                if((da>=0)!=(db>=0)){
                    double t=da/(da-db);
                    if(cut.surface!=null){double low=0,high=1;for(int n=0;n<15;n++){t=(low+high)*.5;double value=cut.distance(a.p.lerp(b.p,t))*sign;if((value>=0)==(da>=0))low=t;else high=t;}}
                    var v=a.mix(b,t);polygon.add(v);if(crossings.stream().noneMatch(p->p.distanceToSqr(v.p)<1e-10))crossings.add(v.p);
                }
                if(db>=0)polygon.add(b);a=b;da=db;
            }
            if(polygon.size()==4)output.addAll(polygon);
            else for(int i=1;i+1<polygon.size();i++){output.add(polygon.getFirst());output.add(polygon.get(i));output.add(polygon.get(i+1));output.add(polygon.get(i+1));}
            if(crossings.size()==2&&crossings.get(0).distanceToSqr(crossings.get(1))>1e-10){
                if(cut.surface!=null&&!cut.surface.covers(crossings.get(0),crossings.get(1))){
                    if(!System.getProperty("blacklight.test.ragdollVisual","").isEmpty())com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Bounded cut outside ribbon blade={} region={} a={} b={} gaps={},{}",cut.key,part.bind.region,crossings.get(0),crossings.get(1),cut.surface.gap(crossings.get(0)),cut.surface.gap(crossings.get(1)));
                    return List.of();
                }
                seams.add(new Vec3[]{crossings.get(0),crossings.get(1)});
            }
        }
        // Closed loops only. An open ribbon/wing is intentionally left open instead of inventing tissue.
        while(!seams.isEmpty()){
            var first=seams.removeFirst();var loop=new ArrayList<Vec3>();loop.add(first[0]);loop.add(first[1]);
            boolean closed=false;
            for(int step=0;step<2048;step++){
                Vec3 end=loop.getLast();if(end.distanceToSqr(loop.getFirst())<1e-8){loop.removeLast();closed=true;break;}
                int found=-1;Vec3 next=null;
                for(int i=0;i<seams.size();i++){var edge=seams.get(i);if(end.distanceToSqr(edge[0])<1e-8){found=i;next=edge[1];break;}if(end.distanceToSqr(edge[1])<1e-8){found=i;next=edge[0];break;}}
                if(found<0)break;seams.remove(found);loop.add(next);
            }
            if(closed&&loop.size()>=3){var normal=cut.surface==null?cut.normal:cut.surface.normal(loop.getFirst());cap(output,part.key,loop,normal.scale(-sign),capType);}
        }
        return output;
    }
    private static void cap(List<Vertex> output,Object key,List<Vec3> loop,Vec3 normal,RenderType type){
        // Ear clipping also handles concave custom-model cross sections.
        Vec3 area=Vec3.ZERO,origin=loop.getFirst();for(int i=0;i<loop.size();i++)area=area.add(loop.get(i).subtract(origin).cross(loop.get((i+1)%loop.size()).subtract(origin)));
        if(area.dot(normal)<0)Collections.reverse(loop);
        int attempts=0;
        while(loop.size()>2&&attempts++<4096){boolean removed=false;
            for(int i=0;i<loop.size();i++){
                Vec3 a=loop.get((i+loop.size()-1)%loop.size()),b=loop.get(i),c=loop.get((i+1)%loop.size());
                double convex=b.subtract(a).cross(c.subtract(b)).dot(normal);
                if(Math.abs(convex)<1e-10){loop.remove(i);removed=true;break;}if(convex<0)continue;
                boolean contains=false;
                for(var p:loop){if(p==a||p==b||p==c)continue;
                    if(b.subtract(a).cross(p.subtract(a)).dot(normal)>1e-9&&c.subtract(b).cross(p.subtract(b)).dot(normal)>1e-9&&a.subtract(c).cross(p.subtract(c)).dot(normal)>1e-9){contains=true;break;}}
                if(contains)continue;
                for(var p:List.of(a,b,c,c))output.add(new Vertex(key,type,p,normal,.5F,.5F,0xff70231f,OverlayTexture.NO_OVERLAY,0));
                loop.remove(i);removed=true;break;
            }
            if(!removed)break;
        }
    }
    private static final class Joint {
        final Part parent,child;final Vec3 bindA,bindB;final RagdollHinge hinge;Vec3 a,b;boolean severed;
        Joint(Part parent,Part child,RagdollHinge hinge){this.parent=parent;this.child=child;this.hinge=hinge;Vec3 pivot=child.attachment();a=bindA=parent.pressedOffset(pivot.subtract(parent.center));b=bindB=child.pressedOffset(pivot.subtract(child.center));}
        void solve(){
            if(severed)return;
            Vec3 ra=rotate(a.scale(parent.scale),parent.rotation),rb=rotate(b.scale(child.scale),child.rotation);
            Vec3 error=RagdollDrive.limit(child.pos.add(rb).subtract(parent.pos.add(ra)),.15);
            double weight=parent.invMass/(parent.invMass+child.invMass);
            parent.move(error.scale(weight*.8));child.move(error.scale(-(1-weight)*.8));
            torque(parent,ra,error);torque(child,rb,error.scale(-1));
            if(hinge!=null)return;
            var relative=new Quaternionf(parent.rotation).conjugate().mul(child.rotation).normalize();
            double angle=2*Math.acos(Math.clamp(Math.abs(relative.w),0,1));
            if(angle>1.9)child.rotation.slerp(parent.rotation,(float)Math.min(.12,(angle-1.9)*.12));
        }
        void constrainHinge(){
            if(severed||hinge==null)return;
            var before=new Quaternionf(child.rotation);
            var turn=hinge.constrain(parent.rotation,before);
            var velocity=hinge.constrainSpin(parent.rotation,turn,parent.rotation.transform(new Vector3f(parent.spin)),before.transform(new Vector3f(child.spin)));
            child.rotation.set(turn);
            child.move(RagdollHinge.pivotShift(b,before,turn,child.scale));
            child.spin.set(new Quaternionf(turn).conjugate().transform(velocity));
        }
        static void torque(Part p,Vec3 arm,Vec3 error){
            p.rotation.premul(jointCorrection(arm,error)).normalize();
        }
    }
    private static final class Body {
        final ConsumeSkinPath materialBind;
        int attachmentVertices,unboundAttachmentVertices;
        double recoveryDuration=RagdollRecovery.DURATION;boolean bruteRecovery,bruteGetup;
        List<Vertex> nativeSkin=List.of();
        final LivingEntity entity;final List<Part> parts=new ArrayList<>();final List<Joint> joints=new ArrayList<>();
        final Vec3 origin;final Matrix4f woundFrame;final long started;long expires,deadAt=-1,freezeUntil;boolean light;double targetAge;Part root;int appliedCuts,components,materialIncisions;
        final List<Boundary> cutSurfaces=new ArrayList<>();
        final Map<String,Vec3> cutMomentum=new HashMap<>();
        final Set<String> sectionEvents=new HashSet<>();
        final List<RagdollImpactPayload> waitingCuts=new ArrayList<>();double poseTime;
        final Map<Object,Node> bindNodes;final Map<Object,Object> bindParents;
        private record CutKey(Part part,String edge){}
        private record CutRequest(CutKey key,String base,int generation,List<RagdollImpactPayload.BladeSpan> path,boolean consuming){}
        private record CutJob(CutRequest request,int revision,CompletableFuture<PreparedCut> future){}
        final Map<CutKey,CutRequest> pendingConsumeCuts=new LinkedHashMap<>();
        final List<CutJob> consumeJobs=new ArrayList<>();
        long consumeSplit=Long.MIN_VALUE;Part consumeCutPart;Vec3 consumeCutPoint;
        Part heldFragment;
        long checkedGibSection=Long.MIN_VALUE;
        double lastVisibleEdge=Double.NEGATIVE_INFINITY;
        Vec3 gripOnSkin;

        final ConsumeTrauma trauma=new ConsumeTrauma();
        double supportTime=Double.NaN,supportTarget=Double.NaN,supportShift;
        double supportedSkin(Vec3[] positions,float partial){
            double clock=scene.getGameTime()+partial;
            if(clock==supportTime && supportTarget==targetAge)return supportShift;
            supportTime=clock;supportTarget=targetAge;
            var skin=new ArrayList<Vec3>();
            double settle=trauma.settleAfterCrush(clock);
            var lostSupport=new ArrayList<Vec3>();
            double x0=Double.POSITIVE_INFINITY,y0=x0,z0=x0,x1=-x0,y1=-x0,z1=-x0;
            for(int i=0;i<parts.size();i++){
                var p=parts.get(i);var at=positions[i];if(p.scale<.1)continue;
                // A hat brim or nose is cosmetic. The back, shoulders and pelvis
                // carry a prone villager; a transparent hat quad must never jack up the coat.
                if(com.codex.blacklight.combat.ConsumePlacementTrack.villagerReceiver(entity) && p.bind.region==1){
                    var parent=bindNodes.get(p.bind.parent);
                    if(parent!=null && parent.region==1)continue;
                }
                boolean gone=trauma.hides(p);
                if(gone && settle>=1)continue;
                var turn=new Quaternionf(p.oldRotation).slerp(p.rotation,partial);
                for(var vertex:p.mesh){
                    Vec3 world=at.add(rotate(p.skinPoint(vertex).subtract(p.center).scale(p.scale),turn));
                    (gone?lostSupport:skin).add(world);
                    x0=Math.min(x0,world.x);y0=Math.min(y0,world.y);z0=Math.min(z0,world.z);
                    x1=Math.max(x1,world.x);y1=Math.max(y1,world.y);z1=Math.max(z1,world.z);
                }
            }
            if(skin.isEmpty() || x1-x0>8 || z1-z0>8)return supportShift=0;
            var bounds=new AABB(x0,y0,z0,x1,y1,z1);
            var terrain=new ArrayList<AABB>();
            for(var shape:scene.getBlockCollisions(entity,bounds.expandTowards(0,-3,0).inflate(.001)))terrain.addAll(shape.toAabbs());
            double visible=RagdollTerrainSupport.rest(skin,terrain,bounds.minY);
            if(lostSupport.isEmpty())return supportShift=visible;
            // The neck and shoulders take the weight as the head fragments separate.
            skin.addAll(lostSupport);
            double before=RagdollTerrainSupport.rest(skin,terrain,bounds.minY);
            return supportShift=before+(visible-before)*settle;
        }

        Vec3 actorWrist(float partial){
            var visual=entity.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL);
            var owner=scene.getEntity(visual.consumerId());
            if(!(owner instanceof LivingEntity carrier))return null;
            var form=com.codex.blacklight.power.PowerForm.values()[visual.form()];
            if(form==com.codex.blacklight.power.PowerForm.MUSCLE_MASS){
                var palm=ConsumeTrauma.musclePalm(carrier,1,partial);
                if(palm!=null)return palm;
            }
            String key=com.codex.blacklight.combat.ConsumeSequence.key(form,visual.variant());
            var row=com.codex.blacklight.combat.ConsumePlacementTrack.contactFrame(key,visual.motionProgress(scene.getGameTime(),partial)).orElse(null);
            if(row==null)return null;
            Vec3 forward=Vec3.directionFromRotation(0,visual.facing());
            Vec3 right=new Vec3(-forward.z,0,forward.x);
            return carrier.getPosition(partial).add(right.scale(.9375*row[0])).add(forward.scale(.9375*row[2])).add(0,.9375*row[1],0);
        }

        void holdFragment(Vec3 wrist,int half){
            if(wrist==null || heldFragment!=null)return;
            // Only tissue actually in reach of the grasp can inherit the wrist.
            // A cut further down the weapon or whip stays at the point of impact.
            double closest=half==0?.34*.34:Double.POSITIVE_INFINITY;
            for(var p:parts){
                if(half!=0 && p.consumeHalf!=half)continue;
                if(half==0 && p.boundaries.stream().noneMatch(b->b.key.equals("gib:"+consumeSplit)))continue;
                Vec3 hand=p.center.add(RagdollRecovery.localAnchor(wrist,p.pos,p.rotation,p.scale));
                for(int i=0;i+3<p.mesh.size();i+=4){
                    for(int j=1;j<=2;j++){
                        var at=SweptBladeSurface.closest(hand,p.mesh.get(i).p,p.mesh.get(i+j).p,p.mesh.get(i+j+1).p);
                        double gap=at.distanceToSqr(hand)*p.scale*p.scale;
                        if(gap<closest){closest=gap;heldFragment=p;gripOnSkin=at;}
                    }
                }
            }
        }
        void inheritGrip(Part original,List<Part> fragments){
            if(heldFragment!=original)return;
            heldFragment=fragments.stream().filter(p->p.contains(gripOnSkin))
                    .min(Comparator.comparingDouble(p->p.center.distanceToSqr(gripOnSkin))).orElse(null);
        }

        Body(LivingEntity e,List<Vertex> vertices,Map<Object,Node> nodes,Map<Object,Object> parents,History history,float partial,RagdollImpactPayload hit){
            entity=e;origin=e.getPosition(partial);woundFrame=RagdollDrive.frame(e,partial);started=scene.getGameTime();expires=started+20;
            bindNodes=new LinkedHashMap<>(nodes);bindParents=new IdentityHashMap<>(parents);
            var grouped=new LinkedHashMap<Object,List<Vertex>>();for(var v:vertices)grouped.computeIfAbsent(v.key,k->new ArrayList<>()).add(v);
            for(var g:grouped.entrySet())parts.add(new Part(g.getKey(),g.getValue(),nodes.get(g.getKey())));
            materialBind=ConsumeSkinPath.bind(parts);
            parts.sort(Comparator.comparingDouble(Part::volume).reversed());
            root=RagdollRootSelection.pick(parts,p->p.key,k->{var node=nodes.get(k);return node==null?parents.get(k):node.parent;},p->p.bind.region,Part::volume);
            // Merge small decorative bones into an ancestor instead of creating hundreds of joints.
            while(parts.size()>MAX_BASE_PARTS){
                int smallest=parts.size()-1;if(parts.get(smallest)==root)smallest--;
                var tiny=parts.remove(smallest);Part owner=ancestor(tiny,parts,nodes,parents);if(owner==null)owner=root;
                var merged=new ArrayList<>(owner.mesh);merged.addAll(tiny.mesh);
                // Bounds, centre and inertia must describe all merged skin.
                var replacement=new Part(owner.key,merged,owner.bind);
                parts.set(parts.indexOf(owner),replacement);if(root==owner)root=replacement;
                parts.sort(Comparator.comparingDouble(Part::volume).reversed());
            }
            poseTime=started-1+partial;
            for(var part:parts)part.poses.record(poseTime,part.pos,part.rotation,part.scale);
            double dt=history==null?0:scene.getGameTime()+partial-history.time;
            for(var part:parts){
                var old=history==null?null:history.nodes.get(part.key);
                part.velocity=old!=null&&dt>.05&&dt<3?RagdollDrive.limit(part.bind.pivot.subtract(old.pivot).scale(1/dt),1.2):RagdollDrive.limit(e.getDeltaMovement(),1.2);
                if(old!=null&&dt>.05&&dt<3){
                    var turn=new Quaternionf(part.bind.rotation).mul(new Quaternionf(old.rotation).conjugate()).normalize();
                    if(turn.w<0)turn.mul(-1);double length=Math.sqrt(turn.x*turn.x+turn.y*turn.y+turn.z*turn.z);
                    if(length>1e-6)part.spin.set(RagdollDrive.limit(new Vec3(turn.x,turn.y,turn.z).scale(2*Math.atan2(length,turn.w)/(length*dt)),.5).toVector3f());
                }
            }
            // Initialize bone momentum first; fragments inherit v + omega x r
            // in the same contact event, before either half is rendered.
            if(hit!=null&&hit.sharp()&&hit.severMask()!=0)cut(hit);
            rebuildJoints();
            if(hit!=null){if(!hit.refinement())impulse(hit);else {expires=started+hit.ticks();if(e.isDeadOrDying()||hit.severMask()!=0)deadAt=started;}}
            separateCuts();
        }
        Part ancestor(Part child,List<Part> candidates,Map<Object,Node> nodes,Map<Object,Object> parents){
            Object key=child.bind.parent;var seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
            while(key!=null&&seen.add(key)){
                Part nearest=null;double best=Double.POSITIVE_INFINITY;
                boolean hasParent=false;
                for(var p:candidates)if(p.key==key&&p!=child){hasParent=true;if(!child.connectsTo(p))continue;double d=child.attachment().distanceToSqr(closest(child.attachment(),bounds(p.mesh)));if(d<best){best=d;nearest=p;}}
                if(nearest!=null)return nearest;
                if(hasParent)return null;
                var node=nodes.get(key);key=node==null?parents.get(key):node.parent;
            }
            Part nearest=null;double best=Double.POSITIVE_INFINITY;
            for(var p:candidates)if(p.key==root.key&&p!=child&&child.connectsTo(p)){double d=child.attachment().distanceToSqr(closest(child.attachment(),bounds(p.mesh)));if(d<best){best=d;nearest=p;}}
            return nearest;
        }
        void cut(RagdollImpactPayload hit){
            // The server has already accepted contact. A completed sweep history
            // must not delay the visual handoff until the corpse has fallen over.
            applyCut(hit);
        }
        static double lastSample(RagdollImpactPayload hit){
            double time=-1;for(var plane:hit.planes())if(!plane.path().isEmpty())time=Math.max(time,plane.path().getLast().time());return time;
        }
        void applyCut(RagdollImpactPayload hit){
            var frame=woundFrame;var rotation=frame.getUnnormalizedRotation(new Quaternionf()).normalize();
            for(var plane:hit.planes()){
                if(parts.size()>=MAX_PARTS)break;
                String key="strike:"+plane.stroke()+":"+plane.edge();
                boolean hook=plane.edge().startsWith(com.codex.blacklight.combat.WeaponCutContact.WHIP_HOOK+"/");
                boolean claw=plane.edge().contains("long blade /") || plane.edge().startsWith("Thumb short blade /");
                if(hook || claw){
                    cutRake(plane,key,frame,rotation,hook,hit.fatal());
                    continue;
                }
                if(sectionEvents.contains(key))continue;
                Vec3 point=RagdollDrive.transform(frame,plane.point()),normal=rotate(plane.normal(),rotation).normalize();
                Part struck=null;double best=Double.POSITIVE_INFINITY;
                for(var p:parts){
                    if(plane.region()!=0&&p.bind.region!=plane.region())continue;
                    double gap=point.distanceToSqr(closest(point,bounds(p.mesh)));
                    // Prefer substantial tissue when skin layers share the contact.
                    double score=gap+.0001/Math.max(.001,p.volume());
                    if(score<best){best=score;struck=p;}
                }
                if(struck==null || !completeSection(point,normal,key,struck,Map.of()))continue;
                if(com.codex.blacklight.combat.WeaponCutContact.WHIP_WAIST.equals(plane.edge())){
                    Vec3 travel=hit.cuts().stream().filter(c->c.region()==32)
                            .map(c->new Vec3(c.x(),c.y(),c.z())).findFirst().orElse(Vec3.ZERO).multiply(1,0,1);
                    cutMomentum.put(key,RagdollDrive.limit(travel,.48));
                    WoundBlood.hookTear(entity,point,travel.normalize());
                }else if(plane.edge().startsWith("Continuous sculpted blade /") && plane.path().size()>1){
                    Vec3 edge=edgeVelocity(plane.path(),point);
                    Vec3 across=edge.subtract(normal.scale(edge.dot(normal)));
                    cutMomentum.put(key,RagdollDrive.limit(across.scale(.40),.52));
                    InfectedImpactVisuals.sharpSever(entity,point,across,com.codex.blacklight.combat.FleshStrike.EDGE);
                }
            }
        }

        void cutRake(RagdollImpactPayload.CutPlane plane,String key,Matrix4f frame,Quaternionf rotation,boolean hook,boolean fatal){
            String tear=(hook?"hook-tear:":"claw-tear:")+plane.stroke();
            var path=plane.path();
            if(sectionEvents.contains(tear) || path.size()<2)return;
            Vec3 point=RagdollDrive.transform(frame,plane.point());
            Vec3 normal=rotate(plane.normal(),rotation).normalize();
            if(!cutAt(new Boundary(point,normal,1,null,key),plane.region(),0,path)
                    && (hook || !fatal || !finishClawIncision(key,point,normal)))return;

            // Carry only a newly detached component with the hook at its point of contact.
            Vec3 tangent=edgeVelocity(path,point);
            cutMomentum.put(key,RagdollDrive.limit(tangent.scale(hook?.36:.40),.52));
            sectionEvents.add(tear);
            WoundBlood.hookTear(entity,point,tangent.normalize());
            if(!hook)InfectedImpactVisuals.sharpSever(entity,point,tangent,com.codex.blacklight.combat.FleshStrike.CLAW);
        }

        /** Finish a lethal rake through the one piece of skin that actually carries the claw mark. */
        boolean finishClawIncision(String key,Vec3 contact,Vec3 normal){
            Part struck=null;Vec3 wound=null;double nearest=Double.POSITIVE_INFINITY;
            for(var part:parts){
                var incision=part.incisions.get(key);
                if(incision==null || part.detachedTissue)continue;
                for(var vertex:incision){
                    double distance=vertex.p.distanceToSqr(contact);
                    if(distance<nearest){nearest=distance;struck=part;wound=vertex.p;}
                }
            }
            if(struck==null)return false;
            var plane=ContactSection.fit(wound,normal,struck.mesh.stream().map(v->v.p).toList());
            if(plane==null)return false;
            var cut=new Boundary(plane.point(),plane.normal(),1,null,key);
            return cutAt(cut,struck.bind.region,struck.half.length(),List.of(),struck);
        }

        private static Vec3 edgeVelocity(List<RagdollImpactPayload.BladeSpan> path,Vec3 point){
            double nearest=Double.POSITIVE_INFINITY;
            Vec3 tangent=Vec3.ZERO;
            for(int i=1;i<path.size();i++){
                var a=path.get(i-1);var b=path.get(i);
                Vec3 start=a.worldPoint(a.tip()),end=b.worldPoint(b.tip());
                double gap=point.distanceToSqr(end);
                if(gap<nearest && end.distanceToSqr(start)>1e-8){
                    nearest=gap;tangent=end.subtract(start).scale(1/Math.max(.01,b.time()-a.time()));
                }
            }
            return tangent;
        }
        /** Prepare both sides of every intersected bone/layer before changing
         * any live part. Budget rejection leaves the entire old body untouched. */
        boolean completeSection(Vec3 point,Vec3 normal,String key,Part struck,Map<Part,RagdollPoseHistory.Pose> poses){
            if(sectionEvents.contains(key))return false;
            var skin=struck.mesh.stream().map(v->sectionPosition(struck,v.p,poses)).toList();
            var section=ContactSection.fit(point,normal,skin);if(section==null)return false;
            boolean pairedGibs=key.startsWith("gib:") && components<=1;
            var replacements=new LinkedHashMap<Part,List<Part>>();
            var surfaces=new ArrayList<Boundary>();
            int vertexCount=parts.stream().mapToInt(p->p.mesh.size()).sum(),partCount=parts.size();
            for(var source:parts){
                if(source.flow>.95F)continue;
                var pose=poses.get(source);
                Vec3 at=pose==null?section.point():pose.toBind(section.point(),source.center);
                Vec3 n=pose==null?section.normal():rotate(section.normal(),new Quaternionf(pose.rotation()).conjugate()).normalize();
                var boundary=new Boundary(at,n,1,null,key);
                double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
                for(var v:source.mesh){double d=boundary.distance(v.p);lo=Math.min(lo,d);hi=Math.max(hi,d);}
                if(lo>=-.005||hi<=.005)continue;
                var front=clip(source,source.mesh,boundary,1);var back=clip(source,source.mesh,boundary,-1);
                if(front.size()<12||back.size()<12)continue;
                vertexCount+=front.size()+back.size()-source.mesh.size();partCount++;
                if(vertexCount>MAX_VERTICES||partCount>MAX_PARTS)return false;
                var a=new Part(source.key,front,source.bind);var b=new Part(source.key,back,source.bind);
                for(var p:List.of(a,b)){p.inherit(source);p.boundaries.addAll(source.boundaries);p.finishedCuts.add(key);}
                a.boundaries.add(boundary.side(1));b.boundaries.add(boundary.side(-1));
                if(pairedGibs){a.consumeHalf=1;b.consumeHalf=-1;}
                replacements.put(source,List.of(a,b));surfaces.add(boundary);
            }
            if(replacements.isEmpty())return false;
            if(pairedGibs)for(var part:parts)if(!replacements.containsKey(part)){
                Vec3 position=sectionPosition(part,part.center,poses);
                part.consumeHalf=position.subtract(section.point()).dot(section.normal())>=0?1:-1;
            }
            for(var entry:replacements.entrySet()){
                var source=entry.getKey();var halves=entry.getValue();
                if(source==root)root=halves.stream().max(Comparator.comparingDouble(Part::volume)).orElseThrow();
                inheritGrip(source,halves);
                if(source==consumeCutPart)consumeCutPart=halves.stream().min(Comparator.comparingDouble(p->p.center.distanceToSqr(consumeCutPoint))).orElseThrow();
                parts.remove(source);parts.addAll(halves);
            }
            appliedCuts+=replacements.size();sectionEvents.add(key);cutSurfaces.add(surfaces.getFirst());
            if(pairedGibs){
                var visual=entity.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL);
                if(scene.getEntity(visual.consumerId()) instanceof LivingEntity owner)
                    trauma.response.separated(entity,owner,visual,section.normal(),parts,scene.getGameTime());
            }
            WoundBlood.meshSection(entity,struck.bind.region,key);
            return true;
        }
        Vec3 sectionPosition(Part part,Vec3 point,Map<Part,RagdollPoseHistory.Pose> poses){
            var pose=poses.get(part);return pose==null?point:pose.position().add(rotate(point.subtract(part.center).scale(pose.scale()),pose.rotation()));
        }
        void releaseQuillChip(){
            var exit=QuillWounds.takeChip(entity);
            if(exit==null || parts.size()>=MAX_PARTS)return;
            int vertices=parts.stream().mapToInt(p->p.mesh.size()).sum();
            if(vertices+exit.skin().size()>MAX_VERTICES)return;
            var piece=new Part(new Object(),exit.skin(),null);
            piece.detachedTissue=true;
            piece.velocity=exit.direction().scale(.47).add(0,.08,0);
            Vec3 turn=exit.direction().cross(new Vec3(0,1,0));
            if(turn.lengthSqr()<.01)turn=exit.direction().cross(new Vec3(1,0,0));
            piece.spin.set(turn.normalize().scale(.43).toVector3f());
            parts.add(piece);
            components++;
        }
        void strikeQuill(QuillWounds.Mark mark){
            var connected=connectedPose(1).positions();
            var pose=new IdentityHashMap<Object,Node>();var skin=new ArrayList<Vertex>();
            for(int i=0;i<parts.size();i++){
                var p=parts.get(i);var at=connected[i];
                pose.put(p,new Node(p,null,at,p.rotation,p.bind.region));
                for(var v:p.mesh)skin.add(v.world(p,at.add(rotate(v.p.subtract(p.center).scale(p.scale),p.rotation)),rotate(v.n,p.rotation)));
            }
            var changed=QuillWounds.fresh(entity,skin,pose,mark);
            if(!(mark.key instanceof Part struck)||struck.scale<.001)return;
            // A live body may recover onto its native rig after this physical skin is baked.
            mark.key=null;
            var at=pose.get(struck).pivot;var inv=new Quaternionf(struck.rotation).conjugate();
            struck.mesh.clear();
            for(var v:changed)if(v.key==struck)struck.mesh.add(v.world(struck.key,
                    struck.center.add(rotate(v.p.subtract(at),inv).scale(1/struck.scale)),rotate(v.n,inv)));
            struck.meshRevision++;
        }
        void receive(RagdollImpactPayload hit){
            int before=appliedCuts;
            if(hit.sharp()&&hit.severMask()!=0)cut(hit);
            boolean carried=appliedCuts!=before && !cutMomentum.isEmpty();
            if(appliedCuts!=before){rebuildJoints();if(!carried)separateCuts();}
            if(!hit.refinement())impulse(hit);
            if(carried)separateCuts();
        }
        boolean cutAt(Vec3 point,Vec3 normal,int region,double radius){
            return cutAt(new Boundary(point,normal,1),region,radius);
        }
        boolean cutAt(Boundary cut,int region,double radius){
            return cutAt(cut,region,radius,List.of());
        }
        boolean cutAt(Boundary seed,int region,double radius,List<RagdollImpactPayload.BladeSpan> timedPath){
                return cutAt(seed,region,radius,timedPath,null);
        }
        boolean cutAt(Boundary seed,int region,double radius,List<RagdollImpactPayload.BladeSpan> timedPath,Part only){
                return cutAt(seed,region,radius,timedPath,only,null);
        }
        boolean cutAt(Boundary seed,int region,double radius,List<RagdollImpactPayload.BladeSpan> timedPath,Part only,PreparedCut prepared){
                boolean cutPlane=false;
                Vec3 bleedPoint=null,bleedNormal=seed.normal;int bleedRegion=32;double bleedVolume=0;
                // Snapshot: each plane clips every contacted layer once, never its own new halves.
                for(var struck:List.copyOf(parts)){
                    if(parts.size()>=MAX_PARTS)break;
                    if(only!=null&&struck!=only)continue;
                    if(region!=0&&struck.bind.region!=region)continue;
                    if(prepared!=null){
                        if(!prepared.incision.isEmpty()){
                            int total=prepared.incision.size();for(var p:parts)if(p!=struck)total+=p.mesh.size();
                            if(total<=MAX_VERTICES){struck.mesh.clear();struck.mesh.addAll(prepared.incision);struck.meshRevision++;struck.incisions.remove(seed.key);struck.kerfTimes.put(seed.key,prepared.through);materialIncisions++;}
                            continue;
                        }
                        struck.kerfTimes.put(seed.key,prepared.through);
                    }
                    Boundary cut=seed;
                    if(!timedPath.isEmpty()){
                        var path=struck.poses.localPath(timedPath,struck.center);
                        var surface=new SweptBladeSurface(path);if(surface.empty())continue;
                        cut=new Boundary(seed.point,seed.normal,1,surface,seed.key);
                    }
                    if(!cut.key.isEmpty()&&struck.finishedCuts.contains(cut.key))continue;
                    if(cut.surface!=null){var box=bounds(struck.mesh);if(!cut.surface.overlaps(new Vec3(box.minX,box.minY,box.minZ),new Vec3(box.maxX,box.maxY,box.maxZ),.045))continue;}
                    var mesh=prepared!=null?prepared.mesh:cut.surface==null?struck.mesh:detailed(struck.mesh,cut.surface);
                    if(mesh.isEmpty())continue;
                    if(prepared==null){
                        double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
                        for(var v:mesh){double d=cut.distance(v.p);lo=Math.min(lo,d);hi=Math.max(hi,d);}
                        if(lo>-.018||hi<.018)continue;
                    }
                    if(cut.surface==null&&cut.point.distanceToSqr(closest(cut.point,bounds(struck.mesh)))>radius*radius)continue;
                    var replacements=new ArrayList<Part>();
                    if(cut.surface==null){
                        var front=clip(struck,mesh,cut,1);var back=clip(struck,mesh,cut,-1);
                        if(front.size()<12||back.size()<12)continue;
                        var a=new Part(struck.key,front,struck.bind);var b=new Part(struck.key,back,struck.bind);
                        a.boundaries.add(cut.side(1));b.boundaries.add(cut.side(-1));replacements.add(a);replacements.add(b);
                    }else {
                        List<List<Vertex>> faces;
                        if(prepared!=null)faces=prepared.faces;else{faces=new ArrayList<>();for(int i=0;i+3<mesh.size();i+=4)faces.add(mesh.subList(i,i+4));}
                        var trace=prepared!=null?prepared.result:FiniteMeshCut.trace(faces,MESH_VERTICES,cut.surface);var pieces=trace.pieces();
                        if(!System.getProperty("blacklight.test.ragdollVisual","").isEmpty()&&!trace.incisions().isEmpty())
                            com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Blade topology victim={} region={} blade={} wounds={} pieces={} reason={}",entity.getType().getDescriptionId(),struck.bind.region,cut.key,trace.incisions().size(),pieces.size(),trace.reason());
                        struck.wound(cut.key,woundMesh(struck.key,faces,trace.incisions()));
                        if(pieces.size()<2||parts.size()-1+pieces.size()>MAX_PARTS)continue;
                        for(int index=0;index<pieces.size();index++){
                            var piece=pieces.get(index);var output=new ArrayList<Vertex>();
                            for(var polygon:piece.faces()){
                                if(polygon.size()==4)output.addAll(polygon);
                                else for(int i=1;i+1<polygon.size();i++){output.add(polygon.getFirst());output.add(polygon.get(i));output.add(polygon.get(i+1));output.add(polygon.get(i+1));}
                            }
                            for(var loop:piece.caps())for(var triangle:loop.triangles())for(var point:List.of(triangle[0],triangle[1],triangle[2],triangle[2]))
                                output.add(new Vertex(struck.key,RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE),point,cut.surface.normal(point).scale(-loop.side()),.5F,.5F,0xff70231f,OverlayTexture.NO_OVERLAY,0));
                            var part=new Part(struck.key,output,struck.bind);
                            int side=piece.caps().isEmpty()?(cut.distance(part.center)>=0?1:-1):piece.caps().getFirst().side();
                            part.boundaries.add(new Boundary(cut.point,cut.normal,side,cut.surface,cut.key,new Domain(pieces,index)));replacements.add(part);
                        }
                    }
                    int total=replacements.stream().mapToInt(p->p.mesh.size()).sum();for(var p:parts)if(p!=struck)total+=p.mesh.size();
                    if(replacements.stream().anyMatch(p->p.mesh.size()<12)||total>MAX_VERTICES){
                        if(!System.getProperty("blacklight.test.ragdollVisual","").isEmpty())com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Blade split rejected region={} vertices={} parts={}",struck.bind.region,total,replacements.stream().map(p->p.mesh.size()).toList());
                        continue;
                    }
                    for(var p:replacements){p.boundaries.addAll(struck.boundaries);p.inherit(struck);if(!cut.key.isEmpty()&&(prepared==null||cut.key.startsWith("consume:")))p.finishedCuts.add(cut.key);}
                    // A split must not discard the other claws or later motion
                    // already queued against the same piece of skin.
                    var carry=new ArrayList<>(pendingConsumeCuts.values());for(var job:consumeJobs)carry.add(job.request);
                    for(var request:carry)if(request.key.part==struck)for(var fragment:replacements){
                        var id=new CutKey(fragment,request.key.edge);pendingConsumeCuts.putIfAbsent(id,new CutRequest(id,request.base,request.generation,request.path,request.consuming));
                    }
                    // Earlier surface gashes stay on the closest resulting skin. The
                    // current sever already has a real cap and needs no duplicate stripe.
                    for(var wound:struck.incisions.entrySet())if(!wound.getKey().equals(cut.key)){
                        var assigned=new IdentityHashMap<Part,List<Vertex>>();
                        for(int i=0;i+3<wound.getValue().size();i+=4){var quad=wound.getValue().subList(i,i+4);Vec3 at=Vec3.ZERO;for(var v:quad)at=at.add(v.p);at=at.scale(.25);
                            Part nearest=null;double best=Double.POSITIVE_INFINITY;
                            for(var p:replacements){double d=at.distanceToSqr(closest(at,bounds(p.mesh)))+(p.contains(at)?0:1);if(d<best){nearest=p;best=d;}}
                            assigned.computeIfAbsent(nearest,k->new ArrayList<>()).addAll(quad);
                        }
                        assigned.forEach((part,skin)->part.wound(wound.getKey(),skin));
                    }
                    if(struck.volume()>bleedVolume){
                        Vec3 sum=Vec3.ZERO;int count=0;
                        for(var p:replacements)for(var v:p.mesh)if(v.color==0xff70231f&&Math.abs(cut.distance(v.p))<.002){sum=sum.add(v.p);count++;}
                        if(count>0){bleedPoint=sum.scale(1.0/count);bleedNormal=cut.surface==null?cut.normal:cut.surface.normal(bleedPoint);bleedRegion=struck.bind.region;bleedVolume=struck.volume();}
                    }
                    inheritGrip(struck,replacements);
                    if(consumeCutPart==struck)consumeCutPart=replacements.stream().min(Comparator.comparingDouble(p->p.center.distanceToSqr(consumeCutPoint))).orElseThrow();
                    parts.remove(struck);parts.addAll(replacements);
                    appliedCuts++;cutPlane=true;
                    if(struck==root)root=replacements.stream().max(Comparator.comparingDouble(Part::volume)).orElseThrow();
                }
                if(cutPlane){
                    cutSurfaces.add(seed.side(1));
                    if(bleedPoint!=null){
                        var inverse=new Matrix4f(woundFrame).invert();
                        WoundBlood.meshCut(entity,bleedRegion,RagdollDrive.transform(inverse,bleedPoint),rotate(bleedNormal,inverse.getUnnormalizedRotation(new Quaternionf()).normalize()));
                    }
                }
                return cutPlane;
        }
        RagdollHinge kneeHinge(Part parent,Part child){
            if(!(entity instanceof com.codex.blacklight.entity.BlacklightMutant brute)||brute.kind()!=com.codex.blacklight.entity.BlacklightMutant.Kind.BRUTE)return null;
            if(!(child.key instanceof software.bernie.geckolib.cache.object.GeoBone bone)||!bone.getName().endsWith("_shin"))return null;
            if(!(parent.key instanceof software.bernie.geckolib.cache.object.GeoBone upper)||!upper.getName().equals(bone.getName().replace("_shin","_leg")))return null;
            return new RagdollHinge(parent.bind.rotation,child.bind.rotation,(float)Math.toRadians(-155),0);
        }
        void rebuildJoints(){
            joints.clear();
            for(var part:parts)if(part.key!=root.key && !part.detachedTissue){
                var parent=ancestor(part,parts,bindNodes,bindParents);
                if(parent!=null && parent.consumeHalf==part.consumeHalf)
                    joints.add(new Joint(parent,part,kneeHinge(parent,part)));
            }
            if(root.consumeHalf!=0){
                joinConsumeHalf(1);
                joinConsumeHalf(-1);
            }
        }
        // A native SpawnGib contains a complete skin. Clothing and limbs on that
        // side can have distinct Minecraft rig roots but share a physical body.
        void joinConsumeHalf(int side){
            var roots=new ArrayList<Part>();
            for(var part:parts)if(part.consumeHalf==side)roots.add(part);
            for(var joint:joints)roots.remove(joint.child);
            if(roots.size()<2)return;
            var main=Collections.max(roots,Comparator.comparingDouble(Part::volume));
            for(var part:roots)if(part!=main){
                var joint=new Joint(main,part,null);
                var pivot=part.pos.add(rotate(part.attachment().subtract(part.center).scale(part.scale),part.rotation));
                joint.a=RagdollRecovery.localAnchor(pivot,main.pos,main.rotation,main.scale);
                joint.b=RagdollRecovery.localAnchor(pivot,part.pos,part.rotation,part.scale);
                joints.add(joint);
            }
        }
        /** An intact consume receiver is animated by the other half of the paired clip. */
        boolean pairedReceiver(){
            if(!RagdollDrive.captured(entity))return false;
            var visual=entity.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL);
            return visual.consuming()&&consumeSplit!=visual.started();
        }
        void consumeFracture(){
            var visual=entity.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL);
            if(!visual.consuming())return;
            if(consumeSplit==visual.started()){
                if(checkedGibSection!=consumeSplit &&
                        com.codex.blacklight.combat.ConsumePlacementTrack.villagerReceiver(entity)){
                    var form=com.codex.blacklight.power.PowerForm.values()[visual.form()];
                    float split=com.codex.blacklight.combat.ConsumeSequence.splitSeconds(form,false,visual.variant());
                    if((scene.getGameTime()-consumeSplit)/20F>=split+.10F){
                        checkedGibSection=consumeSplit;
                        if(form!=com.codex.blacklight.power.PowerForm.MUSCLE_MASS && heldFragment==null
                                && lastVisibleEdge<consumeSplit+split*20-2)
                            splitVillagerGibs();
                    }
                }
                return;
            }
            var phase=ConsumeFracture.phase(entity,1);
            float split=com.codex.blacklight.combat.ConsumeSequence.splitSeconds(phase.form(),false,visual.variant());
            if(!Float.isFinite(split)||(scene.getGameTime()-visual.started())/20F<split)return;
            consumeSplit=visual.started();
            heldFragment=null;
            for(var p:parts){
                // The last two puppet poses share the physics tick clock. A gib
                // inherits this motion once and is then driven by the solver.
                var last=p.poses.sample(poseTime);
                var prior=p.poses.sample(poseTime-1);
                p.velocity=RagdollDrive.limit(last.position().subtract(prior.position()),.45);
                var delta=new Quaternionf(prior.rotation()).conjugate().mul(last.rotation()).normalize();
                if(delta.w<0)delta.set(-delta.x,-delta.y,-delta.z,-delta.w);
                p.spin.set(RagdollDrive.limit(new Vec3(delta.x,delta.y,delta.z).scale(2),.35).toVector3f());
            }
            if(phase.style()==ConsumeFracture.Style.ARM_RIP){
                // The P1 receiver swaps to ConsumeMaleGenericVert01LeftGib and RightGib.
                // Only its anatomical left half carries the R_Wrist_Grapple slot.
                Part torso=parts.stream().filter(p->p.bind.region==32).max(Comparator.comparingDouble(Part::volume)).orElse(null);
                if(torso==null)return;
                var orientation=new Quaternionf(torso.rotation).mul(torso.bind.rotation());
                Vec3 left=rotate(new Vec3(1,0,0),orientation).normalize();
                var poses=new IdentityHashMap<Part,RagdollPoseHistory.Pose>();
                for(var p:parts)poses.put(p,new RagdollPoseHistory.Pose(poseTime,p.pos,p.rotation,p.scale));
                Vec3 palmWound=null;
                if(scene.getEntity(visual.consumerId()) instanceof LivingEntity actor){
                    Vec3[] contactPose=parts.stream().map(part->part.pos).toArray(Vec3[]::new);
                    palmWound=ConsumeTrauma.gripTear(entity,actor,visual,parts,contactPose,1);
                }
                Vec3 sectionAt=torso.pos;
                if(palmWound!=null){
                    // The hands pull the two sides apart; retain the midline but place the seam at the actual grip height.
                    Vec3 offset=palmWound.subtract(torso.pos);
                    sectionAt=torso.pos.add(offset.subtract(left.scale(offset.dot(left))));
                }
                if(!completeSection(sectionAt,left,"gib:"+consumeSplit,torso,poses))return;
                rebuildJoints();
                separateCuts();
                holdFragment(actorWrist(1),1);
                if(scene.getEntity(visual.consumerId()) instanceof LivingEntity actor){
                    for(int half:new int[]{-1,1}){
                        int side=half<0?0:1;
                        var destination=com.codex.blacklight.combat.ConsumePlacementTrack.ripFollowThrough(actor,visual,side);
                        Vec3 contact=villagerGrip(entity,half*.22F,1);
                        var release=destination==null || contact==null?ConsumeTrauma.releaseMotion(actor,side)
                                :RagdollDrive.limit(destination.subtract(contact).scale(.75),.95);
                        for(var part:parts)if(part.consumeHalf==half)part.velocity=release.add(left.scale(half*.055));
                    }
                }
            }else {
                holdFragment(actorWrist(1),0);
                return;
            }
            com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Generic consume split victim={} id={} style={} parts={} joints={} meshCuts={} components={}",entity.getType().getDescriptionId(),entity.getId(),phase.style(),parts.size(),joints.size(),appliedCuts,components);
        }
        /** SpawnGib is a committed encounter event. If no weapon edge was drawn
         * (first-person or off-screen), split the posed robe at the waist. */
        void splitVillagerGibs(){
            Part torso=null,leg=null;
            for(var p:parts){
                if(p.bind.region==32&&(torso==null||p.volume()>torso.volume()))torso=p;
                if(p.bind.region==8||p.bind.region==16)leg=p;
            }
            if(torso==null||leg==null)return;
            var direction=torso.pos.subtract(leg.pos).normalize();
            if(direction.lengthSqr()<.5)return;
            var poses=new IdentityHashMap<Part,RagdollPoseHistory.Pose>();
            for(var p:parts)poses.put(p,new RagdollPoseHistory.Pose(poseTime,p.pos,p.rotation,p.scale));
            var waist=torso.pos.lerp(leg.pos,.35);
            if(completeSection(waist,direction,"gib:"+consumeSplit,torso,poses)){
                rebuildJoints();separateCuts();holdFragment(actorWrist(1),0);
            }
        }

        void consumeBlades(Map<String,List<Vec3>> edges,float partial){
            var visual=entity.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL);
            if(edges.isEmpty())return;
            if(visual.form()==com.codex.blacklight.power.PowerForm.WHIPFIST.ordinal() && visual.variant()==com.codex.blacklight.combat.ConsumeVariant.CLASSIC){
                trauma.sampleLash(scene.getGameTime()+partial,edges);
                return;
            }
            lastVisibleEdge=scene.getGameTime()+partial;
            consumeSection("gib:"+visual.started(),edges,partial);
        }
        long grazedScene=Long.MIN_VALUE;
        int grazedOwner=-1;
        void consumeSection(String event,Map<String,List<Vec3>> edges,float partial){
            var visual=entity.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL);
            if(sectionEvents.contains(event)||sectionEvents.contains("gib:"+visual.started()))return;
            long now=scene.getGameTime();var phase=ConsumeFracture.phase(entity,partial);
            float split=com.codex.blacklight.combat.ConsumeSequence.splitSeconds(phase.form(),
                    com.codex.blacklight.combat.ConsumeSequence.mountedStage(visual.stage()),visual.variant());
            // The rendered skin closes surviving joints and lifts connected bodies
            // off support as a whole. A per-bone interpolated position can be far
            // from that skin; use exactly the same projection as render().
            var connected=connectedPose(partial);
            var poses=new IdentityHashMap<Part,RagdollPoseHistory.Pose>();
            for(int i=0;i<parts.size();i++){
                var part=parts.get(i);var turn=new Quaternionf(part.oldRotation).slerp(part.rotation,partial);
                poses.put(part,new RagdollPoseHistory.Pose((double)now+partial,connected.positions()[i],turn,part.scale));
            }
            Part contacted=null,grazed=null;ContactSection.Plane contact=null,graze=null;
            Vec3 grazeTravel=null;
            double age=(now+partial-visual.started())/20.;
            boolean canGraze=(grazedScene!=visual.started()||grazedOwner!=visual.consumerId())&&age>=split-.18&&age<=split+.24;
            // Collect every visible profile before choosing the same stroke
            // so the first wing contact cannot pre-empt the other sharp side.
            for(var edge:edges.entrySet()){
            String base="consume:"+visual.started()+":"+edge.getKey();
            for(var struck:List.copyOf(parts)){
                if(struck.flow>.95F)continue;
                var pose=poses.get(struck);
                var span=new RagdollImpactPayload.BladeSpan(edge.getValue().stream().map(p->pose.toBind(p,struck.center)).toList(),now+partial,Vec3.ZERO,180);
                var sequence=struck.consumePaths.computeIfAbsent(base,k->new BladeCutSequence());sequence.record(span);
                var path=sequence.snapshot();
                while(struck.consumePaths.size()>16)struck.consumePaths.remove(struck.consumePaths.keySet().iterator().next());
                if(path.size()<2||!Float.isFinite(split))continue;
                // Trace only the new interval while the move plays. Retain its
                // latest real contact for the authored sever event, instead of
                // scanning every historical triangle against every new fragment
                // on the event frame (especially costly for eight claws).
                var surface=new SweptBladeSurface(path.subList(path.size()-2,path.size()));var box=bounds(struck.mesh);
                if(surface.overlaps(new Vec3(box.minX,box.minY,box.minZ),new Vec3(box.maxX,box.maxY,box.maxZ),0)){
                    var fresh=ContactSection.contact(surface.triangles(),struck.mesh.stream().map(v->v.p).toList());
                    if(fresh!=null){
                        if(canGraze&&grazed==null){
                            grazed=struck;graze=fresh;
                            var blade=edge.getValue();
                            grazeTravel=rotate(blade.get(blade.size()-1).subtract(blade.get(0)),new Quaternionf(pose.rotation()).conjugate());
                        }
                        struck.sectionContacts.put(base,fresh);
                        struck.consumeCutTicks.put(base,now);
                    }
                }
                if((now+partial-visual.started())/20<split || now-struck.consumeCutTicks.getOrDefault(base,now-3)>2)continue;
                var found=struck.sectionContacts.get(base);
                if(found!=null&&!struck.contains(found.point()))found=null;
                // A broad wing's empty bounding volume must not displace a
                // confirmed thorax contact during the blade's paired slice.
                // This preference applies only after a real edge/skin crossing.
                boolean central=phase.form()==com.codex.blacklight.power.PowerForm.BLADE&&struck.bind.region==32;
                boolean previousCentral=contacted!=null&&phase.form()==com.codex.blacklight.power.PowerForm.BLADE&&contacted.bind.region==32;
                if(found!=null&&(contacted==null||central&&!previousCentral||central==previousCentral&&struck.volume()>contacted.volume())){contacted=struck;contact=found;}
            }
            }
            if(grazed!=null){
                Vec3 inward=grazed.center.subtract(graze.point()).normalize();
                if(inward.lengthSqr()<.5)inward=graze.normal();
                int before=grazed.mesh.size();
                if(ConsumeTrauma.cutAt(grazed,graze.point(),inward,grazeTravel,.145/grazed.scale)){
                    grazedScene=visual.started();grazedOwner=visual.consumerId();
                    ConsumeContactMarks.confirmed(entity,visual,"slash",0,sectionPosition(grazed,graze.point(),poses),
                            grazed.bind.region(),before,grazed.mesh.size());
                }
            }
            if(contacted!=null){
                var pose=poses.get(contacted);
                Vec3 at=sectionPosition(contacted,contact.point(),poses),normal=rotate(contact.normal(),pose.rotation());
                int before=parts.stream().mapToInt(p->p.mesh.size()).sum();
                if(completeSection(at,normal,event,contacted,poses)){
                    ConsumeContactMarks.confirmed(entity,visual,"cleave",0,at,contacted.bind.region(),before,
                            parts.stream().mapToInt(p->p.mesh.size()).sum());
                    rebuildJoints();separateCuts();
                    if(consumeSplit==visual.started())holdFragment(actorWrist(partial),0);
                }
            }
        }
        void drainConsumeCuts(){
            int before=appliedCuts;
            for(var iterator=consumeJobs.iterator();iterator.hasNext();){
                var job=iterator.next();if(!job.future.isDone())continue;iterator.remove();
                var id=job.request.key;var part=id.part;
                if(!parts.contains(part)||part.flow>.95F||job.request.consuming&&!entity.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL).consuming())continue;
                if(part.meshRevision!=job.revision){pendingConsumeCuts.putIfAbsent(id,job.request);continue;}
                try{
                    var result=job.future.join();
                    var beforeParts=List.copyOf(parts);
                    if(result!=null&&cutAt(result.boundary,0,0,List.of(),part,result)){
                        for(var fragment:parts)if(!beforeParts.contains(fragment)){
                            if(job.request.consuming){
                                var sequence=fragment.consumePaths.get(job.request.base);
                                if(sequence!=null&&sequence.generation()==job.request.generation){
                                    var completed=job.request.path.stream().filter(s->s.time()<=result.through).reduce((a,b)->b).orElse(job.request.path.getFirst());
                                    sequence.advance(completed);var next=new CutKey(fragment,sequence.key(job.request.base));
                                    pendingConsumeCuts.putIfAbsent(next,new CutRequest(next,job.request.base,sequence.generation(),sequence.snapshot(),true));
                                }
                            }else if(result.more){var next=new CutKey(fragment,id.edge);pendingConsumeCuts.putIfAbsent(next,new CutRequest(next,job.request.base,0,job.request.path,false));}
                        }
                    }
                    if(result!=null&&result.more&&parts.contains(part)&&part.kerfTimes.getOrDefault(id.edge,Double.NEGATIVE_INFINITY)>=result.through)
                        pendingConsumeCuts.putIfAbsent(id,job.request);
                }catch(CompletionException ex){com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Consume mesh job failed",ex);}
            }
            if(before!=appliedCuts){
                rebuildJoints();separateCuts();
                com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Blade contact victim={} id={} meshCuts={} components={}",entity.getType().getDescriptionId(),entity.getId(),appliedCuts,components);
            }
            startConsumeCuts();
        }
        void startConsumeCuts(){
            pendingConsumeCuts.entrySet().removeIf(e->!parts.contains(e.getKey().part)||e.getKey().part.flow>.95F||e.getKey().part.finishedCuts.contains(e.getKey().edge));
            while(pendingConsumeCuts.size()>128)pendingConsumeCuts.remove(pendingConsumeCuts.keySet().iterator().next());
            for(var iterator=pendingConsumeCuts.values().iterator();iterator.hasNext()&&consumeJobs.size()<2;){
                var request=iterator.next();var part=request.key.part;
                if(consumeJobs.stream().anyMatch(j->j.request.key.equals(request.key)))continue;
                var skin=List.copyOf(part.mesh);var center=part.center;double after=part.kerfTimes.getOrDefault(request.key.edge,Double.NEGATIVE_INFINITY);
                var capType=RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE);
                try{
                    var future=CompletableFuture.supplyAsync(()->prepareCut(skin,center,request.key.edge,request.path,after,capType),CUT_WORKERS);
                    consumeJobs.add(new CutJob(request,part.meshRevision,future));iterator.remove();
                }catch(RejectedExecutionException busy){break;}
            }
        }
        void separateCuts(){
            components=0;
            var remaining=new LinkedHashSet<>(parts);
            while(!remaining.isEmpty()){
                var component=new ArrayList<Part>();var queue=new ArrayDeque<Part>();queue.add(remaining.iterator().next());
                while(!queue.isEmpty()){
                    var p=queue.removeFirst();if(!remaining.remove(p))continue;component.add(p);
                    for(var joint:joints){if(joint.parent==p)queue.add(joint.child);else if(joint.child==p)queue.add(joint.parent);}
                }
                components++;Vec3 separation=Vec3.ZERO,pull=Vec3.ZERO;
                for(var surface:cutSurfaces){
                    int signs=0;Vec3 normal=surface.normal;
                    for(var p:component)for(var boundary:p.boundaries)
                        if(surface.same(boundary)){signs|=boundary.sign>0?1:2;normal=rotate(boundary.surface==null?boundary.normal:boundary.surface.normal(p.center),p.rotation);}
                    // Shared momentum for an entire connected fragment; intact limbs stay attached.
                    if(signs==1||signs==2){
                        separation=separation.add(normal.scale(signs==1?.075:-.075));
                        boolean waist=surface.key.endsWith(":"+com.codex.blacklight.combat.WeaponCutContact.WHIP_WAIST);
                        if(waist?signs==1:!component.contains(root))pull=pull.add(cutMomentum.getOrDefault(surface.key,Vec3.ZERO));
                    }
                }
                separation=RagdollDrive.limit(separation,.14).add(RagdollDrive.limit(pull,.52));
                for(var p:component){
                    p.velocity=p.velocity.add(separation);
                    if(pull.lengthSqr()>0)p.velocity=RagdollDrive.limit(p.velocity,.74);
                }
            }
            cutSurfaces.clear();cutMomentum.clear();
        }
        private double supportSample=Double.NaN;
        private BeatdownSupport mountSupport;
        private long plantedScene=Long.MIN_VALUE;
        private Vec3 plantedTorso;
        private long plantedTorsoScene=Long.MIN_VALUE;
        private boolean plantedBeatdown(){
            var visual=entity.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL);
            return visual.consuming() && visual.variant()==com.codex.blacklight.combat.ConsumeVariant.BEATDOWN
                    && visual.form()==com.codex.blacklight.power.PowerForm.NONE.ordinal() && plantedScene==visual.started() && !com.codex.blacklight.combat.ConsumeSequence.humanoid(entity);
        }
        void target(Map<Object,Node> nodes,Vec3 now,float partial){
            recoveryClock();
            targetAge=scene.getGameTime()+partial;
            for(var p:parts)p.target(nodes.get(p.key),now.subtract(origin));
            boolean captured=RagdollDrive.captured(entity);
            boolean paired=pairedReceiver();
            boolean live=deadAt<0&&!entity.isDeadOrDying()&&!captured;
            // Fragments inherit the final animated joint anchors at separation.
            if(captured&&(!paired||plantedBeatdown()))return;
            double recovery=paired?1:live?RagdollRecovery.progress(scene.getGameTime()+partial,poseDeadline(),poseDuration()):0;
            // Animated rigs can translate their pivots (breathing, crouching,
            // growth). Restore their current joint anchors, not the hit-frame rig.
            for(var joint:joints){
                var node=nodes.get(joint.child.key);if(node==null)continue;
                joint.a=joint.bindA.lerp(RagdollRecovery.localAnchor(node.pivot,joint.parent.target,joint.parent.targetRotation,joint.parent.scale),recovery);
                joint.b=joint.bindB.lerp(RagdollRecovery.localAnchor(node.pivot,joint.child.target,joint.child.targetRotation,joint.child.scale),recovery);
            }
            // Once recovered, follow this frame's native pose rather than a target
            // from the preceding render. The final frame must match the handoff.
            if(paired||(live&&scene.getGameTime()>=poseDeadline())){
                for(var p:parts){p.old=p.pos=p.target;p.oldRotation.set(p.targetRotation);p.rotation.set(p.targetRotation);p.velocity=Vec3.ZERO;p.spin.zero();}
                if(paired){
                    var visual=entity.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL);
                    if(!com.codex.blacklight.combat.ConsumeSequence.humanoid(entity) && visual.variant()==com.codex.blacklight.combat.ConsumeVariant.BEATDOWN
                            && visual.form()==com.codex.blacklight.power.PowerForm.NONE.ordinal() && visual.elapsed(scene.getGameTime(),partial)>=BEATDOWN_LANDING_TICK){
                        var resting=connectedPose(partial);
                        for(int i=0;i<parts.size();i++)parts.get(i).old=parts.get(i).pos=resting.positions()[i];
                        plantedScene=visual.started();
                    }
                    return;
                }
                // Native animation can itself dip below support. Keep the
                // physical handoff pose on the same skin support as render().
                var connected=connectedPose(1,true);
                for(int i=0;i<parts.size();i++)parts.get(i).old=parts.get(i).pos=connected.positions()[i];
            }
        }
        // A living Brute takes control of its rig early enough to play the push-up.
        // Keep the physical skin/terrain bridge until GETUP actually finishes.
        long poseDeadline(){return bruteGetup?expires-(long)recoveryDuration+com.codex.blacklight.combat.BruteKnockdown.PHYSICS_BLEND_TICKS:expires;}
        double poseDuration(){return bruteGetup?com.codex.blacklight.combat.BruteKnockdown.PHYSICS_BLEND_TICKS:recoveryDuration;}
        /** Keep this live Brute unpowered while down, then meet the server's actual get-up deadline. */
        void recoveryClock(){
            bruteGetup=false;
            if(!(entity instanceof com.codex.blacklight.entity.BlacklightMutant brute)||brute.kind()!=com.codex.blacklight.entity.BlacklightMutant.Kind.BRUTE
                    ||deadAt>=0||entity.isDeadOrDying()||RagdollDrive.captured(entity))return;
            var hit=com.codex.blacklight.registry.ModAttachments.peekData(entity,com.codex.blacklight.registry.ModAttachments.HIT_REACTION,com.codex.blacklight.combat.HitReaction.EMPTY);
            long now=scene.getGameTime();
            if(hit.duration()>0&&hit.kind()==com.codex.blacklight.combat.HitReaction.DOWN){
                bruteRecovery=true;recoveryDuration=com.codex.blacklight.combat.BruteKnockdown.GETUP_TICKS;expires=now+(long)recoveryDuration+1;
            }else if(hit.duration()>0&&hit.kind()==com.codex.blacklight.combat.HitReaction.GETUP){
                bruteRecovery=true;bruteGetup=true;recoveryDuration=hit.duration();expires=hit.started()+hit.duration();
            }else if(bruteRecovery){
                bruteRecovery=false;recoveryDuration=RagdollRecovery.DURATION;expires=RagdollRecovery.releaseDeadline(now,expires,hit.duration()<=0);
            }
        }
        FatalWeaponImpact fatalImpact;
        Vec3 quillRollAxis=Vec3.ZERO,quillLocalUp=Vec3.ZERO;
        long quillRollUntil;
        // Four planted feet can dissipate the initial impulse in the first solver step.
        // Transfer the remainder of a lethal quill's angular impulse across the connected rig.
        void rollAfterQuill(long now){
            if(now>quillRollUntil||quillRollAxis.lengthSqr()<.5)return;
            if(rotate(quillLocalUp,root.rotation).y<.28){quillRollAxis=Vec3.ZERO;return;}
            double speed=Math.min(.50,.13*(quillRollUntil-now+1));
            for(var p:parts){
                p.still=0;
                Vec3 axis=localAngularImpulse(quillRollAxis,p.rotation);
                double shortfall=Math.max(0,speed-vec(p.spin).dot(axis));
                p.spin.add(axis.scale(Math.min(.28,shortfall)).toVector3f());
                Vec3 tangent=quillRollAxis.cross(p.pos.subtract(root.pos));
                if(tangent.lengthSqr()>.001){
                    double current=p.velocity.subtract(root.velocity).dot(tangent)/tangent.lengthSqr();
                    p.velocity=p.velocity.add(RagdollDrive.limit(tangent.scale(Math.max(0,speed-current)),.19));
                }
            }
        }
        boolean landingPlayed,fragmentLanded;
        void landing(Part part) {
            boolean fragment=part==ruptureFragment;
            if ((fragment?fragmentLanded:landingPlayed) || fatalImpact==null || part.landingSpeed<(fragment?.13:.32)
                    || !fragment && part.half.length()<.14) return;
            var floor = part.floorBelow(part.pos,part.extent());
            if (floor.getType()!=HitResult.Type.BLOCK || floor.getDirection()!=net.minecraft.core.Direction.UP) return;
            var point = floor.getLocation();
            var block = scene.getBlockState(floor.getBlockPos());
            if (block.isAir() || !scene.getFluidState(floor.getBlockPos().above()).isEmpty()) return;
            if(fragment){
                fragmentLanded=true;
                scene.playLocalSound(point.x,point.y,point.z,block.getSoundType().getHitSound(),
                        net.minecraft.sounds.SoundSource.NEUTRAL,.18F+.22F*(float)Math.min(1,part.landingSpeed),.91F,false);
                return;
            }
            landingPlayed=true;
            float force=(float)Math.min(1,part.landingSpeed);
            scene.playLocalSound(point.x,point.y,point.z,block.getSoundType().getFallSound(),
                    net.minecraft.sounds.SoundSource.NEUTRAL,.42F+.45F*force,.75F,false);
            var chips=new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,block);
            for(int i=0;i<3+(int)(5*force);i++) {
                double angle=scene.random.nextDouble()*Math.PI*2, speed=.06+.12*force;
                scene.addParticle(chips,point.x+.12*Math.cos(angle),point.y+.04,point.z+.12*Math.sin(angle),
                        speed*Math.cos(angle),.05+.12*force,speed*Math.sin(angle));
            }
        }
        Part pendingPress,crushedTissue;
        Vec3 pressAcross=Vec3.ZERO;
        int rupturedRegion;
        Part ruptureFragment;
        boolean pressed;

        /** The root and its joints survive a torso hit. A limb retains a short, capped stump. */
        boolean rupture(Part struck,Vec3 at,Vec3 travel){
            int region=struck.bind.region;
            boolean limb=region!=32 && struck!=root;
            Vec3 axis=limb?struck.center.subtract(struck.bind.pivot).normalize():at.subtract(struck.center).normalize();
            if(axis.lengthSqr()<.5)axis=travel.scale(-1);
            double low=Double.POSITIVE_INFINITY,high=-low;
            for(var v:struck.mesh){
                double projection=v.p.subtract(struck.center).dot(axis);
                low=Math.min(low,projection);high=Math.max(high,projection);
            }
            if(high-low<.04)return false;
            var cut=new Boundary(struck.center.add(axis.scale(low+(high-low)*(limb?.22:.63))),axis,1,null,"rupture");
            if(parts.size()>=MAX_PARTS)return false;
            var keep=clip(struck,struck.mesh,cut,-1);
            var detached=clip(struck,struck.mesh,cut,1);
            if(keep.size()<12 || detached.size()<12)return false;

            // Stage both sides before mutating the rig. A sleeve and its underlying
            // arm leave together as a single rigid piece with their original UVs.
            var remnants=new LinkedHashMap<Part,List<Vertex>>();
            remnants.put(struck,keep);
            int total=parts.stream().mapToInt(p->p.mesh.size()).sum()-struck.mesh.size()+keep.size()+detached.size();
            for(var layer:parts){
                if(!limb || layer==struck || layer==root || layer.bind.region!=region)continue;
                var remnant=clip(layer,layer.mesh,cut,-1);
                var outer=clip(layer,layer.mesh,cut,1);
                total+=remnant.size()+outer.size()-layer.mesh.size();
                detached.addAll(outer);
                remnants.put(layer,remnant);
            }
            if(total>MAX_VERTICES)return false;
            var fragment=new Part(new Object(),detached,struck.bind);
            fragment.inherit(struck);
            Vec3 away=rotate(axis,struck.rotation);
            Vec3 travelWorld=rotate(travel,struck.rotation);
            Vec3 carry=travelWorld.scale(.33).add(away.scale(.22)).add(0,.11,0);
            fragment.velocity=RagdollDrive.limit(fragment.velocity.scale(.33).add(carry),.82);
            Vec3 tumble=fragment.pos.subtract(struck.pos).cross(carry);
            fragment.spin.add(localAngularImpulse(RagdollDrive.limit(tumble.scale(2.4),.48),fragment.rotation).toVector3f());
            for(var entry:remnants.entrySet()){
                var layer=entry.getKey();
                if(layer!=struck && entry.getValue().size()<12)parts.remove(layer);
                else {layer.mesh.clear();layer.mesh.addAll(entry.getValue());layer.refit();layer.floorContact();}
            }
            fragment.detachedTissue=true;
            fragment.floorContact();
            fragment.old=fragment.pos;
            ruptureFragment=fragment;
            parts.add(fragment);
            struck.mark=StrikeMark.attach(struck,cut.point,axis.scale(-1),FatalWeaponImpact.MUSCLE_BURST,.30);
            rupturedRegion=region;
            appliedCuts++;
            rebuildJoints();separateCuts();
            // A contact dent can already cross the floor before the first physics step.
            var fit=connectedPose(1,true);
            for(int i=0;i<parts.size();i++){
                var part=parts.get(i);Vec3 settled=fit.positions()[i];
                part.old=part.old.add(settled.subtract(part.pos));part.pos=settled;
            }
            return true;
        }

        void pressOnSupport(){
            if(pendingPress==null || pressed)return;
            double floorY=Double.NEGATIVE_INFINITY;
            for(var p:parts){
                if(!p.supported())continue;
                var floor=p.floorBelow(p.pos,p.extent());
                if(floor.getType()==HitResult.Type.BLOCK && floor.getDirection()==net.minecraft.core.Direction.UP)
                    floorY=Math.max(floorY,floor.getLocation().y);
            }
            if(!Double.isFinite(floorY))return;
            boolean small=entity.getBbHeight()<=1 && entity.getBbWidth()<=1;
            for(var p:parts){
                if(small){
                    // Compress the entire small rig about its real support, including the
                    // interpolated pose shown during hitstop. Its feet stay on the block.
                    p.press(.23);
                    p.pos=new Vec3(p.pos.x,floorY+(p.pos.y-floorY)*p.thickness,p.pos.z);
                    p.old=new Vec3(p.old.x,floorY+(p.old.y-floorY)*p.thickness,p.old.z);
                }else if(p==pendingPress || pendingPress.bind.region!=32 && p.bind.region==pendingPress.bind.region){
                    p.press(.34);
                }
            }
            if(small){
                // Seat the deformed skin, including the struck part's dent, as one body.
                double lowest=Double.POSITIVE_INFINITY;
                for(var p:parts)for(var v:p.mesh)
                    lowest=Math.min(lowest,p.pos.y+rotate(v.p.subtract(p.center).scale(p.scale),p.rotation).y);
                double lift=floorY+.001-lowest;
                for(var p:parts){p.pos=p.pos.add(0,lift,0);p.old=p.old.add(0,lift,0);}
            }
            if(!small && pendingPress.mark!=null)crushedTissue=pendingPress;
            rebuildJoints();
            pressed=true;
            pendingPress=null;
        }

        void shedCrushedEdges(){
            var tissue=crushedTissue;
            if(tissue==null)return;
            int index=parts.indexOf(tissue);
            if(index<0){crushedTissue=null;return;}
            // The skeleton can transmit the floor load through a neighbouring limb.
            if(!supportedParts()[index])return;
            crushedTissue=null;
            if(parts.size()+2>MAX_PARTS)return;
            var across=rotate(pressAcross,new Quaternionf(tissue.rotation).conjugate());
            double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
            for(var v:tissue.mesh){
                double d=v.p.subtract(tissue.center).dot(across);
                low=Math.min(low,d);high=Math.max(high,d);
            }
            if(high-low<.12)return;
            var core=tissue.mesh;
            var chips=new ArrayList<Part>();
            int otherVertices=parts.stream().mapToInt(p->p.mesh.size()).sum()-core.size(),chipVertices=0;
            for(int sign:new int[]{-1,1}){
                double edge=sign<0?low+(high-low)*.19:high-(high-low)*.15;
                var plane=new Boundary(tissue.center.add(across.scale(edge)),across.scale(sign),1,null,"compressed-rim");
                var rim=clip(tissue,core,plane,1);var next=clip(tissue,core,plane,-1);
                if(rim.size()<12 || next.size()<12)return;
                chipVertices+=rim.size();
                if(otherVertices+next.size()+chipVertices>MAX_VERTICES)return;
                var chip=new Part(new Object(),rim,tissue.bind);
                chip.inherit(tissue);chip.detachedTissue=true;
                chip.velocity=pressAcross.scale(sign*(sign<0?.33:.25)).add(0,.07,0);
                chip.spin.set(localAngularImpulse(pressAcross.cross(new Vec3(0,1,0)).scale(sign*.22),chip.rotation).toVector3f());
                chip.floorContact();chip.old=chip.pos;chips.add(chip);
                core=next;
            }
            tissue.mesh.clear();tissue.mesh.addAll(core);tissue.refit();parts.addAll(chips);
            separateCuts();
        }

        void fatalContact(FatalWeaponImpact style,Part struck,Vec3 contact,Vec3 direction){
            fatalImpact=style;
            var inverse=new Quaternionf(struck.rotation).conjugate();
            var at=struck.center.add(rotate(contact.subtract(struck.pos),inverse));
            var axis=rotate(direction.normalize(),inverse);
            if(axis.lengthSqr()<.5)return;
            var box=bounds(struck.mesh);
            at=closest(at,box);
            double radius=Math.min(1.6,Math.max(.22,struck.half.length()*1.8));
            // Keep the original cutting mesh for any subsequent swept lash samples.
            // All fragments inherit the same local imprint when a real edge crosses it.
            struck.imprint=new Imprint(style,at,axis,radius);
            if(com.codex.blacklight.combat.PlayerHitEffects.fleshBody(entity) && !com.codex.blacklight.combat.PlayerHitEffects.metalAt(entity,contact))
                struck.mark=StrikeMark.attach(struck,at,axis,style,radius);
            if(style==FatalWeaponImpact.MUSCLE_BURST && struck.mark!=null && rupture(struck,at,axis)){
                InfectedImpactVisuals.rupture(entity,contact,direction,radius);
            }else if(style==FatalWeaponImpact.HAMMER_PRESS){
                pressAcross=direction.cross(new Vec3(0,1,0)).normalize();
                if(pressAcross.lengthSqr()<.5)pressAcross=new Vec3(1,0,0).yRot((float)Math.toRadians(-entity.yBodyRot));
                pendingPress=struck;
                pressOnSupport();
            }else if(style==FatalWeaponImpact.HAMMER_BREAK){
                // Break off the impacted section of this part only. The existing clipper
                // closes the exposed surfaces and hands both fragments to terrain physics.
                var plane=new Boundary(at.lerp(struck.center,.49),axis,1,null,"fatal-impact");
                if(cutAt(plane,0,radius,List.of(),struck)){
                    rebuildJoints();
                    separateCuts();
                }
            }
        }
        void impulse(RagdollImpactPayload hit){
            boolean soft=!RagdollDrive.fullImpact(hit);
            long now=scene.getGameTime(),contactTime=hit.at()<0?now:hit.at();
            freezeUntil=Math.max(freezeUntil,Math.min(now+8,contactTime+Math.clamp(hit.freeze(),0,8)));
            if(!soft){light=false;expires=freezeUntil+hit.ticks();}
            if((hit.fatal()||entity.isDeadOrDying()||hit.severMask()!=0)&&deadAt<0)deadAt=now;
            Vec3 velocity=new Vec3(hit.vx(),hit.vy(),hit.vz());var frame=RagdollDrive.frame(entity,1);
            var style=hit.fatal()&&!hit.refinement()&&fatalImpact==null?FatalWeaponImpact.fromMotion(hit.motion()):null;
            boolean throughBody=hit.fatal()&&hit.motion()==com.codex.blacklight.combat.QuillImpact.HEAVY;
            double transfer=style!=null?style.bodyTransfer:throughBody?.63:soft?.12:.26;
            double turn=style!=null?style.turn:throughBody?.52:0;
            var roll=new Vec3(velocity.z,0,-velocity.x).normalize().scale(turn*Math.min(1,velocity.length()));
            if (style!=null) {
                Vec3 hinge=new Vec3(velocity.z,0,-velocity.x).normalize();
                if (style==FatalWeaponImpact.WHIP) {
                    double side=0;
                    if (!hit.wounds().isEmpty()) {
                        var wound=hit.wounds().get(0);
                        side=RagdollDrive.transform(frame,wound.point()).subtract(root.pos).dot(hinge);
                    }
                    if (Math.abs(side)<.015) side=(entity.getId()%2==0?1:-1);
                    hinge=hinge.scale(.34).add(0,Math.copySign(.94,side),0);
                } else if (style==FatalWeaponImpact.HAMMER || style==FatalWeaponImpact.HAMMER_BREAK || style==FatalWeaponImpact.HAMMER_PRESS) {
                    hinge=hinge.scale(-1);
                }
                roll=hinge.scale(style.turn*Math.min(1,velocity.length()));
            }
            if(throughBody&&velocity.horizontalDistanceSqr()>.001){
                quillRollAxis=roll.normalize();
                quillLocalUp=rotate(new Vec3(0,1,0),new Quaternionf(root.rotation).conjugate());
                quillRollUntil=scene.getGameTime()+10;
            }
            for(var p:parts){
                p.still=0;
                p.velocity=p.velocity.add(velocity.scale(transfer));
                if(turn>0){
                    // Start the connected skeleton rolling as one body, before constraints run.
                    p.velocity=p.velocity.add(RagdollDrive.limit(roll.cross(p.pos.subtract(root.pos)),.6));
                    p.spin.add(localAngularImpulse(roll,p.rotation).toVector3f());
                }
            }
            for(var wound:hit.wounds()){
                Vec3 at=RagdollDrive.transform(frame,wound.point());Part struck=null;double distance=Double.POSITIVE_INFINITY;
                for(var p:parts){
                    Vec3 local=rotate(at.subtract(p.pos),new Quaternionf(p.rotation).conjugate());
                    double d=local.distanceToSqr(closest(local,new AABB(p.half.scale(-1),p.half)));
                    // Anatomical skin beats a nearby oversized decorative layer.
                    if(wound.region()!=32 && p.bind.region!=wound.region())d+=.09;
                    if(d<distance){distance=d;struck=p;}
                }
                if(struck==null)continue;
                Vec3 kick=velocity;
                for(var impulse:hit.cuts())if(impulse.region()==wound.region()){kick=new Vec3(impulse.x(),impulse.y(),impulse.z());break;}
                kick=RagdollDrive.limit(kick.scale(soft?.3:.65),.9);struck.velocity=struck.velocity.add(kick);
                Vec3 angular=at.subtract(struck.pos).cross(kick).scale(.7/Math.max(.04,struck.half.lengthSqr()));
                struck.spin.add(localAngularImpulse(RagdollDrive.limit(angular,.45),struck.rotation).toVector3f());
                if(hit.motion()==com.codex.blacklight.combat.ImpactProfile.HAMMER_BUCKLE && !hit.fatal()){
                    // Push the struck limb around its attached joint before the ordinary recovery.
                    Vec3 lever=struck.pos.subtract(at);
                    Vec3 bend=lever.cross(kick).add(kick.z*.12,0,-kick.x*.12);
                    struck.spin.add(localAngularImpulse(RagdollDrive.limit(bend.scale(2.2),.65),struck.rotation).toVector3f());
                    struck.velocity=struck.velocity.add(0,-.23,0);
                }
                if(style!=null&&fatalImpact==null){
                    // Cuts carry part of the world-space launch. Recover the original
                    // contact travel independently of the launch of the complete body.
                    Vec3 travel=kick;
                    for(var sample:hit.cuts())if(sample.region()==wound.region()){
                        travel=new Vec3(sample.x(),sample.y(),sample.z()).subtract(velocity.scale(.5));break;
                    }
                    if(travel.lengthSqr()<1e-6)travel=struck.pos.subtract(at);
                    if(style==FatalWeaponImpact.HAMMER_BREAK&&velocity.y<-.2)travel=travel.normalize().add(0,-.7,0);
                    fatalContact(style,struck,at,travel);
                }
            }
        }
        QuillOrgan quill;
        long quillAge=QuillOrgan.FIRST_DRAW_TICK;
        Vec3 quillStart;
        List<Part> quillParts=List.of();

        Vec3 quillSink(float partial) { return quillSink(quill.age(),partial); }

        Vec3 quillSink(long age,float partial) {
            double follow=QuillTendrils.ease((age+partial-QuillOrgan.FIRST_DRAW_TICK)/6);
            return quillStart.lerp(quill.getPosition(partial),follow);
        }

        Vec3 physicalWound(Vec3 wound,float partial) {
            var anchor=parts.stream().min(Comparator.comparingDouble(p->p.target.distanceToSqr(wound))).orElse(root);
            var local=rotate(wound.subtract(anchor.target),new Quaternionf(anchor.targetRotation).conjugate());
            var position=anchor.old.lerp(anchor.pos,partial);
            var turn=new Quaternionf(anchor.oldRotation).slerp(anchor.rotation,partial);
            return position.add(rotate(local,turn));
        }

        void burst(QuillOrgan organ) {
            quill=organ;
            var posed=connectedPose(1);
            for(int i=0;i<parts.size();i++)parts.get(i).pos=posed.positions()[i];
            quillStart=physicalWound(organ.position(),1);
            var inside=root.pos.lerp(quillStart,.32);
            double kick=net.minecraft.util.Mth.clamp(Math.sqrt(entity.getBbWidth()*entity.getBbHeight())*.50,.22,.88);
            // Break major anatomical joints only. Armour, small bones and decorations stay on their parent.
            for(var joint:joints){
                if(joint.parent.bind.region!=joint.child.bind.region || joint.parent==root && joint.child.volume()>=root.volume()*.06)
                    joint.severed=true;
            }
            var islands=connectedPose(1).components();
            var anchors=new LinkedHashMap<Integer,Part>();
            for(int i=0;i<parts.size();i++){
                var part=parts.get(i);
                anchors.merge(islands[i],part,(a,b)->a.volume()>=b.volume()?a:b);
                var radial=part.pos.subtract(inside);
                var push=radial.multiply(1.3,.48,1.3).normalize().scale(part==root?kick*.28:kick);
                push=push.add(0,kick*.48,0);
                part.velocity=part.velocity.scale(.48).add(push);
                Vec3 lever=rotate(part.center.subtract(part.bind.pivot),part.rotation);
                var twist=lever.cross(push).scale(2.8/Math.max(.15,lever.length()));
                part.spin.add(localAngularImpulse(RagdollDrive.limit(twist,.84),part.rotation).toVector3f());
                part.still=0;
            }
            if(organ.age()<=QuillOrgan.FIRST_DRAW_TICK+1)
                WoundBlood.quillBurst(entity,quillStart,inside,kick);
            quillParts=anchors.values().stream().sorted(Comparator.comparingDouble(Part::volume).reversed()).limit(8).toList();
            // Joining visibility mid-extraction starts at the synchronized age.
            advanceQuill();
        }

        void advanceQuill(){
            long age=Math.min(QuillOrgan.RELEASE_TICK,quill.age());
            while(quillAge<age){
                quillAge++;
                quillStep(scene.getGameTime()-(age-quillAge),quillAge);
            }
        }

        void quillStep(long now,long elapsed) {
            for(var p:parts){p.old=p.pos;p.oldRotation.set(p.rotation);}
            for(int sub=0;sub<2;sub++){
                float fraction=(sub+1)*.5F;
                var sink=quillSink(elapsed-1,fraction);
                for(var p:parts){
                    // Proximal tissue draws in first. Give the detached limbs two free physics ticks of travel.
                    double distance=Math.min(1,p.center.distanceTo(root.center)/Math.max(.15,entity.getBbHeight()));
                    double age=elapsed-1+fraction;
                    p.flow=(float)QuillTendrils.ease((age-QuillOrgan.FIRST_DRAW_TICK-distance)/(QuillOrgan.RELEASE_TICK-QuillOrgan.FIRST_DRAW_TICK-1.4-distance));
                    p.scale=Math.max(.012F,1-.988F*p.flow);
                    if(p.flow>0){
                        var delta=sink.subtract(p.pos);
                        var desired=RagdollDrive.limit(delta.scale(.50+1.30*p.flow),1.65);
                        p.velocity=p.velocity.lerp(desired,.15+.80*p.flow);
                        p.spin.mul(1-.30F*p.flow);
                    }
                    p.step();
                }
                for(int i=0;i<4;i++)for(var joint:joints){joint.solve();joint.constrainHinge();}
                for(var p:parts)p.floorContact();
                var fit=connectedPose(1,true);
                for(int i=0;i<parts.size();i++)parts.get(i).pos=fit.positions()[i];
            }
            recordPoses(now);
        }
        void tick(){
            if(quill!=null){advanceQuill();return;}
            drainConsumeCuts();
            recoveryClock();
            long now=scene.getGameTime();boolean captured=RagdollDrive.captured(entity),dead=deadAt>=0||entity.isDeadOrDying();
            if(dead&&deadAt<0)deadAt=now;
            if(captured){expires=now+16;deadAt=-1;dead=false;}
            if(captured)consumeFracture();
            for(var p:parts){p.old=p.pos;p.oldRotation.set(p.rotation);}
            if(captured&&pairedReceiver()&&!plantedBeatdown()){
                for(var p:parts){
                    p.flow=RagdollDrive.partFlow(entity,p.bind.region,1);
                    p.scale=Math.max(.015F,1-.985F*p.flow);
                }
                recordPoses(now);
                return;
            }
            // A hit captured after this tick first simulates at now + 1. Include
            // the deadline so a one-tick hitstop actually holds one physics step.
            if(now<=freezeUntil){for(var p:parts)p.floorContact();recordPoses(now);return;}
            if(dead&&!captured){
                pressOnSupport();
                rollAfterQuill(now);
            }
            if(dead&&!captured&&parts.stream().allMatch(p->p.still>=RagdollConstraintMotion.SLEEP_STEPS)){
                var supported=supportedParts();boolean asleep=true;
                for(int i=0;i<parts.size();i++)if(!supported[i]){parts.get(i).still=0;asleep=false;}
                if(asleep){recordPoses(now);return;}
            }
            float flow=RagdollDrive.flow(entity,1),power=captured?RagdollDrive.power(entity):!dead&&light?.32F:0;
            boolean freeFragments=captured&&consumeSplit==entity.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL).started();
            Vec3 wrist=freeFragments?actorWrist(1):null;
            boolean recovering=!captured&&!dead;
            boolean fresh=now-targetAge<3 && !plantedBeatdown();Vec3 sink=captured?RagdollDrive.sink(entity,1):Vec3.ZERO;
            for(int sub=0;sub<2;sub++){
                for(var p:parts){
                    p.flow=captured?RagdollDrive.partFlow(entity,p.bind.region,1):0;p.scale=Math.max(.015F,1-.985F*p.flow);
                    if(captured||!dead)p.still=0;
                    if(fresh&&power>0&&!freeFragments){
                        p.velocity=p.velocity.add(RagdollDrive.limit(p.target.subtract(p.pos),.5).scale(power*.2));
                        p.rotation.slerp(p.targetRotation,power*.13F);
                    }
                    // Leave time to see the loose component turn and hit the floor.
                    // Its material starts disappearing before its centre chases the chest.
                    float pull=freeFragments?RagdollDrive.smooth((p.flow-.40F)/.60F):p.flow;
                    if(pull>0)p.velocity=RagdollDrive.pull(p.pos,p.velocity,sink,pull,.5);
                    p.stepOrigin=p.pos;p.stepRotation.set(p.rotation);p.step();
                    if (dead && !captured) landing(p);
                    p.predictedPosition=p.pos;p.predictedRotation.set(p.rotation);
                }
                for(int i=0;i<6;i++){
                    for(var joint:joints)joint.solve();
                    for(var joint:joints)joint.constrainHinge();
                    if(wrist!=null&&heldFragment!=null&&heldFragment.flow<.55F){
                        var arm=rotate(gripOnSkin.subtract(heldFragment.center).scale(heldFragment.scale),heldFragment.rotation);
                        var gap=wrist.subtract(heldFragment.pos.add(arm));
                        heldFragment.move(RagdollDrive.limit(gap,.12).scale(.5));
                    }else if(!freeFragments&&fresh&&captured&&RagdollDrive.attached(entity)&&flow==0){
                        Part grip=root;for(var p:parts)if(p.bind.region==RagdollDrive.gripRegion(entity)){grip=p;break;}
                        grip.move(RagdollDrive.limit(grip.target.subtract(grip.pos),.25).scale(.4));
                    }
                }
                if(recovering&&fresh){
                    float blend=RagdollRecovery.step(now-1+sub*.5,now-.5+sub*.5,poseDeadline(),poseDuration());
                    for(var p:parts){
                        p.move(p.target.subtract(p.pos).scale(blend));p.rotation.slerp(p.targetRotation,blend);
                        p.velocity=p.velocity.scale(1-blend);p.spin.mul(1-blend);
                    }
                }
                for(var joint:joints)joint.constrainHinge();
                for(var p:parts)p.floorContact();
                // Collision may refuse an individual joint correction. Close the
                // surviving skeleton as a component, then lift that whole component
                // off its support; lifting each bone independently tears living skin.
                var connected=connectedPose(1,true);
                for(int i=0;i<parts.size();i++)parts.get(i).pos=connected.positions()[i];
                if(dead&&!captured||plantedBeatdown()){
                    // Positional constraints must also reject their stored
                    // inward velocity. Otherwise suspended limbs accumulate
                    // gravity every step while the skeleton lifts them back.
                    var supported=supportedParts();
                    for(int i=0;i<parts.size();i++)parts.get(i).finishConstraints(supported[i]);
                }
            }
            if(dead && !captured)shedCrushedEdges();
            recordPoses(now);
        }
        boolean[] supportedParts(){
            var ids=new IdentityHashMap<Part,Integer>();boolean[] contacts=new boolean[parts.size()];
            for(int i=0;i<parts.size();i++){var p=parts.get(i);ids.put(p,i);contacts[i]=p.supported();}
            var links=new ArrayList<RagdollConstraintMotion.Link>();
            for(var joint:joints)if(!joint.severed)links.add(new RagdollConstraintMotion.Link(ids.get(joint.parent),ids.get(joint.child)));
            return RagdollConstraintMotion.supported(contacts,links);
        }
        private long carriedScene=Long.MIN_VALUE;
        private double carriedSeen;
        private Vec3 carriedEntryOffset;
        private Vec3[] carriedEntryParts;
        private void carryVisibleSkin(Vec3[] points,Vec3 center,long sceneStart,float partial,float flow){
            int body=parts.indexOf(root);
            if(body<0)return;
            Vec3 raw=points[body];
            double clock=scene.getGameTime()+partial;
            if(carriedScene!=sceneStart||carriedEntryParts==null||carriedEntryParts.length!=parts.size()){
                carriedScene=sceneStart;carriedSeen=clock;
                Vec3 previous=root.renderedPosition!=null&&clock-root.renderedAt<3?root.renderedPosition:raw;
                carriedEntryOffset=previous.subtract(center);
                carriedEntryParts=new Vec3[parts.size()];
                for(int i=0;i<parts.size();i++){
                    var part=parts.get(i);
                    carriedEntryParts[i]=part.renderedPosition!=null&&clock-part.renderedAt<3
                            ?part.renderedPosition.subtract(previous):points[i].subtract(raw);
                }
            }
            float blend=RagdollDrive.smooth((float)((clock-carriedSeen)/4));
            Vec3 anchor=center.add(carriedEntryOffset.scale(1-blend));
            double span=1-.97*com.codex.blacklight.combat.MobileConsumeCarry.contraction(flow);
            for(int i=0;i<parts.size();i++){
                Vec3 relative=points[i].subtract(raw).scale(span);
                points[i]=anchor.add(carriedEntryParts[i].lerp(relative,blend));
            }
        }

        RagdollJointProjection.Result connectedPose(float partial){
            boolean paired=pairedReceiver();
            RagdollJointProjection.Result result;
            if(paired&&!plantedBeatdown()){
                var positions=new Vec3[parts.size()];
                for(int i=0;i<parts.size();i++)positions[i]=parts.get(i).target;
                var visual=entity.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL);
                var form=com.codex.blacklight.power.PowerForm.values()[visual.form()];
                float split=com.codex.blacklight.combat.ConsumeSequence.splitSeconds(form,false,visual.variant());
                ConsumeImpactResponse.windup(visual,split-visual.elapsed(scene.getGameTime(),partial)/20F,parts,positions);
                if(scene.getEntity(visual.consumerId()) instanceof LivingEntity carrier)
                    trauma.response.apply(entity,carrier,visual,parts,positions,partial);
                double weight=com.codex.blacklight.combat.ConsumePlacementTrack.groundWeight(form,visual.variant(),visual.motionProgress(scene.getGameTime(),partial));
                weight*=1-RagdollDrive.smooth((RagdollDrive.flow(entity,partial)-.15F)/.37F);
                if(weight>0){
                    if(visual.variant()==com.codex.blacklight.combat.ConsumeVariant.HEAD_STOMP &&
                            com.codex.blacklight.combat.ConsumePlacementTrack.villagerReceiver(entity) && weight>.95){
                        Vec3 torso=root.target;
                        if(plantedTorsoScene!=visual.started()){
                            plantedTorsoScene=visual.started();
                            plantedTorso=torso;
                        }
                        Vec3 drift=new Vec3(plantedTorso.x-torso.x,0,plantedTorso.z-torso.z);
                        for(int i=0;i<positions.length;i++)positions[i]=positions[i].add(drift);
                    }
                    double dy=supportedSkin(positions,partial)*weight;
                    for(int i=0;i<positions.length;i++)positions[i]=positions[i].add(0,dy,0);
                }
                result=new RagdollJointProjection.Result(positions,new int[parts.size()]);
            }else{
                result=connectedPose(partial,false);
                if(heldFragment!=null && heldFragment.consumeHalf==1 && heldFragment.flow<.55F){
                    var hand=actorWrist(partial);
                    if(hand!=null){
                        int index=parts.indexOf(heldFragment);
                        if(index>=0){
                            var rotation=new Quaternionf(heldFragment.oldRotation).slerp(heldFragment.rotation,partial);
                            Vec3 site=result.positions()[index].add(rotate(gripOnSkin.subtract(heldFragment.center)
                                    .scale(heldFragment.scale),rotation));
                            Vec3 offset=RagdollDrive.limit(hand.subtract(site),1.5);
                            for(int i=0;i<parts.size();i++)if(parts.get(i).consumeHalf==1)
                                result.positions()[i]=result.positions()[i].add(offset);
                        }
                    }
                }
            }
            float flow=RagdollDrive.flow(entity,partial);
            var mobileVisual=entity.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL);
            if(mobileVisual.stage()==com.codex.blacklight.combat.ConsumeVisual.MOBILE_CONSUME
                    &&scene.getEntity(mobileVisual.consumerId()) instanceof LivingEntity owner){
                Vec3 center=com.codex.blacklight.combat.MobileConsumeCarry.center(entity,owner,mobileVisual,partial);
                if(center!=null){
                    if(ChestConsumeTendrils.matches(mobileVisual)){
                        // FIG 531712 gives the chest punch a one-second return to the moving Spine_3.
                        float seconds=mobileVisual.elapsed(scene.getGameTime(),partial)/20F-55F/30;
                        Vec3 spine=ChestConsumeTendrils.actorPoints(owner,partial)[0];
                        center=center.lerp(spine,RagdollDrive.smooth(seconds));
                    }
                    carryVisibleSkin(result.positions(),center,mobileVisual.started(),partial,flow);
                    return result;
                }
            }
            if(flow>0){
                // Keep the socket and skin together as the final visible tissue returns.
                var sink=RagdollDrive.sink(entity,partial);
                // The puppet stays at the authored contact while its skin contracts.
                // Only the remaining tissue crosses the gap to the consumer.
                float travel=RagdollDrive.smooth((flow-.52F)/.48F);
                for(int i=0;i<result.positions().length;i++)
                    result.positions()[i]=result.positions()[i].lerp(sink,travel);
            }
            return result;
        }
        RagdollJointProjection.Result connectedPose(float partial,boolean collision){
            var ids=new IdentityHashMap<Part,Integer>();var poses=new ArrayList<RagdollJointProjection.Pose>();
            for(var p:parts){ids.put(p,poses.size());poses.add(new RagdollJointProjection.Pose(p.old.lerp(p.pos,partial),new Quaternionf(p.oldRotation).slerp(p.rotation,partial),p.scale));}
            var links=new ArrayList<RagdollJointProjection.Link>();
            for(var j:joints)if(!j.severed)links.add(new RagdollJointProjection.Link(ids.get(j.parent),ids.get(j.child),j.a,j.b));
            var result=RagdollJointProjection.close(poses,links);
            // Resolve actual connected skin before choosing vertical support.
            // A shallow toe/slab overlap should not lift every grounded limb.
            var skins=new LinkedHashMap<Integer,List<Vec3>>();
            for(int i=0;i<parts.size();i++){
                var p=parts.get(i);var skin=skins.computeIfAbsent(result.components()[i],key->new ArrayList<>());
                for(var v:p.mesh)skin.add(result.positions()[i].add(rotate(p.skinPoint(v).subtract(p.center).scale(p.scale),poses.get(i).rotation())));
            }
            var fallback=new HashSet<Integer>();
            for(var entry:skins.entrySet()){
                var skin=entry.getValue();if(skin.isEmpty())continue;
                double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;
                for(var p:skin){minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);minZ=Math.min(minZ,p.z);maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);maxZ=Math.max(maxZ,p.z);}
                var box=new AABB(minX,minY,minZ,maxX,maxY,maxZ);
                if((box.getXsize()+2)*(box.getYsize()+2)*(box.getZsize()+2)>512){fallback.add(entry.getKey());continue;}
                var terrain=new ArrayList<AABB>();
                // Include obstacles that a bounded escape could move the skin into.
                for(var shape:scene.getBlockCollisions(null,box.inflate(.75))){terrain.addAll(shape.toAabbs());if(terrain.size()>256)break;}
                if(terrain.size()>256){fallback.add(entry.getKey());continue;}
                var shift=RagdollTerrainSupport.settle(skin,terrain);
                if(shift.lengthSqr()>0)for(int i=0;i<parts.size();i++)if(result.components()[i]==entry.getKey())result.positions()[i]=result.positions()[i].add(shift);
            }
            // Preserve the bounded broad support path for exceptionally large rigs.
            if(!fallback.isEmpty()){
                double[] lift=new double[parts.size()];
                for(int i=0;i<parts.size();i++)if(fallback.contains(result.components()[i])){
                    var p=parts.get(i);var turn=poses.get(i).rotation();var at=result.positions()[i];var floor=p.floorBelow(at,p.extent(turn));
                    if(floor.getType()==HitResult.Type.BLOCK&&floor.getDirection()==net.minecraft.core.Direction.UP)
                        lift[i]=collision?Math.max(0,floor.getLocation().y+.001+p.extent(turn).y-at.y):meshFloorLift(p.mesh,p::skinPoint,p.center,turn,p.scale,at.y,floor.getLocation().y);
                    lift[i]=Math.max(lift[i],p.terrainSupport(at,turn));
                }
                result.lift(lift);
            }
            return result;
        }
        void recordPoses(long now){
            poseTime=now;for(var p:parts)p.poses.record(now,p.pos,p.rotation,p.scale);
            int before=appliedCuts;
            for(var it=waitingCuts.iterator();it.hasNext();){var hit=it.next();if(lastSample(hit)<=poseTime){it.remove();applyCut(hit);}}
            if(appliedCuts!=before){rebuildJoints();separateCuts();}
        }
        void render(Matrix4f matrix,MultiBufferSource buffers,Vec3 offset,float partial){
            var receiver=buffers instanceof ChestConsumeReceiver coat?coat:null;
            if(receiver!=null)receiver.bindSkin(materialBind);
            var normalMatrix=new Matrix3f(matrix).invert().transpose();
            int hurtV=OverlayTexture.v(RagdollDrive.hurtFlash(entity));
            double clock=scene.getGameTime()+partial;
            for(var p:parts)if(p.compression!=null)p.compression.strength=p.compression.weightAt(clock-p.compression.started);
            var connected=connectedPose(partial);
            var phase=RagdollDrive.captured(entity)?ConsumeFracture.phase(entity,partial):null;
            if(phase!=null)trauma.contact(entity,entity.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL),phase,parts,connected.positions(),partial);
            var recoil=quill==null?ConsumeBodyImpact.receiver(entity,entity.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL),partial):ConsumeBodyImpact.quill(entity,quill,partial);
            var roots=new LinkedHashMap<Integer,ConsumeTendrils.Attachment>();
            for(int i=0;i<parts.size();i++){var p=parts.get(i);
                if(p.flow>.995F)continue;
                var turn=new Quaternionf(p.oldRotation).slerp(p.rotation,partial);var at=connected.positions()[i];
                if(recoil!=null){
                    at=recoil.point(at);
                    turn.premul(recoil.rotation);
                }
                p.renderedPosition=at;p.renderedRotation=new Quaternionf(turn);p.renderedPartial=partial;p.renderedAt=scene.getGameTime();
                int light=LevelRenderer.getLightColor(scene,BlockPos.containing(at));
                var mark=p.impactMark();
                var skins=new ArrayList<List<Vertex>>();skins.add(p.visibleSkin());skins.addAll(p.incisions.values());skins.add(mark);
                boolean audit=ConsumeSurfaceResponse.observer!=null&&p.compression!=null&&p.compression.mode()!=null;
                int submitted=0,moved=0;double squared=0,peak=0;Vec3 peakOffset=Vec3.ZERO;
                if(!trauma.hides(p))for(var skin:skins)for(var v:skin){
                    Vec3 rest=skin==mark || p.imprint==null?v.p:p.imprint.apply(v.p);
                    Vec3 posed=p.compression==null?rest:p.compression.point(rest);
                    if(audit&&skin==skins.get(0)){
                        Vec3 delta=posed.subtract(rest).scale(p.scale);double distance=delta.lengthSqr();
                        submitted++;squared+=distance;if(distance>1e-8)moved++;
                        if(distance>peak){peak=distance;peakOffset=rotate(delta,turn);}
                    }
                    Vec3 point=transform(matrix,at.add(rotate(posed.subtract(p.center).scale(p.scale),turn)).subtract(offset));
                    Vec3 surfaceNormal=p.compression==null?v.n:p.compression.normal(rest,v.n);
                    Vec3 normal=vec(normalMatrix.transform(rotate(surfaceNormal,turn).toVector3f())).normalize();
                    int color=p.compression==null?v.color:p.compression.shade(posed.distanceTo(rest),v.color);
                    if(p.flow>0){int r=(color>>>16)&255,g=(color>>>8)&255,b=color&255;color=(color&0xff000000)|((int)(r*(1-p.flow*.45F))<<16)|((int)(g*(1-p.flow*.85F))<<8)|(int)(b*(1-p.flow*.8F));}
                    var out=receiver==null?buffers.getBuffer(v.type):receiver.skinBuffer(v.type,p.bind.region,v.p);
                    v.emit(out,point,normal,color,OverlayTexture.pack(v.overlay&0xffff,hurtV),light);
                }
                if(audit&&submitted>0)ConsumeSurfaceResponse.observer.accept(new ConsumeSurfaceResponse.Sample(entity.getId(),p.compression.actor,
                        p.compression.scene,p.compression.mode().name(),p.bind.region(),clock-p.compression.started,
                        submitted,moved,Math.sqrt(peak),Math.sqrt(squared/submitted),peakOffset));
                if(phase!=null&&phase.opening()>0){
                    double radius=Math.max(.03,p.half.length()*.35*p.scale);
                    var previous=roots.get(p.bind.region);
                    if(previous==null||previous.radius()<radius)
                        roots.put(p.bind.region,new ConsumeTendrils.Attachment(at.subtract(offset),radius));
                }
            }
            if(phase!=null){
                var wound=trauma.struckPoint(parts,connected.positions(),partial);
                if(wound!=null){
                    if(!trauma.groundAttachments())roots.clear();
                    roots.put(0,new ConsumeTendrils.Attachment(wound.subtract(offset),.16*(1-phase.returnFlow())));}
                trauma.fragments(entity,scene.getGameTime()+partial,phase.returnFlow(),RagdollDrive.sink(entity,partial),buffers,matrix,normalMatrix,offset,LevelRenderer.getLightColor(scene,entity.blockPosition()));
                trauma.gatheringFragments(roots,offset,phase.returnFlow());
            }
            if(!roots.isEmpty()){
                var stack=new PoseStack();
                stack.last().pose().set(matrix);
                stack.last().normal().set(normalMatrix);
                ConsumeAbsorptionFx.drawPosed(stack,buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE)),
                        LevelRenderer.getLightColor(scene,entity.blockPosition()),List.copyOf(roots.values()),
                        RagdollDrive.sink(entity,partial).subtract(offset),phase,entity.getId(),trauma.groundAttachments());
            }
        }
    }
    /** A sever emitter owns a real cap, not a pre-cut bone center. Descendants
     * retain the boundary key, so later claw cuts keep the original wound attached. */
    static List<WoundBlood.Anchor> sectionAnchors(LivingEntity entity,int region,String key,int side){
        var body=BODIES.get(entity);if(body==null)return List.of();
        Part selected=null;Vec3 local=null,normal=null;double best=0;
        for(int pass=0;pass<2&&selected==null;pass++)for(var part:body.parts){
            if(part.flow>.95F||pass==0&&part.bind.region!=region)continue;
            Boundary boundary=null;for(var b:part.boundaries)if(b.key.equals(key)&&b.sign==side){boundary=b;break;}
            if(boundary==null)continue;
            for(int i=0;i+3<part.mesh.size();i+=4){
                var a=part.mesh.get(i);if(a.color!=0xff70231f)continue;
                for(int triangle=1;triangle<3;triangle++){
                    var b=part.mesh.get(i+triangle);var c=part.mesh.get(i+triangle+1);
                    if(b.color!=a.color||c.color!=a.color)continue;
                    var point=a.p.add(b.p).add(c.p).scale(1.0/3);
                    double area=b.p.subtract(a.p).cross(c.p.subtract(a.p)).lengthSqr()*Math.pow(part.scale,4);
                    if(area<=best||Math.abs(boundary.distance(point))>.002)continue;
                    best=area;selected=part;local=point;normal=a.n;
                }
            }
        }
        if(selected==null)return List.of();var part=selected;
        var offset=rotate(local.subtract(part.center).scale(part.scale),part.rotation);
        var at=body.connectedPose(1).positions()[body.parts.indexOf(part)].add(offset);
        var velocity=fragmentVelocity(part.velocity,vec(part.spin),local.subtract(part.center).scale(part.scale),part.rotation);
        return List.of(new WoundBlood.Anchor(at,rotate(normal,part.rotation),velocity));
    }
    static List<WoundBlood.Anchor> woundAnchors(LivingEntity e,Vec3 point,Vec3 direction){
        var body=BODIES.get(e);if(body==null)return List.of();
        Vec3 bindAt=RagdollDrive.transform(body.woundFrame,point),normal=rotate(direction,body.woundFrame.getUnnormalizedRotation(new Quaternionf()).normalize());
        Part nearest=null;double best=Double.POSITIVE_INFINITY;
        // Bind against the capture pose, then follow its actual detached rigid body.
        for(var p:body.parts){double d=bindAt.distanceToSqr(closest(bindAt,new AABB(p.center.subtract(p.half),p.center.add(p.half))))+(p.contains(bindAt)?0:.05);if(d<best){best=d;nearest=p;}}
        if(nearest==null)return List.of();var p=nearest;
        return List.of(new WoundBlood.Anchor(p.pos.add(rotate(bindAt.subtract(p.center).scale(p.scale),p.rotation)),rotate(normal,p.rotation),p.velocity));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        reset();var mc=Minecraft.getInstance();if(scene==null||mc.isPaused())return;long now=scene.getGameTime();
        for(var e:new ArrayList<>(NATIVE_FATAL.keySet()))resumeNativeFatal(e);
        PENDING.entrySet().removeIf(e->e.getKey().isRemoved()||now-e.getValue().at>40);
        COOLDOWN.entrySet().removeIf(e->now>=e.getValue());
        RELEASES.entrySet().removeIf(e->e.getKey().isRemoved()||RagdollDrive.captured(e.getKey())||now-e.getValue().at>=8);
        var iterator=BODIES.values().iterator();
        while(iterator.hasNext()){
            var b=iterator.next();boolean captured=RagdollDrive.captured(b.entity);
            if(b.quill!=null && (b.quill.isRemoved() || b.quill.phase()!=QuillOrgan.EXTRACT)){
                iterator.remove();
                continue;
            }
            if(b.entity.isAlive()&&!b.entity.isRemoved()&&b.deadAt<0&&!captured&&now>b.expires+2&&now-b.targetAge<3){
                var at=b.root.renderedPosition;
                if(at!=null){var offset=at.subtract(b.root.target);if(offset.lengthSqr()>1e-8&&offset.lengthSqr()<=.25*.25)RELEASES.put(b.entity,new Release(offset,now));}
            }
            if(b.entity.level()!=scene||b.entity.isInvisible()||mc.player==null||mc.player.position().distanceToSqr(b.root.pos)>64*64
                    ||!scene.hasChunkAt(BlockPos.containing(b.root.pos))||b.deadAt>=0&&now-b.deadAt>LIFETIME
                    ||b.deadAt<0&&!b.entity.isDeadOrDying()&&!captured&&(b.entity.isRemoved()||now>b.expires+2)
                    ||!captured&&now-b.started>180){iterator.remove();continue;}
            b.tick();
        }
    }
    @SubscribeEvent public static void corpses(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        for(var b:BODIES.values())if(b.quill==null&&b.entity.isRemoved()&&b.deadAt>=0&&b.entity.level()==mc.level)
            b.render(event.getPoseStack().last().pose(),mc.renderBuffers().bufferSource(),event.getCamera().getPosition(),event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }
    @EventBusSubscriber(modid="blacklight",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event){
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->invalidateModels());
        }
    }
}
