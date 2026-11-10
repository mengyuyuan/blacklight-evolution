package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.animation.VictimModelRoot;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.network.RagdollImpactPayload;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Blood originates at confirmed wounds, follows the animated/physical part, and collides with blocks.
 * Spray variation is cosmetic. It never chooses cuts, damages entities, or changes world blocks. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class WoundBlood {
    private static final int MAX_EMITTERS=96,MAX_DROPS=768,MAX_SPLATS=256;
    private static final List<Emitter> EMITTERS=new ArrayList<>();
    private static final List<Drop> DROPS=new ArrayList<>();
    private static final List<Splat> SPLATS=new ArrayList<>();
    private static final Map<LivingEntity,Map<Integer,Frame>> FRAMES=new WeakHashMap<>();
    private static final Map<LivingEntity,Integer> SEVERED=new WeakHashMap<>();
    private static ClientLevel scene;
    public record Anchor(Vec3 point,Vec3 direction,Vec3 velocity) {}
    private WoundBlood() {}
    static Vec3 rotate(Vec3 point,Quaternionf rotation){
        var p=rotation.transform(new Vector3f((float)point.x,(float)point.y,(float)point.z));return new Vec3(p.x,p.y,p.z);
    }
    private static void reset(){
        var level=Minecraft.getInstance().level;
        if(scene!=level){scene=level;EMITTERS.clear();DROPS.clear();SPLATS.clear();FRAMES.clear();SEVERED.clear();}
    }
    private static boolean available(LivingEntity entity){
        var consume=entity.getData(ModAttachments.CONSUME_VISUAL);
        return entity.level()==scene&&!entity.isInvisible()&&(!consume.active()||consume.stage()==ConsumeVisual.FLIGHT);
    }
    public static void accept(LivingEntity entity,RagdollImpactPayload hit){
        reset();if(scene==null||!available(entity))return;
        for(var wound:hit.wounds())add(entity,wound.region(),wound.point(),wound.direction(),wound.strength(),
                (hit.severMask()&wound.region())!=0?20:hit.sharp()?10:6,false);
        int newSevers=hit.severMask()&~SEVERED.getOrDefault(entity,0);
        if(newSevers!=0){
            SEVERED.put(entity,hit.severMask());
            for(int bit=1;bit<=16;bit<<=1)if((newSevers&bit)!=0){
                Vec3 joint=joint(entity,bit);Vec3 outward=joint.subtract(new Vec3(0,entity instanceof Pig?.65:.95,0)).normalize();
                if(outward.lengthSqr()<.5)outward=new Vec3(0,1,0);
                add(entity,32,joint,outward,.85F,22,true);
                add(entity,bit,joint,outward.scale(-1),.65F,18,true);
            }
            if((newSevers&32)!=0)for(var plane:hit.planes())add(entity,32,plane.point(),plane.normal(),.8F,22,true);
        }
    }
    private static void add(LivingEntity e,int region,Vec3 point,Vec3 direction,float strength,int lifetime,boolean stump){
        if(EMITTERS.size()>=MAX_EMITTERS)EMITTERS.removeFirst();
        EMITTERS.add(new Emitter(e,region,point,direction,strength,lifetime,stump,scene.getGameTime()));
        FRAMES.computeIfAbsent(e,k->new HashMap<>());
    }
    private static Vec3 joint(LivingEntity e,int bit){
        if(e instanceof Pig)return switch(bit){
            case 1->new Vec3(0,.751,-.375);case 2->new Vec3(.1875,.376,-.3125);case 4->new Vec3(-.1875,.376,-.3125);
            case 8->new Vec3(.1875,.376,.4375);default->new Vec3(-.1875,.376,.4375);};
        return switch(bit){case 1->new Vec3(0,1.501,0);case 2->new Vec3(.3125,1.376,0);case 4->new Vec3(-.3125,1.376,0);
            case 8->new Vec3(.125,.751,0);default->new Vec3(-.125,.751,0);};
    }
    private record Frame(Vec3 pivot,Vec3 bindPivot,Quaternionf rotation,Quaternionf inverseBind,long tick){
        Anchor sample(Vec3 point,Vec3 direction,Vec3 velocity){
            Vec3 local=rotate(new Vec3(-point.x,1.501-point.y,point.z).subtract(bindPivot),inverseBind);
            Vec3 normal=rotate(new Vec3(-direction.x,-direction.y,direction.z),inverseBind);
            return new Anchor(pivot.add(rotate(local,rotation)),rotate(normal,rotation),velocity);
        }
    }
    /** After animation/physical posing and before shared model visibility is changed. */
    static void capturePose(EntityModel<?> model,LivingEntity e,float partial){
        var frames=FRAMES.get(e);if(frames==null)return;
        if(model instanceof HumanoidModel<?> h){
            bind(frames,e,32,h.body,partial);bind(frames,e,1,h.head,partial);bind(frames,e,2,h.rightArm,partial);
            bind(frames,e,4,h.leftArm,partial);bind(frames,e,8,h.rightLeg,partial);bind(frames,e,16,h.leftLeg,partial);
        }else if(model instanceof PigModel<?> &&model instanceof VictimModelRoot root){
            var r=root.blacklight$victimRoot();String[] names={"body","head","right_front_leg","left_front_leg","right_hind_leg","left_hind_leg"};int[] bits={32,1,2,4,8,16};
            for(int i=0;i<bits.length;i++)bind(frames,e,bits[i],r.getChild(names[i]),partial);
        }
    }
    private static void bind(Map<Integer,Frame> frames,LivingEntity e,int region,ModelPart part,float partial){
        float angle=(float)Math.toRadians(180-net.minecraft.util.Mth.rotLerp(partial,e.yBodyRotO,e.yBodyRot));var initial=part.getInitialPose();
        Vec3 pivot=new Vec3(-part.x/16,1.501-part.y/16,part.z/16).yRot(angle).add(e.getPosition(partial));
        frames.put(region,new Frame(pivot,new Vec3(initial.x/16,initial.y/16,initial.z/16),
                new Quaternionf().rotationY(angle).rotateZ((float)Math.PI).rotateZYX(part.zRot,part.yRot,part.xRot),
                new Quaternionf().rotationZYX(initial.zRot,initial.yRot,initial.xRot).conjugate(),e.level().getGameTime()));
    }
    private static List<Anchor> anchors(Emitter emitter){
        var e=emitter.entity;
        var pig=PigRagdoll.woundAnchors(e,emitter.region,emitter.point,emitter.direction);if(!pig.isEmpty())return pig;
        var detached=DismemberVisuals.woundAnchor(e,emitter.region,emitter.point,emitter.direction);if(detached!=null)return List.of(detached);
        var frames=FRAMES.get(e);var frame=frames==null?null:frames.get(emitter.region);
        if(frame!=null&&scene.getGameTime()-frame.tick<=2)return List.of(frame.sample(emitter.point,emitter.direction,e.getDeltaMovement()));
        if(e.isRemoved())return List.of();
        float angle=(float)Math.toRadians(180-e.yBodyRot);
        return List.of(new Anchor(e.position().add(emitter.point.yRot(angle)),emitter.direction.yRot(angle),e.getDeltaMovement()));
    }
    private static final class Emitter {
        final LivingEntity entity;final int region,lifetime;final Vec3 point,direction;final float strength;final boolean stump;final long started;
        boolean emitted;
        Emitter(LivingEntity entity,int region,Vec3 point,Vec3 direction,float strength,int lifetime,boolean stump,long started){
            this.entity=entity;this.region=region;this.point=point;this.direction=direction;this.strength=strength;this.lifetime=lifetime;this.stump=stump;this.started=started;
        }
        void tick(){
            int age=(int)(scene.getGameTime()-started);if(emitted&&age>5&&(age&1)!=0)return;
            float decay=1-age/(float)lifetime;int count=!emitted?(stump?5:8):stump?2:1;
            for(var anchor:anchors(this)){
                var random=scene.random;Vec3 normal=anchor.direction.normalize();
                Vec3 tangent=normal.cross(Math.abs(normal.y)<.85?new Vec3(0,1,0):new Vec3(1,0,0)).normalize(),up=normal.cross(tangent);
                for(int i=0;i<count;i++){
                    double side=random.nextGaussian()*(emitted?.12:.3),vertical=random.nextGaussian()*.18;
                    Vec3 direction=normal.add(tangent.scale(side)).add(up.scale(vertical)).normalize();
                    double speed=(.12+random.nextDouble()*.26)*strength*(.25+.75*decay);
                    if(stump)speed*=.7+.3*Math.sin(age*1.4);
                    Vec3 velocity=direction.scale(speed).add(anchor.velocity.scale(.3)).add(0,.025*decay,0);
                    if(velocity.lengthSqr()>1)velocity=velocity.normalize();
                    if(DROPS.size()>=MAX_DROPS)DROPS.removeFirst();
                    DROPS.add(new Drop(anchor.point.add(direction.scale(.012)),velocity,(float)(.009+random.nextDouble()*.019)*Math.min(1.4F,strength),random.nextInt(55)+125));
                }
            }
            emitted=true;
        }
    }
    private static final class Drop {
        Vec3 pos,old,velocity;final float radius;final int red;int age;
        Drop(Vec3 point,Vec3 velocity,float radius,int red){pos=old=point;this.velocity=velocity;this.radius=radius;this.red=red;}
        boolean tick(){
            old=pos;age++;if(age>32||!scene.hasChunkAt(BlockPos.containing(pos)))return false;
            boolean water=!scene.getFluidState(BlockPos.containing(pos)).isEmpty();
            velocity=velocity.scale(water?.65:.97).add(0,water?-.004:-.033,0);
            var hit=scene.clip(new ClipContext(pos,pos.add(velocity),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.empty()));
            if(hit.getType()==HitResult.Type.BLOCK){
                if(!water&&velocity.lengthSqr()>.0003)splat(hit,radius*(2.2F+(float)Math.min(2,velocity.length()*4)),red);
                return false;
            }
            pos=pos.add(velocity);return !water||age<12;
        }
        void render(VertexConsumer out,PoseStack stack,Vec3 camera,float partial){
            Vec3 point=old.lerp(pos,partial),normal=camera.subtract(point).normalize();
            Vec3 axis=velocity.lengthSqr()>.0001?velocity.normalize():new Vec3(0,1,0);
            Vec3 across=axis.cross(normal).normalize().scale(radius);if(across.lengthSqr()<1e-8)return;
            Vec3 along=axis.scale(Math.clamp(velocity.length()*.42,radius,radius*7));
            Vec3 front=point.add(along).subtract(camera),back=point.subtract(along).subtract(camera);
            int light=LevelRenderer.getLightColor(scene,BlockPos.containing(point));int alpha=(int)(230*Math.min(1,(32-age)/7F));
            vertex(out,stack,front.subtract(across),normal,red,alpha,light);vertex(out,stack,front.add(across),normal,red,alpha,light);
            vertex(out,stack,back.add(across.scale(.45)),normal,red,alpha/2,light);vertex(out,stack,back.subtract(across.scale(.45)),normal,red,alpha/2,light);
        }
    }
    private record Splat(Vec3 center,Vec3 normal,List<Vec3> rim,BlockPos block,int red,long started) {}
    private static void splat(BlockHitResult hit,float radius,int red){
        Vec3 normal=Vec3.atLowerCornerOf(hit.getDirection().getNormal()),center=hit.getLocation().add(normal.scale(.0025));
        Vec3 x=normal.cross(Math.abs(normal.y)<.85?new Vec3(0,1,0):new Vec3(1,0,0)).normalize(),y=normal.cross(x);
        var rim=new ArrayList<Vec3>();double phase=scene.random.nextDouble()*Math.PI*2;
        for(int i=0;i<8;i++){
            double angle=phase+i*Math.PI/4,length=radius*(.55+scene.random.nextDouble()*.45);
            Vec3 offset=x.scale(Math.cos(angle)*length).add(y.scale(Math.sin(angle)*length));
            // Validate each edge against this exact collision face; splashes cannot hang across a stair/ledge.
            for(int shrink=0;shrink<5;shrink++){
                Vec3 point=center.add(offset);var support=scene.clip(new ClipContext(point.add(normal.scale(.035)),point.subtract(normal.scale(.07)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.empty()));
                if(support.getType()==HitResult.Type.BLOCK&&support.getBlockPos().equals(hit.getBlockPos())&&support.getDirection()==hit.getDirection())break;
                offset=offset.scale(.45);
            }
            rim.add(offset);
        }
        if(SPLATS.size()>=MAX_SPLATS)SPLATS.removeFirst();
        SPLATS.add(new Splat(center,normal,List.copyOf(rim),hit.getBlockPos(),red,scene.getGameTime()));
    }
    private static void vertex(VertexConsumer out,PoseStack stack,Vec3 point,Vec3 normal,int red,int alpha,int light){
        out.addVertex(stack.last(),(float)point.x,(float)point.y,(float)point.z).setColor(red,9,16,Math.clamp(alpha,0,255))
                .setUv(.5F,.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(stack.last(),(float)normal.x,(float)normal.y,(float)normal.z);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        reset();var mc=Minecraft.getInstance();if(scene==null||mc.isPaused())return;long now=scene.getGameTime();
        EMITTERS.removeIf(e->!available(e.entity)||now-e.started>=e.lifetime||mc.player==null||mc.player.distanceToSqr(e.entity)>64*64);
        for(var e:EMITTERS)e.tick();
        DROPS.removeIf(d->!d.tick()||mc.player==null||mc.player.position().distanceToSqr(d.pos)>64*64);
        SPLATS.removeIf(s->now-s.started>160||scene.getBlockState(s.block).isAir()||mc.player==null||mc.player.position().distanceToSqr(s.center)>64*64);
        var tracked=Collections.newSetFromMap(new IdentityHashMap<LivingEntity,Boolean>());for(var e:EMITTERS)tracked.add(e.entity);
        FRAMES.keySet().removeIf(e->!tracked.contains(e));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||scene==null||scene!=Minecraft.getInstance().level)return;
        var buffers=Minecraft.getInstance().renderBuffers().bufferSource();var out=buffers.getBuffer(RenderType.entityTranslucent(ConsumedMobVisuals.WHITE));
        var camera=event.getCamera().getPosition();var stack=event.getPoseStack();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for(var d:DROPS)d.render(out,stack,camera,partial);
        for(var s:SPLATS){
            float age=scene.getGameTime()+partial-s.started;float fade=Math.min(1,(160-age)/35F),growth=.65F+.35F*Math.min(1,age/4);
            int light=LevelRenderer.getLightColor(scene,BlockPos.containing(s.center));Vec3 center=s.center.subtract(camera);
            int red=(int)(s.red*(1-Math.min(.45,age/220)));int alpha=(int)(210*fade);
            for(int i=0;i<s.rim.size();i++){
                Vec3 a=center.add(s.rim.get(i).scale(growth)),b=center.add(s.rim.get((i+1)%s.rim.size()).scale(growth));
                vertex(out,stack,center,s.normal,red,alpha,light);vertex(out,stack,a,s.normal,red,alpha,light);
                vertex(out,stack,b,s.normal,red,alpha,light);vertex(out,stack,b,s.normal,red,alpha,light);
            }
        }
    }
}
