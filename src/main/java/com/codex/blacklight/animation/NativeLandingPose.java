package com.codex.blacklight.animation;

/** Running impact cushioning owns the torso, while world travel and stride own the feet. */
public final class NativeLandingPose {
    private static final String[] UPPER={"Torso","Chest","Head","Shoulder_R","Arm_R","Elbow_R","Hand_R","Tool_R",
            "Shoulder_L","Arm_L","Elbow_L","Hand_L","Tool_L"};
    private NativeLandingPose() { }
    public static void stripHorizontalRoot(BlacklightPose pose) {
        var root=pose.get("Root");
        if(root!=null){root.translation().x=0;root.translation().z=0;}
    }
    public static BlacklightPose blendUpper(BlacklightPose stride,BlacklightPose cushion,float weight) {
        var result=stride.copy();weight=com.codex.blacklight.forge.WingMath.clamp(weight,0,1);
        if(weight==0)return result;
        for(String joint:UPPER) {
            var landing=cushion.get(joint);if(landing==null)continue;
            var gait=stride.get(joint);
            result.getJointTransformData().put(joint,BoneTransform.blend(gait==null?new BoneTransform():gait,landing,weight));
        }
        return result;
    }
}
