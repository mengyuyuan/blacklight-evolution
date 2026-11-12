package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.animation.VictimModelRoot;
import com.codex.blacklight.client.animation.RagdollDrive;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.network.RagdollImpactPayload;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Complete six-body quadruped: horizontal torso, head and four articulated legs.
 * Captures the animated skin once. Only server-confirmed sharp contacts break joints or slice skin.
 * All motion after capture is cosmetic; entity authority, health and drops stay on the server. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class PigRagdoll {
    private static final int LIFETIME=100, MAX_BODIES=24, MAX_PIECES=144;
    private static final String[] PARTS={"body","head","right_front_leg","left_front_leg","right_hind_leg","left_hind_leg"};
    private static final int[] BITS={32,1,2,4,8,16};
    private static final Map<LivingEntity,Pending> PENDING=new LinkedHashMap<>();
    private static final Map<LivingEntity,Body> BODIES=new LinkedHashMap<>();
    private static ClientLevel scene;
    private static final Map<LivingEntity,PoseSample> HISTORY=new WeakHashMap<>();
    private record PoseSample(double time,Map<Integer,Vec3> points,Map<Integer,Vec3> velocities) {}
    private record Pending(RagdollImpactPayload hit,long received) {}
    private PigRagdoll() {}
    /** Transfer ownership to the mesh cutter without leaving a duplicate whole corpse. */
    public static void release(LivingEntity e){BODIES.remove(e);PENDING.remove(e);HISTORY.remove(e);}

    private static boolean available(LivingEntity e){
        var capture=e.getData(ModAttachments.CONSUME_VISUAL);
        return !e.isInvisible()&&!e.isBaby()&&!e.isPassenger()&&!e.isSleeping()
                &&(!capture.active()||capture.stage()==ConsumeVisual.FLIGHT||RagdollDrive.captured(e));
    }
    public static boolean active(LivingEntity e){
        if(!(e instanceof Pig)||!RagdollDrive.supported(e)||!available(e))return false;
        var pending=PENDING.get(e);
        return BODIES.containsKey(e)||pending!=null||RagdollDrive.captured(e);
    }
    public static void accept(LivingEntity entity,RagdollImpactPayload hit){
        resetScene();if(scene==null||!available(entity))return;
        if(hit.severMask()!=0)entity.setData(ModAttachments.DISMEMBER,entity.getData(ModAttachments.DISMEMBER)|(hit.severMask()&63));
        var body=BODIES.get(entity);
        if(body==null&&!RagdollDrive.fullImpact(hit))return;
        if(body!=null){
            // A later lethal cut must recapture the connected skin before it disappears.
            if((hit.severMask()&~body.mask)!=0){BODIES.remove(entity);}
            else {if(!hit.refinement())body.impulse(hit);return;}
        }
        if(PENDING.size()>=MAX_BODIES)PENDING.remove(PENDING.keySet().iterator().next());
        PENDING.put(entity,new Pending(hit,scene.getGameTime()));
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    public static boolean pose(EntityModel<?> model,LivingEntity entity,float partial){
        if(!(model instanceof PigModel<?>)||!(model instanceof VictimModelRoot root)||!RagdollDrive.supported(entity))return false;
        resetScene();observe(entity,root.blacklight$victimRoot(),partial);if(!active(entity))return false;var body=BODIES.get(entity);
        if(body==null){
            var pending=PENDING.remove(entity);if(pending==null&&!RagdollDrive.captured(entity))return false;
            var renderer=(LivingEntityRenderer)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            body=new Body(entity,root.blacklight$victimRoot(),renderer.getTextureLocation(entity),pending==null?null:pending.hit,partial);
            while(!BODIES.isEmpty()&&(BODIES.size()>=MAX_BODIES||pieceCount()+body.pieces.size()>MAX_PIECES))
                BODIES.remove(BODIES.keySet().iterator().next());
            BODIES.put(entity,body);
        }
        // Recover by blending every joint back into the current animated pose at the server position.
        if(!entity.isDeadOrDying())body.recoveryPose(root.blacklight$victimRoot(),partial);
        for(String name:PARTS)DismemberVisuals.hide(root.blacklight$victimRoot().getChild(name));
        return true;
    }
    private static int pieceCount(){int count=0;for(var b:BODIES.values())count+=b.pieces.size();return count;}
    public static Vec3 contact(LivingEntity e,int region,float partial){
        var body=BODIES.get(e);if(body==null||!active(e))return null;
        Vec3 total=Vec3.ZERO;double mass=0;
        for(var part:body.pieces)if(part.region==region){double weight=1/part.invMass;total=total.add(part.old.lerp(part.pos,partial).scale(weight));mass+=weight;}
        return mass>0?total.scale(1/mass):null;
    }
    static List<WoundBlood.Anchor> woundAnchors(LivingEntity entity,int region,Vec3 point,Vec3 direction){
        var body=BODIES.get(entity);if(body==null)return List.of();
        var flip=new Quaternionf().rotationZ((float)Math.PI);
        var relative=new Quaternionf(flip).mul(body.bindRotations.get(region))
                .mul(new Quaternionf(body.restRotations.get(region)).conjugate()).mul(new Quaternionf(flip).conjugate());
        Vec3 at=body.bindPivots.get(region).add(rotate(point.subtract(body.restPivots.get(region)),relative));
        Vec3 normal=rotate(direction,relative);var found=new ArrayList<Piece>();double best=Double.POSITIVE_INFINITY;
        for(var p:body.pieces){
            if(p.region!=region)continue;boolean inside=true;
            for(var h:p.boundaries)if(at.subtract(h.plane.point()).dot(h.plane.normal())*h.sign<-.03){inside=false;break;}
            if(!inside)continue;
            Vec3 d=at.subtract(p.center);double dx=Math.max(0,Math.abs(d.x)-p.half.x),dy=Math.max(0,Math.abs(d.y)-p.half.y),dz=Math.max(0,Math.abs(d.z)-p.half.z);
            double distance=dx*dx+dy*dy+dz*dz;
            if(distance<best-1e-5){best=distance;found.clear();}
            if(distance<=best+1e-5&&found.size()<2)found.add(p);
        }
        var result=new ArrayList<WoundBlood.Anchor>();
        for(var p:found){
            float recovery=body.deadAt>=0?0:Math.clamp((body.level.getGameTime()-body.expires)/8F,0,1);
            Vec3 origin=p.pos;var rotation=new Quaternionf(p.rotation);
            if(recovery>0&&p.recoveryPosition!=null){origin=origin.lerp(p.recoveryPosition,recovery);rotation.slerp(p.recoveryRotation,recovery);}
            result.add(new WoundBlood.Anchor(origin.add(rotate(at.subtract(p.center).scale(p.scale),rotation)),rotate(normal,rotation),p.velocity));
        }
        return result;
    }
    private static void resetScene(){
        var level=Minecraft.getInstance().level;if(scene!=level){PENDING.clear();BODIES.clear();HISTORY.clear();scene=level;}
    }
    /** Track the authored pivots before hiding the model, including root motion and carrier rotation. */
    private static void observe(LivingEntity e,ModelPart root,float partial){
        var frame=RagdollDrive.frame(e,partial);var old=HISTORY.get(e);double now=e.level().getGameTime()+partial,dt=old==null?0:now-old.time;
        if(old!=null&&dt<=.05)return;
        var points=new HashMap<Integer,Vec3>();var velocities=new HashMap<Integer,Vec3>();
        for(int i=0;i<PARTS.length;i++){
            Vec3 at=RagdollDrive.transform(frame,pivot(root.getChild(PARTS[i])));points.put(BITS[i],at);
            velocities.put(BITS[i],dt>.05&&dt<3?RagdollDrive.limit(at.subtract(old.points.get(BITS[i])).scale(1/dt),1.2):e.getDeltaMovement());
        }
        HISTORY.put(e,new PoseSample(now,points,velocities));
    }
    private record Vertex(Vec3 p,Vec3 n,float u,float v){
        Vertex mix(Vertex b,double t){return new Vertex(p.lerp(b.p,t),n.lerp(b.n,t).normalize(),(float)(u+(b.u-u)*t),(float)(v+(b.v-v)*t));}
    }
    private record Face(List<Vertex> vertices,boolean cap) {}
    /** ModelPart's normal rendering supplies real UVs, nose, pivots and limb animation. */
    private static final class Collector implements VertexConsumer {
        final List<Vertex> vertices=new ArrayList<>();Vec3 p,n=Vec3.ZERO;float u,v;
        void flush(){if(p!=null){vertices.add(new Vertex(p,n,u,v));p=null;}}
        @Override public VertexConsumer addVertex(float x,float y,float z){flush();p=new Vec3(x,y,z);return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        @Override public VertexConsumer setUv(float u,float v){this.u=u;this.v=v;return this;}
        @Override public VertexConsumer setUv1(int u,int v){return this;}
        @Override public VertexConsumer setUv2(int u,int v){return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){n=new Vec3(x,y,z);return this;}
        List<Face> faces(){flush();var faces=new ArrayList<Face>();for(int i=0;i+3<vertices.size();i+=4)faces.add(new Face(List.copyOf(vertices.subList(i,i+4)),false));return faces;}
    }
    private static List<Face> capture(ModelPart part){
        var stack=new PoseStack();stack.translate(0,1.501,0);stack.scale(-1,-1,1);
        var collector=new Collector();part.render(stack,collector,0,OverlayTexture.NO_OVERLAY);return collector.faces();
    }
    private static Vec3 pivot(ModelPart part){return new Vec3(-part.x/16,1.501-part.y/16,part.z/16);}
    private static Vec3 rotate(Vec3 p,Quaternionf q){var v=q.transform(new Vector3f((float)p.x,(float)p.y,(float)p.z));return new Vec3(v.x,v.y,v.z);}

    /** Clip a convex torso piece, preserving skin UVs and closing the new surface. */
    private static List<Face> clip(List<Face> mesh,RagdollImpactPayload.CutPlane plane,int sign){
        var faces=new ArrayList<Face>();var seam=new ArrayList<Vec3>();
        for(var face:mesh){
            var output=new ArrayList<Vertex>();var input=face.vertices;if(input.isEmpty())continue;
            Vertex a=input.getLast();double da=a.p.subtract(plane.point()).dot(plane.normal())*sign;
            for(Vertex b:input){
                double db=b.p.subtract(plane.point()).dot(plane.normal())*sign;
                if((da>=0)!=(db>=0)){var v=a.mix(b,da/(da-db));output.add(v);if(seam.stream().noneMatch(p->p.distanceToSqr(v.p)<1e-9))seam.add(v.p);}
                if(db>=0)output.add(b);a=b;da=db;
            }
            if(output.size()>=3)faces.add(new Face(List.copyOf(output),face.cap));
        }
        if(seam.size()>=3){
            Vec3 sum=Vec3.ZERO;for(var p:seam)sum=sum.add(p);final Vec3 center=sum.scale(1.0/seam.size());
            Vec3 normal=plane.normal().scale(-sign);
            Vec3 x=normal.cross(Math.abs(normal.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0)).normalize(),y=normal.cross(x);
            seam.sort(Comparator.comparingDouble(p->Math.atan2(p.subtract(center).dot(y),p.subtract(center).dot(x))));
            var cap=new ArrayList<Vertex>();for(var p:seam)cap.add(new Vertex(p,normal,.5F,.5F));
            faces.add(new Face(List.copyOf(cap),true));
        }
        return faces;
    }
    private static AABB bounds(List<Face> mesh){
        Vec3 lo=new Vec3(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY),hi=lo.scale(-1);
        for(var f:mesh)for(var v:f.vertices){lo=new Vec3(Math.min(lo.x,v.p.x),Math.min(lo.y,v.p.y),Math.min(lo.z,v.p.z));hi=new Vec3(Math.max(hi.x,v.p.x),Math.max(hi.y,v.p.y),Math.max(hi.z,v.p.z));}
        return new AABB(lo,hi);
    }
    private record Halfspace(RagdollImpactPayload.CutPlane plane,int sign) {}
    private record Chunk(List<Face> mesh,List<Halfspace> boundaries){
        boolean contains(Vec3 p){for(var h:boundaries)if(p.subtract(h.plane.point()).dot(h.plane.normal())*h.sign<-.005)return false;return true;}
    }

    private static final class Body {
        final LivingEntity entity;final ClientLevel level;final ResourceLocation texture;
        final List<Piece> pieces=new ArrayList<>();final List<Joint> joints=new ArrayList<>();
        final Map<Integer,Vec3> bindPivots=new HashMap<>();final Map<Integer,Quaternionf> bindRotations=new HashMap<>();
        final Map<Integer,Vec3> restPivots=new HashMap<>();final Map<Integer,Quaternionf> restRotations=new HashMap<>();
        final long started;final int mask;long expires,frozen,deadAt=-1;Vec3 lastOrigin;boolean consumeSplit;
        Body(LivingEntity entity,ModelPart root,ResourceLocation texture,RagdollImpactPayload hit,float partial){
            this.entity=entity;level=(ClientLevel)entity.level();this.texture=texture;mask=hit==null?0:hit.severMask();
            started=level.getGameTime();lastOrigin=entity.position();
            var frame=RagdollDrive.frame(entity,partial);
            Quaternionf rotation=frame.getUnnormalizedRotation(new Quaternionf()).normalize();Vec3 origin=RagdollDrive.transform(frame,Vec3.ZERO);
            for(int i=0;i<PARTS.length;i++){
                var part=root.getChild(PARTS[i]);bindPivots.put(BITS[i],pivot(part));
                bindRotations.put(BITS[i],new Quaternionf().rotationZYX(part.zRot,part.yRot,part.xRot));
                var rest=part.getInitialPose();restPivots.put(BITS[i],new Vec3(-rest.x/16,1.501-rest.y/16,rest.z/16));
                restRotations.put(BITS[i],new Quaternionf().rotationZYX(rest.zRot,rest.yRot,rest.xRot));
            }
            var chunks=new ArrayList<Chunk>();chunks.add(new Chunk(capture(root.getChild("body")),List.of()));
            if(hit!=null&&entity.isDeadOrDying()&&(mask&32)!=0)for(var plane:hit.planes()){
                var next=new ArrayList<Chunk>();
                for(var chunk:chunks){
                    double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
                    for(var f:chunk.mesh)for(var v:f.vertices){double d=v.p.subtract(plane.point()).dot(plane.normal());lo=Math.min(lo,d);hi=Math.max(hi,d);}
                    // A grazing edge doesn't delete the torso or create zero-thickness slivers.
                    if(lo>-.025||hi<.025){next.add(chunk);continue;}
                    for(int sign:new int[]{-1,1}){var limits=new ArrayList<>(chunk.boundaries);limits.add(new Halfspace(plane,sign));next.add(new Chunk(clip(chunk.mesh,plane,sign),List.copyOf(limits)));}
                }
                chunks=next;
            }
            for(var chunk:chunks){var piece=new Piece(chunk.mesh,32,origin,rotation);piece.boundaries=chunk.boundaries;pieces.add(piece);}
            for(int i=1;i<PARTS.length;i++){
                ModelPart source=root.getChild(PARTS[i]);var limb=new Piece(capture(source),BITS[i],origin,rotation);
                Vec3 anchor=pivot(source);pieces.add(limb);
                if((mask&BITS[i])==0){
                    int owner=-1;for(int j=0;j<chunks.size();j++)if(chunks.get(j).contains(anchor)){owner=j;break;}
                    if(owner<0){double nearest=Double.POSITIVE_INFINITY;for(int j=0;j<chunks.size();j++){double d=pieces.get(j).center.distanceToSqr(anchor);if(d<nearest){nearest=d;owner=j;}}}
                    joints.add(new Joint(pieces.get(owner),limb,anchor));
                }
            }
            var history=HISTORY.get(entity);
            if(history!=null)for(var p:pieces)p.velocity=history.velocities.getOrDefault(p.region,Vec3.ZERO);
            expires=level.getGameTime()+20;if(hit!=null)impulse(hit);
            if(chunks.size()>1){
                Vec3 center=Vec3.ZERO;for(int i=0;i<chunks.size();i++)center=center.add(pieces.get(i).center);center=center.scale(1.0/chunks.size());
                for(int i=0;i<chunks.size();i++){var p=pieces.get(i);p.velocity=p.velocity.add(rotate(p.center.subtract(center).normalize(),rotation).scale(.08));}
            }
            org.slf4j.LoggerFactory.getLogger(PigRagdoll.class).debug("Pig ragdoll {}: {} pieces, {} joints, {} cut planes, mask {}",entity.getId(),pieces.size(),joints.size(),hit==null?0:hit.planes().size(),mask);
        }
        void impulse(RagdollImpactPayload hit){
            frozen=Math.max(frozen,level.getGameTime()+Math.clamp(hit.freeze(),0,8));
            if(RagdollDrive.fullImpact(hit))expires=frozen+hit.ticks();
            Vec3 velocity=new Vec3(hit.vx(),hit.vy(),hit.vz());
            for(var p:pieces){
                Vec3 launch=velocity;
                for(var c:hit.cuts())if(c.region()==p.region)launch=new Vec3(c.x(),c.y(),c.z());
                double local=.25;for(var w:hit.wounds())if(w.region()==p.region)local=1;
                p.velocity=RagdollDrive.limit(p.velocity.add(launch.scale(local)),1.5);
                p.spin=new Vector3f((float)(launch.z*.2+.025),(float)(launch.x*.08),(float)(-launch.x*.2+.06));
                p.still=0;
            }
        }
        void recoveryPose(ModelPart root,float partial){
            var frame=RagdollDrive.frame(entity,partial);
            for(int i=0;i<PARTS.length;i++){
                var part=root.getChild(PARTS[i]);Quaternionf q=frame.getUnnormalizedRotation(new Quaternionf()).normalize();
                // Offset in the original ModelPart coordinate system, including the body's 90 degree rest rotation.
                for(var p:pieces)if(p.region==BITS[i]){
                    Quaternionf rest=bindRotations.get(BITS[i]);
                    Quaternionf current=new Quaternionf().rotationZYX(part.zRot,part.yRot,part.xRot);
                    Quaternionf flip=new Quaternionf().rotationZ((float)Math.PI);
                    Quaternionf relative=new Quaternionf(flip).mul(current).mul(new Quaternionf(rest).conjugate()).mul(new Quaternionf(flip).conjugate());
                    p.recoveryRotation=new Quaternionf(q).mul(relative);
                    Vec3 anchor=bindPivots.get(BITS[i]);
                    p.recoveryPosition=RagdollDrive.transform(frame,pivot(part).add(rotate(p.center.subtract(anchor),relative)));
                }
            }
        }
        void tick(){
            long now=level.getGameTime();boolean captured=RagdollDrive.captured(entity);
            if(entity.isDeadOrDying()&&deadAt<0)deadAt=now;
            if(captured){deadAt=-1;expires=now+20;for(var p:pieces)p.still=0;}
            if(now<frozen)return;
            lastOrigin=entity.position();
            var phase=ConsumeFracture.phase(entity,1);float flow=RagdollDrive.flow(entity,1),power=RagdollDrive.power(entity);
            if(captured&&phase.opening()>0&&!consumeSplit){splitForConsume(phase);consumeSplit=true;}
            Vec3 sink=captured?RagdollDrive.sink(entity,1):Vec3.ZERO;
            for(var p:pieces){p.old=p.pos;p.oldRotation=new Quaternionf(p.rotation);}
            for(int sub=0;sub<2;sub++){
                for(var p:pieces){
                    float local=RagdollDrive.partFlow(entity,p.region,1);p.absorption=local;p.scale=Math.max(.025F,1-local*.975F);
                    if(p.region==1&&(phase.style()==ConsumeFracture.Style.HEAD_CRUSH||phase.style()==ConsumeFracture.Style.HEAD_IMPACT))p.scale*=1-phase.opening()*.55F;
                    if(power>0&&p.recoveryPosition!=null){
                        p.velocity=p.velocity.add(RagdollDrive.limit(p.recoveryPosition.subtract(p.pos),.65).scale(power*.3));
                        p.rotation.slerp(p.recoveryRotation,power*.18F);
                    }
                    if(flow>0)p.velocity=RagdollDrive.pull(p.pos,p.velocity,sink,flow,.5);
                    p.step(level);
                }
                for(int iteration=0;iteration<7;iteration++)for(var joint:joints)joint.solve(level);
                if(deadAt<0&&flow==0&&(!captured||RagdollDrive.attached(entity))){
                    Piece anchor=pieces.getFirst();
                    if(captured)for(var p:pieces)if(p.region==RagdollDrive.gripRegion(entity)){anchor=p;break;}
                    Vec3 goal=anchor.recoveryPosition;
                    if(goal!=null)anchor.move(level,RagdollDrive.limit(goal.subtract(anchor.pos),.4).scale(captured?.5:.13));
                }
            }
        }
        void splitForConsume(ConsumeFracture.Phase phase){
            if(phase.style()==ConsumeFracture.Style.ARM_RIP){
                joints.removeIf(j->j.limb.region==2);
                for(var p:pieces)if(p.region==2)p.velocity=p.velocity.add(rotate(new Vec3(-.12,.06,0),p.rotation));
                return;
            }
            if(phase.style()!=ConsumeFracture.Style.SEVER||pieces.stream().filter(p->p.region==32).count()!=1)return;
            var torso=pieces.getFirst();var plane=new RagdollImpactPayload.CutPlane(torso.center,new Vec3(0,0,1));
            var halves=new ArrayList<Piece>();
            for(int sign:new int[]{-1,1}){
                var mesh=clip(torso.mesh,plane,sign);if(mesh.isEmpty())return;
                Vec3 origin=torso.pos.subtract(rotate(torso.center,torso.rotation));
                var half=new Piece(mesh,32,origin,torso.rotation);
                half.velocity=torso.velocity.add(rotate(new Vec3(0,.025,sign*.045),torso.rotation));half.spin.set(torso.spin);
                half.boundaries=List.of(new Halfspace(plane,sign));halves.add(half);
            }
            pieces.removeFirst();pieces.addAll(0,halves);joints.clear();
            for(var limb:pieces)if(limb.region!=32&&(mask&limb.region)==0){
                Vec3 anchor=bindPivots.get(limb.region);Piece owner=halves.get(anchor.z<torso.center.z?0:1);
                joints.add(new Joint(owner,limb,anchor));
            }
        }
        void render(PoseStack stack,MultiBufferSource buffers,Vec3 camera,float partial){
            float recovery=deadAt>=0?0:Math.clamp((level.getGameTime()+partial-expires)/8F,0,1);
            int overlay=OverlayTexture.pack(0,RagdollDrive.hurtFlash(entity));
            var visual=entity.getData(ModAttachments.CONSUME_VISUAL);
            var profile=com.codex.blacklight.combat.ConsumeReceiverScene.profile(com.codex.blacklight.power.PowerForm.values()[visual.form()],false,visual.variant());
            float seconds=(level.getGameTime()-visual.started()+partial)/20F;
            float front=visual.consuming()&&profile!=null?Math.clamp((seconds-profile.effectSeconds())*1.4F,0,1):0;
            for(var p:pieces){p.stain=Math.max(p.absorption,RagdollDrive.smooth((front-(p.region==32?0:p.region==1?.15F:.3F))/.65F));p.render(level,texture,stack,buffers,camera,partial,recovery,overlay);}
            if(RagdollDrive.captured(entity)){
                var phase=ConsumeFracture.phase(entity,partial);if(phase.opening()<=0)return;
                var roots=new ArrayList<ConsumeTendrils.Attachment>();
                for(var p:pieces)roots.add(new ConsumeTendrils.Attachment(p.old.lerp(p.pos,partial),Math.max(.03,p.half.length()*.35*p.scale)));
                stack.pushPose();try{stack.translate(-camera.x,-camera.y,-camera.z);
                    ConsumeAbsorptionFx.drawPosed(stack,buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE)),
                            LevelRenderer.getLightColor(level,entity.blockPosition()),roots,RagdollDrive.sink(entity,partial),phase,entity.getId());
                }finally{stack.popPose();}
            }
        }
    }
    private static final class Joint {
        final Piece torso,limb;final Vec3 a,b;
        Joint(Piece torso,Piece limb,Vec3 anchor){this.torso=torso;this.limb=limb;a=anchor.subtract(torso.center);b=anchor.subtract(limb.center);}
        void solve(ClientLevel level){
            Vec3 ra=rotate(a.scale(torso.scale),torso.rotation),rb=rotate(b.scale(limb.scale),limb.rotation);
            Vec3 delta=limb.pos.add(rb).subtract(torso.pos.add(ra));double distance=delta.length();if(distance<.002)return;
            if(distance>.12)delta=delta.scale(.12/distance);
            double weight=torso.invMass/(torso.invMass+limb.invMass);
            torso.move(level,delta.scale(weight*.8));limb.move(level,delta.scale(-(1-weight)*.8));
            // Angular correction around the original joint; limbs are not independent flying cubes.
            torque(torso,ra,delta,.09F);torque(limb,rb,delta.scale(-1),.14F);
            Quaternionf relative=new Quaternionf(torso.rotation).conjugate().mul(limb.rotation).normalize();
            double angle=2*Math.acos(Math.clamp(Math.abs(relative.w),0,1));
            if(angle>1.65)limb.rotation.slerp(torso.rotation,(float)Math.min(.12,(angle-1.65)*.12));
        }
        private static void torque(Piece piece,Vec3 arm,Vec3 error,float amount){
            Vec3 axis=arm.cross(error);double length=axis.length();if(length<1e-6)return;
            var turn=new Quaternionf().rotationAxis((float)Math.min(.06,length/Math.max(.02,arm.lengthSqr())*amount),(float)(axis.x/length),(float)(axis.y/length),(float)(axis.z/length));
            piece.rotation.premul(turn).normalize();
        }
    }
    private static final class Piece {
        final List<Face> mesh;final int region;final Vec3 center,half;final double invMass;
        List<Halfspace> boundaries=List.of();
        Vec3 pos,old,velocity=Vec3.ZERO,recoveryPosition;Quaternionf rotation,oldRotation,recoveryRotation;Vector3f spin=new Vector3f();int still;float scale=1,absorption,stain;
        Piece(List<Face> mesh,int region,Vec3 origin,Quaternionf rotation){
            this.mesh=mesh;this.region=region;var bounds=bounds(mesh);center=bounds.getCenter();
            half=new Vec3(Math.max(.025,bounds.getXsize()*.5),Math.max(.025,bounds.getYsize()*.5),Math.max(.025,bounds.getZsize()*.5));
            invMass=1/Math.max(.015,half.x*half.y*half.z*8);
            this.rotation=new Quaternionf(rotation);oldRotation=new Quaternionf(rotation);pos=origin.add(rotate(center,rotation));old=pos;
        }
        Vec3 extent(){
            var m=new Matrix3f().rotation(rotation);
            return new Vec3(Math.abs(m.m00())*half.x+Math.abs(m.m10())*half.y+Math.abs(m.m20())*half.z,
                    Math.abs(m.m01())*half.x+Math.abs(m.m11())*half.y+Math.abs(m.m21())*half.z,
                    Math.abs(m.m02())*half.x+Math.abs(m.m12())*half.y+Math.abs(m.m22())*half.z).scale(scale);
        }
        Vec3 move(ClientLevel level,Vec3 requested){
            Vec3 e=extent();Vec3 allowed=Entity.collideBoundingBox(null,requested,new AABB(pos.subtract(e),pos.add(e)),level,List.of());
            pos=pos.add(allowed);if(allowed.lengthSqr()>.0001)still=0;return allowed;
        }
        void floor(ClientLevel level){
            Vec3 ext=extent();var hit=level.clip(new ClipContext(pos.add(0,ext.y+.12,0),pos.add(0,-ext.y-.025,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if(hit.getType()==HitResult.Type.BLOCK&&hit.getDirection()==net.minecraft.core.Direction.UP&&pos.y-ext.y<hit.getLocation().y)
                pos=new Vec3(pos.x,hit.getLocation().y+ext.y+.001,pos.z);
        }
        void step(ClientLevel level){
            if(still>=24)return;
            rotation.rotateXYZ(spin.x*.5F,spin.y*.5F,spin.z*.5F).normalize();floor(level);
            boolean water=!level.getFluidState(BlockPos.containing(pos)).isEmpty();velocity=RagdollDrive.limit(velocity.scale(water?.82:.97).add(0,(water?-.005:-.04)*(1-absorption),0),1.5);
            Vec3 requested=velocity.scale(.5),allowed=move(level,requested);
            boolean x=Math.abs(allowed.x-requested.x)>1e-5,y=Math.abs(allowed.y-requested.y)>1e-5,z=Math.abs(allowed.z-requested.z)>1e-5;
            if(x||y||z){
                double bounce=velocity.lengthSqr()>.04?.18:0;
                velocity=new Vec3(x?-velocity.x*bounce:velocity.x*.68,y?-velocity.y*bounce:velocity.y*.68,z?-velocity.z*bounce:velocity.z*.68);spin.mul(.65F);
            }
            still=y&&requested.y<0&&velocity.lengthSqr()<.002&&spin.lengthSquared()<.002?still+1:0;
        }
        void render(ClientLevel level,ResourceLocation texture,PoseStack stack,MultiBufferSource buffers,Vec3 camera,float partial,float recovery,int overlay){
            Vec3 at=old.lerp(pos,partial);var turn=new Quaternionf(oldRotation).slerp(rotation,partial);
            if(recovery>0&&recoveryPosition!=null){at=at.lerp(recoveryPosition,recovery);turn.slerp(recoveryRotation,recovery);}
            at=at.subtract(camera);stack.pushPose();
            try{
                stack.translate(at.x,at.y,at.z);stack.mulPose(turn);stack.scale(scale,scale,scale);stack.translate(-center.x,-center.y,-center.z);
                int light=LevelRenderer.getLightColor(level,BlockPos.containing(pos));
                for(var f:mesh){
                    var consumer=buffers.getBuffer(RenderType.entityCutoutNoCull(f.cap?ConsumedMobVisuals.WHITE:texture));
                    for(int i=1;i+1<f.vertices.size();i++){
                        emit(consumer,stack,f.vertices.getFirst(),f.cap,light,stain,overlay);emit(consumer,stack,f.vertices.get(i),f.cap,light,stain,overlay);
                        emit(consumer,stack,f.vertices.get(i+1),f.cap,light,stain,overlay);emit(consumer,stack,f.vertices.get(i+1),f.cap,light,stain,overlay);
                    }
                }
            }finally{stack.popPose();}
        }
    }
    private static void emit(VertexConsumer out,PoseStack stack,Vertex v,boolean cap,int light,float stain,int overlay){
        out.addVertex(stack.last(),(float)v.p.x,(float)v.p.y,(float)v.p.z).setColor(cap?112:(int)(255-150*stain),cap?35:(int)(255-220*stain),cap?31:(int)(255-205*stain),255)
                .setUv(v.u,v.v).setOverlay(overlay).setLight(light).setNormal(stack.last(),(float)v.n.x,(float)v.n.y,(float)v.n.z);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        resetScene();var mc=Minecraft.getInstance();if(scene==null||mc.isPaused())return;long now=scene.getGameTime();
        PENDING.entrySet().removeIf(e->e.getKey().isRemoved()||!available(e.getKey())||now-e.getValue().received>80);
        var iterator=BODIES.values().iterator();
        while(iterator.hasNext()){
            var b=iterator.next();var p=b.pieces.getFirst().pos;
            if(b.entity.level()!=scene||!available(b.entity)||mc.player==null||mc.player.position().distanceToSqr(p)>64*64||!scene.hasChunkAt(BlockPos.containing(p))
                    ||b.deadAt>=0&&now-b.deadAt>LIFETIME||b.deadAt<0&&!b.entity.isDeadOrDying()&&(b.entity.isRemoved()||!RagdollDrive.captured(b.entity)&&now>b.expires+8)
                    ||!RagdollDrive.captured(b.entity)&&now-b.started>180){iterator.remove();continue;}
            b.tick();
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for(var b:BODIES.values())if(b.level==mc.level&&available(b.entity))b.render(event.getPoseStack(),mc.renderBuffers().bufferSource(),event.getCamera().getPosition(),partial);
    }
}
