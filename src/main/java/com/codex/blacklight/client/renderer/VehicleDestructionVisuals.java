package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.model.*;
import com.codex.blacklight.military.vehicle.VehicleDestruction;
import com.codex.blacklight.network.VehicleDestructionPayload;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.util.*;

/** Server receipts drive bounded local wrecks. No block edits, collision damage, inventory or salvage. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class VehicleDestructionVisuals {
    private static final class Scene {
        final VehicleDestructionPayload data;final long start;final Random random;
        final MilitaryVehicleModel ground;final HelicopterModel helicopter;final UavWreckModel uav;final ResourceLocation texture;
        Vec3 origin;int lastTick=-1;double fallSpeed;boolean burst;
        Scene(VehicleDestructionPayload p,long now){
            data=p;start=now;random=new Random(p.seed());origin=new Vec3(p.x(),p.y(),p.z());
            var chassis=VehicleDestruction.Chassis.values()[p.chassis()];
            boolean air=chassis==VehicleDestruction.Chassis.HELICOPTER;
            uav=chassis==VehicleDestruction.Chassis.UAV?new UavWreckModel():null;
            ground=air||uav!=null?null:new MilitaryVehicleModel(chassis.id);helicopter=air?new HelicopterModel():null;
            texture=ResourceLocation.fromNamespaceAndPath("blacklight",uav!=null?"textures/entity/recon_uav_wreck.png":air?"textures/entity/blackwatch_helicopter.png":"textures/entity/military_vehicles_"+(p.blackwatch()?"blackwatch":"military")+".png");
        }
    }
    private record Receipt(int actor,long tick){}
    private static final ArrayDeque<Scene> SCENES=new ArrayDeque<>();
    private static final LinkedHashMap<Receipt,Long> SEEN=new LinkedHashMap<>();
    private static ClientLevel world;
    private static long received;
    public static long received(){return received;}
    public static int active(){return SCENES.size();}
    public static void accept(VehicleDestructionPayload p){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||!p.valid())return;
        if(world!=mc.level){clear();world=mc.level;}
        if(mc.player.position().distanceToSqr(p.x(),p.y(),p.z())>128*128)return;
        var key=new Receipt(p.actor(),p.tick());if(SEEN.containsKey(key))return;
        while(SEEN.size()>=64)SEEN.remove(SEEN.keySet().iterator().next());SEEN.put(key,world.getGameTime());
        while(SCENES.size()>=VehicleDestruction.MAX_SCENES)SCENES.removeFirst();
        SCENES.addLast(new Scene(p,world.getGameTime()));received++;
    }
    private static void clear(){SCENES.clear();SEEN.clear();world=null;}
    @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel()==world)clear();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.level!=world){clear();world=mc.level;}
        if(world==null||mc.isPaused())return;
        long now=world.getGameTime();SEEN.values().removeIf(t->now<t||now-t>VehicleDestruction.wreckTicks(VehicleDestruction.Chassis.UAV)+40);
        SCENES.removeIf(s->now<s.start||now-s.start>=VehicleDestruction.wreckTicks(VehicleDestruction.Chassis.values()[s.data.chassis()]));
        for(var scene:SCENES){int age=(int)(now-scene.start);if(scene.lastTick==age)continue;scene.lastTick=age;
            // An airburst or a removed support block cannot leave a wreck suspended in space.
            scene.fallSpeed=Math.max(-.8,scene.fallSpeed-.04);
            var hit=world.clip(new net.minecraft.world.level.ClipContext(scene.origin.add(0,.05,0),scene.origin.add(0,scene.fallSpeed,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
            if(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK){scene.origin=new Vec3(scene.origin.x,hit.getLocation().y,scene.origin.z);scene.fallSpeed=0;}
            else scene.origin=scene.origin.add(0,scene.fallSpeed,0);
            particles(scene,age);
        }
    }
    private static void particles(Scene s,int age){
        var p=s.data;var random=s.random;var at=s.origin;var kind=VehicleDestruction.Chassis.values()[p.chassis()];float flame=VehicleDestruction.flame(kind,age);
        boolean wet=p.submerged()||!world.getFluidState(BlockPos.containing(at.add(0,1,0))).isEmpty();
        float radius=VehicleDestruction.Chassis.values()[p.chassis()].radius;
        float height=VehicleDestruction.Chassis.values()[p.chassis()].height*.82F;
        if(!s.burst){s.burst=true;
            for(int i=0;i<24;i++){
                double a=random.nextDouble()*Math.PI*2,speed=.15+random.nextDouble()*.45;
                world.addParticle(wet?ParticleTypes.BUBBLE:ParticleTypes.CAMPFIRE_COSY_SMOKE,at.x,at.y+1.2,at.z,Math.cos(a)*speed,.15+random.nextDouble()*.3,Math.sin(a)*speed);
            }
            if(!wet)world.addParticle(ParticleTypes.EXPLOSION_EMITTER,at.x,at.y+height,at.z,0,0,0);
        }
        if(age<VehicleDestruction.smokeTicks(kind)&&age%(s.uav!=null&&age>=534?10:3)==0){
            for(int i=0;i<(age<60?5:2);i++){
                double x=at.x+(random.nextDouble()-.5)*radius*.55,z=at.z+(random.nextDouble()-.5)*radius;
                world.addParticle(wet?ParticleTypes.BUBBLE:ParticleTypes.LARGE_SMOKE,x,at.y+height,z,0,.09+random.nextDouble()*.09,0);
                if(!wet&&flame>0){world.addParticle(ParticleTypes.FLAME,x,at.y+height+random.nextDouble(),z,(random.nextDouble()-.5)*.025,.08*flame,(random.nextDouble()-.5)*.025);}
            }
        }
        if(!wet&&flame>0&&(s.uav==null||age<540)&&age%20==0)world.playLocalSound(at.x,at.y+1,at.z,SoundEvents.FIRE_AMBIENT,SoundSource.HOSTILE,.8F*flame,.75F+random.nextFloat()*.2F,false);
        if(s.uav!=null&&age<24&&age%4==0)for(int i=0;i<4;i++)world.addParticle(ParticleTypes.ELECTRIC_SPARK,at.x,at.y+.5,at.z,(random.nextDouble()-.5)*.3,random.nextDouble()*.25,(random.nextDouble()-.5)*.3);
        if(age==10||age==23)world.playLocalSound(at.x,at.y,at.z,SoundEvents.CHAIN_BREAK,SoundSource.HOSTILE,1F,age==10?.65F:.9F,false);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(world==null||mc.level!=world||SCENES.isEmpty())return;
        var stack=event.getPoseStack();var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for(var s:SCENES){
            var kind=VehicleDestruction.Chassis.values()[s.data.chassis()];float age=world.getGameTime()-s.start+partial,fade=VehicleDestruction.fade(kind,age);if(fade<=0||s.origin.distanceToSqr(camera)>128*128)continue;
            var p=s.data;int light=LevelRenderer.getLightColor(world,BlockPos.containing(s.origin.add(0,1,0)));
            stack.pushPose();stack.translate(s.origin.x-camera.x,s.origin.y-camera.y,s.origin.z-camera.z);stack.mulPose(Axis.YP.rotationDegrees(s.uav!=null?180-p.yaw():p.yaw()));
            var root=s.uav!=null?s.uav.wreckPose(age,p.seed()):s.ground==null?s.helicopter.wreckPose(age,p.seed()):s.ground.wreckPose(age,p.turret(),p.pitch(),p.seed());
            int tint=((int)(fade*255)<<24)|0x65574b;
            var type=RenderType.entityTranslucent(s.texture);root.render(stack,buffers.getBuffer(type),light,OverlayTexture.NO_OVERLAY,tint);buffers.endBatch(type);
            boolean wet=p.submerged()||!world.getFluidState(BlockPos.containing(s.origin.add(0,1,0))).isEmpty();
            float fire=VehicleDestruction.flame(kind,age);
            if(!wet&&fire>0){
                // Native animated fire surfaces remain visible even with reduced particle settings.
                float height=VehicleDestruction.Chassis.values()[p.chassis()].height;
                for(int i=0;i<(s.uav!=null?1:3);i++){
                    stack.pushPose();stack.translate(s.uav!=null?0:(i==1?-.55:.25),height*(i==1?.63:.78),s.uav!=null?0:-1.6+i*1.25);
                    float size=(s.uav!=null?.65F:1.15F)+fire*(s.uav!=null?.25F:.55F);size*=i==1?.8F:1;stack.scale(size,fire*((s.uav!=null?.7F:1.2F)+(age<12?1.1F:0)),size);stack.translate(-.5,0,-.5);
                    mc.getBlockRenderer().renderSingleBlock(net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(),stack,buffers,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
                    stack.popPose();
                }
            }
            if(age<20&&!wet){
                var glow=RenderType.entityTranslucent(ConsumedMobVisuals.WHITE);var out=buffers.getBuffer(glow);
                double radius=VehicleDestruction.Chassis.values()[p.chassis()].radius*(.4+age*.09);
                for(int i=0;i<24;i++){
                    double a=i*Math.PI/12,b=(i+1)*Math.PI/12;
                    GunplayGeometry.rod(stack,out,new Vec3(Math.sin(a)*radius,.2,Math.cos(a)*radius),new Vec3(Math.sin(b)*radius,.2,Math.cos(b)*radius),.08,.08,GunplayGeometry.alpha(0xffb04c,(1-age/20)*.65),LightTexture.FULL_BRIGHT);
                }
                buffers.endBatch(glow);
            }
            stack.popPose();
        }
    }
    private VehicleDestructionVisuals(){}
}
