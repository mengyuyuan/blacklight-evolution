package com.codex.blacklight.animation;
import org.junit.jupiter.api.Test;
import org.joml.*;
import static org.junit.jupiter.api.Assertions.*;
class SkeletonEvaluationTest {
 @Test void singlePassMatchesIndependentRecursiveChainsIncludingScaleAndMissingBones(){
  var s=BlacklightSkeleton.get();var pose=new BlacklightPose();
  for(var b:s.bones())if(b.getId()%3!=0){var t=new BoneTransform();pose.getJointTransformData().put(b.getName(),t);t.translation().set(.03F*b.getId(),-.1F,.07F);t.rotation().rotateXYZ(.15F,-.27F,.03F*b.getId());t.scale().set(1.2F,.8F,1.05F);}
  var actual=s.getPoseMatrices(pose);
  for(var b:s.bones())assertTrue(s.getBoundTransformFor(pose,b).equals(actual[b.getId()],.00001F),b.getName());
 }
 @Test void sampledActionsKeepTheSameAttachmentsAndResultsDoNotAlias(){
  var s=BlacklightSkeleton.get();
  for(String action:new String[]{"basic_idle","basic_run","block_throw_two_hold","block_throw_two_release"})for(float t:new float[]{0,.1F,.3F,.7F}){
   var pose=MotionLibrary.pose(action,t,true);var matrices=s.getPoseMatrices(pose);
   for(var b:s.bones())assertTrue(s.getBoundTransformFor(pose,b).equals(matrices[b.getId()],.00001F),action+"/"+b.getName());
   var before=new Matrix4f(matrices[s.handR.getId()]);matrices[s.rootJoint.getId()].zero();assertEquals(before,matrices[s.handR.getId()]);
  }
 }
}
