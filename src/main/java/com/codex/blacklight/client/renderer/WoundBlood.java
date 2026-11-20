package com.codex.blacklight.client.renderer;
import net.minecraftforge.event.TickEvent.ClientTickEvent;

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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;
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
    private record ConsumeMark(long started,int tick) {}
    private static final Map<LivingEntity,ConsumeMark> CONSUME_HITS=new WeakHashMap<>();
    private static ClientLevel scene;
    public record Anchor(Vec3 point,Vec3 direction,Vec3 velocity) {}
    private WoundBlood() {}
    static Vec3 rotate(Vec3 point,Quaternionf rotation){
        var p=rotation.transform(new Vector3f((float)point.x,(float)point.y,(float)point.z));return new Vec3(p.x,p.y,p.z);
    }
    private static void reset(){
        var level=Minecraft.getInstance().level;
        if(scene!=level){scene=level;dropTick=Long.MIN_VALUE;contactDrops=0;EMITTERS.clear();DROPS.clear();SPLATS.clear();FRAMES.clear();SEVERED.clear();CONSUME_HITS.clear();}
    }
    private static long dropTick = Long.MIN_VALUE;
    private static int contactDrops;

    static void contact(com.codex.blacklight.network.InfectedImpactPayload hit, Vec3 point, Vec3 direction, LivingEntity victim) {
        reset();
        var player = Minecraft.getInstance().player;
        if (scene == null || player == null || victim.isInvisible() || player.position().distanceToSqr(point) > 48*48) return;
        long tick = scene.getGameTime();
        if (dropTick != tick) { dropTick = tick; contactDrops = 0; }
        var flight = BloodFlight.of(hit.strike(), com.codex.blacklight.infection.InfectedAttackStyle.resolve(hit.style()));
        int count = Math.min(48 - contactDrops, Math.max(1, Math.round(flight.drops * hit.size()) + (hit.charged() ? 2 : 0)));
        if (count <= 0) return;
        contactDrops += count;
        Vec3 axis = direction.lengthSqr() > 1e-8 ? direction.normalize() : new Vec3(0,1,0);
        Vec3 across = axis.cross(Math.abs(axis.y) < .9 ? new Vec3(0,1,0) : new Vec3(1,0,0)).normalize();
        Vec3 up = across.cross(axis);
        var drift = victim.getDeltaMovement();
        Vec3 inherited = drift.lengthSqr() > 1 ? drift.normalize().scale(.18) : drift.scale(.18);
        var random = net.minecraft.util.RandomSource.create(hit.seed());
        double force = .73 + hit.size() * .46 + (hit.charged() ? .16 : 0);
        for (int i=0;i<count;i++) {
            var velocity = flight.velocity(axis,across,up,inherited,force,random);
            float radius = (.021F + random.nextFloat() * .026F) * (.70F + .48F * hit.size());
            if (DROPS.size() >= MAX_DROPS) DROPS.remove(0);
            DROPS.add(new Drop(point.add(axis.scale(.012)),velocity,radius,112+random.nextInt(75)));
        }
    }

    private static boolean available(LivingEntity entity){
        var consume=com.codex.blacklight.registry.ModAttachments.getData(entity, ModAttachments.CONSUME_VISUAL);
        return com.codex.blacklight.combat.PlayerHitEffects.fleshBody(entity)&&entity.level()==scene&&!entity.isInvisible()&&(!consume.active()||consume.stage()==ConsumeVisual.FLIGHT);
    }
    public static void accept(LivingEntity entity,RagdollImpactPayload hit){
        reset();if(scene==null||!available(entity))return;
        for(var wound:hit.wounds()) {
            var world=entity.position().add(wound.point().yRot((float)Math.toRadians(180-entity.yBodyRot)));
            if(com.codex.blacklight.combat.PlayerHitEffects.metalAt(entity,world)) continue;
            add(entity,wound.region(),wound.point(),wound.direction(),wound.strength(),
                (hit.severMask()&wound.region())!=0?20:hit.sharp()?10:6,false);
        }
        // Generic contact bits identify wounds, not proven amputation. The mesh solver
        // emits stump blood only after its finite cutting surface actually separates skin.
        if(AdaptiveRagdoll.supports(entity))return;
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
    static void hookTear(LivingEntity entity,Vec3 point,Vec3 tangent){
        reset();
        if(scene==null || !available(entity) || com.codex.blacklight.combat.PlayerHitEffects.metalAt(entity,point))return;
        var sound=com.codex.blacklight.registry.ModInfectedSounds.event(com.codex.blacklight.infection.InfectedSoundCue.TEAR);
        scene.playLocalSound(point.x,point.y,point.z,sound,net.minecraft.sounds.SoundSource.NEUTRAL,.68F,1.15F,false);
        var mist=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.43F,.015F,.028F),.57F);
        for(int i=0;i<6;i++){
            var v=tangent.scale(.04+i*.012).add((scene.random.nextDouble()-.5)*.07,
                    (scene.random.nextDouble()-.5)*.07,(scene.random.nextDouble()-.5)*.07);
            scene.addParticle(mist,point.x+v.x,point.y+v.y,point.z+v.z,v.x,v.y,v.z);
        }
    }
    static void meshCut(LivingEntity entity,int region,Vec3 point,Vec3 normal){
        reset();if(scene==null||!available(entity))return;
        add(entity,region,point.add(normal.scale(.015)),normal,.8F,22,true);
        add(entity,region,point.add(normal.scale(-.015)),normal.scale(-1),.8F,22,true);
    }
    static void meshSection(LivingEntity entity,int region,String key){
        reset();if(scene==null||entity.level()!=scene||entity.isInvisible()||!com.codex.blacklight.combat.PlayerHitEffects.fleshBody(entity))return;
        for(int side:new int[]{-1,1}){
            if(EMITTERS.size()>=MAX_EMITTERS)EMITTERS.remove(0);
            var emitter=new Emitter(entity,region,Vec3.ZERO,Vec3.ZERO,.8F,22,true,scene.getGameTime());
            emitter.sectionKey=key;emitter.sectionSide=side;EMITTERS.add(emitter);
        }
    }
    private static void add(LivingEntity e,int region,Vec3 point,Vec3 direction,float strength,int lifetime,boolean stump){
        if(EMITTERS.size()>=MAX_EMITTERS)EMITTERS.remove(0);
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
        var initial=part.getInitialPose();
        var frame=com.codex.blacklight.client.animation.RagdollDrive.frame(e,partial);
        Vec3 pivot=com.codex.blacklight.client.animation.RagdollDrive.transform(frame,new Vec3(-part.x/16,1.501-part.y/16,part.z/16));
        frames.put(region,new Frame(pivot,new Vec3(initial.x/16,initial.y/16,initial.z/16),
                frame.getUnnormalizedRotation(new Quaternionf()).normalize().rotateZ((float)Math.PI).rotateZYX(part.zRot,part.yRot,part.xRot),
                new Quaternionf().rotationZYX(initial.zRot,initial.yRot,initial.xRot).conjugate(),e.level().getGameTime()));
    }
    private static List<Anchor> anchors(Emitter emitter){
        var e=emitter.entity;
        if(emitter.sectionKey!=null)return AdaptiveRagdoll.sectionAnchors(e,emitter.region,emitter.sectionKey,emitter.sectionSide);
        if(emitter.consumeStarted!=Long.MIN_VALUE){
            Vec3 point=emitter.fromMesh?AdaptiveRagdoll.consumeWound(e,1):null;
            if(point==null)point=com.codex.blacklight.client.animation.RagdollDrive.contact(e,emitter.region,1);
            return List.of(new Anchor(point,emitter.direction,e.getDeltaMovement()));
        }
        var adaptive=AdaptiveRagdoll.woundAnchors(e,emitter.point,emitter.direction);if(!adaptive.isEmpty())return adaptive;
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
        boolean fromMesh;
        boolean emitted;long consumeStarted=Long.MIN_VALUE;String sectionKey;int sectionSide;
        Emitter(LivingEntity entity,int region,Vec3 point,Vec3 direction,float strength,int lifetime,boolean stump,long started){
            this.entity=entity;this.region=region;this.point=point;this.direction=direction;this.strength=strength;this.lifetime=lifetime;this.stump=stump;this.started=started;
        }
        void tick(){
            int age=(int)(scene.getGameTime()-started);if(emitted&&age>5&&(age&1)!=0)return;
            float decay=1-age/(float)lifetime;
            // The very short blunt wound already has a weapon-shaped contact spray.
            // Only a few secondary drops need to survive long enough to hit terrain.
            int initialDrops=stump?5:lifetime<=6?2:8;
            int count=emitted?(stump?2:1):initialDrops;
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
                    if(DROPS.size()>=MAX_DROPS)DROPS.remove(0);
                    DROPS.add(new Drop(anchor.point.add(direction.scale(.012)),velocity,(float)(.009+random.nextDouble()*.019)*Math.min(1.4F,strength),random.nextInt(55)+125));
                }
            }
            emitted=true;
        }
    }
    /** The synchronized receiver phase authorizes the effect; the rendered body owns its position. */
    static void blunt(LivingEntity e,Vec3 impact,Vec3 outward){
        reset();
        if(scene==null||e.level()!=scene||e.isInvisible())return;
        for(int i=0;i<7;i++){
            var random=scene.random;
            Vec3 velocity=outward.add(random.nextGaussian()*.25,random.nextDouble()*.25,random.nextGaussian()*.25)
                    .normalize().scale(.10+random.nextDouble()*.15);
            if(DROPS.size()>=MAX_DROPS)DROPS.remove(0);
            DROPS.add(new Drop(impact,velocity,.018F,125+random.nextInt(50)));
        }
    }
    static void rupture(LivingEntity e,int region,Vec3 impact,Vec3 direction,boolean crush){
        reset();if(scene==null || e.level()!=scene || e.isInvisible())return;
        var visual=ModAttachments.getData(e, ModAttachments.CONSUME_VISUAL);
        int count=crush?5:2;
        for(int i=0;i<count;i++){
            double angle=i*2.39996+e.getId();
            Vec3 jet=crush?new Vec3(Math.cos(angle),.25+i*.10,Math.sin(angle)).normalize():i==0?direction:direction.scale(-1);
            if(EMITTERS.size()>=MAX_EMITTERS)EMITTERS.remove(0);
            var emitter=new Emitter(e,region,Vec3.ZERO,jet,crush?1.25F:1.65F,crush?6:15,true,scene.getGameTime());
            emitter.consumeStarted=visual.started();emitter.fromMesh=true;EMITTERS.add(emitter);
        }
        var dust=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.43F,.025F,.054F),.85F);
        for(int i=0;i<(crush?18:9);i++){
            var random=scene.random;Vec3 spray=new Vec3(random.nextGaussian(),.22+random.nextDouble()*.6,random.nextGaussian()).normalize();
            if(!crush)spray=spray.scale(.45).add(direction).normalize();
            Vec3 pos=impact.add(spray.scale(random.nextDouble()*.22));
            scene.addParticle(dust,pos.x,pos.y,pos.z,spray.x*.09,spray.y*.10,spray.z*.09);
        }
    }
    private static void consumeHits(){
        var active=Collections.newSetFromMap(new IdentityHashMap<LivingEntity,Boolean>());
        var player=Minecraft.getInstance().player;if(player==null)return;
        for(var entity:scene.entitiesForRendering()){
            if(!(entity instanceof LivingEntity e)||!com.codex.blacklight.combat.PlayerHitEffects.fleshBody(e)||e.isInvisible()||e.distanceToSqr(player)>64*64)continue;
            var visual=com.codex.blacklight.registry.ModAttachments.getData(e, ModAttachments.CONSUME_VISUAL);
            if(!visual.consuming()||com.codex.blacklight.combat.ConsumeSequence.mountedStage(visual.stage()))continue;
            active.add(e);int elapsed=(int)(scene.getGameTime()-visual.started());
            var old=CONSUME_HITS.put(e,new ConsumeMark(visual.started(),elapsed));
            int previous=old!=null&&old.started==visual.started()?old.tick:elapsed-1;
            var phase=ConsumeFracture.phase(e,1);
            // Sharp absorption emits from confirmed mesh caps. An event alone
            // must not create an unrelated body-center spray when the blade misses.
            if(AdaptiveRagdoll.active(e) && (phase.style()==ConsumeFracture.Style.SEVER || ConsumeTrauma.applies(phase.form(),visual.variant())))continue;
            var profile=com.codex.blacklight.combat.ConsumeReceiverScene.profile(phase.form(),false,visual.variant());
            int fallback=Math.round(visual.duration()*com.codex.blacklight.combat.ConsumeSequence.contact(phase.form(),false))-1;
            boolean impact=false;
            for(int t=Math.max(previous+1,elapsed-4);t<=elapsed;t++)if(profile!=null?profile.impactAt(t):t==fallback){impact=true;break;}
            if(!impact)continue;
            int region=switch(phase.style()){case HEAD_CRUSH,HEAD_IMPACT->1;case RIP->2;default->32;};
            Vec3 point=com.codex.blacklight.client.animation.RagdollDrive.contact(e,region,1);
            var owner=scene.getEntity(visual.consumerId());
            Vec3 direction=owner==null?new Vec3(0,1,0):point.subtract(owner.position().add(0,owner.getBbHeight()*.6,0)).add(0,.2,0).normalize();
            if(direction.lengthSqr()<.5)direction=new Vec3(0,1,0);
            if(EMITTERS.size()>=MAX_EMITTERS)EMITTERS.remove(0);
            int duration=switch(phase.style()) {
                case ARM_RIP,RIP,SEVER -> 16;
                case HEAD_CRUSH,CRUSH -> 10;
                default -> 7;
            };
            float force=switch(phase.style()) {
                case HEAD_CRUSH,CRUSH -> 1.38F;
                case ARM_RIP,RIP -> 1.15F;
                case COIL,CHEST_ABSORB -> .58F;
                default -> .92F;
            };
            if (phase.style()==ConsumeFracture.Style.CHEST_ABSORB && owner!=null)
                direction=owner.getBoundingBox().getCenter().subtract(point).normalize();
            var emitter=new Emitter(e,region,Vec3.ZERO,direction,force,duration,
                    phase.style()!=ConsumeFracture.Style.CHEST_ABSORB,scene.getGameTime());
            emitter.consumeStarted=visual.started();EMITTERS.add(emitter);
        }
        CONSUME_HITS.keySet().removeIf(e->!active.contains(e));
    }
    private static boolean available(Emitter emitter){
        if(emitter.sectionKey!=null)return emitter.entity.level()==scene&&!emitter.entity.isInvisible();
        if(emitter.consumeStarted==Long.MIN_VALUE)return available(emitter.entity);
        var e=emitter.entity;var visual=com.codex.blacklight.registry.ModAttachments.getData(e, ModAttachments.CONSUME_VISUAL);
        return e.level()==scene&&!e.isInvisible()&&!e.isRemoved()&&visual.consuming()&&visual.started()==emitter.consumeStarted;
    }
    private static final class Drop {
        Vec3 pos,old,velocity;final float radius;final int red;int age;
        Drop(Vec3 point,Vec3 velocity,float radius,int red){pos=old=point;this.velocity=velocity;this.radius=radius;this.red=red;}
        boolean tick(){
            old=pos;age++;if(age>32||!scene.hasChunkAt(BlockPos.containing(pos)))return false;
            boolean water=!scene.getFluidState(BlockPos.containing(pos)).isEmpty();
            velocity=velocity.scale(water?.65:.97).add(0,water?-.004:-.033,0);
            var hit=scene.clip(new ClipContext(pos,pos.add(velocity),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(net.minecraft.world.entity.Entity)null));
            if(hit.getType()==HitResult.Type.BLOCK){
                if(!water&&velocity.lengthSqr()>.0003)splat(hit,radius*(2.8F+(float)Math.min(2,velocity.length()*5)),red,velocity);
                return false;
            }
            pos=pos.add(velocity);return !water||age<12;
        }
        void render(VertexConsumer out,PoseStack stack,Vec3 camera,float partial){
            Vec3 point=old.lerp(pos,partial),normal=camera.subtract(point).normalize();
            Vec3 axis=velocity.lengthSqr()>.0001?velocity.normalize():new Vec3(0,1,0);
            Vec3 across=axis.cross(normal).normalize().scale(radius);if(across.lengthSqr()<1e-8)return;
            Vec3 along=axis.scale(net.minecraft.util.Mth.clamp(velocity.length()*.42,radius,radius*7));
            Vec3 front=point.add(along).subtract(camera),back=point.subtract(along).subtract(camera);
            int light=LevelRenderer.getLightColor(scene,BlockPos.containing(point));int alpha=(int)(230*Math.min(1,(32-age)/7F));
            // A pointed trailing end and rounded leading edge keep close droplets
            // readable against the sky even when their velocity is nearly horizontal.
            var shoulder=point.subtract(camera).add(along.scale(.42));
            var left=shoulder.subtract(across);var right=shoulder.add(across);
            vertex(out,stack,back,normal,red,alpha/2,light);vertex(out,stack,left,normal,red,alpha,light);
            vertex(out,stack,right,normal,red,alpha,light);vertex(out,stack,back,normal,red,alpha/2,light);
            vertex(out,stack,left,normal,red,alpha,light);vertex(out,stack,front.subtract(across.scale(.42)),normal,red,alpha,light);
            vertex(out,stack,front.add(across.scale(.42)),normal,red,alpha,light);vertex(out,stack,right,normal,red,alpha,light);
        }
    }
    private record Splat(Vec3 center,Vec3 normal,List<Vec3> rim,BlockPos block,net.minecraft.world.level.block.state.BlockState state,int red,long started) {}
    private static void splat(BlockHitResult hit,float radius,int red,Vec3 velocity){
        Vec3 normal=Vec3.atLowerCornerOf(hit.getDirection().getNormal()),center=hit.getLocation().add(normal.scale(.0025));
        Vec3 x=normal.cross(Math.abs(normal.y)<.85?new Vec3(0,1,0):new Vec3(1,0,0)).normalize(),y=normal.cross(x);
        Vec3 stretch=BloodFlight.surfaceStretch(velocity,normal);
        var rim=new ArrayList<Vec3>();double phase=scene.random.nextDouble()*Math.PI*2;
        for(int i=0;i<8;i++){
            double angle=phase+i*Math.PI/4,length=radius*(.55+scene.random.nextDouble()*.45);
            Vec3 radial=x.scale(Math.cos(angle)).add(y.scale(Math.sin(angle)));
            Vec3 offset=radial.add(stretch.scale(Math.max(0,radial.dot(stretch.normalize())))).scale(length);
            // Validate each edge against this exact collision face; splashes cannot hang across a stair/ledge.
            for(int shrink=0;shrink<5;shrink++){
                Vec3 point=center.add(offset);var support=scene.clip(new ClipContext(point.add(normal.scale(.035)),point.subtract(normal.scale(.07)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(net.minecraft.world.entity.Entity)null));
                if(support.getType()==HitResult.Type.BLOCK&&support.getBlockPos().equals(hit.getBlockPos())&&support.getDirection()==hit.getDirection())break;
                offset=shrink==4?Vec3.ZERO:offset.scale(.45);
            }
            rim.add(offset);
        }
        if(SPLATS.size()>=MAX_SPLATS)SPLATS.remove(0);
        SPLATS.add(new Splat(center,normal,List.copyOf(rim),hit.getBlockPos(),scene.getBlockState(hit.getBlockPos()),red,scene.getGameTime()));
    }
    private static void vertex(VertexConsumer out,PoseStack stack,Vec3 point,Vec3 normal,int red,int alpha,int light){
        out.vertex(stack.last().pose(),(float)point.x,(float)point.y,(float)point.z).color(red,9,16,net.minecraft.util.Mth.clamp(alpha,0,255))
                .uv(.5F,.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(stack.last().normal(),(float)normal.x,(float)normal.y,(float)normal.z).endVertex();
    }
    @SubscribeEvent public static void tick(ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        reset();var mc=Minecraft.getInstance();if(scene==null||mc.isPaused())return;long now=scene.getGameTime();
        consumeHits();
        EMITTERS.removeIf(e->!available(e)||now-e.started>=e.lifetime||mc.player==null||mc.player.distanceToSqr(e.entity)>64*64);
        for(var e:EMITTERS)e.tick();
        DROPS.removeIf(d->!d.tick()||mc.player==null||mc.player.position().distanceToSqr(d.pos)>64*64);
        SPLATS.removeIf(s->now-s.started>160||scene.getBlockState(s.block)!=s.state||mc.player==null||mc.player.position().distanceToSqr(s.center)>64*64);
        var tracked=Collections.newSetFromMap(new IdentityHashMap<LivingEntity,Boolean>());for(var e:EMITTERS)tracked.add(e.entity);
        FRAMES.keySet().removeIf(e->!tracked.contains(e));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||scene==null||scene!=Minecraft.getInstance().level)return;
        var buffers=Minecraft.getInstance().renderBuffers().bufferSource();var out=buffers.getBuffer(RenderType.entityTranslucent(ConsumedMobVisuals.WHITE));
        var camera=event.getCamera().getPosition();var stack=event.getPoseStack();float partial=event.getPartialTick();
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
