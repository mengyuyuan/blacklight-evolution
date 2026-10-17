package com.codex.blacklight.animation;

/** Capture-backed full-body motion with a small directional steering overlay. */
public final class SovereignFlightMotion {
    public static float smooth(float x){x=Math.clamp(x,0,1);return x*x*(3-2*x);}
    public static BlacklightPose airborne(double time,float speed,float forward,float side,float vertical,float bank,boolean boost) {
        var hover=FlightAnimationClips.sample("hover",time);
        var flight=FlightAnimationClips.sample(boost?"boost":"cruise",time);
        float travel=smooth(Math.clamp(speed/.75F,0,1))*Math.max(0,forward);
        var pose=BlacklightPose.interpolatePose(hover,flight,travel);
        float roll=Math.clamp(bank,-22,22)-side*12;
        pose.orElseEmpty("Root").rotation().premul(new org.joml.Quaternionf().rotationZ((float)Math.toRadians(roll)));
        // Climbing tilts the actor as one frame; it must not hinge the shoulder
        // attachment separately from the torso or flap the player's upper body.
        pose.orElseEmpty("Root").rotation().rotateX((float)Math.toRadians(vertical*5));
        return pose;
    }
    /** Signed climb angle of actual travel, in degrees; stationary hover has no heading. */
    public static float pathPitch(double horizontal,double vertical) {
        if(!Double.isFinite(horizontal)||!Double.isFinite(vertical)||horizontal<0
                ||Math.hypot(horizontal,vertical)<.04)return 0;
        return (float)Math.max(-80,Math.min(80,Math.toDegrees(Math.atan2(vertical,horizontal))));
    }
    /** Rotate the entire flight frame so the chest, attachments and legs climb together. */
    public static void followFlightPath(BlacklightPose pose,float pitch,float speed,float forward,float vertical) {
        float travel=smooth(Math.max(0,Math.min(1,speed/.52F)))*Math.max(0,Math.min(1,forward));
        // Replace the small hover lift with real path pitch as forward flight takes over.
        float degrees=(pitch-vertical*5)*travel;
        pose.orElseEmpty("Root").rotation().premul(new org.joml.Quaternionf().rotationX((float)Math.toRadians(degrees)));
    }
    /** Small muscular response to the actual wing stroke, never camera/entity movement. */
    public static void followWingbeat(BlacklightPose pose,SovereignWingMotion.Pose wings,float weight) {
        float stroke=Math.clamp(((wings.left().strokeDegrees()-wings.right().strokeDegrees())*.5F+32)/28,-1,1)*weight;
        pose.orElseEmpty("Root").translation().y+=stroke*.025F;
        rotate(pose,"Torso",-stroke*.8F);
        rotate(pose,"Chest",stroke*2.4F);
        for(String side:new String[]{"L","R"}) {
            rotate(pose,"Arm_"+side,stroke*4);
            rotate(pose,"Thigh_"+side,-stroke*2);
            rotate(pose,"Leg_"+side,stroke*4);
        }
    }
    private static void rotate(BlacklightPose pose,String bone,float degrees){pose.orElseEmpty(bone).rotation().rotateX((float)Math.toRadians(degrees));}
    /** Follow-through is proportional to acceleration, not an input-key toggle. */
    public static void followMomentum(BlacklightPose pose,WingFlightPresentation.Frame motion,float forward,float weight){
        float drive=motion.thrust()*Math.max(0,forward)*weight;
        float brake=motion.brake()*weight;
        float streamlined=motion.streamline()*Math.max(0,forward)*weight*(1-motion.brake());
        rotate(pose,"Root",-drive*5+brake*9-streamlined*8);
        rotate(pose,"Torso",-drive*2+brake*3);
        for(String side:new String[]{"L","R"}){
            rotate(pose,"Thigh_"+side,drive*5-brake*8);
            rotate(pose,"Leg_"+side,drive*7-brake*10);
            pose.orElseEmpty("Arm_"+side).rotation().rotateZ((float)Math.toRadians((side.equals("R")?1:-1)*(drive*6-brake*10)));
        }
    }
    /** A new transition starts from the visible pose, even if interrupted mid-blend. */
    public static final class Transition {
        private String key;private double changed,lastTime=Double.NEGATIVE_INFINITY;
        private BlacklightPose from,last;private float duration;
        public BlacklightPose apply(String next,double time,float seconds,BlacklightPose target,BlacklightPose initial) {
            if(!Double.isFinite(time))throw new IllegalArgumentException("Non-finite transition time");
            time=Math.max(time,lastTime);lastTime=time;
            if(!next.equals(key)) {from=last==null?initial.copy():last.copy();key=next;changed=time;duration=Math.max(.001F,seconds);}
            last=BlacklightPose.interpolatePose(from,target,smooth((float)(time-changed)/duration));return last.copy();
        }
        public void reset(){key=null;from=last=null;lastTime=Double.NEGATIVE_INFINITY;}
    }
    private SovereignFlightMotion(){}
}
