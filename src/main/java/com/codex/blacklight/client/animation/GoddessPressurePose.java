package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.animation.BlacklightSkeleton;
import com.codex.blacklight.combat.BlockThrowMotion;
import com.codex.blacklight.combat.GoddessAttacks;
import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Pressure choreography in character space. The optical surface uses the actual posed joints. */
public final class GoddessPressurePose {
    private record Binding(long tick, EncounterMob owner) {}
    private static final java.util.Map<Player,Binding> owners=new java.util.WeakHashMap<>();

    public static EncounterMob owner(Player player){
        long tick=player.level().getGameTime();
        Binding cached=owners.get(player);
        if(cached!=null && cached.tick()==tick)return cached.owner();
        EncounterMob owner=null;
        for(var b:player.level().getEntitiesOfClass(EncounterMob.class,player.getBoundingBox().inflate(120),
                b->b.goddess.active() && b.goddess.attacks.move()==GoddessAttacks.Move.VACUOLE && b.goddess.attacks.caught())){
            if(player.getUUID().equals(b.goddess.attacks.victim())){ owner=b;break; }
        }
        owners.put(player,new Binding(tick,owner));
        return owner;
    }

    private static float rise(float age,float start,float end){
        float t=Mth.clamp((age-start)/(end-start),0F,1F);
        return t*t*(3-2*t);
    }

    public static BlacklightPose sample(GoddessAttacks attack,float partial){
        float age=attack.age(partial);
        boolean ground=attack.grounded();
        float impact=rise(age,28,31)*(1-rise(age,32,39));
        float brace=rise(age,29,37);
        float fold=rise(age,38,51);
        float letGo=1-rise(age,52,61);
        float shiver=.032F*Mth.sin(age*1.65F)*fold;
        var pose=new BlacklightPose();
        float hip=ground?(.46F*brace+.68F*fold):(.64F*brace+1.02F*fold);
        rotate(pose,"Thigh_R",hip+.37F*impact,0,(ground?.24F:.32F)*brace);
        rotate(pose,"Leg_R",-1.76F*hip,0,0);
        rotate(pose,"Thigh_L",.74F*hip+.28F*brace,0,-.24F*brace);
        rotate(pose,"Leg_L",-1.52F*hip-.2F*brace,0,0);
        float drop=ground?.62F*(1-Mth.cos(hip)):.08F*fold;
        pose.orElseEmpty("Root").translation().set(.04F*impact,-drop,0);
        rotate(pose,"Root",ground?-.12F*fold:.24F*brace-.47F*impact,0,ground?.07F*brace:.27F*impact-.36F*brace);
        rotate(pose,"Torso",-.18F*brace-.24F*fold,.14F*impact-.21F*brace,.13F*impact-.15F*fold);
        rotate(pose,"Chest",-.17F*fold,.19F*brace,-.13F*impact+shiver);
        rotate(pose,"Head",-.09F*brace-.30F*fold,.30F*impact-.28F*brace,.14F*impact);
        rotate(pose,"Arm_R",.52F*brace,0,.62F*brace);
        rotate(pose,"Hand_R",1.1F*brace,0,0);
        rotate(pose,"Arm_L",.65F*brace,0,-.56F*brace);
        rotate(pose,"Hand_L",1.05F*brace,0,0);
        var rig=BlacklightSkeleton.get();
        // One palm goes high to catch the rolling shoulder; the second slides down as the elbows buckle.
        for(String side:new String[]{"R","L"}){
            boolean right=side.equals("R");
            Vector3f palm=BlockThrowMotion.palm(pose,side);
            var wall=new Vector3f((right?1:-1)*(.88F-.51F*fold+shiver),
                    (right?1.62F:1.29F)-drop-.30F*fold,
                    -.19F-(right?.21F:.36F)*fold);
            palm.lerp(wall,rise(age,right?28:32,right?34:39));
            BlacklightBoneIk.reachTwoBone(rig,pose,side,palm,new Vector3f(0,.06F,0));
        }
        if(letGo<1)return BlacklightPose.interpolatePose(new BlacklightPose(),pose,letGo);
        return pose;
    }

    private static void rotate(BlacklightPose pose,String bone,float x,float y,float z){
        pose.orElseEmpty(bone).rotation().rotationXYZ(x,y,z);
    }

    public static Vec3 contact(Player player,BlacklightPose pose,String bone,float yaw,float partial){
        var rig=BlacklightSkeleton.get();
        var local=rig.getBoundTransformFor(pose,rig.searchJointByName(bone)).transformPosition(
                new Vector3f(0,bone.startsWith("Tool")?.06F:0,0));
        return BlockThrowMotion.world(player,local,yaw,partial);
    }
    public static void eyes(net.minecraft.client.Camera camera,float partial){
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(camera.isDetached() || camera.getEntity()!=mc.player || mc.player==null || !mc.options.getCameraType().isFirstPerson()
                || com.codex.blacklight.client.camera.CameraCompatibility.externalOwnsView())return;
        var p=mc.player;var owner=owner(p);if(owner==null)return;
        var a=owner.goddess.attacks;
        var rig=BlacklightSkeleton.get();
        var neutral=new Vector3f(0,.21F,-.07F);
        var compressed=rig.getBoundTransformFor(sample(a,partial),rig.head).transformPosition(new Vector3f(neutral));
        rig.getBoundTransformFor(new BlacklightPose(),rig.head).transformPosition(neutral);
        Vec3 offset=BlockThrowMotion.world(p,compressed,a.captureYaw(),partial).subtract(BlockThrowMotion.world(p,neutral,a.captureYaw(),partial));
        var from=camera.getPosition();var to=from.add(offset);
        var hit=p.level().clip(new net.minecraft.world.level.ClipContext(from,to,net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,p));
        if(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK)to=hit.getLocation().subtract(offset.normalize().scale(.04));
        ((com.codex.blacklight.mixin.client.PrototypeCameraAccess)camera).blacklight$setPosition(to);
    }
    private GoddessPressurePose(){}
}
