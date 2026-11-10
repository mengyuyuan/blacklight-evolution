package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.animation.ImpactRagdoll;
import com.codex.blacklight.combat.WeaponDismemberment;
import com.codex.blacklight.mixin.client.ModelPartChildrenAccessor;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Persistent detached copies, captured AFTER animation; never references a shared mutable model.
 * Dead-body parts have their own velocity, rotation, block collisions and lifetime. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class DismemberVisuals {
    private static final int LIMIT=96, LIFETIME=100;
    private static final List<Fragment> FRAGMENTS=new ArrayList<>();
    private static final Map<LivingEntity,Integer> SEEN=new WeakHashMap<>();
    private static final ThreadLocal<VisibilityScope> SCOPE=new ThreadLocal<>();
    private static ClientLevel scene;
    private DismemberVisuals() {}

    public static final class VisibilityScope implements AutoCloseable {
        private final VisibilityScope parent=SCOPE.get();
        private final Map<ModelPart,Boolean> saved=new IdentityHashMap<>();
        private VisibilityScope(){SCOPE.set(this);}
        @Override public void close(){saved.forEach((part,visible)->part.visible=visible);if(parent==null)SCOPE.remove();else SCOPE.set(parent);}
    }
    public static VisibilityScope scope(){return new VisibilityScope();}
    static void hide(ModelPart part){
        var scope=SCOPE.get();if(scope==null)return;
        scope.saved.putIfAbsent(part,part.visible);part.visible=false;
    }
    private static int mask(LivingEntity e){return e.getData(ModAttachments.DISMEMBER)&WeaponDismemberment.ALL;}
    public static boolean missingArm(LivingEntity e,HumanoidArm arm){return (mask(e)&(arm==HumanoidArm.LEFT?WeaponDismemberment.LEFT_ARM:WeaponDismemberment.RIGHT_ARM))!=0;}
    public static boolean missingHead(LivingEntity e){return (mask(e)&WeaponDismemberment.HEAD)!=0;}
    private static ModelPart part(HumanoidModel<?> m,int bit){return switch(bit){
        case 1->m.head;case 2->m.rightArm;case 4->m.leftArm;case 8->m.rightLeg;case 16->m.leftLeg;default->m.body;};}
    public static void hideParts(HumanoidModel<?> model,LivingEntity entity){
        int bits=mask(entity);
        for(int bit=1;bit<=32;bit<<=1)if((bits&bit)!=0)hide(part(model,bit));
        if((bits&1)!=0)hide(model.hat);
        if(model instanceof PlayerModel<?> p){
            if((bits&2)!=0)hide(p.rightSleeve);if((bits&4)!=0)hide(p.leftSleeve);
            if((bits&8)!=0)hide(p.rightPants);if((bits&16)!=0)hide(p.leftPants);if((bits&32)!=0)hide(p.jacket);
        }
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    public static void pose(EntityModel<?> model,LivingEntity entity,float partial){
        WoundBlood.capturePose(model,entity,partial);
        if(PigRagdoll.pose(model,entity,partial))return;
        if(!(model instanceof HumanoidModel<?> humanoid)||mask(entity)==0||entity.isInvisible())return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        if(scene!=mc.level){FRAGMENTS.clear();SEEN.clear();scene=mc.level;}
        int pending=mask(entity)&~SEEN.getOrDefault(entity,0);
        if(pending!=0){
            var renderer=mc.getEntityRenderDispatcher().getRenderer(entity);
            ResourceLocation texture=((LivingEntityRenderer)renderer).getTextureLocation(entity);
            for(int bit=1;bit<=32;bit<<=1)if((pending&bit)!=0){
                ModelPart original=part(humanoid,bit);
                if(!original.visible)continue;
                ModelPart copy=copy(original);copy.setPos(0,0,0);copy.setRotation(0,0,0);
                if(bit==1&&humanoid.hat.visible){
                    // Hat and head stay together as one physical fragment.
                    ModelPart hat=copy(humanoid.hat);hat.setPos(0,0,0);hat.setRotation(0,0,0);
                    ((ModelPartChildrenAccessor)(Object)copy).blacklight$children().put("severed_hat",hat);
                }
                if(FRAGMENTS.size()>=LIMIT)FRAGMENTS.removeFirst();
                FRAGMENTS.add(new Fragment(entity,original,copy,texture,bit,partial));
            }
            SEEN.put(entity,mask(entity));
        }
        hideParts(humanoid,entity);
    }
    private static ModelPart copy(ModelPart source){
        var access=(ModelPartChildrenAccessor)(Object)source;
        Map<String,ModelPart> children=new HashMap<>();access.blacklight$children().forEach((name,child)->children.put(name,copy(child)));
        var result=new ModelPart(List.copyOf(access.blacklight$cubes()),children);
        result.copyFrom(source);result.visible=source.visible;result.skipDraw=source.skipDraw;return result;
    }
    private static final class Fragment {
        final LivingEntity owner;final int region;final Vec3 bindPivot;final Quaternionf inverseBind;
        final ClientLevel level;
        final ModelPart model;
        final ResourceLocation texture;
        final Vec3 center,half;
        final long started;
        Vec3 pos,old,velocity;
        Quaternionf rotation,oldRotation;
        Vector3f spin;
        int still;
        Fragment(LivingEntity entity,ModelPart source,ModelPart copy,ResourceLocation texture,int bit,float partial){
            owner=entity;region=bit;var initial=source.getInitialPose();
            bindPivot=new Vec3(initial.x/16,initial.y/16,initial.z/16);
            inverseBind=new Quaternionf().rotationZYX(initial.zRot,initial.yRot,initial.xRot).conjugate();
            level=(ClientLevel)entity.level();this.model=copy;this.texture=texture;started=level.getGameTime();
            center=bit==1?new Vec3(0,-.25,0):bit==2?new Vec3(-.0625,.25,0):bit==4?new Vec3(.0625,.25,0):new Vec3(0,.375,0);
            half=bit==1?new Vec3(.25,.25,.25):bit==32?new Vec3(.25,.375,.125):new Vec3(.125,.375,.125);
            float yaw=net.minecraft.util.Mth.rotLerp(partial,entity.yBodyRotO,entity.yBodyRot);
            float angle=(float)Math.toRadians(180-yaw);
            rotation=new Quaternionf().rotationY(angle).rotateZ((float)Math.PI).rotateZYX(source.zRot,source.yRot,source.xRot);
            oldRotation=new Quaternionf(rotation);
            Vec3 pivot=new Vec3(-source.x/16,1.501-source.y/16,source.z/16).yRot(angle).add(entity.getPosition(partial));
            Vector3f offset=rotation.transform(new Vector3f((float)center.x,(float)center.y,(float)center.z));
            pos=pivot.add(offset.x,offset.y,offset.z);old=pos;
            double side=bit==2||bit==8?-1:bit==4||bit==16?1:0;
            Vec3 outward=Vec3.directionFromRotation(0,yaw+90).scale(side*.22);
            velocity=ImpactRagdoll.cutVelocity(entity,bit).add(outward.scale(.35)).add(0,bit==1?.20:.12,0);
            if(velocity.lengthSqr()>2.25)velocity=velocity.normalize().scale(1.5);
            spin=new Vector3f(.12F+(bit%3)*.06F,(float)side*.12F,.10F+(bit%5)*.03F);
            // Start clear of a nearby floor, including fragments detached from a prone corpse.
            floorSupport();old=pos;
        }
        Vec3 extent(){
            var m=new org.joml.Matrix3f().rotation(rotation);
            return new Vec3(Math.abs(m.m00())*half.x+Math.abs(m.m10())*half.y+Math.abs(m.m20())*half.z,
                    Math.abs(m.m01())*half.x+Math.abs(m.m11())*half.y+Math.abs(m.m21())*half.z,
                    Math.abs(m.m02())*half.x+Math.abs(m.m12())*half.y+Math.abs(m.m22())*half.z);
        }
        void floorSupport(){
            Vec3 ext=extent();
            var hit=level.clip(new ClipContext(pos.add(0,.7,0),pos.add(0,-ext.y-.03,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if(hit.getType()==HitResult.Type.BLOCK&&hit.getDirection()==net.minecraft.core.Direction.UP&&pos.y-ext.y<hit.getLocation().y)
                pos=new Vec3(pos.x,hit.getLocation().y+ext.y+.002,pos.z);
        }
        void tick(){
            old=pos;oldRotation=new Quaternionf(rotation);if(still>=12)return;
            boolean grounded=false;
            for(int sub=0;sub<2;sub++){
                rotation.rotateXYZ(spin.x*.5F,spin.y*.5F,spin.z*.5F).normalize();floorSupport();
                Vec3 ext=extent();boolean water=!level.getFluidState(BlockPos.containing(pos)).isEmpty();
                velocity=velocity.scale(water?.82:.99).add(0,water?-.005:-.04,0);
                Vec3 requested=velocity.scale(.5);
                Vec3 allowed=Entity.collideBoundingBox(null,requested,new AABB(pos.subtract(ext),pos.add(ext)),level,List.of());
                pos=pos.add(allowed);
                boolean x=Math.abs(allowed.x-requested.x)>1e-5,y=Math.abs(allowed.y-requested.y)>1e-5,z=Math.abs(allowed.z-requested.z)>1e-5;
                if(x||y||z){
                    grounded|=y&&requested.y<0;
                    double bounce=velocity.lengthSqr()>.035?.22:0;
                    velocity=new Vec3(x?-velocity.x*bounce:velocity.x*.65,y?-velocity.y*bounce:velocity.y*.65,z?-velocity.z*bounce:velocity.z*.65);
                    spin.mul(.60F);
                }
            }
            still=grounded&&velocity.lengthSqr()<.002&&spin.lengthSquared()<.002?still+1:0;
        }
        void render(PoseStack stack,MultiBufferSource buffers,Vec3 camera,float partial){
            Vec3 at=old.lerp(pos,partial).subtract(camera);stack.pushPose();
            try{
                stack.translate(at.x,at.y,at.z);stack.mulPose(new Quaternionf(oldRotation).slerp(rotation,partial));
                stack.translate(-center.x,-center.y,-center.z);
                model.render(stack,buffers.getBuffer(RenderType.entityCutoutNoCull(texture)),LevelRenderer.getLightColor(level,BlockPos.containing(pos)),OverlayTexture.NO_OVERLAY);
            }finally{stack.popPose();}
        }
    }
    static WoundBlood.Anchor woundAnchor(LivingEntity entity,int region,Vec3 point,Vec3 direction){
        for(int i=FRAGMENTS.size()-1;i>=0;i--){
            var f=FRAGMENTS.get(i);if(f.owner!=entity||f.region!=region)continue;
            Vec3 local=WoundBlood.rotate(new Vec3(-point.x,1.501-point.y,point.z).subtract(f.bindPivot),f.inverseBind);
            Vec3 normal=WoundBlood.rotate(new Vec3(-direction.x,-direction.y,direction.z),f.inverseBind);
            return new WoundBlood.Anchor(f.pos.add(WoundBlood.rotate(local.subtract(f.center),f.rotation)),WoundBlood.rotate(normal,f.rotation),f.velocity);
        }
        return null;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.level!=scene){FRAGMENTS.clear();SEEN.clear();scene=mc.level;}
        if(mc.level==null||mc.isPaused())return;
        FRAGMENTS.removeIf(f->f.level!=mc.level||mc.level.getGameTime()-f.started>LIFETIME||mc.player==null||mc.player.position().distanceToSqr(f.pos)>64*64||!mc.level.hasChunkAt(BlockPos.containing(f.pos)));
        for(var fragment:FRAGMENTS)fragment.tick();
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for(var fragment:FRAGMENTS)if(fragment.level==mc.level)fragment.render(event.getPoseStack(),mc.renderBuffers().bufferSource(),event.getCamera().getPosition(),partial);
    }
}
