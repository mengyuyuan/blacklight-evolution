package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.LocalVisualMotion;
import com.codex.blacklight.animation.BlacklightSkeleton;
import com.codex.blacklight.animation.MotionLibrary;
import com.codex.blacklight.animation.MatrixMotionClip;
import com.codex.blacklight.animation.WallSurfacePose;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.*;

/** Exercises Blacklight's independent matrix sampler, decomposition and skeletal binding. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class TraversalPoseGameTest {
    @GameTest(template="wall_test",timeoutTicks=100)
    public static void clientAndServerEntityIdsCannotShareWallContacts(GameTestHelper h){
        var predicted=net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel());
        var authority=net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel());
        authority.setId(predicted.getId());
        h.assertTrue(predicted.equals(authority),"Fixture must reproduce Minecraft's ID-based entity equality");
        com.codex.blacklight.movement.WallPoseState.set(predicted,com.codex.blacklight.movement.WallTraversal.Mode.RUN,0,-1);
        com.codex.blacklight.movement.WallPoseState.set(authority,com.codex.blacklight.movement.WallTraversal.Mode.CLIMB,1,0);
        h.assertTrue(com.codex.blacklight.movement.WallPoseState.get(predicted).normalZ()==-1,"Authority overwrote predicted normal");
        com.codex.blacklight.movement.WallPoseState.set(authority,com.codex.blacklight.movement.WallTraversal.Mode.NONE,0,0);
        h.assertTrue(com.codex.blacklight.movement.WallPoseState.get(predicted)!=null,"Authority cleared a different entity's contact");
        com.codex.blacklight.movement.WallPoseState.set(predicted,com.codex.blacklight.movement.WallTraversal.Mode.NONE,0,0);
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=200)
    public static void concurrentWallClipLookupIsSafe(GameTestHelper h) throws Exception {
        if(LocalVisualMotion.find("basic_idle").isEmpty()){h.succeed();return;}
        var pool=java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            for(int attempt=0;attempt<12;attempt++){
                var gate=new java.util.concurrent.CountDownLatch(1);
                var tasks=new java.util.ArrayList<java.util.concurrent.Future<MatrixMotionClip>>();
                for(int i=0;i<8;i++)tasks.add(pool.submit(()->{gate.await();return MotionLibrary.clip("basic_wall_run_left");}));
                gate.countDown();var first=tasks.getFirst().get(10,java.util.concurrent.TimeUnit.SECONDS);
                for(var task:tasks)h.assertTrue(task.get(10,java.util.concurrent.TimeUnit.SECONDS)==first,"Concurrent lookup published different/incomplete clips");
                h.assertTrue(first.duration()>0,"Concurrent load lost the clip");
            }
        } finally {pool.shutdownNow();}
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=200)
    public static void wallRunUsesSurfaceCoordinatesAndKeepsHeadOutside(GameTestHelper h){
        if(LocalVisualMotion.find("basic_idle").isEmpty()){h.succeed();return;}
        var armature=BlacklightSkeleton.get();
        String[] ids={"run_up","run_left","run_right","run_diag_left","run_diag_right","climb_up","climb_down","climb_left","climb_right","cling","leap"};
        for(String suffix:ids){
            String id="basic_wall_"+suffix;
            var clip=MotionLibrary.clip(id);
            for(int frame=0;frame<60;frame++){
                var pose=MotionLibrary.pose(id,clip.duration()*frame/60F,false);
                if(suffix.startsWith("run")){
                    var before=armature.getBoundTransformFor(pose,armature.searchJointByName("Head"));
                    var hip=armature.getBoundTransformFor(pose,armature.rootJoint);
                    WallSurfacePose.orientRun(pose,armature,0,1,.3F);
                    var after=armature.getBoundTransformFor(pose,armature.searchJointByName("Head"));
                    var placedHip=armature.getBoundTransformFor(pose,armature.rootJoint);
                    var originalAxis=before.getTranslation(new org.joml.Vector3f()).sub(hip.getTranslation(new org.joml.Vector3f()));
                    var axis=after.getTranslation(new org.joml.Vector3f()).sub(placedHip.getTranslation(new org.joml.Vector3f()));
                    h.assertTrue(Math.abs(placedHip.m31()-armature.rootJoint.getLocalTransform().m31())<.002,"Wall-run height anchor cannot sink below the entry floor: "+id);
                    h.assertTrue(axis.z/axis.length()>.72F,"Torso must stand away from the wall rather than retain the source clip's deep crouch: "+id);
                    h.assertTrue(Math.abs(axis.length()-originalAxis.length())<.002,"Wall correction cannot stretch the torso: "+id);
                }
                WallSurfacePose.clearHead(pose,armature,.3F);
                h.assertTrue(WallSurfacePose.headMinimumZ(pose,armature)>=-.296F,"Head skin crossed wall: "+id+" frame "+frame);
                if(frame==20){
                    float depth=armature.getBoundTransformFor(pose,armature.searchJointByName("Head")).m32();
                    for(float yaw:new float[]{0,60,90,180,270})for(int direction=0;direction<4;direction++){
                        double nx=direction==0?1:direction==1?-1:0,nz=direction==2?1:direction==3?-1:0;
                        var turned=pose.copy();
                        var model=new org.joml.Matrix4f().rotationY((float)Math.toRadians(180-yaw));
                        WallSurfacePose.faceWall(turned,armature,model,nx,nz);
                        var head=new org.joml.Matrix4f(model).mul(armature.getBoundTransformFor(turned,armature.searchJointByName("Head")));
                        h.assertTrue(Math.abs(head.m30()*nx+head.m32()*nz-depth)<.003,"Rendered wall facing reversed with camera yaw: "+id);
                    }
                }
            }
        }
        h.succeed();
    }


    @GameTest(template="wall_test",timeoutTicks=100)
    public static void allInstalledTraversalPosesBindToThePlayerSkeleton(GameTestHelper h){
        if(LocalVisualMotion.find("basic_idle").isEmpty()){h.succeed();return;}
        String[] ids=("idle idle_claw idle_blade idle_hammer idle_whip walk run sprint sprint_fast sprint_fastest "
                +"wall_run_up wall_run_left wall_run_right wall_run_diag_left wall_run_diag_right "
                +"wall_climb_up wall_climb_down wall_climb_left wall_climb_right wall_cling brake sprint_brake turn "
                +"jump_charge jump_rise jump_tactical jump_running fall wall_leap wall_mantle wall_vault "
                +"air_dash air_dash_brake glide_forward glide_backward glide_left glide_right "
                +"land_light land_moving land_heavy land_devastating").split(" ");
        var armature=BlacklightSkeleton.get();
        for(String suffix:ids){
            String id="basic_"+suffix;
            var clip=MotionLibrary.clip(id);
            h.assertTrue(clip.duration()>0,id+" must contain a real interval");
            for(float fraction:new float[]{0,.25F,.5F,.75F,.999F}){
                var pose=MotionLibrary.pose(id,clip.duration()*fraction,false);
                for(String joint:new String[]{"Root","Head","Hand_R","Hand_L","Leg_R","Leg_L"}){
                    var m=armature.getBoundTransformFor(pose,armature.searchJointByName(joint));
                    h.assertTrue(Float.isFinite(m.m30())&&Float.isFinite(m.m31())&&Float.isFinite(m.m32()),id+" invalid joint "+joint);
                    h.assertTrue(Math.abs(m.m30())<4&&Math.abs(m.m31())<4&&Math.abs(m.m32())<4,id+" contains unextracted travel "+joint);
                    if(joint.equals("Root"))h.assertTrue(m.m30()*m.m30()+m.m31()*m.m31()+m.m32()*m.m32()<4,
                            id+" retains source scene placement above the player's collision body");
                }
            }
        }
        h.succeed();
    }
}
